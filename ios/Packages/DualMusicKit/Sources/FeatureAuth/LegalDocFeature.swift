import SwiftUI
import CoreUI

/// Document légal affichable en plein écran (conditions d'utilisation ou confidentialité).
public enum LegalKind: Identifiable, Sendable {
    case terms
    case privacy
    public var id: Self { self }
}

/// Un document = un titre + une liste de sections (titre, contenu).
private struct LegalDoc {
    let title: String
    let sections: [(title: String, content: String)]
}

/// Écran **légal intégré** (hors-ligne) : affiche le contenu des Conditions d'utilisation ou
/// de la Politique de confidentialité, dans la langue courante. Le texte est aligné sur les
/// pages web `/terms` et `/privacy` (source de vérité unique côté produit). Miroir de
/// `LegalDocScreen` (Android).
@MainActor
public struct LegalDocView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let kind: LegalKind
    private let onBack: () -> Void

    /// - Parameters:
    ///   - kind: document à afficher.
    ///   - onBack: retour (ferme le document).
    public init(kind: LegalKind, onBack: @escaping () -> Void) {
        self.kind = kind
        self.onBack = onBack
    }

    private var doc: LegalDoc {
        let en = AppStrings.language == .en
        switch kind {
        case .terms: return en ? Self.termsEn : Self.termsFr
        case .privacy: return en ? Self.privacyEn : Self.privacyFr
        }
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: theme.spacing.md) {
                DMButton("← \(s.back)", style: .outline, action: onBack)
                Text(doc.title)
                    .font(DMFont.body).bold()
                    .foregroundStyle(theme.colors.foreground)
                ForEach(Array(doc.sections.enumerated()), id: \.offset) { _, section in
                    DMCard {
                        VStack(alignment: .leading, spacing: theme.spacing.xs) {
                            Text(section.title)
                                .font(DMFont.body).bold()
                                .foregroundStyle(theme.colors.foreground)
                            Text(section.content)
                                .foregroundStyle(theme.colors.mutedForeground)
                        }
                    }
                }
            }
            .padding(theme.spacing.lg)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .dmScreenBackground()
    }

    private static let termsFr = LegalDoc(
        title: "Conditions d'utilisation",
        sections: [
            ("1. Acceptation des conditions", "En accédant à Dual Music, vous acceptez ces conditions. Si vous n'acceptez pas ces conditions, veuillez ne pas utiliser notre service."),
            ("2. Description du service", "Dual Music est une plateforme de compétition musicale permettant aux utilisateurs de voter, d'envoyer des cadeaux et d'interagir avec des artistes."),
            ("3. Inscription et compte", "Vous devez fournir des informations exactes lors de l'inscription. Vous êtes responsable de la sécurité de votre compte."),
            ("4. Monnaie virtuelle et achats", "Les crédits achetés sont non remboursables. Ils ne peuvent être échangés contre de l'argent réel."),
            ("5. Conduite des utilisateurs", "Vous vous engagez à ne pas utiliser la plateforme pour des activités illégales ou nuisibles."),
            ("6. Propriété intellectuelle", "Tout le contenu de Dual Music est protégé par les lois sur la propriété intellectuelle."),
        ]
    )

    private static let termsEn = LegalDoc(
        title: "Terms of Use",
        sections: [
            ("1. Acceptance of Terms", "By accessing Dual Music, you agree to these terms. If you do not accept these terms, please do not use our service."),
            ("2. Service Description", "Dual Music is a music competition platform allowing users to vote, send gifts and interact with artists."),
            ("3. Registration and Account", "You must provide accurate information when registering. You are responsible for the security of your account."),
            ("4. Virtual Currency and Purchases", "Purchased credits are non-refundable. They cannot be exchanged for real money."),
            ("5. User Conduct", "You agree not to use the platform for illegal or harmful activities."),
            ("6. Intellectual Property", "All content on Dual Music is protected by intellectual property laws."),
        ]
    )

    private static let privacyFr = LegalDoc(
        title: "Politique de confidentialité",
        sections: [
            ("1. Collecte des données", "Nous collectons les informations que vous nous fournissez lors de l'inscription et de l'utilisation de nos services."),
            ("2. Utilisation des données", "Vos données sont utilisées pour améliorer nos services, personnaliser votre expérience et vous contacter si nécessaire."),
            ("3. Partage des données", "Nous ne vendons pas vos données. Nous pouvons partager des informations avec des partenaires de confiance."),
            ("4. Sécurité", "Nous prenons des mesures de sécurité pour protéger vos informations personnelles."),
            ("5. Vos droits", "Vous avez le droit d'accéder, de modifier et de supprimer vos données personnelles."),
        ]
    )

    private static let privacyEn = LegalDoc(
        title: "Privacy Policy",
        sections: [
            ("1. Data Collection", "We collect information you provide when registering and using our services."),
            ("2. Data Usage", "Your data is used to improve our services, personalize your experience and contact you if necessary."),
            ("3. Data Sharing", "We do not sell your data. We may share information with trusted partners."),
            ("4. Security", "We take security measures to protect your personal information."),
            ("5. Your Rights", "You have the right to access, modify and delete your personal data."),
        ]
    )
}
