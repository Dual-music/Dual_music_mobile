package com.dualmusic.feature.giftshop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.components.DMEmptyState
import com.dualmusic.core.ui.components.DMRemoteImage
import com.dualmusic.core.ui.components.formatCredits
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.gift.InventoryItem
import com.dualmusic.domain.model.VirtualGift
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** État de la boutique. */
data class GiftShopUiState(
    val catalog: List<VirtualGift> = emptyList(),
    val inventory: List<InventoryItem> = emptyList(),
    val balance: Double = 0.0,
    val isLoading: Boolean = false,
    val message: String? = null,
)

/**
 * ViewModel de la boutique de cadeaux.
 *
 * @param repository catalogue + inventaire + achat.
 */
class GiftShopViewModel(private val repository: GiftShopRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(GiftShopUiState())
    val uiState: StateFlow<GiftShopUiState> = _uiState.asStateFlow()

    /** Charge catalogue + inventaire. */
    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val catalog = runCatching { repository.catalog() }.getOrDefault(emptyList())
            val inventory = runCatching { repository.inventory() }.getOrDefault(emptyList())
            val balance = repository.balance()
            _uiState.update { it.copy(catalog = catalog, inventory = inventory, balance = balance, isLoading = false) }
        }
    }

    /** Achète un cadeau, puis rafraîchit l'inventaire + le solde. */
    fun buy(gift: VirtualGift) {
        viewModelScope.launch {
            val ok = repository.purchase(gift.id, 1)
            if (ok) {
                val inventory = runCatching { repository.inventory() }.getOrDefault(_uiState.value.inventory)
                val balance = repository.balance()
                _uiState.update { it.copy(inventory = inventory, balance = balance, message = "✅ ${gift.name} acheté !") }
            } else {
                _uiState.update { it.copy(message = com.dualmusic.core.ui.i18n.appStrings.errPurchaseFailed) }
            }
        }
    }
}

/**
 * Boutique de cadeaux : grille du catalogue (visuel emoji + nom + prix en Crédits + achat)
 * façon web, avec un badge « ×N » sur les cadeaux déjà possédés.
 *
 * @param viewModel état + actions.
 */
@Composable
fun GiftShopScreen(viewModel: GiftShopViewModel, onOpenRecharge: () -> Unit = {}) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors

    LaunchedEffect(Unit) { viewModel.load() }

    // Quantité possédée par id de cadeau (pour le badge).
    val ownedByGift = ui.inventory.associate { it.giftId to it.quantity }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .padding(DualMusicTheme.spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
    ) {
        // Solde en haut + accès recharge (comme le web).
        item(span = { GridItemSpan(maxLineSpan) }) {
            DMCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(com.dualmusic.core.ui.i18n.LocalStrings.current.myBalance, color = colors.mutedForeground)
                        Text(
                            formatCredits(ui.balance),
                            color = colors.foreground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                        )
                    }
                    DMButton(com.dualmusic.core.ui.i18n.LocalStrings.current.recharge, onClick = onOpenRecharge)
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs)) {
                Text(
                    "Achète des cadeaux virtuels à envoyer aux artistes pendant les duels.",
                    color = colors.mutedForeground,
                )
                ui.message?.let { Text(it, color = colors.primaryGlow) }
                if (ui.isLoading) CircularProgressIndicator(color = colors.primary)
            }
        }

        if (!ui.isLoading && ui.catalog.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                DMEmptyState(
                    title = "Boutique vide",
                    subtitle = "Les cadeaux seront bientôt disponibles.",
                )
            }
        }

        items(ui.catalog) { gift ->
            GiftCard(gift = gift, owned = ownedByGift[gift.id] ?: 0) { viewModel.buy(gift) }
        }
    }
}

/** Carte d'un cadeau : carré dégradé + emoji, nom, prix (Crédits), bouton Acheter. */
@Composable
private fun GiftCard(gift: VirtualGift, owned: Int, onBuy: () -> Unit) {
    val colors = DualMusicTheme.colors
    DMCard(modifier = Modifier.fillMaxWidth(), padded = false) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Vignette carrée dégradée.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(DualMusicTheme.gradients.primary),
                contentAlignment = Alignment.Center,
            ) {
                GiftVisual(gift)
            }
            // Badge quantité possédée.
            if (owned > 0) {
                Text(
                    "×$owned",
                    color = colors.accentForeground,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(DualMusicTheme.spacing.sm)
                        .clip(RoundedCornerShape(DualMusicTheme.radii.pill))
                        .background(colors.accent)
                        .padding(horizontal = DualMusicTheme.spacing.sm, vertical = 2.dp),
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DualMusicTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs),
        ) {
            Text(gift.name, color = colors.foreground, fontWeight = FontWeight.Bold)
            Text(formatCredits(gift.price), color = colors.accent, fontWeight = FontWeight.Bold)
            DMButton(com.dualmusic.core.ui.i18n.LocalStrings.current.buy, modifier = Modifier.fillMaxWidth(), onClick = onBuy)
        }
    }
}

/**
 * Visuel d'un cadeau : le backend range un EMOJI dans `image_url` (rendu en grand, comme le
 * web). Si c'est exceptionnellement une vraie URL (http…), on la charge en image.
 */
@Composable
private fun GiftVisual(gift: VirtualGift) {
    val raw = gift.imageUrl?.takeIf { it.isNotBlank() } ?: gift.emoji
    if (raw != null && raw.startsWith("http")) {
        DMRemoteImage(url = raw, contentDescription = gift.name, modifier = Modifier.fillMaxSize())
    } else {
        Text(raw ?: "🎁", fontSize = 56.sp)
    }
}
