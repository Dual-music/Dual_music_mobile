import SwiftUI

/// Gate d'accès à un événement programmé (duel/concert/compétition) — équivalent iOS EXACT du
/// composant Android `ScheduledAccessGate.kt` (lui-même miroir du web `ScheduledAccessGate.tsx`) :
/// bloque l'entrée dans un direct **avant** l'heure programmée (`scheduledAtIso`), et exige un
/// billet si l'événement est déjà en direct mais payant et que le caller n'en a pas encore un.
/// AUCUNE exception pour les acteurs (artiste/manager/admin) tant que l'heure programmée n'est
/// pas atteinte — seul le temps qui passe (ou l'achat du billet dans le cas "déjà en direct,
/// payant") permet de franchir la porte.
///
/// Ne rend rien si l'accès n'est pas bloqué.
public struct ScheduledAccessGateView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let type: String
    private let scheduledAtIso: String?
    private let status: String?
    private let isActor: Bool
    private let hasTicket: Bool
    private let ticketPrice: Double
    private let onPurchase: () async -> Result<Void, Error>
    private let onDismiss: () -> Void

    @State private var now = Date()
    @State private var paying = false
    @State private var purchaseError: String?

    /// - Parameters:
    ///   - type: `"duel"` | `"concert"` | `"competition"` — sélectionne le message `{date}`.
    ///   - scheduledAtIso: date/heure programmée (ISO, UTC) de l'événement, ou `nil`.
    ///   - status: statut courant de l'événement (`"live"`, `"ended"`, …).
    ///   - isActor: vrai si le caller est un acteur (artiste/manager/admin) — sans effet sur
    ///     le blocage « pas encore commencé », seulement sur l'exigence de billet.
    ///   - hasTicket: vrai si le caller possède déjà son billet.
    ///   - ticketPrice: prix du billet (crédits) ; `0` = accès gratuit pour les spectateurs.
    ///   - onPurchase: déclenche le VRAI achat de billet côté appelant.
    ///   - onDismiss: retour à la liste catalogue.
    public init(
        type: String,
        scheduledAtIso: String?,
        status: String?,
        isActor: Bool,
        hasTicket: Bool,
        ticketPrice: Double,
        onPurchase: @escaping () async -> Result<Void, Error>,
        onDismiss: @escaping () -> Void
    ) {
        self.type = type
        self.scheduledAtIso = scheduledAtIso
        self.status = status
        self.isActor = isActor
        self.hasTicket = hasTicket
        self.ticketPrice = ticketPrice
        self.onPurchase = onPurchase
        self.onDismiss = onDismiss
    }

    private var startDate: Date? {
        guard let scheduledAtIso, !scheduledAtIso.isEmpty else { return nil }
        return ISO8601DateFormatter().date(from: scheduledAtIso)
            ?? { let f = ISO8601DateFormatter(); f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]; return f.date(from: scheduledAtIso) }()
    }

    private var notStartedYet: Bool {
        guard let startDate else { return false }
        return now < startDate && status != "live" && status != "ended"
    }

    /// Événement déjà en direct, payant, et le spectateur (jamais un acteur) n'a pas payé.
    private var liveRequiresPayment: Bool {
        status == "live" && !isActor && !hasTicket && ticketPrice > 0
    }

    private var blocked: Bool { notStartedYet || liveRequiresPayment }

    /// Le paiement reste proposé même AVANT l'heure programmée (achat anticipé).
    private var canPay: Bool { !isActor && !hasTicket && ticketPrice > 0 }

    public var body: some View {
        if blocked {
            ZStack {
                Color.black.opacity(0.78).ignoresSafeArea()
                VStack(spacing: theme.spacing.md) {
                    Image(systemName: liveRequiresPayment ? "creditcard.fill" : "calendar")
                        .font(.system(size: 34))
                        .foregroundStyle(theme.colors.accent)
                    Text(title)
                        .font(DMFont.pageTitle).bold()
                        .foregroundStyle(theme.colors.foreground)
                        .multilineTextAlignment(.center)
                    Text(message)
                        .font(DMFont.body)
                        .foregroundStyle(theme.colors.mutedForeground)
                        .multilineTextAlignment(.center)
                    if let purchaseError {
                        DMMessage(purchaseError, kind: .error)
                    }
                    if canPay {
                        DMButton("\(s.scheduledPayAccess) · \(Int(ticketPrice)) \(s.creditUnit)", isLoading: paying) { purchase() }
                        Button(s.scheduledBackHome, action: onDismiss)
                            .font(DMFont.caption)
                            .foregroundStyle(theme.colors.mutedForeground)
                    } else {
                        DMButton(s.scheduledBackHome, action: onDismiss)
                    }
                }
                .padding(theme.spacing.xl)
                .frame(maxWidth: 340)
                .background(theme.colors.background, in: RoundedRectangle(cornerRadius: theme.radius.lg, style: .continuous))
            }
            // Ticker « maintenant » (5 s) : la porte s'auto-referme dès que l'heure programmée
            // est atteinte, sans qu'un événement externe ne soit nécessaire.
            .task {
                while !Task.isCancelled {
                    try? await Task.sleep(nanoseconds: 5_000_000_000)
                    now = Date()
                }
            }
        }
    }

    private func purchase() {
        guard !paying else { return }
        paying = true
        purchaseError = nil
        Task {
            let result = await onPurchase()
            paying = false
            if case .failure(let error) = result {
                purchaseError = error.localizedDescription
            }
        }
    }

    private var title: String {
        liveRequiresPayment ? s.scheduledPaidAccessTitle : s.scheduledNotStartedTitle
    }

    private var message: String {
        if liveRequiresPayment { return s.scheduledPaidAccessMsg }
        let template: String
        switch type {
        case "duel": template = s.scheduledNotStartedMsgDuel
        case "competition": template = s.scheduledNotStartedMsgCompetition
        default: template = s.scheduledNotStartedMsgConcert
        }
        return template.replacingOccurrences(of: "{date}", with: isoMinute(scheduledAtIso) ?? "")
    }
}
