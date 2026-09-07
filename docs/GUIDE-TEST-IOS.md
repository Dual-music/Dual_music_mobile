# Tester l'app iOS Dual Music **sans posséder de Mac ni d'iPhone**

Ce guide part de la situation réelle du projet : le développement se fait sous Windows,
sans matériel Apple. Il décrit, du moins engageant au plus complet, les moyens de vérifier
que l'app compile, fonctionne et se comporte correctement.

> Règle à retenir : **la compilation Swift n'est pas vérifiable sous Windows.** Tout ce qui
> suit vise à faire faire ce travail par une machine macOS distante, automatiquement.

---

## Niveau 1 — CI GitHub (gratuit, à mettre en place en premier)

C'est le filet de sécurité principal, et il ne coûte rien pour un dépôt public
(2 000 min/mois inclus sur un dépôt privé, les runners macOS comptant ×10).

Le workflow `.github/workflows/ios-ci.yml` est déjà écrit. À chaque push sur `main` ou PR
touchant `ios/`, il :

1. lance SwiftLint (non bloquant) ;
2. **compile et exécute les tests unitaires** du paquet sur simulateur iPhone 15 ;
3. **génère le projet Xcode**, compile l'app, exécute les tests UI ;
4. publie les rapports `.xcresult` en artefacts téléchargeables.

### Mise en route

```bash
cd Dual_music_mobile
git add .github/workflows/ios-ci.yml ios
git commit -m "iOS: application native complète + CI macOS"
git push
```

Puis, dans GitHub → onglet **Actions** → « iOS CI ». Un échec affiche l'erreur de
compilation Swift exacte, fichier et ligne compris.

> Astuce : `workflow_dispatch` est activé — on peut relancer la CI depuis l'interface sans
> pousser de nouveau commit.

### Lire un échec

| Symptôme dans les logs | Cause la plus probable |
|---|---|
| `no such module 'X'` | dépendance oubliée dans `Package.swift` |
| `cannot find 'Y' in scope` | `import` manquant en tête de fichier |
| `value of type 'Z' has no member` | API d'un SDK tiers qui a évolué (LiveKit, Socket.IO) |
| `Signing for … requires a development team` | build sur appareil au lieu du simulateur |
| `Unable to find a destination` | nom de simulateur absent de l'image du runner |

---

## Niveau 2 — Mac distant à l'heure (test manuel réel)

Pour **voir** l'app tourner et cliquer dedans, sans acheter de Mac :

| Service | Ordre de prix | Remarque |
|---|---|---|
| MacStadium / MacinCloud | ~1–2 $/h ou ~25 $/mois | Mac dédié ou partagé, accès à distance |
| Scaleway Mac mini | ~0,10 €/h | facturation à l'heure, Xcode à installer |
| AWS EC2 Mac | ~1 $/h | minimum 24 h de réservation |
| Codemagic / Bitrise | gratuit puis à l'usage | build + **simulateur en vidéo** dans le navigateur |

Une fois connecté :

```bash
# Prérequis (une seule fois)
brew install xcodegen
xcode-select --install

git clone <dépôt>
cd Dual_music_mobile/ios
xcodegen generate
open DualMusic.xcodeproj
```

Sélectionner le simulateur **iPhone 15** puis ⌘R.

---

## Niveau 3 — Tester avec le backend

L'app pointe par défaut sur `http://localhost:4000` en configuration `Debug`.

### Simulateur (sur le Mac distant)

`localhost` fonctionne si le backend tourne **sur ce même Mac**. Sinon, exposer le backend
Windows via un tunnel :

```powershell
# Sur le PC Windows, avec le backend démarré sur :4000
npx localtunnel --port 4000
# → https://xyz.loca.lt
```

puis, dans `ios/project.yml`, configuration `Debug` :

```yaml
DM_API_BASE_URL: https://xyz.loca.lt
```

et régénérer : `xcodegen generate`.

> Un tunnel HTTPS évite d'avoir à assouplir App Transport Security.

### iPhone physique

1. Renseigner `DEVELOPMENT_TEAM` dans `ios/project.yml` (identifiant d'équipe Apple
   Developer, 10 caractères).
2. `DM_API_BASE_URL` = IP locale du poste hébergeant le backend, ex.
   `http://192.168.1.20:4000` — **pas** `localhost`, qui désignerait l'iPhone lui-même.
