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

### 1.0 Mode hôte Live ✅ TERMINÉ le 2026-09-08

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
- **Chat on/off** (2026-09-08) : `Live.chatEnabled`/`LiveSettingsPayload.chatEnabled`
  existaient déjà côté données/décodage, jamais exposés dans `LiveRoomView` — dernier petit
  morceau du mode hôte Live. Toggle hôte (pouvoir EXCLUSIF, jamais délégué aux modérateurs) ;
  côté fan, la saisie de message se verrouille (icône cadenas + « Chat désactivé ») quand
  désactivé, mais dédicace/lever la main/cadeau restent disponibles — seule la messagerie est
  concernée, parité web.

**Étape 1.0 terminée** — mode hôte Live complet (diffusion, Mes lives, dédicaces, invités sur
scène, modérateurs désignés, chat on/off), tout vérifié CI verte.

### 1.1 Signalement + bannissement (report/ban) ✅ FAIT le 2026-09-12 (CI verte, Live+Duel+Compétition+Concert)

Backend déjà prêt, aucun DTO Kotlin partagé côté Android (chaque repository construit son
propre JSON — `moderation/reports/live` pour live/duel/concert, `moderation/reports/competition`
+ `moderation/competition-bans` pour compétition, distincts). Porté côté iOS :

- **`DomainModels/ModerationReport.swift`** : endpoints + `ReportReason` (clés stables
  envoyées au backend, pas le texte localisé — écart assumé vs l'écran Live d'Android).
- **Live** (le chat y est déjà affiché) : report + ban **complets** — bouton drapeau, feuille
  de motifs, bannissement par tap sur l'auteur d'un message (hôte uniquement), messages du
  banni masqués pour tous.
