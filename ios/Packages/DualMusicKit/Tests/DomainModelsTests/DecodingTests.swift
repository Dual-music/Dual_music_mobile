import XCTest
@testable import DomainModels

/// Vérifie que le décodage des DTOs est **tolérant** exactement comme côté Android
/// (`ignoreUnknownKeys` + valeurs par défaut) : c'est la garantie qu'un champ ajouté ou
/// omis par le backend ne fait pas tomber un écran entier.
final class DecodingTests: XCTestCase {

    private let decoder = JSONDecoder()

    // MARK: - Tolérance

    func testDecodesLiveWithMissingOptionalFields() throws {
        let json = Data("""
        { "id": "live-1", "artist_id": "artist-9" }
        """.utf8)

        let live = try decoder.decode(Live.self, from: json)

        XCTAssertEqual(live.id, "live-1")
        XCTAssertEqual(live.artistId, "artist-9")
        XCTAssertEqual(live.viewerCount, 0, "Un compteur absent doit valoir 0, pas échouer")
        XCTAssertEqual(live.status, .live, "Statut par défaut d'un live")
        XCTAssertEqual(live.liveKitRoom, "live:live-1", "Repli sur `live:<id>` sans room_id")
    }

    func testIgnoresUnknownKeys() throws {
        let json = Data("""
        { "id": "u1", "full_name": "Ada", "champ_inconnu": 42 }
        """.utf8)

        let profile = try decoder.decode(DisplayProfile.self, from: json)

        XCTAssertEqual(profile.displayName, "Ada")
    }

    func testDecodesUnknownEnumValueWithoutThrowing() throws {
        let json = Data("""
        { "id": "d1", "artist1_id": "a", "artist2_id": "b", "status": "statut_futur" }
        """.utf8)

        let duel = try decoder.decode(Duel.self, from: json)

        XCTAssertEqual(duel.status, .upcoming, "Un statut inconnu retombe sur `upcoming`")
    }

    /// PostgreSQL sérialise fréquemment les colonnes `numeric` en **chaîne** : les montants
    /// doivent rester lisibles dans les deux formes.
    func testDecodesAmountsSentAsStrings() throws {
        let json = Data("""
        { "balance": "1250.50", "eurValue": 625.25 }
        """.utf8)

        let wallet = try decoder.decode(WalletBalance.self, from: json)

        XCTAssertEqual(wallet.balance, 1250.50, accuracy: 0.001)
        XCTAssertEqual(wallet.eurValue, 625.25, accuracy: 0.001)
    }

    // MARK: - Règles métier portées par les modèles

    func testDisplayNamePrefersStageName() {
        let profile = DisplayProfile(id: "1", fullName: "Jean Dupont", stageName: "DJ Neon")
        XCTAssertEqual(profile.displayName, "DJ Neon")
    }

    func testReplayRequiresUnlockOnlyWhenPremiumAndPriced() throws {
        let premiumFree = Data("""
        { "id": "r1", "is_premium": true, "replay_price": 0 }
        """.utf8)
        let premiumPaid = Data("""
        { "id": "r2", "is_premium": true, "replay_price": 25 }
        """.utf8)

        XCTAssertFalse(try decoder.decode(ReplayVideo.self, from: premiumFree).requiresUnlock)
        XCTAssertTrue(try decoder.decode(ReplayVideo.self, from: premiumPaid).requiresUnlock)
    }

    func testUpdateProfileRequestOmitsNilFields() throws {
        let request = UpdateProfileRequest(fullName: "Ada")
        let data = try JSONEncoder().encode(request)
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])

        XCTAssertEqual(json["full_name"] as? String, "Ada")
        XCTAssertNil(json["bio"], "Un champ non modifié ne doit pas être envoyé (sinon il serait effacé)")
        XCTAssertEqual(json.count, 1)
    }
}

/// Vérifie les calculs d'aperçu et les validateurs partagés.
final class EconomyTests: XCTestCase {

    func testCreditsToEuroPreview() {
        XCTAssertEqual(CreditMath.creditsToEurPreview(10), 5.0, accuracy: 0.0001)
    }

    func testEuroToCreditsPreviewFloorsToWholeCredits() {
        XCTAssertEqual(CreditMath.eurToCreditsPreview(5.99), 11, "5,99 € = 11 crédits (plancher)")
    }

    func testWithdrawalNetPreviewRoundsToTwoDecimals() {
        let preview = CreditMath.withdrawalNetPreview(amountCredits: 100, feePct: 12.5)
        XCTAssertEqual(preview.fee, 12.5, accuracy: 0.0001)
        XCTAssertEqual(preview.net, 87.5, accuracy: 0.0001)
    }

    func testPinAndOtpValidation() {
        XCTAssertTrue(Validators.isValidWithdrawalPin("123456"))
        XCTAssertFalse(Validators.isValidWithdrawalPin("12345"))
        XCTAssertFalse(Validators.isValidWithdrawalPin("12345a"))
        XCTAssertTrue(Validators.isValidOtp("098765"))
    }

    func testEmailValidation() {
        XCTAssertTrue(Validators.isValidEmail("a@b.co"))
        XCTAssertFalse(Validators.isValidEmail("a@b"))
        XCTAssertFalse(Validators.isValidEmail("ab.co"))
    }

    func testCountryLookupFallsBackToDefault() {
        XCTAssertEqual(Countries.byCode("CI").name, "Côte d'Ivoire")
        XCTAssertEqual(Countries.byCode("ZZ").code, "FR", "Un code inconnu retombe sur le pays par défaut")
    }
}

/// Vérifie le contrat temps réel (nommage des rooms + décodage des payloads).
final class RealtimeContractTests: XCTestCase {

    func testRoomNameMatchesBackendConvention() {
        XCTAssertEqual(Realtime.roomName(.duel, "123"), "duel:123")
        XCTAssertEqual(Realtime.roomName(.live, "abc"), "live:abc")
    }

    func testDecodesVotePayload() throws {
        let json = Data("""
        { "duel_id": "d1", "artist_id": "a1", "amount": 25 }
        """.utf8)

        let payload = try JSONDecoder().decode(VotePayload.self, from: json)

        XCTAssertEqual(payload.duelId, "d1")
        XCTAssertEqual(payload.artistId, "a1")
        XCTAssertEqual(payload.amount, 25, accuracy: 0.001)
    }
}
