// swift-tools-version: 5.10
import PackageDescription

/// `CoreMedia` — pipeline vidéo live de Dual Music (iOS).
///
/// Enveloppe le **LiveKit iOS SDK** (WebRTC + décodage matériel VideoToolbox) : obtention
/// du jeton via le backend, connexion à la room SFU, souscription aux pistes et exposition
/// de la piste vidéo à rendre (via `SwiftUIVideoView` dans la feature).
let package = Package(
    name: "CoreMedia",
    platforms: [.iOS(.v17)],
    products: [
        .library(name: "CoreMedia", targets: ["CoreMedia"]),
    ],
    dependencies: [
        .package(path: "../CoreNetwork"),
        .package(url: "https://github.com/livekit/client-sdk-swift", from: "2.0.0"),
    ],
    targets: [
        .target(
            name: "CoreMedia",
            dependencies: [
                "CoreNetwork",
                .product(name: "LiveKit", package: "client-sdk-swift"),
            ]
        ),
    ]
)
