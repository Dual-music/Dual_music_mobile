// swift-tools-version: 5.10
import PackageDescription

/// `CoreNetwork` — client HTTP typé de Dual Music (iOS).
///
/// Responsabilités : préfixe `API base URL` + Bearer JWT, décodage de l'enveloppe
/// `{ data, meta }` / `{ error }`, refresh **single-flight** du token sur 401, et
/// traduction de tout échec en `APIError`. Aucune dépendance tierce (URLSession pur).
let package = Package(
    name: "CoreNetwork",
    platforms: [.iOS(.v17)],
    products: [
        .library(name: "CoreNetwork", targets: ["CoreNetwork"]),
    ],
    targets: [
        .target(name: "CoreNetwork"),
        .testTarget(name: "CoreNetworkTests", dependencies: ["CoreNetwork"]),
    ]
)
