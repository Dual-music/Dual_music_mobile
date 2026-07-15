// swift-tools-version: 5.10
import PackageDescription

/// `FeatureAuth` — écrans et logique d'authentification (iOS).
///
/// Connexion **email + mot de passe** (primaire), inscription, vérification OTP téléphone
/// (post-login), point d'entrée Google, déverrouillage biométrique. S'appuie sur
/// `CoreNetwork`, `CoreAuth`, `CoreUI`.
let package = Package(
    name: "FeatureAuth",
    platforms: [.iOS(.v17)],
    products: [
        .library(name: "FeatureAuth", targets: ["FeatureAuth"]),
    ],
    dependencies: [
        .package(path: "../CoreNetwork"),
        .package(path: "../CoreAuth"),
        .package(path: "../CoreUI"),
    ],
    targets: [
        .target(name: "FeatureAuth", dependencies: ["CoreNetwork", "CoreAuth", "CoreUI"]),
        .testTarget(name: "FeatureAuthTests", dependencies: ["FeatureAuth"]),
    ]
)
