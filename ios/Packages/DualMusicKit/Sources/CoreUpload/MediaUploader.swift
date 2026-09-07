import Foundation
import CoreNetwork
import DomainModels

/// Upload de média en trois temps (contrat `shared-domain/upload`) :
///  1. `POST /uploads/presign` (via ``CoreNetwork/HTTPClient``, authentifié) → URL signée +
///     clé + URL publique ;
///  2. `PUT` **brut** des octets vers l'URL signée (session dédiée, **sans** Bearer ni
///     préfixe `/api/v1` — c'est du stockage objet, pas l'API) ;
///  3. `POST /uploads/confirm` (best-effort) → URL finale exploitable après validation
///     magic-bytes + scan côté serveur.
///
/// Miroir de `MediaUploader` côté Android.
public struct MediaUploader: Sendable {

    private let http: HTTPClient
    private let rawSession: URLSession

    /// - Parameters:
    ///   - http: client HTTP applicatif (presign/confirm).
    ///   - rawSession: session pour le `PUT` signé. Par défaut, une session dédiée sans
    ///     cache ni cookies et avec un timeout ressource long (gros médias sponsor).
    public init(http: HTTPClient, rawSession: URLSession? = nil) {
        self.http = http
        if let rawSession {
            self.rawSession = rawSession
        } else {
            let config = URLSessionConfiguration.ephemeral
            config.timeoutIntervalForRequest = 60
            config.timeoutIntervalForResource = 600 // vidéos sponsor : jusqu'à 10 min
            self.rawSession = URLSession(configuration: config)
        }
    }

    /// Envoie `media` dans la catégorie donnée et renvoie l'URL publique finale.
    ///
    /// - Parameters:
    ///   - media: fichier local lu (voir ``PhotoPickerLoader/load(_:maxBytes:)``).
    ///   - category: catégorie serveur (`UploadCategory.avatar`, `.image`, `.sponsor`…) —
    ///     elle détermine le bucket et les contraintes de taille/MIME côté backend.
    /// - Returns: l'URL publique à persister sur la ressource (pochette, `mediaUrl`…).
    /// - Throws: ``CoreNetwork/APIError`` (presign/confirm) ou ``UploadError`` (PUT).
    public func upload(_ media: LocalMedia, category: String) async throws -> String {
        // 1. Presign.
        let presign: PresignResult = try await http.request(
            .post(
                UploadEndpoints.presign,
                body: PresignRequest(
                    category: category,
                    filename: media.filename,
                    contentType: media.contentType,
                    size: media.size
                )
            ),
            as: PresignResult.self
        )

        // 2. PUT brut des octets vers l'URL signée.
        guard let uploadURL = URL(string: presign.uploadUrl) else {
            throw UploadError.invalidUploadURL
        }
        var request = URLRequest(url: uploadURL)
        request.httpMethod = "PUT"
        request.setValue(media.contentType, forHTTPHeaderField: "Content-Type")

        let (_, response): (Data, URLResponse)
        do {
            (_, response) = try await rawSession.upload(for: request, from: media.data)
        } catch {
            throw UploadError.transferFailed(error.localizedDescription)
        }
        guard let http200 = response as? HTTPURLResponse, (200...299).contains(http200.statusCode) else {
            let status = (response as? HTTPURLResponse)?.statusCode ?? 0
            throw UploadError.rejected(status: status)
        }

        // 3. Confirm (best-effort : le serveur valide les magic bytes et scanne le fichier).
        let confirmed: ConfirmResult? = try? await http.request(
            .post(UploadEndpoints.confirm, body: ConfirmRequest(key: presign.key)),
            as: ConfirmResult.self
        )

        guard let url = confirmed?.publicUrl ?? confirmed?.url ?? presign.publicUrl else {
            throw UploadError.noPublicURL
        }
        return url
    }
}

/// Erreurs propres à l'étape de transfert vers le stockage objet.
public enum UploadError: LocalizedError, Equatable {
    /// L'URL signée renvoyée par le backend est invalide.
    case invalidUploadURL
    /// Le transfert a échoué (réseau).
    case transferFailed(String)
    /// Le stockage a refusé l'objet (signature expirée, taille, MIME…).
    case rejected(status: Int)
    /// Upload confirmé mais aucune URL publique exploitable retournée.
    case noPublicURL

    public var errorDescription: String? {
        switch self {
        case .invalidUploadURL:
            return "URL d'upload invalide."
        case .transferFailed(let message):
            return "Échec de l'envoi du fichier : \(message)"
        case .rejected(let status):
            return "Échec de l'envoi du fichier (HTTP \(status))."
        case .noPublicURL:
            return "Upload confirmé mais aucune URL publique retournée."
        }
    }
}
