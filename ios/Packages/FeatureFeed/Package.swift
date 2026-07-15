// swift-tools-version: 5.10
import PackageDescription

/// `FeatureFeed` — feed vertical plein écran (style TikTok) des lives en cours.
///
/// Scroll paginé vertical ; seule la cellule active connecte la vidéo (via `FeatureLive`),
/// et le jeton de la prochaine room est pré-chauffé pour réduire la latence d'entrée.
let package = Package(
    name: "FeatureFeed",
    platforms: [.iOS(.v17)],
    products: [
        .library(name: "FeatureFeed", targets: ["FeatureFeed"]),
    ],
    dependencies: [
        .package(path: "../CoreNetwork"),
        .package(path: "../CoreMedia"),
        .package(path: "../CoreUI"),
        .package(path: "../FeatureLive"),
    ],
    targets: [
        .target(name: "FeatureFeed", dependencies: ["CoreNetwork", "CoreMedia", "CoreUI", "FeatureLive"]),
    ]
)
