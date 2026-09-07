# Architecture iOS — Dual Music

Application **100 % native SwiftUI**, sans WebView, consommant le même backend REST +
Socket.IO que la version Android et le web.

---

## 1. Vue d'ensemble

```
ios/
├── project.yml                    # Définition du projet Xcode (XcodeGen) — le .xcodeproj est GÉNÉRÉ
├── .swiftlint.yml
├── Gemfile                        # fastlane + xcpretty épinglés
├── fastlane/                      # Fastfile (tests, beta TestFlight, release App Store)
├── App/
│   ├── Sources/                   # Coque applicative : entrée, DI, navigation, push, Google
│   ├── Resources/                 # Info.plist, entitlements, Assets.xcassets
│   ├── Tests/                     # Tests dépendants du bundle (config, préférences)
│   └── UITests/                   # Parcours critiques (lancement, écran de connexion)
└── Packages/DualMusicKit/
    ├── Package.swift              # 26 modules Swift en un seul manifeste
    ├── Sources/
    │   ├── DomainModels/          # miroir Swift de shared-domain (DTOs, endpoints, contrat RT)
    │   ├── CoreNetwork/           # client HTTP, enveloppe, JWT + refresh single-flight
    │   ├── CoreUI/                # design system + i18n FR/EN
    │   ├── CoreAuth/              # Keychain, refresh, Face ID
    │   ├── CoreRealtime/          # Socket.IO
    │   ├── CoreLiveMedia/         # LiveKit (vidéo temps réel)
    │   ├── CoreUpload/            # presign → PUT → confirm
    │   └── Feature*/              # 19 features, une par module Gradle Android
    └── Tests/                     # DomainModelsTests, CoreNetworkTests, CoreUITests
```

### Correspondance stricte avec Android

| Cible Swift        | Module Gradle       | Contenu |
|--------------------|---------------------|---------|
| `DomainModels`     | `:shared-domain`    | DTOs, enums, endpoints, contrat Socket.IO, calculs d'aperçu, validateurs |
| `CoreNetwork`      | `:core:network`     | `HTTPClient` (actor), `Endpoint`, enveloppe, `APIError` |
| `CoreUI`           | `:core:ui`          | Thème, composants, `DMStrings` (FR/EN) |
| `CoreAuth`         | `:core:auth`        | `KeychainTokenStore`, `AuthRefresher`, `BiometricGate` |
| `CoreRealtime`     | `:core:realtime`    | `RealtimeClient`, `NamespaceSession` |
| `CoreLiveMedia`    | `:core:media`       | `LiveRoomClient`, `LiveKitTokenService` |
| `CoreUpload`       | `:core:upload`      | `MediaUploader`, `PhotoPickerLoader` |
| `Feature<X>`       | `:feature:<x>`      | Repository + ViewModel + Vues |

> **Pourquoi `CoreLiveMedia` et non `CoreMedia` ?** `CoreMedia` est un **framework système
> Apple**. Un module portant ce nom rendrait `import CoreMedia` ambigu et casserait la
> compilation de LiveKit, qui importe le framework Apple.

---

## 2. Décisions d'architecture et leur raison d'être

### 2.1 Un paquet Swift multi-cibles plutôt que 26 paquets

Chaque `target` reste un **module Swift à part entière** : un `import` explicite est requis,
les dépendances sont déclarées, rien n'est visible par défaut. Les frontières sont donc
aussi strictes que celles des modules Gradle.

Ce qu'on gagne à tout décrire dans un seul `Package.swift` :

- **un seul graphe à lire** (et à corriger) au lieu de 26 manifestes qui se référencent ;
- **résolution et compilation nettement plus rapides** en CI ;
- surtout : **une seule source d'erreur possible** sur le câblage des dépendances — ce qui
  compte beaucoup quand la compilation n'est pas vérifiable localement.

### 2.2 Aucune dépendance à KMM

La version Android partage `shared-domain` (Kotlin Multiplatform). Le portage iOS **ne
consomme pas** ce framework : `DomainModels` en est un miroir Swift.

