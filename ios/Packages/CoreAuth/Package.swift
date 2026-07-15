// swift-tools-version: 5.10
import PackageDescription

/// `CoreAuth` — authentification & stockage sécurisé (iOS).
///
/// Fournit l'implémentation concrète de `TokenStore` (Keychain) et `TokenRefresher`
/// (`POST /auth/refresh`) attendus par `CoreNetwork`, plus la garde biométrique
/// (FaceID/TouchID) pour les actions sensibles (retraits, PIN wallet).
let package = Package(
    name: "CoreAuth",
    platforms: [.iOS(.v17)],
    products: [
        .library(name: "CoreAuth", targets: ["CoreAuth"]),
    ],
    dependencies: [
        .package(path: "../CoreNetwork"),
    ],
    targets: [
        .target(name: "CoreAuth", dependencies: ["CoreNetwork"]),
        .testTarget(name: "CoreAuthTests", dependencies: ["CoreAuth"]),
    ]
)
