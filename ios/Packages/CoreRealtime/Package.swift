// swift-tools-version: 5.10
import PackageDescription

/// `CoreRealtime` — client Socket.IO de Dual Music (iOS).
///
/// Enveloppe le SDK officiel `socket.io-client-swift` : connexion aux namespaces
/// `/chat` `/live` `/notifications` avec handshake JWT, rejoint les rooms `type:id`,
/// expose les événements serveur en flux `AsyncStream`, et gère la reconnexion.
let package = Package(
    name: "CoreRealtime",
    platforms: [.iOS(.v17)],
    products: [
        .library(name: "CoreRealtime", targets: ["CoreRealtime"]),
    ],
    dependencies: [
        // SDK Socket.IO officiel (compatible Socket.IO v4 côté serveur).
        .package(url: "https://github.com/socketio/socket.io-client-swift", from: "16.1.0"),
    ],
    targets: [
        .target(
            name: "CoreRealtime",
            dependencies: [.product(name: "SocketIO", package: "socket.io-client-swift")]
        ),
        .testTarget(name: "CoreRealtimeTests", dependencies: ["CoreRealtime"]),
    ]
)
