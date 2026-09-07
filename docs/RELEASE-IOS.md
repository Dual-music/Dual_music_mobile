# Publication iOS — de zéro à l'App Store

Procédure complète de mise en production de l'app Dual Music iOS. Elle suppose le code déjà
présent (`ios/`) et la CI verte.

---

## 0. Ce qu'il faut avoir sous la main

| Élément | Où l'obtenir | Coût |
|---|---|---|
| Compte **Apple Developer Program** | developer.apple.com/programs | 99 $/an |
| Accès à un Mac (ou runner CI macOS) | cf. `GUIDE-TEST-IOS.md` niveau 2 | à l'heure |
| Projet Firebase `dual-music` | console.firebase.google.com | gratuit |
| Backend en HTTPS avec domaine stable | infra existante | — |

> Sans le Programme Développeur, **aucune** distribution n'est possible : ni TestFlight, ni
> App Store. C'est le seul prérequis réellement bloquant.

---

## 1. Créer l'App ID et l'app App Store Connect

1. **developer.apple.com → Certificates, Identifiers & Profiles → Identifiers → +**
   - Type : *App IDs* → *App*
   - Bundle ID (explicit) : `com.dualmusic.app`
   - Capabilities à cocher : **Push Notifications**, **Sign in with Apple** *(uniquement si
     vous ajoutez ce mode de connexion — voir §6)*
2. **appstoreconnect.apple.com → Mes apps → +**
   - Plateforme : iOS, Nom : *Dual Music*, Langue principale : Français
   - Bundle ID : celui créé ci-dessus
   - SKU : `dualmusic-ios`

Puis renseigner l'identifiant d'équipe dans `ios/project.yml` :

```yaml
settings:
  base:
    DEVELOPMENT_TEAM: "ABCDE12345"   # Membership → Team ID
```

---

## 2. Notifications push (APNs → FCM)

Le backend envoie via Firebase : l'app doit donc fournir un **jeton FCM**.

1. **developer.apple.com → Keys → +** : cocher *Apple Push Notifications service (APNs)*,
   télécharger la clé `AuthKey_XXXXXXXX.p8` (**téléchargeable une seule fois**).
2. **Firebase → Paramètres du projet → Vos applications → Ajouter une app → iOS**
   - Bundle ID : `com.dualmusic.app`
   - Télécharger **`GoogleService-Info.plist`** → le placer dans
     `ios/App/Resources/GoogleService-Info.plist`
     *(le fichier est volontairement ignoré par Git : il contient les identifiants du projet)*
3. **Firebase → Paramètres → Cloud Messaging → Configuration de l'app Apple** : téléverser
   la clé `.p8`, avec le **Key ID** et le **Team ID**.

Sans ces étapes, l'app fonctionne mais ne reçoit aucune notification distante — le centre de
notifications in-app (Socket.IO), lui, continue de fonctionner.

---

## 3. Connexion Google (facultatif)

Tant que ce n'est pas configuré, le bouton « Continuer avec Google » est **automatiquement
masqué** et l'app reste pleinement utilisable en email/mot de passe.

1. Dans le même projet Firebase, l'ajout de l'app iOS crée un **client OAuth iOS**.
2. Récupérer, dans `GoogleService-Info.plist` :
   - `CLIENT_ID` → à mettre dans `DM_GOOGLE_CLIENT_ID` (`ios/project.yml`) ;
   - `REVERSED_CLIENT_ID` → à mettre dans `DM_GOOGLE_REVERSED_CLIENT_ID`.
3. `xcodegen generate` pour régénérer le projet.

Côté backend, l'endpoint `POST /auth/oauth/google/native` doit accepter l'**audience** du
client iOS en plus de celle du client Android (ou accepter le Web client ID commun) —
sinon la vérification de l'ID token échouera avec une erreur d'audience.

---

## 4. Build et envoi sur TestFlight

```bash
cd ios
bundle install                 # fastlane + xcpretty (versions épinglées)
xcodegen generate

# Clé API App Store Connect (Users and Access → Integrations → App Store Connect API).
# Elle remplace identifiant Apple + 2FA : indispensable en CI.
export ASC_KEY_ID=XXXXXXXXXX
export ASC_ISSUER_ID=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
export ASC_KEY_CONTENT="$(cat AuthKey_XXXXXXXXXX.p8)"

bundle exec fastlane beta
```

