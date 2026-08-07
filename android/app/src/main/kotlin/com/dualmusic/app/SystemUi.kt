package com.dualmusic.app

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Passe l'écran en PLEIN ÉCRAN IMMERSIF tant que ce composable reste dans la composition :
 * masque les barres système (statut + navigation), garde l'écran allumé, puis restaure tout
 * à la sortie. À utiliser pour les directs (duel/concert/compétition) afin d'obtenir la même
 * expérience plein écran que le web (`fixed inset-0`, sans le chrome de l'application).
 *
 * Les barres restent rappelables par un swipe depuis le bord (BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE).
 */
@Composable
fun ImmersiveFullscreen() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val hadKeepScreenOn = view.keepScreenOn
        controller?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        view.keepScreenOn = true
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = hadKeepScreenOn
        }
    }
}
