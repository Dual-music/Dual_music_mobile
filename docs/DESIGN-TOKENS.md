# Design tokens — parité visuelle avec le web

**Objectif :** le mobile doit être visuellement **identique** au web Dual Music. Ce
fichier est la **source de vérité unique** des couleurs/thème, copiée telle quelle
depuis le frontend (`duel_music_frontend/src/index.css`). `core-ui` (module 2) génère
les couleurs iOS/Android **à partir de ces valeurs** — ne jamais réinventer une teinte.

Les couleurs sont exprimées en **HSL** (comme sur le web : `H S% L%`). On garde le HSL
comme source et on convertit à l'exécution (voir helpers en bas) pour éviter toute
dérive de conversion.

## Palette — thème clair (`:root`)

| Token | HSL | Usage |
|---|---|---|
| `background` | `0 0% 100%` | Fond principal |
| `foreground` | `240 10% 10%` | Texte principal |
| `card` / `popover` | `0 0% 100%` | Surfaces |
| `primary` | `280 70% 55%` | **Violet — couleur de marque** |
| `primary-foreground` | `0 0% 100%` | Texte sur primary |
| `primary-glow` | `280 80% 65%` | Halo/dégradés |
| `electric-blue` | `210 100% 60%` | Accent électrique |
| `neon-pink` | `330 100% 65%` | Accent rose néon |
| `neon-cyan` | `180 100% 60%` | Accent cyan |
| `secondary` | `240 15% 20%` | Boutons secondaires |
| `muted` | `240 10% 90%` | Fonds atténués |
| `muted-foreground` | `240 5% 40%` | Texte atténué |
| `accent` | `330 100% 65%` | **Accent (= neon-pink)** |
| `destructive` | `0 84.2% 60.2%` | Erreurs/dangers |
| `border` / `input` | `240 10% 85%` | Bordures / champs |
| `ring` | `280 70% 55%` | Focus (= primary) |

## Palette — thème sombre (`.dark`) — thème par défaut de l'app

| Token | HSL |
|---|---|
| `background` | `240 15% 8%` |
| `foreground` | `0 0% 98%` |
| `card` / `popover` | `240 15% 12%` |
| `primary` | `280 70% 55%` |
| `secondary` | `240 15% 18%` |
| `muted` | `240 15% 18%` |
| `muted-foreground` | `240 5% 65%` |
| `accent` | `330 100% 65%` |
| `destructive` | `0 62.8% 50%` |
| `border` / `input` | `240 15% 20%` |
| `ring` | `280 70% 55%` |

## Dégradés, ombres, rayon, transitions

```
--radius: 0.75rem                      # 12pt — rayon des cartes/boutons

--gradient-primary : linear-gradient(135°, hsl(280 70% 55%), hsl(330 100% 65%))   # violet → rose
--gradient-electric: linear-gradient(135°, hsl(210 100% 60%), hsl(180 100% 60%))  # bleu → cyan
--gradient-hero    : linear-gradient(180°, hsl(240 15% 8%), hsl(280 50% 15%))     # sombre → violet

--shadow-glow        : 0 0 40px hsl(280 70% 55% / 0.40)
--shadow-glow-strong : 0 0 60px hsl(280 70% 55% / 0.60)
--shadow-elegant     : 0 10px 40px -10px hsl(280 70% 55% / 0.30)

--transition-smooth  : 0.30s cubic-bezier(0.4, 0, 0.2, 1)   # ~ easeInOut 300ms
```

## Typographie

Le web utilise la **pile sans-serif système** (aucune police custom déclarée). Mobile :
- iOS : `SF Pro` (police système) via `.body`, `.title`, etc.
- Android : `Roboto` / police système Material 3.
Si une police de marque est ajoutée plus tard, la définir ici en premier.

## Helpers de conversion (à placer dans `core-ui`)

### iOS — SwiftUI (extension HSL → Color)
SwiftUI n'a pas d'init HSL natif (seulement HSB). Ce helper convertit exactement le HSL
du web.
```swift
import SwiftUI

extension Color {
    /// Construit une Color depuis les mêmes valeurs HSL que le design web.
    /// - Parameters:
    ///   - h: teinte en degrés (0–360)
    ///   - s: saturation (0–1)
    ///   - l: luminosité (0–1)
    ///   - a: opacité (0–1)
    init(webHSL h: Double, _ s: Double, _ l: Double, alpha a: Double = 1) {
        let c = (1 - abs(2 * l - 1)) * s
        let x = c * (1 - abs((h / 60).truncatingRemainder(dividingBy: 2) - 1))
        let m = l - c / 2
        let (r, g, b): (Double, Double, Double)
        switch h {
        case ..<60:   (r, g, b) = (c, x, 0)
        case ..<120:  (r, g, b) = (x, c, 0)
        case ..<180:  (r, g, b) = (0, c, x)
        case ..<240:  (r, g, b) = (0, x, c)
        case ..<300:  (r, g, b) = (x, 0, c)
        default:      (r, g, b) = (c, 0, x)
        }
        self.init(.sRGB, red: r + m, green: g + m, blue: b + m, opacity: a)
    }
}

// Exemple — jeton `primary` : Color(webHSL: 280, 0.70, 0.55)
```

### Android — Jetpack Compose
Compose fournit `Color.hsl(...)` nativement — parité directe :
```kotlin
import androidx.compose.ui.graphics.Color

// jeton `primary` (280 70% 55%)
val Primary = Color.hsl(hue = 280f, saturation = 0.70f, lightness = 0.55f)
val Accent  = Color.hsl(330f, 1.00f, 0.65f)          // neon-pink
// opacité : Color.hsl(280f, .70f, .55f, alpha = 0.4f) pour le glow
```

> Règle : `core-ui` expose ces jetons sous forme sémantique (`Theme.colors.primary`,
> `Theme.colors.accent`, `Theme.gradients.primary`, `Theme.radius`, …) — les features
> n'utilisent JAMAIS de couleur en dur, uniquement les jetons.
