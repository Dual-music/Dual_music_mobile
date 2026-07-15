# Architecture — Dual Music Mobile

## Vue d'ensemble

```mermaid
flowchart TB
    subgraph iOS["📱 iOS (Swift / SwiftUI)"]
        iUI[Presentation<br/>SwiftUI + ViewModels]
        iDom[Domain<br/>use-cases]
        iData[Data<br/>repositories]
        iNet[CoreNetwork<br/>URLSession]
        iRT[CoreRealtime<br/>socket.io-swift]
        iMedia[CoreMedia<br/>LiveKit iOS]
        iUI --> iDom --> iData
        iData --> iNet & iRT & iMedia
    end

    subgraph Android["🤖 Android (Kotlin / Compose)"]
        aUI[Presentation<br/>Compose + ViewModels]
        aDom[Domain<br/>use-cases]
        aData[Data<br/>repositories]
        aNet[core:network<br/>Ktor/OkHttp]
        aRT[core:realtime<br/>socket.io-java]
        aMedia[core:media<br/>LiveKit Android]
        aUI --> aDom --> aData
        aData --> aNet & aRT & aMedia
    end

    subgraph Shared["🔗 shared-domain (KMM)"]
        SD[Modèles · DTOs · Enums<br/>Contrat Socket.IO<br/>Aperçus économie · Validation<br/>ZÉRO UI / réseau / média]
    end

    iDom -.->|Kotlin/Native framework| SD
    aDom -.->|Kotlin/JVM| SD

    subgraph Backend["☁️ Dual_music_backend"]
        REST[REST /api/v1/*<br/>enveloppe data/meta]
        WS[Socket.IO<br/>/chat /live /notifications]
        LK[LiveKit token]
        DB[(MySQL 8 + procédures)]
        REST --> DB
        WS --> DB
    end

    iNet & aNet -->|HTTPS + JWT| REST
    iRT & aRT -->|WSS + JWT| WS
    iMedia & aMedia -->|WebRTC| LK
```

## Principes

1. **Clean Architecture** — 4 couches identiques sur les 2 plateformes : Presentation →
   Domain → Data → Infrastructure. Les dépendances pointent vers le Domain.
2. **shared-domain (KMM)** — uniquement le *quoi* (modèles, contrats, validation,
   aperçus d'affichage). Jamais le *comment* (UI, réseau, média = 100 % natif).
3. **Argent** — les montants qui font foi viennent du backend (procédures atomiques). Le
   mobile n'affiche que des aperçus et lit les soldes/nets via l'API.
4. **Realtime** — Socket.IO, un client par plateforme, contrat partagé dans
   `shared-domain/realtime`.

## Conventions transverses (posées par cette fondation)

- **Enveloppe API** : succès `{ data, meta }`, erreur `{ error:{ code, message, details } }`.
  Décodée une seule fois dans `core-network`, qui renvoie soit `data`, soit lève `ApiError`.
- **Auth** : `TokenStore` (protocole) fournit access/refresh ; `core-network` gère le
  refresh **single-flight** sur 401 puis rejoue la requête.
- **Erreurs** : type `ApiError` typé (statut HTTP + `code` métier), branchables par l'UI.
- **Realtime** : `RealtimeContract` (namespaces, `roomName(type,id)`, catalogue d'events)
  est la référence unique partagée iOS/Android.
