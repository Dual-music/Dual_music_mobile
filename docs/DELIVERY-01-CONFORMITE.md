# Livraison 01 — Fondation (shared-domain + core-network + core-realtime)

Rapport de conformité contre le prompt mobile. ✅ = livré · 🔜 = module ultérieur · ⚠️ = note.

## Périmètre de cette livraison
Fondations communes des deux apps : le module partagé `shared-domain` (KMM) + les clients
`core-network` et `core-realtime` sur iOS **et** Android, + le design system capturé.

## Conformité — points couverts maintenant

| Exigence du prompt | État | Où |
|---|---|---|
| Nom **Dual Music** (pas Synergy) | ✅ | tout le repo |
| Mono-repo `ios/` `android/` `shared-domain/` `openapi/` `docs/` | ✅ | structure racine |
| **shared-domain** KMM : modèles, DTOs kotlinx.serialization, enums, validation | ✅ | `shared-domain/…/model,api,validation` |
| shared-domain : ZÉRO UI, ZÉRO réseau, ZÉRO média | ✅ | (aucune dép. UI/réseau) |
| shared-domain : calculs d'**aperçu** seulement, argent = backend | ✅ | `economy/CreditMath.kt` (doc explicite) |
| **Realtime = Socket.IO** (PAS Supabase) | ✅ | `realtime/RealtimeContract.kt`, `core-realtime` |
| Contrat realtime : namespaces `/chat` `/live` `/notifications`, rooms `type:id`, events | ✅ | `RealtimeContract` (KMM + miroir Swift) |
| **core-network** : enveloppe `{data,meta}`/`{error}` décodée une fois | ✅ | `HTTPClient.swift`, `ApiClient.kt` |
| core-network : Bearer JWT + **refresh single-flight** sur 401 | ✅ | iOS `actor` / Android `Mutex` |
| core-network : `ApiError`/`DomainError` typé branché sur `code` | ✅ | `APIError.swift`, `DomainError.kt` |
| core-network : `Idempotency-Key` pour les débits | ✅ | `Endpoint` (iOS+Android) |
| core-realtime : handshake JWT, join/leave rooms, écoute typée, reconnect+jitter | ✅ | `RealtimeClient` (iOS+Android) |
| Génération client depuis **OpenAPI** | ⚠️ | spec présente (`Dual_music_backend/docs/openapi.json`) ; DTOs modélisés à la main pour le cœur + à générer au fil des features |
| **Stack** : Swift 5.10 strict concurrency / Kotlin 2.0 K2, Ktor, socket.io SDKs | ✅ | `Package.swift`, `build.gradle.kts` |
| **Identité visuelle = web** (couleurs/thème identiques) | ✅ | `docs/DESIGN-TOKENS.md` (palette web exacte + helpers SwiftUI/Compose) |
| Code commenté professionnellement (reprise dev facile) | ✅ | doc-comments sur chaque fichier/fonction publique |
| Tests (patron ≥ 80 % domain) | ✅ (amorce) | `SharedDomainTest.kt` |

## Modules 🔜 (livraisons suivantes, dans l'ordre du prompt)
- 2. `core-ui` (design system violet néon/rose depuis DESIGN-TOKENS)
- 3. Feature auth (téléphone+OTP, Google, biométrie)
- 4. Feature feed + live (LiveKit + chat + cadeaux GPU)
- 5. duel/concert/competition · 6. wallet/payments (**IAP/Play Billing**) · 7. profile/artist/manager/moderation
- 8. notifications (APNs/FCM) · 9. observabilité · 10. CI/CD Fastlane

## ⚠️ Notes de conformité honnêtes
1. **Non compilé ici** : environnement Windows sans Xcode. Le code iOS est écrit selon
   l'API `socket.io-client-swift` 16.x et Swift Concurrency, mais **doit être compilé/testé
   sur macOS** (Xcode 16). Android est prêt pour `./gradlew` mais non exécuté ici.
2. **HTTP/3** : non activé côté client à ce stade (sujet infra/CDN — voir prompt). Les
   clients utilisent HTTP/1.1-2 via URLSession/OkHttp ; l'upgrade h3 se fera au
   déploiement (proxy/CDN devant le backend).
3. **Paiements** : rappel — l'achat de crédits devra passer par **StoreKit 2 / Play
   Billing** (module 6). L'enum `RechargeProvider` le prévoit déjà.
4. **Backend à étendre** (au moment des modules concernés) : endpoints de vérification de
   reçu IAP/Play (crédit du wallet) + enregistrement de tokens APNs/FCM.

## Arbre livré
```
Dual_music_mobile/
├── README.md · .gitignore
├── docs/ (ARCHITECTURE, DESIGN-TOKENS, DELIVERY-01-CONFORMITE)
├── shared-domain/ (KMM)
│   ├── build.gradle.kts
│   └── src/commonMain/kotlin/com/dualmusic/domain/
│       ├── api/ (Envelope.kt, DomainError.kt)
│       ├── model/ (Enums.kt, Models.kt)
│       ├── realtime/ (RealtimeContract.kt)
│       ├── economy/ (CreditMath.kt)
│       └── validation/ (Validators.kt)
│   └── src/commonTest/… (SharedDomainTest.kt)
├── ios/Packages/
│   ├── CoreNetwork/  (Package.swift + APIError, Envelope, TokenStore, Endpoint, HTTPClient)
│   └── CoreRealtime/ (Package.swift + RealtimeContract, RealtimeClient)
└── android/core/
    ├── network/  (build.gradle.kts + TokenStore, Endpoint, ApiClient)
    └── realtime/ (build.gradle.kts + RealtimeClient)
```
