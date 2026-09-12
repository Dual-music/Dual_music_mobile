// swift-tools-version: 5.10
import PackageDescription

/// `DualMusicKit` — **tout le code métier et UI de l'app iOS Dual Music**, découpé en
/// modules Swift qui reflètent 1-pour-1 les modules Gradle de la version Android.
///
/// ## Pourquoi un seul paquet multi-cibles ?
/// Chaque `target` reste un **module Swift distinct** (mêmes frontières que
/// `:core:network`, `:feature:duel`, … côté Android : un `import` explicite est requis, les
/// dépendances sont déclarées, rien n'est visible par défaut). Mais tout est décrit dans
/// **un seul manifeste**, ce qui évite 25 `Package.swift` et 25 résolutions locales : le
/// graphe est vérifiable d'un coup d'œil et la compilation CI est nettement plus rapide.
///
/// ## Correspondance avec Android
/// | Cible Swift      | Module Gradle     |
/// |------------------|-------------------|
/// | `DomainModels`   | `:shared-domain`  |
/// | `CoreNetwork`    | `:core:network`   |
/// | `CoreUI`         | `:core:ui`        |
/// | `CoreAuth`       | `:core:auth`      |
/// | `CoreRealtime`   | `:core:realtime`  |
/// | `CoreLiveMedia`  | `:core:media`     |
/// | `CoreUpload`     | `:core:upload`    |
/// | `Feature*`       | `:feature:*`      |
///
/// > `CoreLiveMedia` (et non `CoreMedia`) : **CoreMedia est un framework système Apple**.
/// > Réutiliser ce nom rendrait `import CoreMedia` ambigu et casserait la compilation de
/// > LiveKit, qui importe le framework Apple du même nom.
///
/// ## Dépendances externes
/// - `socket.io-client-swift` → `CoreRealtime` (miroir de `socket.io-client-java`).
/// - `client-sdk-swift` (LiveKit) → `CoreLiveMedia` (miroir de `livekit-android-sdk`).
///
/// Firebase (push) et Google Sign-In restent **hors du kit**, au niveau de la cible
/// applicative (voir `ios/project.yml`) : ils ne sont utilisés que par la coque, et les
/// garder dehors permet un `swift build` du kit rapide et sans compte Google en CI.
let package = Package(
    name: "DualMusicKit",
    defaultLocalization: "fr",
    // Le paquet est iOS-only, mais SwiftPM valide quand même la compatibilité de TOUTE la
    // chaîne de dépendances sur chaque plateforme connue lors de la résolution — sans cette
    // déclaration explicite, macOS retombe sur un minimum implicite (10.13) trop bas pour
    // `client-sdk-swift` (LiveKit), qui exige 10.15+. Ça fait échouer la résolution même si
    // seul iOS est réellement construit (`-destination "platform=iOS Simulator"` en CI).
    platforms: [.iOS(.v17), .macOS(.v13)],
    products: [
        .library(
            name: "DualMusicKit",
            targets: [
                "DomainModels",
                "CoreNetwork", "CoreUI", "CoreAuth", "CoreRealtime", "CoreLiveMedia", "CoreUpload",
                "FeatureAuth", "FeatureFeed", "FeatureLive", "FeatureWallet", "FeatureDuel",
                "FeatureConcert", "FeatureCompetition", "FeatureProfile", "FeatureNotifications",
                "FeatureWithdrawal", "FeatureReplay", "FeatureGiftShop", "FeatureLeaderboard",
                "FeatureReferral", "FeatureSubscription", "FeatureContent", "FeatureArtists",
                "FeatureSponsor", "FeatureCreator",
            ]
        ),
    ],
    dependencies: [
        .package(url: "https://github.com/socketio/socket.io-client-swift", from: "16.1.0"),
        .package(url: "https://github.com/livekit/client-sdk-swift", from: "2.0.0"),
    ],
    targets: [

        // MARK: - Domaine partagé (aucune dépendance : ni UI, ni réseau, ni média)

        .target(name: "DomainModels"),

        // MARK: - Modules socle

        .target(name: "CoreNetwork", dependencies: ["DomainModels"]),
        .target(
            name: "CoreUI",
            dependencies: ["DomainModels"],
            // Embarque le logo du web (parité de marque), accessible via `Bundle.module`.
            resources: [.process("Resources")]
        ),
        .target(name: "CoreAuth", dependencies: ["CoreNetwork", "DomainModels"]),
        .target(
            name: "CoreRealtime",
            dependencies: [
                "DomainModels",
                .product(name: "SocketIO", package: "socket.io-client-swift"),
            ]
        ),
        .target(
            name: "CoreLiveMedia",
            dependencies: [
                "CoreNetwork", "DomainModels",
                .product(name: "LiveKit", package: "client-sdk-swift"),
            ]
        ),
        .target(name: "CoreUpload", dependencies: ["CoreNetwork", "DomainModels"]),

        // MARK: - Features

        .target(name: "FeatureAuth", dependencies: ["CoreNetwork", "CoreUI", "DomainModels"]),
        .target(name: "FeatureLive", dependencies: ["CoreNetwork", "CoreUI", "CoreLiveMedia", "CoreRealtime", "DomainModels"]),
        .target(name: "FeatureFeed", dependencies: ["CoreNetwork", "CoreUI", "CoreLiveMedia", "DomainModels", "FeatureLive"]),
        .target(name: "FeatureWallet", dependencies: ["CoreNetwork", "CoreUI", "DomainModels"]),
        .target(name: "FeatureDuel", dependencies: ["CoreNetwork", "CoreUI", "CoreLiveMedia", "CoreRealtime", "DomainModels", "FeatureWallet"]),
        .target(name: "FeatureConcert", dependencies: ["CoreNetwork", "CoreUI", "CoreLiveMedia", "CoreRealtime", "DomainModels"]),
        .target(name: "FeatureCompetition", dependencies: ["CoreNetwork", "CoreUI", "CoreLiveMedia", "CoreRealtime", "DomainModels"]),
        .target(name: "FeatureProfile", dependencies: ["CoreNetwork", "CoreUI", "CoreUpload", "DomainModels"]),
        .target(name: "FeatureNotifications", dependencies: ["CoreNetwork", "CoreUI", "CoreRealtime", "DomainModels"]),
        .target(name: "FeatureWithdrawal", dependencies: ["CoreNetwork", "CoreUI", "DomainModels"]),
        .target(name: "FeatureReplay", dependencies: ["CoreNetwork", "CoreUI", "DomainModels"]),
        .target(name: "FeatureGiftShop", dependencies: ["CoreNetwork", "CoreUI", "DomainModels"]),
        .target(name: "FeatureLeaderboard", dependencies: ["CoreNetwork", "CoreUI", "DomainModels"]),
        .target(name: "FeatureReferral", dependencies: ["CoreNetwork", "CoreUI", "DomainModels"]),
        .target(name: "FeatureSubscription", dependencies: ["CoreNetwork", "CoreUI", "DomainModels"]),
        .target(name: "FeatureContent", dependencies: ["CoreNetwork", "CoreUI", "DomainModels"]),
        .target(name: "FeatureArtists", dependencies: ["CoreNetwork", "CoreUI", "DomainModels"]),
        .target(name: "FeatureSponsor", dependencies: ["CoreNetwork", "CoreUI", "CoreUpload", "DomainModels"]),
        .target(name: "FeatureCreator", dependencies: ["CoreNetwork", "CoreUI", "CoreUpload", "DomainModels"]),

        // MARK: - Tests

        .testTarget(name: "DomainModelsTests", dependencies: ["DomainModels"]),
        .testTarget(name: "CoreNetworkTests", dependencies: ["CoreNetwork", "DomainModels"]),
        .testTarget(name: "CoreUITests", dependencies: ["CoreUI"]),
    ]
)
