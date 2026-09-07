# TODO iOS — de l'état actuel à l'App Store

Feuille de route unique, établie le 2026-09-07 en comparant le code Android (quasi terminé)
et le code iOS réellement présent sur disque — pas seulement les docs. Chaque point est
vérifié dans le code, pas supposé. À dérouler dans l'ordre : chaque étape débloque la
suivante.

> **Correctif du 2026-09-07 (après premier passage d'implémentation)** : `PARITE-ANDROID-IOS.md`
> marque les écrans de room temps réel (Live/Duel/Compétition/Concert) ✅, mais un comptage de
> lignes montre qu'iOS n'a que **10 à 20 % du volume de code Android** sur ces 4 écrans — ce
> sont des squelettes spectateur, sans mode hôte/manager. Voir **Étape 1.0** ci-dessous, ajoutée
> avant le reste de l'étape 1. Les modules plus simples (Auth, Wallet, Profil, Créateur,
> Sponsor) sont eux à 50-70 % du volume Android, un écart de portage normal — pas concernés par
> ce correctif.

Contexte : développement sous Windows, sans Mac ni iPhone. On ne peut donc **rien tester
visuellement pour l'instant** — la stratégie est de coder tout ce qui est vérifiable
statiquement/par CI, puis d'attaquer le test réel (`GUIDE-TEST-IOS.md`) une fois le plus gros
du travail fait. Voir aussi `PARITE-ANDROID-IOS.md` (inventaire écran par écran) et
`RELEASE-IOS.md` (procédure de publication détaillée).

---

## Étape 0 — Rendre le travail existant visible et vérifiable (bloquant, à faire en premier)

Constat du `git status` : **tout le code iOS actuel (`ios/App`, `ios/Packages/DualMusicKit`,
`ios/project.yml`, `ios/fastlane`, `.github/workflows/ios-ci.yml`) est non commité.** L'ancienne
arborescence (`ios/Packages/CoreAuth`, `CoreMedia`, `CoreNetwork`, `CoreRealtime`, `CoreUI`,
`FeatureAuth`, `FeatureFeed`, `FeatureLive`, un `Package.swift` par module) est marquée
supprimée : elle a été remplacée par le paquet unique `DualMusicKit` décrit dans
`ARCHITECTURE-IOS.md`. Tant que rien n'est poussé sur GitHub :

- la CI (`ios-ci.yml`) ne s'est **jamais exécutée** — on ne sait pas si le code compile ;
- aucune des pistes de `GUIDE-TEST-IOS.md` (Mac loué, TestFlight) n'est utilisable.

- [ ] Vérifier que `shared-domain/` compile toujours côté Android après les modifs en cours
      (fichiers modifiés : `CompetitionDtos.kt`, `ConcertEndpoints.kt`, `CreatorDtos.kt`,
      `Live.kt`, `Models.kt`, `BroadcastEnvelope.kt`, `RealtimeContract.kt`, `RecordingDtos.kt`,
      `ReplayDtos.kt`, + nouveau dossier `moderation/`) : `./gradlew :shared-domain:build`.
- [ ] Commit + push de tout `ios/`, `.github/workflows/ios-ci.yml` et des docs `??` listées.
- [ ] Sur GitHub → onglet **Actions**, attendre/lancer « iOS CI » et corriger tout ce qui casse
      (`lint` non bloquant, `kit-tests` et `app-build` doivent passer au vert).
- [ ] Ne pas avancer sur la suite tant que `app-build` n'est pas vert : c'est la seule preuve
      que Swift compile réellement.

---

## Étape 1 — Compléter la parité fonctionnelle manquante

Écart vérifié dans le code (Android a la fonctionnalité, iOS ne l'a pas du tout) :

### 1.1 Signalement + bannissement (report/ban) — absent d'iOS, sur 4 écrans

Backend déjà prêt (`POST /moderation/reports/live`, `POST /moderation/stream-bans`), Android
déjà branché sur les 4 repositories (`LiveRepository`, `DuelRepository`,
`CompetitionRepository`, `ConcertRepository`). Côté iOS, **aucun** des DTOs ni endpoints
n'existe dans `DomainModels`, et aucune UI dans `FeatureLive`/`FeatureDuel`/
`FeatureCompetition`/`FeatureConcert`.

- [ ] Porter les DTOs dans `DomainModels` : mirror de `shared-domain/.../moderation/ModerationDtos.kt`
      (`EventModerator`, `ModerationEndpoints`, `AppointModeratorBody`) + les deux endpoints à
      chaîne fixe utilisés par Android (`/moderation/reports/live`, `/moderation/stream-bans`).
- [ ] Ajouter `reportLive(reason:)` et `createStreamBan(streamId:bannedUserId:reason:)` dans les
      repositories iOS équivalents (`LiveRepository`, `DuelRepository`,
      `CompetitionRepository`, `ConcertRepository` — dans `CoreNetwork`/`Feature*`).
- [ ] UI côté `FeatureLive` : bouton « Signaler » (icône drapeau, cf. Android
      `LiveRoomScreen.kt:670`), feuille de motifs (inapproprié / harcèlement / spam / violence,
      cf. `reportInappropriate`/`reportHarassment`/`reportSpam`/`reportViolence` déjà présents
      dans `DMStrings`), et bannissement d'un spectateur par tap sur l'avatar (hôte/modérateur
      désigné seulement) avec confirmation.
- [ ] Répliquer la même UI dans `FeatureDuel`, `FeatureCompetition`, `FeatureConcert`.
- [ ] C'est un **bloquant App Store** (règle 1.2, UGC) — cf. `RELEASE-IOS.md` §6.2.

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
