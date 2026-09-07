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
├── ios/               # App SwiftUI + DualMusicKit (26 modules Swift)
│   ├── project.yml    #   projet Xcode GÉNÉRÉ par XcodeGen (pas de .xcodeproj committé)
│   ├── App/           #   coque : entrée, DI, navigation, push, Google Sign-In
│   ├── Packages/DualMusicKit/   # DomainModels, Core*, Feature* (+ tests)
│   └── fastlane/      #   tests, TestFlight, App Store
├── android/           # projet Gradle (app + core:* + feature:*)
├── openapi/           # copie de la spec backend → génération des clients
└── docs/              # ARCHITECTURE, DESIGN-TOKENS, PARITÉ, GUIDES DE TEST, RELEASE
```

## État des deux plateformes

| Domaine | Android | iOS |
|---|---|---|
| Authentification (email, OTP, Google, reset) | ✅ | ✅ |
| Accueil, feed de lives, room de live | ✅ | ✅ |
| Duels (catalogue + room, votes payants, minuteur) | ✅ | ✅ |
| Concerts, compétitions (catalogues + room) | ✅ | ✅ |
| Portefeuille, recharge, retrait, boutique de cadeaux | ✅ | ✅ |
| Profil, rôles, espace créateur, sponsoring, admin | ✅ | ✅ |
| Replays, classements, parrainage, abonnements, contenu, artistes | ✅ | ✅ |
| Notifications in-app + push | ✅ (FCM) | ✅ (APNs → FCM) |
| Thème clair/sombre/système + i18n FR/EN | ✅ | ✅ |
| CI automatisée | ✅ Linux | ✅ macOS |

Inventaire détaillé et écarts assumés : **`docs/PARITE-ANDROID-IOS.md`**.

## Le backend (source de vérité)

- REST : `https://<api>/api/v1/*` — enveloppe `{ data, meta }` / `{ error:{ code, message } }`.
- Auth : JWT RS256, access 15 min + refresh 30 j.
- Realtime : **Socket.IO** (`/chat`, `/live`, `/notifications`), handshake JWT, rooms `type:id`.
- Spec : `Dual_music_backend/docs/openapi.json` (copiée dans `openapi/`).
- Contrat partagé : `shared-domain` (Kotlin) — **fait foi** ; `ios/.../DomainModels` en est
  le miroir Swift.

## Identité visuelle

Le mobile reprend **à l'identique** le design system du web (violet néon + rose + cyan,
thème sombre par défaut). Valeurs exactes dans **`docs/DESIGN-TOKENS.md`**, appliquées par
`core:ui` (Android) et `CoreUI` (iOS) — un test vérifie la conversion HSL côté iOS.

## Démarrage rapide

### iOS (nécessite macOS)

```bash
brew install xcodegen
cd ios
xcodegen generate      # crée DualMusic.xcodeproj à partir de project.yml
open DualMusic.xcodeproj
```

Configurer avant le premier build :
- `DEVELOPMENT_TEAM` dans `ios/project.yml` (appareil physique uniquement) ;
- `DM_API_BASE_URL` (configuration `Debug`) → IP locale du backend pour un iPhone réel ;
- `ios/App/Resources/GoogleService-Info.plist` pour les notifications push (facultatif).

### Android

```bash
cd android
./gradlew assembleDebug
```

## Tester sans Mac ni iPhone

C'est le cas d'usage principal du projet : voir **`docs/GUIDE-TEST-IOS.md`**.
En résumé — la CI GitHub sur runner macOS compile, teste et rapporte chaque erreur Swift à
chaque push ; TestFlight permet ensuite une recette sur de vrais appareils.

## Prérequis de build

- **iOS** : macOS + Xcode 15.4+, XcodeGen, Fastlane. **Non compilable sous Windows.**
- **Android** : JDK 17, Gradle 8.13, AGP 8.6.
- **shared-domain** : Kotlin 2.0 (K2), plugin KMP.

## Documentation

| Fichier | Contenu |
|---|---|
| `docs/ARCHITECTURE.md` | architecture générale du mobile |
| `docs/ARCHITECTURE-IOS.md` | architecture iOS détaillée + décisions et leurs raisons |
| `docs/PARITE-ANDROID-IOS.md` | inventaire écran par écran, écarts assumés |
| `docs/GUIDE-TEST-IOS.md` | tester l'app iOS sans matériel Apple |
| `docs/RELEASE-IOS.md` | App ID, push, TestFlight, App Store, points de conformité |
| `docs/GUIDE-TEST-ANDROID.md` | recette Android |
| `docs/DESIGN-TOKENS.md` | jetons visuels partagés avec le web |