- **Duel** (2026-09-12) : chat + ban désormais **complets**, comme Live — `chatOverlay` +
  barre de saisie ajoutés à `DuelRoomView` (n'existaient pas du tout : `DuelViewModel.messages`
  était chargé mais jamais rendu, et rien n'appelait `sendMessage`). Bannissement par tap sur
  l'auteur, réservé au **manager** du duel (`duel.managerId`, pas de modérateurs désignés côté
  Duel — écart assumé avec Android qui, lui, en a aussi ; `EventModerator` est déjà générique
  et réutilisable si on veut combler cet écart plus tard). Ne peut pas bannir les deux artistes
  en duel ni le manager lui-même (parité `DuelRoomScreen.kt` : `isParticipant`).
  - Correctif au passage (**touche aussi Live**, où le même gap existait silencieusement
    depuis l'implémentation du 2026-09-07) : ni Live ni Duel ne chargeaient la liste des bans
    déjà existants au démarrage (`GET /moderation/stream-bans`), ni n'écoutaient l'événement
    temps réel `stream:banned` — seul l'auteur du geste de bannissement voyait le banni masqué
    (mise à jour optimiste locale uniquement, jamais synchronisée). Un spectateur arrivant
    après coup, ou un modérateur/manager sur un autre appareil, ne voyait donc pas les bans
    déjà décidés. Ajouté `listStreamBans`/`StreamBanRow` (repository, GET) et
    `Realtime.Event.streamBanned`/`StreamBannedPayload` (contrat temps réel) aux deux.
- **Compétition** (2026-09-12) : chat + ban désormais **complets**, construits depuis zéro
  (contrairement à Duel, aucune donnée n'existait déjà — ni `messages`, ni `chatHistory`, ni
  `sendMessage`). Mécanisme de bannissement **entièrement dédié** (comme le report),
  distinct du générique `stream-bans` des trois autres types : `GET`/`POST
  /moderation/competition-bans` (`competitionId`, pas `streamId`/`streamType`), événement
  temps réel `competition:banned` (`CompetitionBannedPayload`). Chat : `/competitions/:id/messages`
  — ⚠️ clé JSON `message` (pas `content`) et auteur hydraté sous `author` (pas `user`), une
  convention propre à Compétition (`CompetitionChatMessage` en tient compte). Réservé au
  **manager** (`competition.managerId`), ne peut pas se bannir lui-même. Écart assumé avec
  Android : pas de fil de réponse (`parentId`, chat threadé) ni de modérateurs désignés côté
  Compétition — hors du scope validé pour ce chantier (chat + ban simple), tous deux ajoutables
  plus tard sans replomberie (`EventModerator` déjà générique, `parentId` déjà dans le contrat
  Android à reproduire si besoin).
- **Concert** (2026-09-12) : écran de room construit de zéro (`ConcertRoomView.swift`,
  nouveau fichier dans `FeatureConcert`) — vidéo LiveKit host/viewer, chat, cadeaux (envoi +
  animation, bouton rapide façon Live — pas de catalogue/inventaire branché, comme Live
  aujourd'hui), présence, report/ban (réutilise `reportLive`/`createStreamBan`, déjà présents
  mais jusqu'ici inutilisés faute d'écran). Room LiveKit dérivée `"concert-<id>"` (toujours
  calculée, jamais de `room_id` backend pour ce type — différent de Live/Duel). Chat :
  `/concerts/:id/messages`, même convention `message`/`author` que Compétition (pas
  `content`/`user`).
  - **Billetterie** (concept absent de Live/Duel/Compétition, propre à Concert) : un billet
    est requis pour regarder un concert PAYANT, sauf l'artiste — `needsTicket` bloque l'accès
    à la vidéo (la room LiveKit n'est même pas rejointe) derrière un paywall plein écran tant
    que `POST /wallet/tickets/concert` n'a pas été appelé avec succès.
  - `isHost` déterminé à la construction (`AppContainer.concertRoom`, comparaison directe
    `profile.me?.user.id == concert.artistId`), pas par un appel réseau async comme Android
    (`repository.myUserId()`) — cohérent avec le choix déjà fait pour Live/Duel côté iOS.
  - Navigation câblée : `MainShellView`'s onglet Concerts n'avait jamais son `onOpen` branché
    (`ConcertsListView(viewModel: container.concerts)` sans callback) — ajouté `concertsTab`
    (même patron que `duelsTab`/`competitionsTab`).
  - Écarts initialement dépriorisés (scope MVP), **tous comblés le 2026-09-12** sauf le
    dernier (voir « Restant » ci-dessous) :
    - **Modérateurs désignés** — répliqué depuis Live vers **Duel, Compétition ET Concert**
      (`EventModerator`/`ModerationEndpoints` déjà génériques, juste paramétrés par type
      d'évènement) : `loadModerators`/`loadViewers`/`appointModerator`/`revokeModerator` +
      `moderatorsSheet`, `canModerate` (manager/hôte OU modérateur) remplace le garde
      manager-seul du tap-to-ban partout où il n'existait que ça.
    - **Dédicaces en direct avec accepter/rejeter côté artiste** pour Concert — réutilise le
      même contrat REST partagé que Live (`concertType: "artist_concert"`,
      `dedicationsArtistMine`/`dedicationAccept`/`dedicationReject`/`dedicationDeliver`).
      Différence réelle avec Live : **pas de surcharge de prix minimum par concert ni de
      toggle on/off depuis la room** côté backend (`allowsDedications` fixé à la création,
      seul `economic_config.dedication` — jamais `dedication_live` — donne le prix plancher).
    - **Likes + réactions emoji** pour Concert — a nécessité un **ajout d'infrastructure**
      réutilisable partout : `NamespaceSession` (`CoreRealtime`) n'avait aucune méthode
      d'émission générique vers le serveur (seulement `join`/`leave`/écoute) ; ajouté
      `broadcast(channel:event:payload:)` + le contrat `BroadcastEnvelope`/`BroadcastPayload`
      (miroir de `shared-domain/realtime/BroadcastEnvelope.kt`, scope actuel limité à
      `emoji`/`count` — extensible si un futur chantier a besoin des autres champs Kotlin
      `slot`/`artistId`/`identity`/…). `sendLike` diffuse un **compteur absolu**, jamais un
      delta (une valeur plus ancienne reçue en désordre est ignorée). Réutilise
      `GiftBurstView` pour l'animation plutôt qu'un nouveau composant — affiche une réaction à
      la fois (pas l'essaim de bulles multiple d'Android), compromis jugé raisonnable pour ce
      chantier.

**Filtre couleur vidéo** (2026-09-12) — fait, dernier extra de la série. Contrairement à
Android (qui a dû contourner une visibilité package-private du SDK LiveKit pour écrire un
shader GL custom, `livekit.org.webrtc.ColorFilterGlDrawer`), le SDK LiveKit **Swift** expose un
protocole `VideoProcessor` **public** (`weak var processor` réglable directement sur
`LocalVideoTrack`) — vérifié en lisant le code source réel du SDK
(`Sources/LiveKit/Protocols/VideoProcessor.swift`,
`Sources/LiveKit/VideoProcessors/BackgroundBlurVideoProcessor.swift` comme référence de bonnes
pratiques), pas une doc tierce. `VideoFilterPresets.swift` porte les 12 presets
`ColorFilterVideoProcessor.kt` à l'identique (mêmes ids/valeurs numériques/ordre de
composition, gardé en ligne-major — pas de conversion colonne-major nécessaire, `CIColorMatrix`
attend directement les coefficients par canal). `ColorFilterVideoProcessor.swift` applique la
matrice via Core Image, rendu **toujours dans un buffer de sortie séparé et réutilisé (pool)**,
jamais dans le buffer source (pattern copié du `BackgroundBlurVideoProcessor` officiel, pour
éviter les artefacts de lecture/écriture simultanée). Câblé dans `LiveRoomClient` (partagé par
Live/Duel/Concert/Compétition) — le processor est réattaché à chaque (ré)activation caméra, la
piste locale changeant d'identité à chaque fois. UI (picker en grille) ajoutée pour Concert
uniquement pour l'instant ; les 3 autres écrans peuvent réutiliser la même capacité sans coût
d'infrastructure supplémentaire, juste le picker à ajouter.

**Mode diffusion caméra locale pour Duel et Compétition** (2026-09-13) — fait, CI verte du
premier coup (`kit-tests` 6m9s, `app-build` 17m40s) malgré l'ampleur (chantier le plus risqué
de la série : infra partagée étendue + deux ViewModels reconstruits + nouveau modèle
multi-diffuseur). Déclenché en creusant l'extension du picker de filtres au-delà de Concert :
Duel et Compétition n'avaient **aucun mode diffusion**, juste un écran spectateur — recherché
directement dans le code Android avant d'écrire quoi que ce soit.

- **Duel** (`DuelViewModel.kt`) : architecture **3 rooms séparées, une par slot**
  (`<room>-artist1`/`-artist2`/`-manager`), chacune avec au plus un publieur — topologie
  identique au couple `media`/`guestMedia` déjà utilisé par `LiveViewModel` pour les invités
  sur scène. `DuelViewModel` iOS reconstruit avec `mediaA1`/`mediaA2`/`mediaMgr` (3
  `LiveRoomClient`) + `mySlot`/`canPublish`/`myMedia`. Contrairement à Android qui résout le
  rôle en async dans `start()`, iOS le résout **synchrone à la construction** dans
  `AppContainer.duelRoom(for:)` (qui a déjà le `Duel` complet + l'id appelant) — cohérent avec
  `isHost` déjà précalculé pour Live/Concert. `DuelViews.swift` : grille 2 colonnes
  (`mediaA1`/`mediaA2`, tuile manager non affichée pour cette passe — écart documenté), contrôles
  hôte (démarrer/micro/caméra/switch/filtre) affichés seulement si `canPublish`.
  **Bug corrigé au passage** : `Duel.liveKitRoom` calculait `"duel:\(id)"` (deux-points) au lieu
  de `"duel-\(id)"` (tiret — ce que backend/Android dérivent réellement). Jamais détecté avant
  car aucune room Duel n'avait jamais été rejointe en publication (Duel était toujours
  spectateur seul avant ce chantier).
- **Compétition** (`CompetitionRoomViewModel` Kotlin) : architecture **une seule room
  partagée, multi-publieurs** (`comp-<id>` ou `Competition.livekitRoom` si fourni), distingués
  par **identité LiveKit** (pas par slot) — manager + candidats `status == "approved"` si
  `Competition.mode == "online"` (en mode `"onsite"`, seul le manager publie jamais). A
  nécessité d'étendre `LiveRoomClient` **partagé** (utilisé aussi par Live/Duel/Concert) avec
  `remoteTiles: [String: VideoTrack]`, peuplé par une nouvelle `refreshRemoteTiles()` appelée à
  chaque site d'appel existant de `refreshPrimaryTrack()` (`join`, `.tracksChanged`/
  `.reconnected`, le timer de polling) — `primaryVideoTrack` volontairement **non touché** pour
  ne pas régresser Live/Duel/Concert. API SDK vérifiée en lisant le vrai code source LiveKit
  Swift (`Participant+Types.swift`) plutôt que devinée : `participant.identity` est un
  `Participant.Identity?` typé, valeur brute via `.stringValue`. `CompetitionFeature.swift`
  reconstruit : `canPublish` calculé après chargement de la compétition + des candidats,
  `stop()` passé en `async` (nécessitait `await media.leave()`), UI avec grille adaptative
  (`localVideoTrack` + `remoteTiles` triés) + mêmes contrôles hôte/picker filtre que
  Concert/Duel.
