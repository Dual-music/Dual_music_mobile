import Foundation
import AVFoundation
import PhotosUI
import SwiftUI
import UniformTypeIdentifiers

/// Fichier local sélectionné par l'utilisateur, prêt pour l'upload.
///
/// Miroir de `LocalMedia` côté Android.
public struct LocalMedia: Sendable {
    /// Contenu brut (chargé en mémoire — voir la garde de taille à la lecture).
    public let data: Data
    /// Nom d'origine (sert à déduire l'extension côté serveur).
    public let filename: String
    /// Type MIME (ex. `image/jpeg`, `video/mp4`).
    public let contentType: String

    public init(data: Data, filename: String, contentType: String) {
        self.data = data
        self.filename = filename
        self.contentType = contentType
    }

    /// Taille en octets.
    public var size: Int { data.count }

    /// `image` si le MIME commence par `image/`, `video` s'il commence par `video/`,
    /// sinon `nil` (type non supporté par le backend).
    public var mediaKind: String? {
        if contentType.hasPrefix("image/") { return "image" }
        if contentType.hasPrefix("video/") { return "video" }
        return nil
    }
}

/// Erreurs de lecture d'un média local.
public enum LocalMediaError: LocalizedError, Equatable {
    /// Fichier trop volumineux pour être chargé en mémoire.
    case tooLarge(actual: Int, max: Int)
    /// Contenu illisible (permission refusée, format inattendu…).
    case unreadable

    public var errorDescription: String? {
        switch self {
        case let .tooLarge(actual, max):
            return "Fichier trop volumineux (\(actual / (1024 * 1024)) Mo, max \(max / (1024 * 1024)) Mo)."
        case .unreadable:
            return "Fichier illisible."
        }
    }
}

/// Chargement d'un média depuis le sélecteur photo système (`PhotosPicker`).
///
/// Équivalent de `readLocalMedia(context:uri:)` côté Android. Une garde de taille évite les
/// pics mémoire : les octets sont chargés en RAM car le stockage objet exige un `PUT` d'un
/// corps complet vers une URL signée.
public enum PhotoPickerLoader {

    /// Plafond par défaut (100 Mo), aligné sur la garde locale d'Android.
    public static let defaultMaxBytes = 100 * 1024 * 1024

    /// Lit un élément de `PhotosPicker` en ``LocalMedia``.
    ///
    /// - Parameters:
    ///   - item: élément sélectionné.
    ///   - maxBytes: plafond de sécurité (défaut 100 Mo).
    /// - Returns: le média lu, prêt à uploader.
    /// - Throws: ``LocalMediaError``.
    public static func load(_ item: PhotosPickerItem, maxBytes: Int = defaultMaxBytes) async throws -> LocalMedia {
        guard let data = try? await item.loadTransferable(type: Data.self), !data.isEmpty else {
            throw LocalMediaError.unreadable
        }
        guard data.count <= maxBytes else {
            throw LocalMediaError.tooLarge(actual: data.count, max: maxBytes)
        }

        // Type MIME : déduit du type de contenu annoncé par le picker, avec repli robuste
        // sur les magic bytes puis sur `application/octet-stream`.
        let type = item.supportedContentTypes.first
        let mime = type?.preferredMIMEType ?? sniffMIME(data) ?? "application/octet-stream"
        let ext = type?.preferredFilenameExtension ?? (mime.hasPrefix("video/") ? "mp4" : "jpg")
        let name = "upload-\(UUID().uuidString.prefix(8)).\(ext)"

        return LocalMedia(data: data, filename: name, contentType: mime)
    }

    /// Durée d'une vidéo en secondes, bornée à `1…600` (limite backend du sponsoring).
    ///
    /// Écrit temporairement les octets sur disque : `AVURLAsset` a besoin d'une URL. Le
    /// fichier temporaire est supprimé immédiatement après la mesure.
    ///
    /// - Parameter media: média vidéo lu.
    /// - Returns: durée en secondes, ou `nil` si indisponible.
    public static func videoDurationSeconds(_ media: LocalMedia) async -> Int? {
        let tmp = FileManager.default.temporaryDirectory.appendingPathComponent(media.filename)
        defer { try? FileManager.default.removeItem(at: tmp) }
        do {
            try media.data.write(to: tmp, options: .atomic)
            let asset = AVURLAsset(url: tmp)
            let duration = try await asset.load(.duration)
            let seconds = CMTimeGetSeconds(duration)
            guard seconds.isFinite, seconds > 0 else { return nil }
            return min(600, max(1, Int(seconds.rounded())))
        } catch {
            return nil
        }
    }

    /// Détecte le type MIME à partir des premiers octets (repli si le picker ne le donne pas).
    /// - Parameter data: contenu du fichier.
    private static func sniffMIME(_ data: Data) -> String? {
        guard data.count >= 12 else { return nil }
        let bytes = [UInt8](data.prefix(12))
        // JPEG : FF D8 FF
        if bytes[0] == 0xFF, bytes[1] == 0xD8, bytes[2] == 0xFF { return "image/jpeg" }
        // PNG : 89 50 4E 47
        if bytes[0] == 0x89, bytes[1] == 0x50, bytes[2] == 0x4E, bytes[3] == 0x47 { return "image/png" }
        // MP4 / QuickTime : "ftyp" en octets 4..7
        if bytes[4] == 0x66, bytes[5] == 0x74, bytes[6] == 0x79, bytes[7] == 0x70 { return "video/mp4" }
        return nil
    }
}
