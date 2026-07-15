# Livraison 05 — feature-feed (scroll TikTok) + cadeaux GPU

Complète le module 4. ✅ = livré · 🔜 = à suivre · ⚠️ = note.

## Périmètre
`feature-feed` (pager vertical plein écran + prefetch) et les **cadeaux animés GPU**
(shader Metal iOS + AGSL Android), iOS **et** Android.

## Conformité

| Exigence | État | Où |
|---|---|---|
| **Feed vertical plein écran** (une room par page) | ✅ | `FeedView` (ScrollView paging) / `FeedScreen` (VerticalPager) |
| Scroll paginé + chargement à l'approche du bas | ✅ | `loadMoreIfNeeded` / `onPageChanged` |
| **Seule la cellule active** connecte la vidéo | ✅ | cellule active → `LiveRoomView/Screen`, sinon affiche |
| **Prefetch du prochain jeton LiveKit** | ✅ | `FeedViewModel.prewarmAround` (i, i+1) |
| Jeton pré-chauffé consommé au join (latence réduite) | ✅ | `LiveRoomClient.join(prewarmedToken:)` |
| **Cadeaux animés sur GPU** (Metal / AGSL), sans bloquer le main thread | ✅ | `GiftGlow.metal` + `GiftBurstView` / `GiftBurst` (AGSL) |
| GPU distinct du décodage vidéo (60 fps en live) | ✅ | `.layerEffect` (Metal) / `RenderEffect` AGSL (RenderThread) |
| Burst déclenché par le feed de cadeaux temps réel | ✅ | overlay branché sur `giftFeed` (iOS+Android) |
| Repli propre si GPU/shader indisponible | ✅ | Android < API 33 → glyphe simple |
| Design system appliqué (halo violet de marque) | ✅ | teinte `280 70% 55%` dans les shaders |
| Code commenté professionnellement | ✅ | doc-comments + shaders commentés |
| **Pré-décodage keyframe** de la prochaine room | 🔜 | prewarm jeton fait ; pré-décodage vidéo = optimisation avancée à suivre |
| Fallback HLS low-latency | 🔜 | option de repli à ajouter |
| Buffer circulaire cadeaux (coalescing < 300ms) | 🔜 | actuellement 1 burst par cadeau ; file/coalescing à ajouter si débit élevé |

## Fichiers livrés
```
shared-domain/…/model/Live.kt

ios/Packages/
├── CoreMedia/LiveRoomClient.swift          (+ prewarmedToken)
├── CoreUI/
│   ├── Shaders/GiftGlow.metal              (shader Metal stitchable)
│   └── Components/GiftBurstView.swift       (animation + halo GPU)
├── FeatureLive/…                            (+ overlay cadeaux, prewarm)
└── FeatureFeed/ (Package.swift + FeedRepository, FeedViewModel, FeedView)

android/
├── core/media/LiveRoomClient.kt            (+ prewarmedToken)
├── core/ui/gifts/GiftBurst.kt              (RuntimeShader AGSL + repli)
├── feature/live/…                          (+ overlay cadeaux, prewarm)
└── feature/feed/ (build.gradle.kts + FeedRepository, FeedViewModel, FeedScreen)
```

## ⚠️ Notes
1. **Non compilé ici** (Windows). Points à vérifier à la compilation :
   - iOS : le shader `GiftGlow.metal` doit être exposé via `Bundle.module` (SwiftPM compile
     les `.metal` du target ; `ShaderLibrary.bundle(.module)` requiert la génération de
     `Bundle.module`). Vérifier sur Xcode 16.
   - Android : `RuntimeShader` AGSL nécessite **API 33+** (repli fourni). Le rendu
     `SurfaceViewRenderer` + `RenderEffect` sur le pager à valider sur device.
2. **Pré-décodage keyframe** (garder en mémoire vidéo la 1re image de la room suivante) est
   l'optimisation TikTok la plus poussée — le prewarm du **jeton** est déjà en place ; le
   pré-décodage vidéo est une brique dédiée ultérieure.
3. **Coalescing des cadeaux** : à fort débit, remplacer l'affichage « dernier cadeau » par
   un buffer circulaire (32 slots) avec coalescing < 300ms (conforme au doc perf).