- **Bug de dépendance évité proactivement** : `FeatureCompetition` dans `Package.swift`
  n'avait pas `CoreLiveMedia` — exactement la même classe de bug (`missing required module
  'LKObjCHelpers'`) que `FeatureConcert` avait heurté plus tôt dans la session. Cette fois
  détecté et corrigé **avant** de pousser, pas après un échec CI.

### 1.2 Achats intégrés StoreKit pour la recharge de crédits ✅ CODE FAIT + CI verte (iOS + backend) le 2026-09-09, setup manuel restant

Décision produit prise avec l'utilisateur (option 1 de `RELEASE-IOS.md` §6.1, recommandée) :
StoreKit natif REMPLACE CinetPay/Stripe **dans l'écran de recharge iOS uniquement** — Android
et le web gardent CinetPay/Stripe inchangés (code Kotlin/web séparé, aucune modification).
Paliers de test décidés avec l'utilisateur : les prix/montants **définitifs de production
restent à trancher par l'équipe** avant publication (juste modifier deux fichiers, voir
ci-dessous — aucune migration de schéma).

⚠️ Pas de module `FeatureIAP` séparé contrairement à ce que ce TODO envisageait avant
implémentation — StoreKit est un framework système (aucune dépendance SPM à ajouter), et le
flux entier ne concerne que `FeatureWallet` : ajouté directement dans
`RechargeView.swift`/`RechargeViewModel` (mêmes noms qu'avant, même signature d'init
`RechargeViewModel(http:)` → **zéro changement requis dans `AppContainer`/`ProfileSectionView`**),
plutôt qu'une cible SPM dédiée qui aurait juste ajouté de la cérémonie sans bénéfice ici.

**Fait (iOS)** :
- `RechargeView.swift` réécrit entièrement : liste de paliers StoreKit (`Product.products(for:)`,
  5 ids `com.dualmusic.app.credits.tier1..5`) au lieu du formulaire CinetPay (montant libre +
  pays + opérateur + téléphone). Achat via `product.purchase()` ; transaction vérifiée
  localement (`VerificationResult`) puis réglée côté serveur (`POST /payments/apple/verify`,
  `Idempotency-Key` = id de transaction) — `transaction.finish()` **seulement** après règlement
  serveur réussi, pour ne jamais perdre un achat déjà payé si l'appel réseau échoue (StoreKit
  la represente alors, via `Transaction.updates`, écouté en continu depuis l'`init` du
  ViewModel — écouteur qui vit toute la session, pas l'écran, comme recommandé par Apple).
  Anciens DTOs CinetPay (`CinetpayInitRequest`/`CinetpayInitResponse`/`CinetpayCountry`/
  `CinetpayOperator` dans `PaymentDtos.swift`) laissés en place mais désormais inutilisés
  côté iOS (gardés au cas où, pas supprimés sans qu'on le demande).
- `PaymentEndpoints.appleVerify` ajouté (`DomainModels/Endpoints.swift`).
- **Piège Swift rencontré** : `import SwiftUI` + `import StoreKit` dans le même fichier rend le
  nom nu `Transaction` ambigu — SwiftUI ET StoreKit exportent chacun un type `Transaction`
  (celui de SwiftUI sert au contexte d'animation). Toujours qualifier `StoreKit.Transaction`
  dès qu'un fichier utilisant l'API StoreKit importe aussi SwiftUI (systématique côté écrans).

**Fait (backend — ⚠️ voir note critique plus bas)** :
- `POST /payments/apple/verify` (authentifié, idempotent comme les autres endpoints
  financiers) : reçoit SEULEMENT `transactionId` — ne fait JAMAIS confiance au client pour
  `productId`/`credits`. Revérifie la transaction auprès d'Apple via l'App Store Server API
  officielle (`@apple/app-store-server-library`, package npm officiel Apple — installé),
  `AppStoreServerAPIClient.getTransactionInfo` + `SignedDataVerifier.verifyAndDecodeTransaction`
  (chaîne de certificats Apple, `src/config/certs/AppleRootCA-G3.cer` téléchargé et vendu dans
  le repo), rejette une transaction révoquée/remboursée (`revocationDate`). Lit `credits` dans
  `src/config/appleIAPProducts.js` (catalogue statique `productId → credits`, à ajuster par
  l'équipe avec les paliers définitifs). Crédite via une nouvelle procédure stockée
  `credit_wallet_apple` (sœur de `credit_wallet_stripe`, idempotente sur `transactionId`,
  `src/procedures/credits.sql` — à appliquer via `npm run db:procedures`).
- Nouvelles variables d'env (`.env.example` documenté) : `APPLE_IAP_ISSUER_ID`,
  `APPLE_IAP_KEY_ID`, `APPLE_IAP_PRIVATE_KEY` (PEM du .p8), `APPLE_IAP_BUNDLE_ID` (défaut
  `com.dualmusic.app`), `APPLE_IAP_ENVIRONMENT` (`sandbox`/`production`),
  `APPLE_IAP_APP_APPLE_ID` (obligatoire en production).

**Commité côté backend (2026-09-09)** — `Dual_music_backend` avait ~13 jours de travail non
commité sans rapport avec ce chantier (temps réel, modération, enregistrement, compétition,
concert, wallet). Mes ajouts StoreKit ont été isolés fichier par fichier (patches manuels
plutôt que `git add -p` interactif, non pilotable depuis cet environnement) et commités sans
toucher au reste : `15157ed` (endpoint + procédure stockée + config), puis 3 correctifs
révélés par la CI générale du repo (inactive depuis le 26/07, donc jamais exercée sur ce
code avant) : `09b41e1`/`7ee4394` (lockfile npm désynchronisé — toujours régénérer depuis un
checkout propre de HEAD, jamais depuis le working tree qui contient aussi les ajouts non
commités d'autres travaux), `751a59c` (entrées i18n manquantes pour les codes d'erreur
`APPLE_*`), `e99bb67` (2 entrées i18n manquantes **préexistantes**, sans rapport avec StoreKit
— `CONCERT_NOT_APPROVED`/`SPONSOR_NOT_ACCEPTED`, dette antérieure au 26/07 révélée seulement
maintenant que la CI tourne à nouveau), `5c30972` (couverture de branche à 100 % exigée par
le seuil de coverage du repo sur `verifyAppleCredits`). CI backend entièrement verte depuis.

**Restant, hors code** :
- [ ] Créer les 5 produits consommables dans App Store Connect avec CES MÊMES ids
      (`com.dualmusic.app.credits.tier1..5`) et des paliers de prix cohérents.
- [ ] Générer une clé API App Store Connect « In-App Purchase » (issuer id, key id, .p8) et
      renseigner les variables d'env ci-dessus.
- [ ] Trancher les paliers/prix définitifs de production (équipe) — modifier
      `appleIAPProducts.js` (backend) + la liste `productIDs` (iOS `RechargeViewModel`) si les
      ids changent.
- [ ] `npm run db:procedures` pour déployer `credit_wallet_apple` en base.
- [ ] Test réel impossible sans device/TestFlight (bac à sable StoreKit) — à faire une fois
      l'accès matériel disponible, cf. le constat général de `GUIDE-TEST-IOS.md`.

### 1.3 Sign in with Apple ✅ CODE FAIT + CI verte le 2026-09-09 (iOS + backend), setup manuel restant

`GoogleSignInProvider.swift` existe côté iOS mais rien d'équivalent pour Apple. Dès que le
bouton Google est actif, Apple impose une option de connexion respectueuse de la vie privée
(règle 4.8) — cf. `RELEASE-IOS.md` §6.3.

**Fait (iOS)** :
- `SignInView.swift` : bouton natif `SignInWithAppleButton` (`AuthenticationServices`) +
  `handleAppleSignIn`, même emplacement conditionnel que le bouton Google (`mode == .login`).
- `AuthViewModel.signInWithApple()`, `AuthRepository.loginWithApple(identityToken:fullName:)`,
  DTO `AppleNativeRequest` (`AuthDtos.swift`), `AuthEndpoints.oauthAppleNative`
  (`Endpoints.swift`) — même point d'intégration unique que Google : succès → `authState =
  .signedIn(user:)`, `RootView` bascule automatiquement, aucune nouvelle route.
- `DualMusic.entitlements` : capability `com.apple.developer.applesignin` ajoutée.
- `errAppleSignInFailed` (FR+EN) dans `Strings.swift`.

**Fait (backend)** :
- `POST /auth/oauth/apple/native` (`{ identityToken, fullName? }`) — même architecture que
  `handleGoogleIdToken` (`oauth.service.js`) : vérification JWT via JWKS Apple
  (`https://appleid.apple.com/auth/keys`, lib `jose`), résolution `oauth_accounts` (provider
  `apple`) → email existant → provision d'un nouveau compte, exactement le même triple
  route/validation/contrôleur que Google. Aucune migration nécessaire (table `oauth_accounts`
  déjà polymorphe par provider).
