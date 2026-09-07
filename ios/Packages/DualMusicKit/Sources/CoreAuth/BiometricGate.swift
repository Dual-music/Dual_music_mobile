import Foundation
import LocalAuthentication

/// Garde biométrique (Face ID / Touch ID) pour les actions sensibles.
///
/// Utilisée avant un **retrait** ou un **changement de PIN wallet**. Elle s'ajoute
/// par-dessus le PIN de retrait 6 chiffres déjà vérifié côté backend : la biométrie protège
/// l'appareil, le PIN protège le compte. Miroir de `BiometricGate` côté Android.
///
/// > Important : l'app doit déclarer `NSFaceIDUsageDescription` dans son `Info.plist`,
/// > sinon iOS termine le processus au premier appel Face ID.
public struct BiometricGate {

    /// Issues possibles d'une demande d'authentification.
    public enum BiometricError: Error, Equatable {
        /// Pas de capteur, ou biométrie non configurée sur l'appareil.
        case unavailable
        /// Authentification refusée (mauvais visage/empreinte, trop d'échecs).
        case failed
        /// Annulée par l'utilisateur ou par le système.
        case cancelled
    }

    public init() {}

    /// Type de biométrie disponible (pour adapter le libellé/l'icône dans l'UI).
    public var availableType: LABiometryType {
        let context = LAContext()
        _ = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
        return context.biometryType // .faceID / .touchID / .none
    }

    /// Vrai si une biométrie est configurée et utilisable.
    public var isAvailable: Bool {
        LAContext().canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
    }

    /// Demande une authentification biométrique.
    ///
    /// - Parameter reason: raison affichée à l'utilisateur (ex. « Confirmer le retrait »).
    /// - Throws: ``BiometricError`` selon l'issue.
    public func authenticate(reason: String) async throws {
        let context = LAContext()
        context.localizedFallbackTitle = "" // pas de repli code : biométrie stricte

        var authError: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &authError) else {
            throw BiometricError.unavailable
        }
        do {
            let ok = try await context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: reason)
            if !ok { throw BiometricError.failed }
        } catch let laError as LAError where laError.code == .userCancel || laError.code == .appCancel || laError.code == .systemCancel {
            throw BiometricError.cancelled
        } catch let biometric as BiometricError {
            throw biometric
        } catch {
            throw BiometricError.failed
        }
    }
}
