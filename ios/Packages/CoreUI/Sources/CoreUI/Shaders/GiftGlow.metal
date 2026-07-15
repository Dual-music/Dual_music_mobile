#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

/*
 * GiftGlow — halo violet pulsant appliqué au rendu d'un cadeau (effet « wow »).
 *
 * Shader `stitchable` pour SwiftUI (`.layerEffect`). Tourne sur le GPU, sur un thread de
 * rendu distinct du décodage vidéo → 60 fps garantis même pendant un live.
 *
 * Paramètres : la couche source échantillonnée + le temps animé + la taille de la vue.
 */
[[ stitchable ]]
half4 giftGlow(float2 position, SwiftUI::Layer layer, float time, float2 size) {
    half4 color = layer.sample(position);

    float2 center = size * 0.5;
    float radius = max(size.x, size.y) * 0.5;
    float dist = distance(position, center) / radius;      // 0 au centre → 1 au bord

    // Pulsation temporelle (respiration du halo).
    float pulse = 0.5 + 0.5 * sin(time * 6.0);

    // Halo violet de marque (HSL 280 70% 55% ≈ sRGB 0.65, 0.24, 0.86).
    half3 brand = half3(0.65h, 0.24h, 0.86h);
    half3 glow = brand * half(saturate(1.0 - dist)) * half(pulse);

    // Ajout additif pondéré par l'alpha (ne colore que le sujet, pas le fond transparent).
    return half4(color.rgb + glow * color.a, color.a);
}