3. Téléphone et backend sur le **même Wi-Fi**.
4. iOS demandera l'autorisation « réseau local » au premier appel (chaîne déjà déclarée
   dans l'`Info.plist`).

---

## Niveau 4 — TestFlight (recommandé pour la recette)

C'est la voie la plus confortable pour faire tester l'app **par de vraies personnes sur
leurs iPhones**, y compris depuis Windows une fois la CI en place.

Prérequis : compte **Apple Developer Program** (99 $/an) — obligatoire pour toute
distribution iOS, même de test.

```bash
cd ios
bundle install
export ASC_KEY_ID=... ASC_ISSUER_ID=... ASC_KEY_CONTENT="$(cat AuthKey_XXXX.p8)"
bundle exec fastlane beta
```

Le build monte sur TestFlight ; les testeurs internes (jusqu'à 100 appareils) l'installent
depuis l'app TestFlight. Détails dans **`RELEASE-IOS.md`**.

---

## Checklist de recette fonctionnelle

À dérouler sur simulateur ou TestFlight, backend accessible. Chaque ligne a son équivalent
Android : les deux doivent se comporter identiquement.

### Authentification
- [ ] Inscription email + mot de passe → réception du code par email
- [ ] Saisie du code → écran « Complète ton profil » (nom, pays, téléphone)
- [ ] « Plus tard » entre bien dans l'app
- [ ] Connexion / déconnexion / reconnexion (session restaurée au relancement)
- [ ] Mot de passe oublié → code → réinitialisation → connexion
- [ ] Bouton Google **masqué** tant que `GIDClientID` est vide ; fonctionnel une fois rempli

### Navigation
- [ ] 5 onglets bas, item actif surligné en rose
- [ ] Accueil : 3 pastilles animées → Lifestyle, Classement, Artistes
- [ ] Cloche → centre de notifications ; retour à l'écran précédent
- [ ] Avatar → profil ; menu → chaque entrée ouvre sa page et revient au menu

### Temps réel et média
- [ ] Onglet Lives : défilement vertical page par page, vidéo uniquement sur la page active
- [ ] Chat live : message envoyé visible chez un second compte
- [ ] Compteur de spectateurs mis à jour
- [ ] Duel : la barre de votes bouge à réception de l'événement `vote`
- [ ] Reconnexion automatique après passage en mode avion puis retour

### Argent (à vérifier avec un compte de test crédité)
- [ ] Solde et contre-valeur € identiques à ceux du web
- [ ] Vote : débit une seule fois même en tapant vite (idempotence)
- [ ] Achat de cadeau : badge « ×N » incrémenté, solde décrémenté
- [ ] Retrait : création du PIN, aperçu du net, refus si PIN erroné
- [ ] Recharge Mobile Money : ouverture de la page de paiement hébergée

### Préférences
- [ ] Thème clair / sombre / système appliqué immédiatement et **persisté**
- [ ] Langue FR/EN appliquée immédiatement, y compris les messages d'erreur
- [ ] Suppression de compte : confirmation, date affichée, annulation possible

### Robustesse
- [ ] Mode avion : messages d'erreur lisibles, aucun écran vide sans explication
- [ ] Backend arrêté : l'app se lance et affiche l'écran de connexion
- [ ] Rotation impossible (portrait verrouillé), pas de contenu tronqué sur iPhone SE
- [ ] Dynamic Type au maximum : aucun texte coupé sur les écrans principaux

---

## Erreurs fréquentes et solutions

**« Le simulateur ne joue pas la vidéo LiveKit »**
Le simulateur iOS n'a pas de caméra et son décodage matériel est limité. La **réception**
d'un flux fonctionne ; la **publication** exige un appareil réel.

**« Aucune notification push sur simulateur »**
Normal : APNs n'est pas disponible en simulateur. Tester sur appareil réel, avec la clé APNs
uploadée dans Firebase.

**« Face ID ne se déclenche pas »**
Dans le simulateur : menu *Features → Face ID → Enrolled*, puis *Matching Face*.

**« L'app se ferme immédiatement au lancement »**
Presque toujours une clé `Info.plist` manquante (autorisation) ou `FirebaseApp.configure()`
sans `GoogleService-Info.plist`. Le code protège déjà ce second cas.
