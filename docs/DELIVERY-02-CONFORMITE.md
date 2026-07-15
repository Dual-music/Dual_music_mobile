# Livraison 02 — core-ui (design system)

Module 2 du prompt. ✅ = livré · 🔜 = ultérieur · ⚠️ = note.

## Périmètre
Design system iOS (SwiftUI) **et** Android (Compose Material 3), dérivé **à l'identique**
des jetons du web (`docs/DESIGN-TOKENS.md`).

## Conformité

| Exigence | État | Où |
|---|---|---|
| Palette **identique au web** (violet 280/70/55, rose 330/100/65, cyan, thème sombre) | ✅ | `Theme.swift` / `theme/Color.kt` (HSL exacts) |
| **Thème sombre par défaut** (comme le web) | ✅ | `DMThemeKey.defaultValue = .dark` / `DualMusicTheme(darkTheme = true)` |
| Thème clair aussi disponible | ✅ | `.light` / `LightColors` |
| Dégradés de marque (primary violet→rose, electric, hero) | ✅ | `DMGradients` (iOS + Android) |
| Halos « néon » (glow / glow-strong / elegant shadow) | ✅ | `Effects.swift` / `Effects.kt` |
| Rayon 12pt/dp (= `--radius`) + échelle d'espacement | ✅ | `DMRadius`/`DMRadii`, `DMSpacing` |
| Typographie système (parité web) | ✅ | `Typography.swift` / `DMTypography` |
| Jetons **sémantiques** (pas de couleur en dur dans les features) | ✅ | `@Environment(\.dmTheme)` / `DualMusicTheme.colors` |
| Mapping Material 3 (composants standards héritent des couleurs) | ✅ | `toMaterialScheme()` |
| Composants signature réutilisables | ✅ | `DMButton` (CTA dégradé), `DMCard`, `CreditPill` |
| Code commenté professionnellement + previews | ✅ | doc-comments + `#Preview` iOS |

## Parité de conversion couleur
- iOS : helper `Color(webHSL:_:_:)` (conversion HSL→sRGB identique au CSS).
- Android : `Color.hsl(...)` natif Compose.
→ Les deux partent des **mêmes valeurs HSL** que le web : aucune dérive de teinte.

## Fichiers livrés
```
ios/Packages/CoreUI/
├── Package.swift
└── Sources/CoreUI/
    ├── Color+HSL.swift          # conversion HSL → Color
    ├── Theme.swift              # DMColors (light/dark), DMGradients, DMRadius, DMSpacing, injection env
    ├── Typography.swift         # DMFont
    ├── Effects.swift            # dmGlow / dmGlowStrong / dmElegantShadow
    └── Components/
        ├── DMButton.swift       # CTA dégradé violet→rose + halo
        ├── DMCard.swift
        └── CreditPill.swift

android/core/ui/
├── build.gradle.kts
└── src/main/kotlin/com/dualmusic/core/ui/
    ├── Effects.kt               # Modifier.dmGlow / dmGlowStrong
    ├── theme/
    │   ├── Color.kt             # DMColors (LightColors / DarkColors)
    │   ├── Tokens.kt            # DMGradients, DMRadii, DMSpacing
    │   └── Theme.kt             # DualMusicTheme + CompositionLocal + mapping M3
    └── components/
        ├── DMButton.kt
        ├── DMCard.kt
        └── CreditPill.kt
```

## ⚠️ Notes
1. **Non compilé ici** (Windows) : iOS à valider sur Xcode 16, Android via `./gradlew`.
2. Composants livrés = base signature. Le reste (inputs, OTP, chips, sheets, tab bar…)
   s'ajoute au fil des features en réutilisant ces jetons.
3. Le glow Android approxime le box-shadow web via une ombre colorée (Compose n'a pas de
   flou natif) — rendu proche, ajustable si besoin.
