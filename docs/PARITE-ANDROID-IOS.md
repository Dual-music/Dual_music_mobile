# Parité Android ↔ iOS

Inventaire écran par écran, établi en comparant le code des deux plateformes. Sert de base
de recette et de suivi de dette.

Légende : ✅ identique · 🟰 équivalent (implémentation native différente, même résultat) ·
⚠️ écart assumé · ❌ absent

---

## Modules socle

| Module | Android | iOS | État | Note |
|---|---|---|---|---|
| Domaine partagé | `:shared-domain` (KMM) | `DomainModels` (miroir Swift) | 🟰 | Contrat unique : `shared-domain` fait foi |
| Client HTTP | Ktor/OkHttp | `URLSession` (actor) | 🟰 | Enveloppe, Bearer, refresh single-flight, idempotence : identiques |
| Stockage jetons | EncryptedSharedPreferences | Keychain | 🟰 | iOS : non synchronisé iCloud, non restaurable |
| Temps réel | socket.io-client-java | socket.io-client-swift | ✅ | Mêmes namespaces, rooms et événements |
| Vidéo live | LiveKit Android | LiveKit Swift | 🟰 | `SurfaceViewRenderer` → `SwiftUIVideoView` |
| Upload | presign → PUT → confirm | idem | ✅ | |
| Biométrie | BiometricPrompt | Face ID / Touch ID | ✅ | |
| Design system | Compose | SwiftUI | ✅ | Mêmes HSL, rayons, espacements, dégradés |
| i18n FR/EN | `Strings.kt` | `DMStrings` | ✅ | Mêmes clés ; iOS en a quelques-unes de plus (voir écarts) |

---

## Écrans

| Écran | Android | iOS | État |
|---|---|---|---|
| Connexion / inscription / mot de passe oublié / reset | ✅ | ✅ | ✅ |
| Vérification email (étape 2/3) | ✅ | ✅ | ✅ |
| Complétion de profil (étape 3/3) | ✅ | ✅ | ✅ |
| Connexion Google | Credential Manager | GoogleSignIn-iOS | 🟰 |
| Accueil + 3 accès rapides animés | ✅ | ✅ | ✅ |
| Feed vertical de lives | `VerticalPager` | défilement paginé iOS 17 | 🟰 |
| Room de live (chat, cadeaux, présence) | ✅ | ✅ | ✅ |
| Catalogue de duels | ✅ | ✅ | ✅ |
| Room de duel (votes, minuteur, cadeaux) | ✅ | ✅ | ✅ |
| Catalogue de concerts | ✅ | ✅ | ✅ |
| Catalogue de compétitions | ✅ | ✅ | ✅ |
| Room de compétition (classement, vote) | ✅ | ✅ | ✅ |
| Profil + statistiques par rôle | ✅ | ✅ | ✅ |
| Menu « Mon espace » (gating par rôle) | ✅ | ✅ | ✅ |
| Portefeuille (achats / dépenses / revenus) | ✅ | ✅ | ✅ |
| Recharge Mobile Money | ✅ | ✅ | ⚠️ conformité App Store, cf. `RELEASE-IOS.md` §6.1 |
| Retrait (PIN, méthodes, net, historique) | ✅ | ✅ | ✅ |
| Boutique de cadeaux | ✅ | ✅ | ✅ |
| Replays (catalogue + lecteur + paywall) | ExoPlayer | `AVPlayer` | 🟰 |
| Notifications in-app | ✅ | ✅ | ✅ |
| Classements (artistes / donateurs / saisons) | ✅ | ✅ | ✅ |
| Parrainage | copie du code | copie **+ partage natif** | ⚠️ iOS en avance |
| Abonnements | ✅ | ✅ | ⚠️ conformité App Store |
| Contenu Lifestyle | ✅ | ✅ | ✅ |
| Contenu Blog | composables présents mais **non branchés** | onglet Blog fonctionnel | ⚠️ **iOS en avance** |
| Annuaire d'artistes + suivi | ✅ | ✅ | ✅ |
| Sponsoring (tarifs, demandes, création) | ✅ | ✅ | ✅ |
| Espace créateur (défis, concerts, création) | ✅ | ✅ | ✅ |
| Espace admin | ✅ | ✅ | ✅ |
| Préférences (thème, langue, suppression) | ✅ | ✅ + version de l'app | ⚠️ iOS en avance |
| Push | FCM | APNs → FCM | 🟰 |

---

## Écarts assumés, et pourquoi

### iOS en avance sur Android

1. **Onglet Blog** — `ContentScreen.kt` contient `BlogRow` et `BlogDetail`, mais le corps du
   composable n'affiche que les vidéos : les articles ne sont donc pas atteignables sur
   Android. Les DTOs, les endpoints et les chaînes existent pourtant des deux côtés.
   L'implémentation iOS branche les deux onglets.
   👉 *Action Android suggérée : ajouter le `TabRow` Lifestyle/Blog dans `ContentScreen`.*

2. **Partage natif du code de parrainage** — Android se limite au presse-papiers ; iOS ajoute
   un `ShareLink` (feuille de partage système), sans retirer la copie.
   👉 *Action Android suggérée : ajouter un `Intent.ACTION_SEND`.*

3. **Version de l'app affichée dans les Préférences** — utile au support pour qualifier un
   retour utilisateur.

4. **URL du backend en configuration de build** — Android la code en dur dans
   `MainActivity.kt` (`private const val API_BASE_URL = "http://172.21.169.43:4000"`), ce qui
   oblige à modifier le code source pour changer d'environnement, avec le risque de committer
   une IP de développement dans une release. iOS la lit depuis l'`Info.plist`, alimenté par
   les build settings `Debug`/`Release`.
   👉 *Action Android suggérée : passer par `buildConfigField` dans `build.gradle.kts`.*

5. **Décodage tolérant des montants en chaîne** — le driver PostgreSQL sérialise volontiers
   `numeric` en chaîne ; iOS accepte les deux formes, Android échouerait.
   👉 *Action Android suggérée : sérialiseur personnalisé pour les champs monétaires.*

### Android en avance sur iOS

1. **Observabilité Sentry** — Android initialise Sentry (DSN vide, donc inactif). iOS ne
   l'embarque pas encore : à activer sur les deux plateformes en même temps, avec le même
   DSN, pour que les taux de crash soient comparables.

### Écarts techniques sans impact fonctionnel

| Point | Android | iOS | Pourquoi |
|---|---|---|---|
| Halo des cadeaux | shader AGSL | dégradé radial animé | Évite d'embarquer un `.metal` dans le paquet ; même rendu, un risque de build en moins |
| Barre de navigation basse | `NavigationBar` Material | composant maison | Le style système iOS ne permet pas la barre colorée en dégradé |
| Images distantes | téléchargement maison | `AsyncImage` | Cache URLSession intégré, moins de code |
| Sélection de langue par défaut | français | langue de l'appareil si FR/EN, sinon français | Convention iOS |

---

## Points à traiter avant publication iOS

Par ordre de blocage (détails dans `RELEASE-IOS.md` §6) :

1. **Achats intégrés (StoreKit)** pour la recharge de crédits — sinon rejet quasi certain.
2. **Signalement / blocage** d'utilisateur dans le chat — exigence sur tout contenu
   généré par les utilisateurs.
3. **Sign in with Apple** si le bouton Google est conservé — sinon retirer Google de la
   build iOS (laisser `DM_GOOGLE_CLIENT_ID` vide suffit).
4. `GoogleService-Info.plist` + clé APNs dans Firebase pour les notifications.
5. `DEVELOPMENT_TEAM` renseigné dans `ios/project.yml`.
