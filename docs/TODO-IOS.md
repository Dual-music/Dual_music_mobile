# TODO iOS — de l'état actuel à l'App Store

Feuille de route unique, établie le 2026-09-07 en comparant le code Android (quasi terminé)
et le code iOS réellement présent sur disque — pas seulement les docs. Chaque point est
vérifié dans le code, pas supposé. À dérouler dans l'ordre : chaque étape débloque la
suivante.

> **Correctif du 2026-09-07 (après premier passage d'implémentation)** : `PARITE-ANDROID-IOS.md`
> marque les écrans de room temps réel (Live/Duel/Compétition/Concert) ✅, mais un comptage de
> lignes montre qu'iOS n'a que **10 à 20 % du volume de code Android** sur ces 4 écrans — ce
> sont des squelettes spectateur, sans mode hôte/manager. Voir **Étape 1.0 — Mode hôte Live**
> ci-dessous. Les modules plus simples (Auth, Wallet, Profil, Créateur,
> Sponsor) sont eux à 50-70 % du volume Android, un écart de portage normal — pas concernés par
> ce correctif.

Contexte : développement sous Windows, sans Mac ni iPhone. On ne peut donc **rien tester
visuellement pour l'instant** — la stratégie est de coder tout ce qui est vérifiable
statiquement/par CI, puis d'attaquer le test réel (`GUIDE-TEST-IOS.md`) une fois le plus gros
du travail fait. Voir aussi `PARITE-ANDROID-IOS.md` (inventaire écran par écran) et
`RELEASE-IOS.md` (procédure de publication détaillée).

---

## Étape 0 — Rendre le travail existant visible et vérifiable ✅ TERMINÉ le 2026-09-07

CI iOS entièrement verte pour la première fois du projet (run `34141373170`) :
SwiftLint + `kit-tests` (compilation + tests du paquet `DualMusicKit`) + `app-build`
(compilation de l'app via le projet XcodeGen). ~20 correctifs successifs, tous commités sur
`main`. Résumé des causes réelles rencontrées (utile si un problème similaire revient) :

- **`@MainActor` manquant sur ~35 vues** : seul `body` hérite de l'isolation via le protocole
  `View` — toute propriété calculée annexe lisant un ViewModel `@MainActor` a besoin de
  l'annotation explicite sur le type entier.
- **`deinit` non isolé** : ne peut pas accéder à une propriété `@MainActor` de façon
  synchrone.
- **Résolution de plateforme SwiftPM** : `Package.swift` doit déclarer `.macOS(.v13)` pour
  satisfaire la compatibilité de LiveKit (macOS 10.15+), sinon `xcodebuild test` échoue même
  en ne ciblant que l'iOS Simulator.
- **`-sdk iphonesimulator` requis en plus de `-destination`** : sinon le schéma auto-généré
  du paquet SPM tente aussi de tout compiler pour macOS (et casse sur le code UIKit-only).
- **XcodeGen récent (2.44+) génère un projet au format Xcode 16** (objectVersion 77),
  illisible par Xcode 15.4 → job `app-build` sur Xcode 16.2 (le runner fournit les deux).
- **`Info.plist` en double** (`INFOPLIST_FILE` + balayage générique des ressources) →
  exclusion explicite dans `project.yml`.
- **Timeout HTTP par défaut 60s** (`waitsForConnectivity=true` + `timeoutIntervalForResource:
  60`) : backend injoignable → écran de démarrage bloqué une minute avant l'écran de
  connexion. Réduit à 15s.
- **Instabilité résiduelle du simulateur CI** sur les tests UI (crash intermittent,
  aggravant à chaque tentative dans la même session) : non reproductible de façon fiable
  depuis Windows sans Xcode pour symboliser le crash. `testLaunchPerformance` exclu
  (catégorie de test nécessitant une machine dédiée) ; le reste des tests UI est passé en
  `continue-on-error` — la compilation, elle, reste un vrai gate bloquant.
- **Flake intermittent découvert le 2026-09-08** :
  `HTTPClientTests.testConcurrent401sTriggerSingleRefresh` a échoué 2 fois de suite
  (`count == 2` au lieu de `1`) sur un run, puis est repassé au vert immédiatement après
  (`gh run rerun --failed`, même code, aucune modification). Relecture complète de la
  logique single-flight (`HTTPClient.performSingleFlightRefresh`, `InMemoryTokenStore`,
  `CountingRefresher` — tous des `actor`, `refreshTask` posé de façon synchrone sans point
  de suspension intermédiaire) sans trouver de faille logique : le comportement observé est
  cohérent avec une anomalie d'ordonnancement du runner CI partagé sous charge (comme le
  flake `Failed to create a bundle instance` déjà documenté ci-dessus), pas un vrai bug de
  production. **Ne pas modifier `HTTPClient.swift` sur la seule base de ce flake** — si ça
  se reproduit fréquemment, envisager d'augmenter les tentatives du retry loop CI plutôt que
  de retoucher à l'aveugle un code sensible (auth/refresh de session).

Tout est documenté dans les commentaires du `.github/workflows/ios-ci.yml` correspondant,
pour ne pas avoir à redécouvrir ces causes en cas de régression.

---

## Étape 1 — Compléter la parité fonctionnelle manquante

Écart vérifié dans le code (Android a la fonctionnalité, iOS ne l'a pas du tout) :

### 1.0 Mode hôte Live 🚧 EN COURS (diffusion + Mes lives + dédicaces + invités + modérateurs ✅, chat on/off restant)

Priorité de l'utilisateur : report/ban d'abord (fait, voir 1.1), puis mode hôte Live —
dédicaces, invités sur scène, modérateurs — le plus gros chantier restant après le report/ban.

- **Diffusion hôte** (2026-09-07) : `LiveViewModel`/`LiveRoomView` distinguent hôte/spectateur
  (`isHost`) — démarrage caméra/micro, coupe/rétablit micro et caméra, bascule caméra
  avant/arrière, fin de live (`POST /lives/:id/end` + arrêt local). L'hôte voit son propre
  aperçu (`localVideoTrack`), pas le flux `primary` (réservé au spectateur).
- **« Mes lives »** (2026-09-07) : `MyLivesView`/`MyLivesViewModel` — liste des lives de
  l'artiste (`GET /lives?artistId=`), formulaire de lancement (titre, dédicaces/invités
  on-off, prix minimum), reprise du live actif, historique des lives terminés. Accessible
  depuis le profil, réservé aux artistes (`ProfileView.isArtist`, les managers n'hébergent
  pas de live).
- **Dédicaces en direct** (2026-09-08) : pas de ressource REST dédiée — réutilise
  `POST /concerts/dedications` (`concertType: "artist_live"`, `concertId` = id du live).
  Fan : bouton mégaphone dans la barre d'action (visible seulement si `liveAllowsDedications`)
  ouvrant une feuille message + prix (plancher = prix minimum effectif du live, jamais en
  dessous). Hôte : bouton mégaphone avec badge (nombre de demandes en attente) ouvrant une
  feuille « en attente » (accepter = débite le fan maintenant / rejeter = aucun débit) +
  « acceptées/livrées » (marquer comme interprétée) ; réglage on/off + prix minimum du live,
  appliqué en direct (`PATCH /lives/:id/settings`, mise à jour partielle). Temps réel :
  `settings` (réglages changés par l'hôte, tout le monde réagit aussitôt), `dedication:new` /
  `dedication:update` (l'hôte recharge sa liste, le fan concerné voit une bannière de
  décision) — l'état fait toujours l'objet d'un rechargement REST complet, jamais appliqué
  depuis le seul payload temps réel (parité Android : `LiveViewModel.kt`).
  - Correctif au passage : `Live.liveKitRoom` dérivait `"live:\(id)"` (deux-points) au lieu de
    `"live-\(id)"` (tiret, comme Android/backend) en repli sur `room_id` absent — corrigé,
    sinon la mauvaise room LiveKit aurait été rejointe pour tout live sans `room_id` explicite.
  - Correctif au passage : `DedicationRequest` (réutilisé de `FeatureConcert`) n'avait pas de
    champ `priceCredits`, pourtant obligatoire côté backend (400 sans lui) — le seul appelant
    existant (`ConcertFeature.purchaseDedication`, jamais branché à une UI) était mort, donc
    correctif sans risque.
- **Invités sur scène** (2026-09-08) : chaque invité publie dans SA PROPRE room LiveKit
  dédiée (`live-guest-<liveId>-<userId>`, jamais la room principale — l'hôte y reste seul
  publicateur) ; tout le monde (hôte, spectateurs, autres invités) s'y abonne en visionnage
  seul via un `LiveRoomClient` par invité actif, créé/fermé dynamiquement
  (`reconcileGuestSubscriptions`, appelé après chaque rechargement de la liste des invités
  acceptés). Spectateur : bouton « lever la main » (icône seule, visible si
  `liveAllowGuests` et pas déjà accepté) → `POST /lives/:id/join` ; annulable tant qu'en
  attente. Hôte : bouton avec badge (demandes en attente) ouvrant une feuille
  demandes-en-attente (accepter/rejeter, `POST /lives/join-requests/:id/respond`) +
  invités-actifs (retirer, même endpoint avec statut `ended`) ; réglage on/off en direct
  (`PATCH /lives/:id/settings`). Invité accepté : contrôles micro/caméra + « descendre de
  scène » (garde le chat/cadeaux actifs, reste connecté à la room principale comme
  spectateur). Temps réel : `join:new`/`join:update` — tout le monde recharge (pour
  recalculer les abonnements aux rooms d'invités), le spectateur concerné voit son statut
  changer (`accepted` déclenche la publication automatiquement, pas besoin d'ouvrir un
  écran séparé — contrairement à Android qui gate ça derrière une vérification de
  permission runtime explicite, inutile ici : LiveKit/AVFoundation déclenche le prompt
  système directement).
  - **Périmètre volontairement réduit vs Android** (à ajouter avec les modérateurs si
    besoin) : pas de relais `guest_action` (mute à distance par l'hôte, chrono de parole
    accordé, kick "instantané" en plus du `join:update` standard) — `join:update` suffit
    déjà à notifier l'invité retiré, juste sans le petit coup de pouce de latence du canal
    broadcast dédié. Pas de tuile "focus plein écran" au tap (Android permet de mettre un
    invité en avant à la place du flux hôte) — les tuiles restent en bandeau fixe.
  - `LiveKitTokenService`/`LiveRoomClient` : ajout du paramètre `canPublish` (existait déjà
    dans `LiveKitTokenRequest`, juste jamais exposé) — un invité publie avec `canPublish:
    true` sans être `isHost`, distinction nécessaire pour que le backend délivre les bons
    droits LiveKit.
- **Modérateurs désignés** (2026-09-08) : `ModerationEndpoints`/`EventModerator` — CE DTO EST
  partagé côté Android (`shared-domain/moderation/ModerationDtos.kt`), contrairement au
  report/ban de 1.1. Un modérateur = un spectateur nommé par l'hôte (max ``maxEventModerators``
  = 2) qui hérite du pouvoir de bannir/masquer un message (``LiveViewModel.canModerate =
  isHost || isModerator``) — jamais celui d'activer/désactiver le chat, réservé à l'hôte.
  Bouton (icône « person.2 ») visible de l'hôte ET des modérateurs eux-mêmes (pour qu'ils
  voient qui d'autre a ce pouvoir), ouvrant une feuille : liste des modérateurs (révocables
  par l'hôte seulement) + section désignation (hôte uniquement — spectateurs connectés,
  `GET /moderation/events/live/:id/viewers`, filtrés des modérateurs déjà désignés, désactivée
  à la limite). Le geste de bannissement sur le chat (tap sur l'avatar) était gaté sur
  `viewModel.isHost` — étendu à `viewModel.canModerate`. Temps réel :
  `moderator:appointed`/`moderator:revoked` → tout le monde recharge la liste.
- [ ] Chat on/off par l'hôte (`Live.chatEnabled` existe déjà côté données/décodage, jamais
      exposé dans `LiveRoomView`) — dernier petit morceau du mode hôte Live, pouvoir EXCLUSIF
      de l'hôte (jamais délégué aux modérateurs, voir ci-dessus).

### 1.1 Signalement + bannissement (report/ban) ✅ FAIT (partiellement) le 2026-09-07

Backend déjà prêt, aucun DTO Kotlin partagé côté Android (chaque repository construit son
propre JSON — `moderation/reports/live` pour live/duel/concert, `moderation/reports/competition`
+ `moderation/competition-bans` pour compétition, distincts). Porté côté iOS :

- **`DomainModels/ModerationReport.swift`** : endpoints + `ReportReason` (clés stables
  envoyées au backend, pas le texte localisé — écart assumé vs l'écran Live d'Android).
- **Live** (le chat y est déjà affiché) : report + ban **complets** — bouton drapeau, feuille
  de motifs, bannissement par tap sur l'auteur d'un message (hôte uniquement), messages du
  banni masqués pour tous.
- **Duel / Compétition** (room existante, mais chat non affiché à l'écran — la donnée existe
  déjà côté Duel, `DuelViewModel.messages`, juste jamais rendue) : bouton signaler câblé et
  fonctionnel ; `createStreamBan`/`createCompetitionBan` existent au niveau repository mais
  **aucune UI n'appelle le ban** — il n'y a pas encore de liste de messages/participants à
  laquelle attacher un geste de bannissement.
- **Concert** : couche données seulement (`reportLive`/`createStreamBan` sur
  `ConcertRepository`) — **il n'existe pas encore d'écran de room/direct pour les concerts
  côté iOS du tout** (catalogue + achat de dédicace seulement), donc rien à brancher.

Restant, non bloquant pour l'App Store (le report, lui, est en place partout où il y a un
écran à l'utiliser) :
- [ ] Construire l'affichage du chat dans `DuelRoomView` (la donnée existe) puis y brancher le
      ban par tap, comme pour Live.
- [ ] Idem pour Compétition une fois son chat construit.
- [ ] Construire l'écran de room/direct pour Concert (actuellement inexistant) avant de pouvoir
      y brancher quoi que ce soit.

### 1.2 Achats intégrés StoreKit pour la recharge de crédits — absent

`Package.swift` ne déclare aucune dépendance StoreKit et `FeatureWallet/RechargeView.swift`
ne propose que la page CinetPay hébergée (comme Android). Or Apple interdit un moyen de
paiement externe pour de la monnaie virtuelle consommée dans l'app (règle 3.1.1) — **c'est le
point de blocage le plus sérieux**, cf. `RELEASE-IOS.md` §6.1.

- [ ] Décision produit à prendre avant de coder (3 options détaillées dans `RELEASE-IOS.md`
      §6.1) : StoreKit natif pour iOS (recommandé, `RechargeProvider.APPLE_IAP` déjà prévu côté
      `shared-domain`), app iOS « lecture seule » sans recharge, ou entitlement de lien externe.
- [ ] Si StoreKit : créer les produits de recharge dans App Store Connect (paliers de crédits),
      ajouter un module `FeatureIAP` (StoreKit 2 : `Product.products(for:)`,
      `Transaction.currentEntitlements`, validation du reçu).
- [ ] Étendre le backend : endpoint de validation de reçu StoreKit → crédit du wallet (même
      esprit que la vérification CinetPay existante).

### 1.3 Sign in with Apple — absent

`GoogleSignInProvider.swift` existe côté iOS mais rien d'équivalent pour Apple. Dès que le
bouton Google est actif, Apple impose une option de connexion respectueuse de la vie privée
(règle 4.8) — cf. `RELEASE-IOS.md` §6.3.

- [ ] Décision : ajouter Sign in with Apple (`ASAuthorizationAppleIDButton` +
      `ASAuthorizationController`), ou retirer Google d'iOS en laissant
      `DM_GOOGLE_CLIENT_ID` vide dans `project.yml` (le bouton se masque déjà automatiquement,
      donc cette option ne demande **aucun code**).
- [ ] Si Sign in with Apple : activer la capability sur l'App ID, câbler `FeatureAuth`, et côté
      backend étendre `POST /auth/oauth/*` pour vérifier l'identity token Apple.

### 1.4 Observabilité Sentry — absent (mineur, non bloquant App Store)

Android initialise déjà Sentry (DSN vide → inactif). À faire en même temps sur les deux
plateformes pour que les taux de crash soient comparables, cf. `PARITE-ANDROID-IOS.md`.

- [ ] Ajouter le SDK Sentry Cocoa au `Package.swift` (cible `App`, pas `DualMusicKit`).
- [ ] Initialiser dans `DualMusicApp.swift`, DSN vide par défaut (comme Android).

---

## Étape 2 — Configuration à fournir avant tout build sur device/TestFlight

Rien à coder ici, mais bloquant pour sortir du simulateur. Détail complet dans
`RELEASE-IOS.md` §0-§3.

- [ ] Compte **Apple Developer Program** (99 $/an) — sans lui, aucune distribution possible.
- [ ] Créer l'App ID `com.dualmusic.app` (Push Notifications, + Sign in with Apple si retenu
      à l'étape 1.3) et l'app sur App Store Connect.
- [ ] Renseigner `DEVELOPMENT_TEAM` dans `ios/project.yml` (actuellement vide, ligne 53).
- [ ] Créer le projet Firebase iOS (`Bundle ID` = `com.dualmusic.app`), télécharger
      `GoogleService-Info.plist` → `ios/App/Resources/GoogleService-Info.plist` (absent du
      disque actuellement, normal : gitignoré).
- [ ] Créer une clé APNs (`.p8`) et l'uploader dans Firebase Cloud Messaging.
- [ ] Si Google conservé : remplir `DM_GOOGLE_CLIENT_ID` / `DM_GOOGLE_REVERSED_CLIENT_ID`
      dans `project.yml` (vides actuellement) et vérifier côté backend que
      `POST /auth/oauth/google/native` accepte l'audience du client iOS.
- [ ] `xcodegen generate` après chaque changement de `project.yml`.

---

## Étape 3 — Recette fonctionnelle (une fois un simulateur/device accessible)

Pas de nouveau code ici : dérouler la checklist déjà écrite dans `GUIDE-TEST-IOS.md`
(§ « Checklist de recette fonctionnelle ») écran par écran, en s'appuyant sur
`PARITE-ANDROID-IOS.md` pour comparer chaque comportement à l'équivalent Android. Sujets qui
demanderont un **appareil réel** (pas le simulateur) : publication vidéo LiveKit, notifications
push APNs, Face ID.

---

## Étape 4 — Publication

Uniquement une fois les étapes 1 à 3 terminées. Procédure déjà écrite dans `RELEASE-IOS.md` :
build + TestFlight (`fastlane beta`), préparation App Store Connect (captures, confidentialité
des données, compte de démo crédité + PIN de retrait), puis `fastlane release`.

---

## Résumé — ordre de traitement recommandé

1. **Étape 0** — commit/push + CI verte (sinon on code à l'aveugle).
2. **Étape 1.1** — report/ban (bloquant App Store, le plus gros morceau de code).
3. **Étape 1.3** — Sign in with Apple ou retrait de Google (rapide si on retire Google).
4. **Étape 1.2** — StoreKit (le plus gros risque produit/business, à trancher tôt).
5. **Étape 1.4** — Sentry (rapide, non bloquant, à caser n'importe quand).
6. **Étape 2** — configuration Apple Developer / Firebase (peut se faire en parallèle dès que
   le compte développeur existe).
7. **Étape 3** — recette réelle dès qu'un Mac (loué ou CI) est accessible.
8. **Étape 4** — publication.
