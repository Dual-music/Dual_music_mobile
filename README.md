# Dual Music — Mobile (iOS & Android natifs)

Applications mobiles natives de la plateforme **Dual Music** (live streaming musical :
duels, concerts, lives, compétitions, économie virtuelle de cadeaux, chat temps réel).

Deux binaires **100 % natifs** (Swift/SwiftUI + Kotlin/Compose) qui consomment le
backend REST + **Socket.IO** existant (`Dual_music_backend`). Aucun runtime partagé,
aucune WebView pour le contenu principal, aucun Supabase.

## Structure du mono-repo

```
Dual_music_mobile/
├── shared-domain/     # KMM — modèles, DTOs, contrat realtime, calculs d'aperçu (ZÉRO UI/réseau/média)
├── ios/               # Xcode workspace + Swift Packages (:core-*, :feature-*)
│   └── Packages/
│       ├── CoreNetwork/     # client HTTP (URLSession), enveloppe, JWT + refresh
│       └── CoreRealtime/    # client Socket.IO (socket.io-client-swift)
├── android/           # projet Gradle
│   └── core/
│       ├── network/   # client HTTP (Ktor/OkHttp), enveloppe, JWT + refresh
│       └── realtime/  # client Socket.IO (socket.io-client-java)
├── openapi/           # copie de la spec backend → génération des clients
└── docs/              # ARCHITECTURE, DESIGN-TOKENS, PERFORMANCE, SECURITY, RELEASE
```

## Modules livrés à ce stade (fondation)

| Module | Rôle | iOS | Android |
|---|---|---|---|
| `shared-domain` | Modèles, DTOs, enums, **contrat Socket.IO**, aperçus économie, validation | Kotlin/Native (framework) | Kotlin/JVM |
| `core-network` | Client HTTP : enveloppe `{data,meta}`/`{error}`, Bearer JWT, refresh single-flight, `ApiError` typé | `CoreNetwork` (URLSession) | `core:network` (Ktor) |
| `core-realtime` | Client Socket.IO : namespaces `/chat` `/live` `/notifications`, rooms `type:id`, events, reconnect | `CoreRealtime` | `core:realtime` |

## Le backend (source de vérité)

- REST : `https://<api>/api/v1/*` — enveloppe `{ data, meta }` / `{ error:{ code, message } }`.
- Auth : JWT RS256, access 15 min + refresh 30 j.
- Realtime : **Socket.IO** (`/chat`, `/live`, `/notifications`), handshake JWT, rooms `type:id`.
- Spec : `Dual_music_backend/docs/openapi.json` (copiée dans `openapi/`).
- Détails complets : voir `docs/ARCHITECTURE.md` et le contrat dans `shared-domain`.

## Identité visuelle

Le mobile reprend **à l'identique** le design system du web (violet néon + rose + cyan,
thème sombre). Valeurs exactes dans **`docs/DESIGN-TOKENS.md`** — elles alimenteront
`core-ui` (module 2). Objectif : qu'un utilisateur reconnaisse immédiatement la même app.

## Prérequis de build

- **iOS** : macOS + Xcode 16, SwiftPM, Tuist, Fastlane. **Non compilable sous Windows.**
- **Android** : JDK 17, Gradle 8.9 (KTS), AGP 8.6. Buildable sur tout OS.
- **shared-domain** : Kotlin 2.0 (K2), plugin KMP.

> ⚠️ Cette fondation a été **écrite** mais pas compilée dans l'environnement de dev
> actuel (Windows, sans Xcode). Les builds/tests iOS s'exécutent sur un Mac / runner
> macOS CI ; Android via `./gradlew`.
