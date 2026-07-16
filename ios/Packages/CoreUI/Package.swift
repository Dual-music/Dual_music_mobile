// swift-tools-version: 5.10
import PackageDescription

/// `CoreUI` — design system de Dual Music (iOS / SwiftUI).
///
/// Reprend **à l'identique** les jetons du web (`docs/DESIGN-TOKENS.md`) : palette violet
/// néon + rose + cyan, thème sombre par défaut, dégradés, halos (glow), rayon 12pt.
/// Les features consomment uniquement les jetons sémantiques (`Theme.colors.primary`,
/// `Theme.gradients.primary`, …) — jamais de couleur en dur.
let package = Package(
    name: "CoreUI",
    platforms: [.iOS(.v17)],
    products: [
        .library(name: "CoreUI", targets: ["CoreUI"]),
    ],
    targets: [
        // `resources: [.process(...)]` embarque le logo web (Resources/dm_logo.png),
        // accessible via `Bundle.module`.
        .target(name: "CoreUI", resources: [.process("Resources")]),
        .testTarget(name: "CoreUITests", dependencies: ["CoreUI"]),
    ]
)