Raison : consommer KMM depuis Xcode impose une chaîne Gradle + génération d'XCFramework à
chaque build, sur une plateforme qui ne peut pas être testée depuis ce poste de
développement. Le coût d'un miroir Swift (des DTOs, mécaniques) est très inférieur au coût
d'une chaîne de build fragile — et le contrat reste unique : **`shared-domain` fait foi**,
`DomainModels` le suit.

> Règle de maintenance : toute évolution du contrat backend se fait **d'abord** dans
> `shared-domain`, puis est répercutée dans `DomainModels`. La table de correspondance
> ci-dessus et les commentaires de chaque DTO indiquent le fichier Kotlin d'origine.

### 2.3 Décodage tolérant

`KeyedDecodingContainer.opt / val / amount / int / bool` (voir `DecodingSupport.swift`)
reproduisent le comportement de kotlinx (`ignoreUnknownKeys` + valeurs par défaut) :

- une clé absente retombe sur le défaut au lieu de faire échouer **toute** la réponse ;
- un enum inconnu retombe sur une valeur sûre (`.upcoming`, `.fan`, `.pending`) ;
- un montant sérialisé en chaîne (`"12.50"`, comportement par défaut du driver PostgreSQL
  sur les colonnes `numeric`) est lu correctement.

C'est ce qui permet au backend d'ajouter un champ sans casser une version d'app déjà
publiée sur l'App Store — où le rollback prend plusieurs jours.

### 2.4 Argent : le backend fait foi, toujours

Aucun calcul d'argent n'est effectué sur l'appareil :

- le **solde** et sa contre-valeur € viennent de `GET /wallet` ;
- le **net d'un retrait** vient de `POST /withdrawals/net` (jamais de `CreditMath`, qui ne
  sert qu'à des aperçus non contractuels) ;
- un **vote** ne met à jour le compteur qu'à réception de l'événement temps réel `vote` —
  pas d'optimisme local ;
- tout **débit** part avec un en-tête `Idempotency-Key` : un rejeu réseau ne débite jamais
  deux fois.

### 2.5 Concurrence

