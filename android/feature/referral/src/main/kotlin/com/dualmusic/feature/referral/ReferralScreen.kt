package com.dualmusic.feature.referral

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.referral.MyReferrals
import com.dualmusic.domain.referral.ReferralEndpoints
import com.dualmusic.domain.referral.ReferralItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel du parrainage : code + filleuls + réclamation des récompenses.
 *
 * @param api client HTTP.
 */
class ReferralViewModel(private val api: ApiClient) : ViewModel() {

    private val _data = MutableStateFlow(MyReferrals())
    val data: StateFlow<MyReferrals> = _data.asStateFlow()

    /** Charge le parrainage du caller. */
    fun load() {
        viewModelScope.launch {
            runCatching { api.request(Endpoint.get(ReferralEndpoints.ME), MyReferrals.serializer()) }
                .getOrNull()?.let { _data.value = it }
        }
    }

    /** Réclame la récompense d'un parrainage complété, puis recharge. */
    fun claim(id: String) {
        viewModelScope.launch {
            runCatching { api.request<Unit>(Endpoint.post(ReferralEndpoints.claim(id))) }
                .onSuccess { load() }
        }
    }
}

/**
 * Écran de parrainage : code partageable, statistiques, liste des filleuls.
 *
 * @param viewModel source d'état.
 */
@Composable
fun ReferralScreen(viewModel: ReferralViewModel) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {

        // Code + copie.
        DMCard {
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                Text("Ton code de parrainage", color = colors.mutedForeground)
                Text(data.referralCode ?: "—", color = colors.primaryGlow, fontWeight = FontWeight.Bold)
                data.referralCode?.let { code ->
                    DMButton("Copier le code", style = DMButtonStyle.OUTLINE) { copyToClipboard(context, code) }
                }
            }
        }

        // Récompenses en attente.
        if (data.stats.pendingRewardCredits > 0) {
            DMCard {
                Text(
                    "🎁 ${data.stats.pendingRewardCredits.toInt()} crédits de récompense en attente",
                    color = colors.accent,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Text("Tes filleuls (${data.referrals.size})", color = colors.foreground)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            items(data.referrals) { ref ->
                ReferralRow(ref, rewardCredits = data.stats.rewardCredits) { viewModel.claim(ref.id) }
            }
        }
    }
}

/** Ligne d'un filleul : nom + bouton réclamer si récompense disponible. */
@Composable
private fun ReferralRow(ref: ReferralItem, rewardCredits: Double, onClaim: () -> Unit) {
    val colors = DualMusicTheme.colors
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(ref.referred?.displayName ?: "Filleul", color = colors.foreground)
            if (!ref.rewardClaimed) {
                DMButton("Réclamer ${rewardCredits.toInt()}", onClick = onClaim)
            } else {
                Text("✅ Réclamé", color = colors.mutedForeground)
            }
        }
    }
}

/** Copie une chaîne dans le presse-papiers. */
private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Code de parrainage Dual Music", text))
}