`fastlane beta` :
1. régénère le projet Xcode ;
2. incrémente le numéro de build à `dernier TestFlight + 1` (évite le rejet
   « build number already exists », l'erreur de livraison la plus fréquente) ;
3. compile en Release, signe, archive, exporte l'IPA ;
4. téléverse sur TestFlight avec le dernier message de commit comme note de version.

Le traitement Apple prend 5 à 30 minutes, puis l'app est installable par les **testeurs
internes** (jusqu'à 100 appareils, sans revue). Pour des testeurs externes (jusqu'à 10 000),
Apple procède à une revue légère, généralement sous 24 h.

---

## 5. Soumission App Store

```bash
bundle exec fastlane release
```

La lane **ne soumet pas** automatiquement à la revue (`submit_for_review: false`) :
les métadonnées doivent être relues dans App Store Connect avant d'engager la revue.

### À préparer dans App Store Connect

- **Captures d'écran** : 6,7″ (iPhone 15 Pro Max) et 6,5″ — obligatoires.
- **Description, mots-clés, URL de support, URL de politique de confidentialité.**
- **Confidentialité des données** (*App Privacy*) : déclarer au minimum
  identifiant utilisateur, email, contenus utilisateur, identifiants d'appareil (push),
  données d'usage.
- **Compte de démonstration** : obligatoire, l'app exigeant une connexion. Fournir un
  compte de test **crédité** ainsi que le PIN de retrait dans les notes pour l'équipe de
  revue — sans cela, les fonctions payantes ne sont pas testables et le rejet est certain.
- **Classification par âge** : la présence de chat en direct et de cadeaux payants implique
  une classification adaptée et, en pratique, un **dispositif de signalement/blocage**
  (voir §6).

---

## 6. Points de conformité App Store à traiter avant soumission

Ces points ne bloquent pas la compilation mais sont des **causes classiques de rejet**.
Ils sont listés ici pour décision produit, pas pour être tranchés par le code.

### 6.1 Achats intégrés (règle 3.1.1) — le point le plus sensible

Les crédits Dual Music sont une **monnaie virtuelle dépensée dans l'app** (votes, cadeaux,
billets, déblocage de replays). Apple impose que ce type d'achat passe par **StoreKit**,
avec la commission afférente. La recharge Mobile Money (CinetPay) et l'abonnement Stripe,
tels qu'implémentés, sont conformes sur Android mais **seront très probablement refusés sur
iOS**.

Trois options, par ordre de réalisme :

1. **Ajouter des achats intégrés StoreKit pour la recharge de crédits sur iOS**, en gardant
   CinetPay/Stripe pour le web et Android. C'est la voie standard, prévue par
   `RechargeProvider.appleIAP` déjà présent dans le domaine.
2. **Ne pas proposer la recharge dans l'app iOS** (« reader app » : l'utilisateur recharge
   sur le web, l'app se contente d'utiliser le solde). Attention : aucun lien ni incitation
   vers l'achat externe n'est toléré sans le *External Link Account Entitlement*.
3. Demander l'entitlement de lien externe (disponible dans certaines régions, avec
   commission réduite) — procédure longue.

**Recommandation : option 1.** Le code est prêt à l'accueillir (un module
`FeatureIAP` viendrait s'ajouter à côté de `FeatureWallet`, avec validation du reçu côté
backend).

### 6.2 Contenu généré par les utilisateurs (règle 1.2)

Le chat en direct impose : conditions d'utilisation acceptées, **signalement** d'un contenu,
**blocage** d'un utilisateur, et un engagement de modération sous 24 h. Le backend possède
déjà des rôles de modération ; il manque l'UI de signalement/blocage côté mobile.

### 6.3 Connexion Google → Sign in with Apple (règle 4.8)

Dès qu'une connexion sociale tierce est proposée, Apple exige de proposer **aussi** une
option de connexion respectueuse de la vie privée — en pratique *Sign in with Apple*.
Deux issues : ajouter Sign in with Apple, ou retirer le bouton Google de la build iOS
(il suffit de laisser `DM_GOOGLE_CLIENT_ID` vide : le bouton disparaît).

### 6.4 Suppression de compte (règle 5.1.1(v))

**Déjà conforme** : l'écran Préférences permet de demander la suppression du compte depuis
l'app, avec un délai de grâce de 20 jours et une annulation possible.

### 6.5 Chiffrement

`ITSAppUsesNonExemptEncryption = false` est déclaré : l'app n'utilise que HTTPS/TLS
standard, ce qui évite le questionnaire export à chaque build.

---

## 7. Versionnage

- `MARKETING_VERSION` (ex. `1.0.0`) : version publique, dans `ios/project.yml`.
  `bundle exec fastlane bump type:minor` l'incrémente.
- `CURRENT_PROJECT_VERSION` : numéro de build, géré automatiquement par `fastlane beta`.

Garder les versions **alignées avec Android** pour que le support puisse corréler les
retours utilisateurs entre les deux plateformes.

---

## 8. Après publication

- **Crashs** : App Store Connect → Analytics, et/ou brancher Sentry comme sur Android
  (le DSN est laissé vide côté Android, à activer sur les deux plateformes en même temps).
- **Rollback** : impossible instantanément sur iOS. Une régression bloquante impose une
  nouvelle version + revue accélérée (*expedited review*, à demander avec parcimonie).
  C'est précisément pourquoi le décodage réseau est tolérant : une évolution du backend ne
  doit jamais casser une version déjà publiée.