- `HTTPClient` est un **`actor`** : l'état de refresh est protégé, garantissant qu'un seul
  refresh part même si dix requêtes échouent en 401 simultanément (sinon le backend
  invalide la chaîne de refresh tokens et déconnecte l'utilisateur). Ce comportement est
  couvert par un test dédié.
- `KeychainTokenStore` est un **`actor`** : accès concurrent sûr depuis l'acteur réseau.
- Tous les ViewModels sont `@Observable @MainActor` : pas de `@Published`, pas de
  `ObservableObject`, et aucune mutation d'UI hors du thread principal possible.

### 2.6 Injection de dépendances

`AppContainer` construit le graphe complet une fois, sans framework de DI (comme Android).
Les ViewModels d'écran y sont **mémoïsés** — l'équivalent de `viewModel { }` en Compose :
revenir sur un écran ne relance pas un chargement complet. Les ViewModels « par entité »
(room de duel, lecteur de replay) sont mémoïsés **par identifiant**, comme
`viewModel(key = id)`.

Le cache est marqué `@ObservationIgnored` : sans cela, créer un ViewModel pendant
l'évaluation d'un `body` serait vu par SwiftUI comme une mutation d'état pendant le rendu.

À la déconnexion, `signOut()` vide tous les caches — sans quoi le prochain utilisateur
verrait brièvement les données du précédent.

---

## 3. Correspondances techniques Android → iOS

| Besoin | Android | iOS |
|---|---|---|
| HTTP | Ktor + OkHttp | `URLSession` (aucune dépendance tierce) |
| Sérialisation | kotlinx.serialization | `Codable` + helpers tolérants |
| Stockage sécurisé | EncryptedSharedPreferences + StrongBox | Keychain (`…AfterFirstUnlockThisDeviceOnly`) |
| Temps réel | socket.io-client-java | socket.io-client-swift |
| Vidéo live | livekit-android-sdk + `SurfaceViewRenderer` | client-sdk-swift + `SwiftUIVideoView` |
| Lecture vidéo | ExoPlayer (Media3) | `AVPlayer` / `VideoPlayer` |
| Images distantes | téléchargement maison | `AsyncImage` (cache URLSession) |
| Sélecteur média | `PickVisualMedia` | `PhotosPicker` |
| Durée vidéo | `MediaMetadataRetriever` | `AVURLAsset.load(.duration)` |
| Biométrie | BiometricPrompt | `LAContext` (Face ID / Touch ID) |
| Push | FCM (`FirebaseMessagingService`) | APNs → **jeton FCM** (Firebase Messaging) |
| Google Sign-In | Credential Manager | GoogleSignIn-iOS |
| Paiement hébergé | `Intent(ACTION_VIEW)` | `openURL` (Safari View Controller) |
| Préférences | SharedPreferences | `UserDefaults` |
| Halo cadeau | shader AGSL | dégradé radial animé (`TimelineView`) |

### Pourquoi un jeton **FCM** et non APNs brut ?

Le backend envoie les notifications via Firebase (`firebase-service-account.json`).
`POST /notifications/devices` attend donc un identifiant que FCM sait router. Firebase
Messaging s'enregistre auprès d'APNs puis échange le jeton APNs contre un jeton FCM : c'est
ce dernier qui est envoyé au backend, exactement comme sur Android.

Si `GoogleService-Info.plist` est absent, Firebase n'est **pas** configuré et l'app se lance
normalement — seules les notifications distantes sont inactives.

---

## 4. Navigation

Reproduction fidèle de `MainShell` (Compose) :

```
MainShellView
├── TopBarView            (masquée dans le profil et les notifications)
├── contenu
│   ├── onglet Accueil    → HomeView + 3 accès rapides (Lifestyle / Classement / Artistes)
│   ├── onglet Lives      → FeedView (pagination verticale plein écran)
│   ├── onglet Duels      → DuelsListView → DuelRoomView
│   ├── onglet Concerts   → ConcertsListView
│   ├── onglet Compét.    → CompetitionsListView → CompetitionRoomView
│   ├── notifications     → NotificationsView (cloche)
│   └── profil            → ProfileSectionView (sous-navigation `ProfileRoute`)
└── BottomBarView         (dégradé violet, item actif en rose)
```

La barre basse est **écrite à la main** plutôt que d'utiliser `TabView` : le style système
iOS ne permet pas de reproduire la `NavigationBar` colorée d'Android.

Les entiers magiques d'Android (`profileSub = 15`) sont remplacés par l'énumération
`ProfileRoute` — même arborescence, mais lisible.

---

## 5. Design system

Les jetons sont **identiques au web et à Android**, à la valeur HSL près :

- `Color(webHSL:_:_:)` reproduit exactement la conversion HSL→sRGB du CSS (un test le
  vérifie sur le violet de marque) ;
- rayons (12 pt = `--radius: 0.75rem`), espacement base 4, dégradés, halos néon ;
- thème **sombre par défaut**, clair et « système » disponibles et persistés.

L'internationalisation FR/EN utilise une **structure de chaînes** (`DMStrings`) plutôt que
`Localizable.strings` : mêmes clés, mêmes textes et même ordre que `Strings.kt`, ce qui rend
les deux plateformes diffables ligne à ligne. Deux tests garantissent qu'aucune chaîne n'est
vide et que les deux langues exposent le même nombre de champs.

---

## 6. Tests

| Niveau | Emplacement | Ce qui est couvert |
|---|---|---|
| Domaine | `Tests/DomainModelsTests` | décodage tolérant, montants en chaîne, enums inconnus, calculs d'aperçu, validateurs, contrat temps réel |
| Réseau | `Tests/CoreNetworkTests` | préfixe d'URL, Bearer, `Idempotency-Key`, enveloppe, erreurs typées, **refresh single-flight** |
| Design system | `Tests/CoreUITests` | parité HSL, jetons, formatage des crédits, complétude i18n |
| App | `App/Tests` | configuration injectée, autorisations `Info.plist`, persistance des préférences |
| UI | `App/UITests` | lancement, écran de connexion, performance de démarrage |

Tous s'exécutent en CI sur runner macOS (voir `.github/workflows/ios-ci.yml`).