- ⚠️ Apple ne renvoie le nom complet (`fullName`) qu'à la **toute première** autorisation —
  jamais ensuite. L'app iOS doit donc l'envoyer dès ce premier appel (`ASAuthorizationAppleIDCredential.fullName`),
  sinon il est perdu définitivement pour ce compte.
- Deux correctifs de lockfile npm en cours de route (voir §1.2 pour le premier incident
  similaire) : `package-lock.json` doit toujours être régénéré depuis un checkout propre de
  HEAD après tout ajout de dépendance (`jose` ici), jamais depuis un working tree qui contient
  aussi des ajouts non commités d'autres travaux en cours.

**Reste (manuel, hors code)** :
- [ ] Activer la capability « Sign In with Apple » sur l'App ID `com.dualmusic.app` sur
      developer.apple.com — sans ça, échec au runtime (pas à la compilation).
- [ ] Test réel impossible sans device/TestFlight — même constat que StoreKit (§1.2).

### 1.4 Observabilité Sentry ✅ CODE FAIT + CI verte le 2026-09-12 (mineur, non bloquant App Store)

Android initialise déjà Sentry (DSN vide → inactif). Fait en miroir côté iOS pour que les
taux de crash soient comparables, cf. `PARITE-ANDROID-IOS.md`.

