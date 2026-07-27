package com.dualmusic.feature.live

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dualmusic.core.media.LiveRoomClient
import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.theme.DualMusicTheme
import io.livekit.android.renderer.SurfaceViewRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Contrôleur de diffusion live (hôte) : connecte la room LiveKit en **mode publication**
 * (caméra + micro), expose la piste locale via [LiveRoomClient], et termine le live
 * (`POST /lives/:id/end`). Cycle de vie piloté par l'écran (start/dispose).
 *
 * @param liveId id du live (pour la clôture).
 * @param roomName room LiveKit à rejoindre.
 * @param media client LiveKit dédié à cette diffusion.
 * @param api client HTTP (clôture).
 */
class LiveBroadcastViewModel(
    private val liveId: String,
    private val roomName: String,
    val media: LiveRoomClient,
    private val api: ApiClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Rejoint la room en hôte et publie caméra + micro. */
    fun start() {
        scope.launch { media.join(roomName, isHost = true) }
    }

    /** Coupe/rétablit le micro. */
    fun toggleMic() {
        scope.launch { media.setMicEnabled(!media.micEnabled.value) }
    }

    /** Termine le live côté backend, quitte la room, puis notifie l'appelant. */
    fun endLive(onDone: () -> Unit) {
        scope.launch {
            runCatching { api.request<Unit>(Endpoint.post("/lives/$liveId/end")) }
            media.leave()
            onDone()
        }
    }

    /** Libère la room + annule le scope (appelé au démontage de l'écran). */
    fun dispose() {
        media.leave()
        scope.cancel()
    }
}

/**
 * Écran plein écran de diffusion live (hôte) : demande caméra/micro, publie la caméra,
 * affiche l'aperçu local + les commandes (micro, terminer). Équivalent mobile de la vue
 * host `/live/:id` du web.
 *
 * @param controller contrôleur de diffusion (créé par le container).
 * @param onExit ferme l'écran (live terminé ou refus de permission).
 */
@Composable
fun LiveBroadcastScreen(controller: LiveBroadcastViewModel, onExit: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val localTrack by controller.media.localVideoTrack.collectAsStateWithLifecycle()
    val micOn by controller.media.micEnabled.collectAsStateWithLifecycle()

    fun hasPerms() =
        context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    var granted by remember { mutableStateOf(hasPerms()) }
    val perms = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result.values.all { it }
    }

    LaunchedEffect(Unit) { if (!granted) launcher.launch(perms) }

    // Démarre la diffusion une fois la permission accordée.
    DisposableEffect(granted) {
        if (granted) controller.start()
        onDispose { }
    }
    // Libère la room au démontage.
    DisposableEffect(Unit) { onDispose { controller.dispose() } }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (!granted) {
            Column(
                modifier = Modifier.fillMaxSize().padding(DualMusicTheme.spacing.xl),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(s.cameraPermissionNeeded, color = Color.White, fontWeight = FontWeight.Bold)
                DMButton(s.liveRetry, modifier = Modifier.fillMaxWidth()) { launcher.launch(perms) }
                DMButton(s.back, style = DMButtonStyle.OUTLINE, modifier = Modifier.fillMaxWidth(), onClick = onExit)
            }
            return@Box
        }

        val track = localTrack
        if (track != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply { controller.media.room.initVideoRenderer(this) }
                },
                update = { renderer -> track.addRenderer(renderer) },
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().background(DualMusicTheme.gradients.hero),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = DualMusicTheme.colors.primary)
                Text(s.liveStarting, color = Color.White)
            }
        }

        // Badge LIVE.
        Text(
            "🔴 LIVE",
            color = Color.White,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(DualMusicTheme.spacing.lg),
        )

        // Commandes (micro / terminer).
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(DualMusicTheme.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
        ) {
            DMButton(
                if (micOn) s.micOn else s.micOff,
                style = DMButtonStyle.OUTLINE,
                modifier = Modifier.weight(1f),
                onClick = { controller.toggleMic() },
            )
            DMButton(s.endLive, modifier = Modifier.weight(1f), onClick = { controller.endLive(onExit) })
        }
    }
}
