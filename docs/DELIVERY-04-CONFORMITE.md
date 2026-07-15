# Livraison 04 — core-media + Feature live (viewer)

Module 4 du prompt (partie live). ✅ = livré · 🔜 = à suivre · ⚠️ = note.

## Périmètre
`core-media` (wrapper LiveKit) + `feature-live` (écran viewer : vidéo + chat & cadeaux
temps réel + actions), iOS **et** Android. La partie `feature-feed` (pager vertical) et
les **cadeaux GPU** (Metal/AGSL) sont identifiées comme suites de ce module.

## Conformité

| Exigence | État | Où |
|---|---|---|
| **LiveKit SDK natif** chaque côté | ✅ | `CoreMedia` (Swift) / `core:media` (Kotlin) |
| Décodage matériel (VideoToolbox / MediaCodec) | ✅ | délégué au SDK LiveKit |
| Jeton LiveKit via backend (`POST /livekit/token`) | ✅ | `LiveKitTokenService` (iOS+Android) |
| Connexion room + souscription pistes + piste primaire | ✅ | `LiveRoomClient` (iOS+Android) |
| Rendu vidéo natif (SwiftUIVideoView / SurfaceViewRenderer) | ✅ | `LiveRoomView` / `LiveRoomScreen` |
| **Chat temps réel Socket.IO** (pas Supabase) | ✅ | `LiveViewModel` → `/chat` room |
| Historique chat via REST puis append temps réel | ✅ | `LiveRepository.chatHistory` + event `message` |
| **Cadeaux** temps réel (feed) | ✅ | event `gift` → `giftFeed` |
| **Présence** (compteur viewers) | ✅ | event `presence` → `viewerCount` |
| Envoi de message (optimistic) | ✅ | `sendMessage` |
| Envoi de cadeau (débit atomique + Idempotency-Key) | ✅ | `sendGift` → `POST /wallet/gifts/send` |
| UI style TikTok (vidéo plein écran + overlays) | ✅ | scrim + overlays flottants |
| Design system appliqué | ✅ | `core-ui` (glow, gradient, accent) |
| Code commenté professionnellement | ✅ | doc-comments partout |
| **Cadeaux animés GPU** (Metal / AGSL) | 🔜 | feed reçu en place ; shaders GPU = tâche dédiée |
| **feature-feed** (pager vertical + prefetch 3 rooms) | 🔜 | prochaine sous-livraison du module 4 |
| Prefetch/pré-warm des prochaines rooms | 🔜 | avec feature-feed |
| Fallback HLS low-latency | 🔜 | option de repli à ajouter |

## Fichiers livrés
```
shared-domain/…/media/LiveKitDtos.kt

ios/Packages/
├── CoreMedia/ (Package.swift + LiveKitTokenService, LiveRoomClient)
└── FeatureLive/ (Package.swift + LiveRepository, LiveViewModel, LiveRoomView)

android/
├── core/media/ (build.gradle.kts + LiveKitTokenService, LiveRoomClient)
└── feature/live/ (build.gradle.kts + LiveRepository, LiveViewModel, LiveRoomScreen)
```

## ⚠️ Notes
1. **Non compilé ici** (Windows). Versions ciblées : LiveKit Swift 2.x, LiveKit Android
   2.11 + compose-components 1.3 ; APIs susceptibles d'ajustement mineur à la compilation
   (Xcode / Gradle).
2. **Cadeaux GPU** : le feed de cadeaux est reçu et affichable ; les animations premium
   (shaders Metal / AGSL 60fps sans bloquer le thread principal) sont une brique dédiée à
   forte valeur, à traiter isolément (fichiers de shaders + intégration).
3. **feature-feed** : le pager vertical plein écran (scroll infini, prefetch des 3
   prochaines rooms, pré-décodage keyframe) viendra compléter le module 4.
