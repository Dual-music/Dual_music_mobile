import SwiftUI
import CoreUI

/// Écran **Préférences** : thème (clair / sombre / système), langue (FR / EN) et zone
/// sensible « Compte » (suppression différée, annulable pendant 20 jours).
///
/// Le thème et la langue sont appliqués **immédiatement** et persistés, comme sur Android.
struct PreferencesView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    let container: AppContainer

    @State private var deletionScheduledAt: String?
    @State private var confirmingDeletion = false
    @State private var isRefreshing = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: theme.spacing.lg) {
                // --- Thème ---
                DMSectionTitle(s.appearance)
                DMCard {
                    VStack(spacing: 0) {
                        themeRow(s.themeSystem, .system)
                        themeRow(s.themeLight, .light)
                        themeRow(s.themeDark, .dark)
                    }
                }

                // --- Langue ---
                DMSectionTitle(s.language)
                DMCard {
                    VStack(spacing: 0) {
                        languageRow(s.french, .fr)
                        languageRow(s.english, .en)
                    }
                }

                // --- Compte (zone sensible) ---
                DMSectionTitle(s.account)
                DMCard {
                    VStack(alignment: .leading, spacing: theme.spacing.sm) {
                        if let scheduled = deletionScheduledAt {
                            Text("\(s.deletionScheduledPrefix) \(isoDay(scheduled) ?? scheduled).")
                                .font(DMFont.caption)
                                .foregroundStyle(theme.colors.destructive)
                            DMButton(s.cancelDeletion) {
                                Task {
                                    await container.cancelAccountDeletion()
                                    await refreshDeletionState()
                                }
                            }
                        } else if confirmingDeletion {
                            Text(s.deletionConfirmHint)
                                .font(DMFont.caption)
                                .foregroundStyle(theme.colors.mutedForeground)
                            HStack(spacing: theme.spacing.sm) {
                                DMButton(s.confirm, style: .outline) {
                                    confirmingDeletion = false
                                    Task {
                                        await container.requestAccountDeletion()
                                        await refreshDeletionState()
                                    }
                                }
                                DMButton(s.cancel, style: .secondary) { confirmingDeletion = false }
                            }
                        } else {
                            DMButton(s.deleteAccount, style: .outline) { confirmingDeletion = true }
                        }
                    }
                }

                // Version de l'app : utile au support pour qualifier un bug.
                Text("Dual Music \(AppConfig.displayVersion)")
                    .font(DMFont.caption)
                    .foregroundStyle(theme.colors.mutedForeground)
                    .frame(maxWidth: .infinity, alignment: .center)
            }
            .padding(theme.spacing.lg)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .dmScreenBackground()
        .task { await refreshDeletionState() }
    }

    /// Ligne de sélection d'un mode de thème.
    private func themeRow(_ label: String, _ mode: ThemeMode) -> some View {
        selectionRow(label: label, isSelected: container.themeController.mode == mode) {
            container.themeController.set(mode)
        }
    }

    /// Ligne de sélection de langue.
    private func languageRow(_ label: String, _ language: AppLanguage) -> some View {
        selectionRow(label: label, isSelected: container.languageController.language == language) {
            container.languageController.set(language)
        }
    }

    /// Ligne « radio » générique (case cochée + libellé), cliquable sur toute la largeur.
    private func selectionRow(label: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: theme.spacing.sm) {
                Image(systemName: isSelected ? "largecircle.fill.circle" : "circle")
                    .foregroundStyle(isSelected ? theme.colors.primary : theme.colors.mutedForeground)
                Text(label)
                    .font(DMFont.body)
                    .foregroundStyle(theme.colors.foreground)
                Spacer()
            }
            .padding(.vertical, theme.spacing.xs)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? [.isSelected, .isButton] : .isButton)
    }

    /// Relit l'état de suppression du compte auprès du backend.
    private func refreshDeletionState() async {
        guard !isRefreshing else { return }
        isRefreshing = true
        deletionScheduledAt = await container.accountDeletionScheduledAt()
        isRefreshing = false
    }
}
