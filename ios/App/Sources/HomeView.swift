import SwiftUI
import CoreUI

/// Page d'accueil : bannière plein écran (même image que le web et Android) avec l'icône
/// « duel » au-dessus du titre en dégradé, un court slogan, et **3 accès rapides** sous
/// forme de grosses pastilles animées, alignées en bas. Tient sans défilement.
///
/// Miroir de `HomeScreen` (Compose).
struct HomeView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    let onOpenLifestyle: () -> Void
    let onOpenRanking: () -> Void
    let onOpenArtists: () -> Void

    var body: some View {
        ZStack {
            Image("hero_bg")
                .resizable()
                .scaledToFill()
                .clipped()
                .ignoresSafeArea()

            // Voile dégradé pour la lisibilité du texte sur la photo.
            LinearGradient(
                colors: [
                    Color(hex: 0x0B0614, alpha: 0.80),
                    Color(hex: 0x0B0614, alpha: 0.53),
                    Color(hex: 0x0B0614, alpha: 0.95),
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                Spacer()

                Image("duel_icon")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 104, height: 104)
                    .accessibilityHidden(true)

                Text(s.homeTitle)
                    .font(.system(size: 30, weight: .black, design: .rounded))
                    .multilineTextAlignment(.center)
                    .foregroundStyle(theme.gradients.brand)
                    .padding(.top, theme.spacing.lg)

                Text(s.homeSubtitle)
                    .font(.system(size: 14))
                    .foregroundStyle(.white.opacity(0.82))
                    .multilineTextAlignment(.center)
                    .padding(.top, theme.spacing.sm)

                Spacer()

                HStack(alignment: .top, spacing: 0) {
                    HomeQuickAccess(
                        label: s.lifestyle,
                        systemImage: "film.fill",
                        pulseDuration: 1.1,
                        action: onOpenLifestyle
                    )
                    HomeQuickAccess(
                        label: s.ranking,
                        systemImage: "trophy.fill",
                        pulseDuration: 1.4,
                        action: onOpenRanking
                    )
                    HomeQuickAccess(
                        label: s.artists,
                        systemImage: "mic.fill",
                        pulseDuration: 1.7,
                        action: onOpenArtists
                    )
                }
                .frame(maxWidth: .infinity)

                Spacer().frame(height: theme.spacing.xxl)
            }
            .padding(.horizontal, theme.spacing.xl)
        }
    }
}

/// Accès rapide de l'accueil : pastille circulaire en dégradé de marque, icône blanche,
/// **pulsation** continue (durée décalée par accès pour un effet vivant), + libellé.
private struct HomeQuickAccess: View {
    @Environment(\.dmTheme) private var theme

    let label: String
    let systemImage: String
    /// Durée d'un aller de la pulsation (décalée entre les 3 pastilles).
    let pulseDuration: Double
    let action: () -> Void

    @State private var pulsing = false

    var body: some View {
        VStack(spacing: theme.spacing.sm) {
            Button(action: action) {
                ZStack {
                    Circle()
                        .fill(theme.gradients.brand)
                        .frame(width: 84, height: 84)
                    Image(systemName: systemImage)
                        .font(.system(size: 34, weight: .semibold))
                        .foregroundStyle(.white)
                }
                .scaleEffect(pulsing ? 1.09 : 1.0)
                .shadow(color: .black.opacity(0.35), radius: 16, y: 6)
                .contentShape(Circle())
            }
            .buttonStyle(.plain)

            Text(label)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)
                .frame(width: 88)
        }
        .frame(maxWidth: .infinity)
        .onAppear {
            withAnimation(.easeInOut(duration: pulseDuration).repeatForever(autoreverses: true)) {
                pulsing = true
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(Text(label))
        .accessibilityAddTraits(.isButton)
    }
}
