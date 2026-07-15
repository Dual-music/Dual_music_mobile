package com.dualmusic.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dualmusic.core.ui.components.CreditPill
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.gifts.GiftBurst
import com.dualmusic.core.ui.theme.DualMusicTheme

/**
 * Activité principale de l'app de démonstration.
 *
 * Premier jalon de test sur téléphone : affiche l'identité visuelle Dual Music (thème
 * sombre, dégradé de marque, boutons, pastille de crédits, cadeau animé GPU) — pour
 * vérifier que le design system rend correctement sur un vrai appareil.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DualMusicTheme {
                ShowcaseScreen()
            }
        }
    }
}

@Composable
private fun ShowcaseScreen() {
    val colors = DualMusicTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .verticalScroll(rememberScrollState())
            .padding(DualMusicTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.lg),
    ) {
        Text(
            "Dual Music",
            color = colors.foreground,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = DualMusicTheme.spacing.xxl),
        )
        Text("Aperçu du design system", color = colors.mutedForeground)

        // Cadeau animé (halo GPU) — la signature "wow".
        GiftBurst(symbol = "🎁")

        DMCard {
            Column(
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CreditPill(credits = 1250.0)
                DMButton("Envoyer le cadeau 🎁") { /* démo */ }
                DMButton("Confirmer", style = DMButtonStyle.SECONDARY) { }
                DMButton("Continuer avec Google", style = DMButtonStyle.OUTLINE) { }
            }
        }

        Text(
            "✅ Si tu vois cet écran en violet/rose, l'app tourne sur ton téléphone.",
            color = colors.mutedForeground,
            textAlign = TextAlign.Center,
        )
    }
}
