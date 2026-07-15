// swift-tools-version: 5.10
import PackageDescription

/// `FeatureLive` — écran de live (viewer) : vidéo LiveKit + chat & cadeaux temps réel.
///
/// Combine `CoreMedia` (vidéo), `CoreRealtime` (chat/cadeaux/présence via Socket.IO),
/// `CoreNetwork` (actions vote/cadeau) et `CoreUI` (design system).
let package = Package(
    name: "FeatureLive",
    platforms: [.iOS(.v17)],
    products: [
        .library(name: "FeatureLive", targets: ["FeatureLive"]),
    ],
    dependencies: [
        .package(path: "../CoreNetwork"),
        .package(path: "../CoreRealtime"),
        .package(path: "../CoreMedia"),
        .package(path: "../CoreUI"),
        .package(url: "https://github.com/livekit/client-sdk-swift", from: "2.0.0"),
    ],
    targets: [
        .target(
            name: "FeatureLive",
            dependencies: [
                "CoreNetwork", "CoreRealtime", "CoreMedia", "CoreUI",
                .product(name: "LiveKit", package: "client-sdk-swift"),
            ]
        ),
    ]
)