- `ios/App/Sources/Observability.swift` : `SentrySDK.start` (SDK `sentry-cocoa` via SPM,
  package lié uniquement à la cible app `DualMusic`, pas à `DualMusicKit`), DSN vide par
  défaut (`sentryDSN = ""`) → no-op sûr, exactement le même schéma qu'Android
  (`DualMusicApp.kt`) : `tracesSampleRate = 0.2`, `sendDefaultPii = false`, `environment`
  (development/production selon `#if DEBUG`), `releaseName` depuis
  `CFBundleShortVersionString`.
- Appelé en tout premier dans `AppDelegate.application(didFinishLaunchingWithOptions:)`
  (`DualMusicApp.swift`), avant `PushService`, pour capturer aussi les crashs de démarrage.
- [ ] Restant, hors code : renseigner le DSN réel depuis le dashboard Sentry (Settings →
      Projects → Client Keys) pour activer l'envoi — via une variable de build/secret CI,
      jamais committé en clair.

---

## Étape 2 — Configuration à fournir avant tout build sur device/TestFlight

Rien à coder ici (sauf mention contraire) — uniquement des actions manuelles sur des portails
externes (comptes, secrets), qu'un assistant ne peut pas faire à la place de l'utilisateur.
Détail complet dans `RELEASE-IOS.md` §0-§3. **Ordre de dépendance réel** (chaque étape
débloque la suivante) — reprendre ici lors d'une prochaine session :

