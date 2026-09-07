import Foundation
import Security
import CoreNetwork

/// Stockage sécurisé des jetons JWT dans le **Keychain iOS** — équivalent de
/// `EncryptedTokenStore` (EncryptedSharedPreferences + StrongBox) côté Android.
///
/// Implémente `CoreNetwork.TokenStore`. Les jetons sont écrits avec l'attribut
/// `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` :
/// - disponibles après le premier déverrouillage (le refresh en tâche de fond fonctionne) ;
/// - **non synchronisés iCloud** et **non restaurables** sur un autre appareil.
///
/// Implémenté en `actor` : accès concurrent sûr depuis l'acteur réseau.
public actor KeychainTokenStore: TokenStore {

    private let service: String
    private let accessKey = "dm.access_token"
    private let refreshKey = "dm.refresh_token"

    /// - Parameter service: identifiant de service Keychain (par défaut, le bundle d'auth).
    public init(service: String = "com.dualmusic.app.auth") {
        self.service = service
    }

    // MARK: TokenStore

    public func accessToken() async -> String? { read(accessKey) }

    public func refreshToken() async -> String? { read(refreshKey) }

    public func setTokens(access: String, refresh: String) async {
        write(access, for: accessKey)
        write(refresh, for: refreshKey)
    }

    public func clear() async {
        delete(accessKey)
        delete(refreshKey)
    }

    // MARK: Primitives Keychain

    private func query(_ account: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }

    private func read(_ account: String) -> String? {
        var q = query(account)
        q[kSecReturnData as String] = true
        q[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        guard SecItemCopyMatching(q as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private func write(_ value: String, for account: String) {
        // Upsert : supprimer puis ajouter (évite les collisions d'attributs).
        SecItemDelete(query(account) as CFDictionary)
        var attrs = query(account)
        attrs[kSecValueData as String] = Data(value.utf8)
        attrs[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(attrs as CFDictionary, nil)
    }

    private func delete(_ account: String) {
        SecItemDelete(query(account) as CFDictionary)
    }
}
