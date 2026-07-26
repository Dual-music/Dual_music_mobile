package com.dualmusic.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.theme.DualMusicTheme

/** Document légal affichable en plein écran (conditions d'utilisation ou confidentialité). */
enum class LegalKind { TERMS, PRIVACY }

/** Un document = un titre + une liste de sections (titre, contenu). */
private data class LegalDoc(val title: String, val sections: List<Pair<String, String>>)

/**
 * Écran **légal intégré** (hors-ligne) : affiche le contenu des Conditions d'utilisation ou de
 * la Politique de confidentialité, dans la langue courante. Le texte est aligné sur les pages
 * web `/terms` et `/privacy` (source de vérité unique côté produit).
 *
 * @param kind document à afficher.
 * @param onBack retour (ferme le document).
 */
@Composable
fun LegalDocScreen(kind: LegalKind, onBack: () -> Unit) {
    val colors = DualMusicTheme.colors
    val strings = LocalStrings.current
    val en = strings.langTag == "en"
    val doc = when (kind) {
        LegalKind.TERMS -> if (en) termsEn() else termsFr()
        LegalKind.PRIVACY -> if (en) privacyEn() else privacyFr()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .verticalScroll(rememberScrollState())
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        DMButton("← ${strings.back}", style = DMButtonStyle.OUTLINE, onClick = onBack)
        Text(doc.title, color = colors.foreground, fontWeight = FontWeight.Bold)
        doc.sections.forEach { (title, content) ->
            DMCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs)) {
                    Text(title, color = colors.foreground, fontWeight = FontWeight.Bold)
                    Text(content, color = colors.mutedForeground)
                }
            }
        }
    }
}

private fun termsFr() = LegalDoc(
    "Conditions d'utilisation",
    listOf(
        "1. Acceptation des conditions" to "En accédant à Dual Music, vous acceptez ces conditions. Si vous n'acceptez pas ces conditions, veuillez ne pas utiliser notre service.",
        "2. Description du service" to "Dual Music est une plateforme de compétition musicale permettant aux utilisateurs de voter, d'envoyer des cadeaux et d'interagir avec des artistes.",
        "3. Inscription et compte" to "Vous devez fournir des informations exactes lors de l'inscription. Vous êtes responsable de la sécurité de votre compte.",
        "4. Monnaie virtuelle et achats" to "Les crédits achetés sont non remboursables. Ils ne peuvent être échangés contre de l'argent réel.",
        "5. Conduite des utilisateurs" to "Vous vous engagez à ne pas utiliser la plateforme pour des activités illégales ou nuisibles.",
        "6. Propriété intellectuelle" to "Tout le contenu de Dual Music est protégé par les lois sur la propriété intellectuelle.",
    ),
)

private fun termsEn() = LegalDoc(
    "Terms of Use",
    listOf(
        "1. Acceptance of Terms" to "By accessing Dual Music, you agree to these terms. If you do not accept these terms, please do not use our service.",
        "2. Service Description" to "Dual Music is a music competition platform allowing users to vote, send gifts and interact with artists.",
        "3. Registration and Account" to "You must provide accurate information when registering. You are responsible for the security of your account.",
        "4. Virtual Currency and Purchases" to "Purchased credits are non-refundable. They cannot be exchanged for real money.",
        "5. User Conduct" to "You agree not to use the platform for illegal or harmful activities.",
        "6. Intellectual Property" to "All content on Dual Music is protected by intellectual property laws.",
    ),
)

private fun privacyFr() = LegalDoc(
    "Politique de confidentialité",
    listOf(
        "1. Collecte des données" to "Nous collectons les informations que vous nous fournissez lors de l'inscription et de l'utilisation de nos services.",
        "2. Utilisation des données" to "Vos données sont utilisées pour améliorer nos services, personnaliser votre expérience et vous contacter si nécessaire.",
        "3. Partage des données" to "Nous ne vendons pas vos données. Nous pouvons partager des informations avec des partenaires de confiance.",
        "4. Sécurité" to "Nous prenons des mesures de sécurité pour protéger vos informations personnelles.",
        "5. Vos droits" to "Vous avez le droit d'accéder, de modifier et de supprimer vos données personnelles.",
    ),
)

private fun privacyEn() = LegalDoc(
    "Privacy Policy",
    listOf(
        "1. Data Collection" to "We collect information you provide when registering and using our services.",
        "2. Data Usage" to "Your data is used to improve our services, personalize your experience and contact you if necessary.",
        "3. Data Sharing" to "We do not sell your data. We may share information with trusted partners.",
        "4. Security" to "We take security measures to protect your personal information.",
        "5. Your Rights" to "You have the right to access, modify and delete your personal data.",
    ),
)