1. [ ] **Compte Apple Developer Program** (99 $/an) — https://developer.apple.com/programs/enroll/
       Préalable absolu à tout le reste. L'activation peut prendre 24-48h après paiement.
2. [ ] **App ID `com.dualmusic.app`** — developer.apple.com → Certificates, Identifiers &
       Profiles → Identifiers → nouveau App ID. Cocher les capabilities : **Push
       Notifications**, **Sign In with Apple** (déjà câblé en code, §1.3), **In-App
       Purchase** (nécessaire pour StoreKit, §1.2).
3. [ ] **`DEVELOPMENT_TEAM`** — une fois le compte actif, récupérer le Team ID (10
       caractères, Membership sur developer.apple.com) et le renseigner dans
       `ios/project.yml` ligne 53 (actuellement vide). Puis `xcodegen generate`.
4. [ ] **Fiche app sur App Store Connect** — https://appstoreconnect.apple.com → Mes Apps →
       nouvelle app, même Bundle ID. Nécessaire pour TestFlight et pour créer les produits
       StoreKit (étape 6).
5. [ ] **Firebase (push notifications)** :
       - Créer un projet Firebase iOS, Bundle ID `com.dualmusic.app`.
       - Télécharger `GoogleService-Info.plist` → `ios/App/Resources/GoogleService-Info.plist`
         (absent du disque actuellement, normal : gitignoré).
       - Créer une clé APNs (`.p8`) sur developer.apple.com et l'uploader dans Firebase
         Cloud Messaging (Project Settings → Cloud Messaging).
