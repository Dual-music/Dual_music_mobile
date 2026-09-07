import XCTest
@testable import CoreNetwork
import DomainModels

/// Tests du client HTTP avec un `URLProtocol` factice : aucun réseau réel n'est joint.
///
/// Ce qui est vérifié ici est précisément ce qui casse en production quand c'est faux :
/// préfixe d'URL, en-têtes d'authentification et d'idempotence, décodage de l'enveloppe,
/// traduction des erreurs, et **refresh single-flight** sur 401.
final class HTTPClientTests: XCTestCase {

    private var session: URLSession!

    override func setUp() {
        super.setUp()
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [StubURLProtocol.self]
        session = URLSession(configuration: config)
        StubURLProtocol.reset()
    }

    override func tearDown() {
        StubURLProtocol.reset()
        session = nil
        super.tearDown()
    }

    // MARK: - Construction de requête

    func testPrefixesPathWithApiV1AndAttachesBearer() async throws {
        StubURLProtocol.handler = { _ in
            (200, Data(#"{"data":{"balance":10,"eurValue":5}}"#.utf8))
        }
        let client = makeClient(access: "jwt-123")

        _ = try await client.request(.get(WalletEndpoints.balance), as: WalletBalance.self)

        let request = try XCTUnwrap(StubURLProtocol.lastRequest)
        XCTAssertEqual(request.url?.path, "/api/v1/wallet")
        XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer jwt-123")
    }

    func testAnonymousRequestOmitsBearer() async throws {
        StubURLProtocol.handler = { _ in (200, Data(#"{"data":[]}"#.utf8)) }
        let client = makeClient(access: "jwt-123")

        _ = try await client.request(.get("/payments/cinetpay/countries", anonymous: true), as: [String].self)

        XCTAssertNil(StubURLProtocol.lastRequest?.value(forHTTPHeaderField: "Authorization"))
    }

    func testSendsIdempotencyKeyForDebits() async throws {
        StubURLProtocol.handler = { _ in (204, Data()) }
        let client = makeClient(access: "jwt")

        try await client.send(
            .post(WalletEndpoints.vote,
                  body: VoteRequest(duelId: "d", artistId: "a", amount: 10),
                  idempotencyKey: "key-42")
        )

        XCTAssertEqual(StubURLProtocol.lastRequest?.value(forHTTPHeaderField: "Idempotency-Key"), "key-42")
    }

    // MARK: - Enveloppe & erreurs

    func testUnwrapsDataField() async throws {
        StubURLProtocol.handler = { _ in
            (200, Data(#"{"data":{"balance":1250.5,"eurValue":625.25},"meta":{"requestId":"r1"}}"#.utf8))
        }
        let client = makeClient(access: "jwt")

        let wallet = try await client.request(.get(WalletEndpoints.balance), as: WalletBalance.self)

        XCTAssertEqual(wallet.balance, 1250.5, accuracy: 0.001)
    }

    func testTranslatesErrorEnvelopeIntoAPIError() async {
        StubURLProtocol.handler = { _ in
            (402, Data(#"{"error":{"code":"WALLET_INSUFFICIENT","message":"Solde insuffisant"}}"#.utf8))
        }
        let client = makeClient(access: "jwt")

        do {
            _ = try await client.request(.get(WalletEndpoints.balance), as: WalletBalance.self)
            XCTFail("Une APIError était attendue")
        } catch let error as APIError {
            XCTAssertEqual(error.code, ErrorCode.walletInsufficient)
            XCTAssertTrue(error.isInsufficientBalance)
            XCTAssertFalse(error.isRetriable)
        } catch {
            XCTFail("Type d'erreur inattendu : \(error)")
        }
    }

    func testTreats204AsSuccessWithoutBody() async throws {
        StubURLProtocol.handler = { _ in (204, Data()) }
        let client = makeClient(access: "jwt")

        try await client.send(.post(NotificationEndpoints.readAll))
    }

    // MARK: - Refresh single-flight

    /// Deux requêtes qui échouent en 401 en parallèle ne doivent déclencher **qu'un seul**
    /// refresh : sinon le backend invalide la chaîne de refresh tokens et déconnecte tout le
    /// monde. C'est le comportement le plus coûteux à diagnostiquer en production.
    func testConcurrent401sTriggerSingleRefresh() async throws {
        let refresher = CountingRefresher()
        let store = InMemoryTokenStore(access: "expired", refresh: "refresh-token")

        StubURLProtocol.handler = { request in
            // Le premier appel de chaque requête échoue ; après refresh, le Bearer change.
            let isRefreshed = request.value(forHTTPHeaderField: "Authorization") == "Bearer new-access"
            return isRefreshed
                ? (200, Data(#"{"data":{"balance":1,"eurValue":0.5}}"#.utf8))
                : (401, Data(#"{"error":{"code":"UNAUTHORIZED","message":"expired"}}"#.utf8))
        }

        let client = HTTPClient(baseURL: Self.baseURL, tokenStore: store, refresher: refresher, session: session)

        async let first = client.request(.get(WalletEndpoints.balance), as: WalletBalance.self)
        async let second = client.request(.get(WalletEndpoints.balance), as: WalletBalance.self)
        _ = try await (first, second)

        let count = await refresher.count
        XCTAssertEqual(count, 1, "Un seul refresh doit partir pour deux 401 concurrents")
    }

    func testFailedRefreshClearsSessionAndThrowsUnauthorized() async {
        let store = InMemoryTokenStore(access: "expired", refresh: nil) // aucun refresh possible
        StubURLProtocol.handler = { _ in (401, Data(#"{"error":{"code":"UNAUTHORIZED","message":"expired"}}"#.utf8)) }
        let client = HTTPClient(baseURL: Self.baseURL, tokenStore: store, refresher: CountingRefresher(), session: session)

        do {
            _ = try await client.request(.get(WalletEndpoints.balance), as: WalletBalance.self)
            XCTFail("Une APIError 401 était attendue")
        } catch let error as APIError {
            XCTAssertTrue(error.isAuthExpired)
            let remaining = await store.accessToken()
            XCTAssertNil(remaining, "La session locale doit être effacée")
        } catch {
            XCTFail("Type d'erreur inattendu : \(error)")
        }
    }

    // MARK: - Utilitaires

    private static let baseURL = URL(string: "https://api.test")!

    private func makeClient(access: String?) -> HTTPClient {
        HTTPClient(
            baseURL: Self.baseURL,
            tokenStore: InMemoryTokenStore(access: access, refresh: "refresh"),
            refresher: CountingRefresher(),
            session: session
        )
    }
}

/// Refresher factice qui compte ses appels et renvoie toujours un nouveau jeton.
private actor CountingRefresher: TokenRefresher {
    private(set) var count = 0

    func refresh(using refreshToken: String) async throws -> RefreshedTokens {
        count += 1
        return RefreshedTokens(access: "new-access", refresh: "new-refresh")
    }
}

/// `URLProtocol` de test : intercepte toutes les requêtes de la session.
private final class StubURLProtocol: URLProtocol {

    /// Renvoie (statut, corps) pour une requête donnée.
    nonisolated(unsafe) static var handler: ((URLRequest) -> (Int, Data))?
    /// Dernière requête interceptée (assertions sur les en-têtes/URL).
    nonisolated(unsafe) static var lastRequest: URLRequest?

    static func reset() {
        handler = nil
        lastRequest = nil
    }

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        Self.lastRequest = request
        let (status, body) = Self.handler?(request) ?? (200, Data())
        let response = HTTPURLResponse(
            url: request.url!,
            statusCode: status,
            httpVersion: "HTTP/1.1",
            headerFields: ["Content-Type": "application/json"]
        )!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: body)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}
