package com.dualmusic.feature.giftshop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMCard
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
            _uiState.update { it.copy(catalog = catalog, inventory = inventory, isLoading = false) }
        }
    }

    /** Achète un cadeau, puis rafraîchit l'inventaire. */
    fun buy(gift: VirtualGift) {
        viewModelScope.launch {
            val ok = repository.purchase(gift.id, 1)
            if (ok) {
                val inventory = runCatching { repository.inventory() }.getOrDefault(_uiState.value.inventory)
                _uiState.update { it.copy(inventory = inventory, message = "✅ ${gift.name ?: "Cadeau"} acheté !") }
            } else {
                _uiState.update { it.copy(message = "Achat impossible (solde insuffisant ?).") }
            }
        }
    }
}

/**
 * Boutique de cadeaux : catalogue (achat) + inventaire possédé.
 *
 * @param viewModel état + actions.
 */
@Composable
fun GiftShopScreen(viewModel: GiftShopViewModel) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors

    LaunchedEffect(Unit) { viewModel.load() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
    ) {
        item {
            Text("Boutique de cadeaux", color = colors.foreground, fontWeight = FontWeight.Bold)
            ui.message?.let { Text(it, color = colors.primary) }
            if (ui.isLoading) CircularProgressIndicator(color = colors.primary)
        }

        // Inventaire.
        if (ui.inventory.isNotEmpty()) {
            item { Text("Mon inventaire", color = colors.mutedForeground) }
            items(ui.inventory) { inv -> InventoryRow(inv) }
        }

        // Catalogue.
        item { Text("À acheter", color = colors.mutedForeground) }
        items(ui.catalog) { gift -> CatalogRow(gift) { viewModel.buy(gift) } }
    }
}

/** Ligne du catalogue : cadeau + prix + bouton acheter. */
@Composable
private fun CatalogRow(gift: VirtualGift, onBuy: () -> Unit) {
    val colors = DualMusicTheme.colors
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${gift.emoji ?: "🎁"}  ${gift.name}", color = colors.foreground)
            DMButton("${gift.price.toInt()} cr.", onClick = onBuy)
        }
    }
}

/** Ligne d'inventaire : cadeau + quantité possédée. */
@Composable
private fun InventoryRow(item: InventoryItem) {
    val colors = DualMusicTheme.colors
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("🎁  ${item.name ?: "Cadeau"}", color = colors.foreground)
            Text("×${item.quantity}", color = colors.accent, fontWeight = FontWeight.Bold)
        }
    }
}