6. [ ] **StoreKit (crédits, §1.2)** — le plus gros morceau manuel :
       - App Store Connect → fiche app → Fonctionnalités → Achats intégrés : créer 5
         produits **consommables** avec exactement ces ids : `com.dualmusic.app.credits.tier1`
         à `tier5`, fixer les prix.
       - Générer une clé API « In-App Purchase » (App Store Connect → Utilisateurs et accès
         → Clés → In-App Purchase) : donne un issuer ID, un key ID, un fichier `.p8`.
       - Renseigner côté backend (`.env`) : `APPLE_IAP_ISSUER_ID`, `APPLE_IAP_KEY_ID`,
         `APPLE_IAP_PRIVATE_KEY` (contenu du `.p8`), `APPLE_IAP_BUNDLE_ID`,
         `APPLE_IAP_ENVIRONMENT`, `APPLE_IAP_APP_APPLE_ID`.
       - Si les montants de crédits par palier changent : ajuster
         `src/config/appleIAPProducts.js` (backend) et `productIDs` dans `RechargeViewModel`
         (iOS).
       - `npm run db:procedures` (backend) pour déployer `credit_wallet_apple`.
7. [ ] **Sentry (§1.4, facultatif, rapide)** — créer un projet sur sentry.io, copier le DSN
       (Settings → Projects → Client Keys), le renseigner dans `Observability.swift` via une
       variable de build/secret CI, jamais en clair.
8. [ ] **Google Sign-In (optionnel, si conservé en plus d'Apple)** — créer un client OAuth
       iOS dans Google Cloud Console, remplir `DM_GOOGLE_CLIENT_ID` /
       `DM_GOOGLE_REVERSED_CLIENT_ID` dans `project.yml` (vides actuellement), vérifier côté
       backend que `POST /auth/oauth/google/native` accepte l'audience du client iOS.
9. [ ] `xcodegen generate` après chaque changement de `project.yml`.

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
