import Foundation
import LocalAuthentication

/// Garde biométrique (FaceID / TouchID) pour les actions sensibles.
///
/// Utilisée avant un **retrait** ou un **changement de PIN wallet** (exigence sécurité).
/// La biométrie s'ajoute par-dessus le PIN de retrait 6 chiffres déjà vérifié côté backend.
public struct BiometricGate {

    public enum BiometricError: Error {
        case unavailable          // pas de capteur / non configuré
        case failed               // échec d'authentification
        case cancelled            // annulé par l'utilisateur
    }

    public init() {}

    /// Type de biométrie disponible (pour adapter le libellé/icone UI).
    public var availableType: LABiometryType {
        let context = LAContext()
        _ = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
        return context.biometryType // .faceID / .touchID / .none
    }

    /// Demande une authentification biométrique.
    /// - Parameter reason: raison affichée à l'utilisateur (ex. « Confirmer le retrait »).
    /// - Throws: `BiometricError` selon l'issue.
    public func authenticate(reason: String) async throws {
        let context = LAContext()
        context.localizedFallbackTitle = "" // pas de repli code (biométrie stricte)

        var authError: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &authError) else {
            throw BiometricError.unavailable
        }
        do {
            let ok = try await context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: reason)
            if !ok { throw BiometricError.failed }
        } catch let laError as LAError where laError.code == .userCancel || laError.code == .appCancel {
            throw BiometricError.cancelled
        } catch {
            throw BiometricError.failed
        }
    }
}
