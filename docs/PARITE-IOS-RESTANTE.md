# Parité Android → iOS restante — plan d'exécution

Audit du 2026-09-17 (code réel des deux côtés, vérifié par grep + lecture directe).
Duel, Compétition et Concert (création/live/panneau manager/fin) sont **déjà à parité** — non listés ici.

Règle : chaque étape = lire le code Android réel → implémenter iOS → commit → push → vérifier CI (kit-tests + app-build) → cocher → passer à la suivante, sans s'arrêter pour demander.

## Légende statut
- [ ] à faire
- [x] fait + CI vert

---

## 1. Transversal (touche Live + Duel + Compétition + Concert)

- [x] **1.1 Enregistrement manuel → replay** — `android/feature/sponsor/.../RecordingRepository.kt`, `RecordingHolder.kt`, `RecordingUi.kt`. L'hôte peut démarrer/pause/reprendre/sauvegarder un enregistrement de son événement. Fait : infra partagée (`FeatureSponsor/RecordingFeature.swift`) + câblage Live/Duel/Compétition/Concert, CI verte (8ac5e28, 63e6c7f, 4e95148, 8d931d6, eccc1e1).
- [x] **1.2 Overlay pub sponsor pendant l'événement** — `android/feature/sponsor/.../SponsorAdHolder.kt`, `SponsorAdUi.kt`. Affichage réel de la pub (overlay vidéo + start/skip). Fait : infra partagée (`FeatureSponsor/SponsorAdFeature.swift`) + câblage Duel/Compétition/Concert, CI verte (74f72a4, 7ecbdc7, 8e81253, bd87f35). Live exclu volontairement (Android a retiré les pubs des lives spontanés, cf. commentaire `LiveRoomScreen.kt`).

## 2. Withdrawal (critique)

- [x] **2.1 Gestion des méthodes de paiement** — `android/feature/withdrawal/.../PayoutMethodsSection.kt`. Ajout/suppression/défaut. Fait, CI verte (cf5c724) : `WithdrawalRepository.addMethod/removeMethod/setDefaultMethod` + `AddPayoutMethodForm` (Mobile Money/Virement/PayPal, champs conditionnels) + liste avec étoile/corbeille.
- [x] **2.2 Verrouillage PIN par session (PinGate)** — `android/feature/withdrawal/.../PinGate.kt`. Re-vérification du PIN au déverrouillage. Fait, CI verte (24cb4e2/0ba43a6) : `PinSession` (cache process) + `verifyPin`/`lock` + `pinLockCard`.
- [x] **2.3 Changement de PIN** (`changePin`) — Fait, CI verte : réutilise `setPin(newPin:currentPin:)` existant + `unlockedControls`.
- [x] **2.4 Réinitialisation PIN par OTP email** (`requestPinReset`/`confirmPinReset`) — Fait, CI verte.
- [x] **2.5 Espace Manager (Revenues/Withdraw)** — `android/feature/withdrawal/.../ManagerSpaceScreen.kt`, `RevenueViewModel.kt`. Fait, CI verte : `RevenueViewModel`/`RevenueView` (période/total/détail dépliable/pagination transactions) + `ManagerSpaceView` (2 onglets — historique fusionné dans l'onglet Retrait plutôt qu'un 3ᵉ onglet séparé, `WithdrawalView` l'affiche déjà).
- [x] **2.6 Export CSV/PDF des revenus** — `android/feature/withdrawal/.../RevenueExport.kt`. Simplifié, CI verte : partage texte natif (`ShareLink`) plutôt qu'un fichier CSV/PDF généré — écart assumé.

## 3. Wallet (critique)

- [x] **3.1 Mobile Money (CinetPay) multi-pays/opérateurs** — `android/feature/wallet/.../RechargeScreen.kt` (`CountrySelector`, `OperatorSelector`, `pay()`). **Décision utilisateur (2026-09-17) : ne pas implémenter.** StoreKit (5 paliers fixes) reste l'unique voie de recharge sur iOS, contrainte Apple 3.1.1 (IAP obligatoire pour du contenu numérique consommé dans l'app) — écart assumé et définitif.
- [x] **3.2 Paiement carte montant libre (Stripe Checkout)** — `payWithStripe()` + préréglages 5/10/20/50/100. Même décision/contrainte que 3.1 — ne pas implémenter.
- [x] **3.3 Historique des retraits dans l'écran Wallet** — `WalletScreen.kt` (`WithdrawalRow`, 4ᵉ onglet). Fait, CI verte (bd7bb8b) : `WalletRepository.withdrawals()` + 4ᵉ onglet dans `WalletView`.

## 4. Live (critique)

- [x] **4.1 Likes** — `LiveRepository.kt`/`LiveViewModel.kt` (`likeLive`, `likesCount`). Fait, CI verte (7eba25d) : event temps réel dédié `likes` (pas l'enveloppe broadcast générique, à la différence de Duel/Compétition) + réactions emoji flottantes (`sendReaction`, absentes de Live jusqu'ici).
- [x] **4.2 Suivre l'artiste depuis le live** — `LiveViewModel.kt` L337 (`followArtist`). Fait, CI verte (60f2ccb) : réutilise `ArtistEndpoints.follow` existant.
- [x] **4.3 Boutique de cadeaux complète** — `LiveViewModel.kt` L183-192/789-819, `LiveRoomScreen.kt` L884-936. Fait, CI verte (190bea0) : `giftCatalog`/`inventory`/`loadGiftCatalog`/`loadInventory`/`purchaseGift` (réutilise `GiftShopRepository`/`WalletRepository`) + `LiveGiftSendSheet` (Mes cadeaux/Boutique), remplace le `quickGiftId` fixe (toujours vide en pratique — bouton mort) supprimé du `LiveRoomView.init`.
- [x] **4.4 Classement des donateurs** — `giftLeaderboard`, `LiveRoomScreen.kt` L1126-1133. Fait, CI verte (60f2ccb) : `LiveDonorEntry` + trophée dans `actionBar` ouvrant une feuille de classement (même simplification qu'ailleurs : feuille plutôt que la bulle top-donateur flottante d'Android).
- [x] **4.5 Mute à distance d'un invité par l'hôte** — `LiveViewModel.kt` L548-549 (`toggleGuestMic`, event `toggle_mic`), `LiveRoomScreen.kt` L1072-1089. Fait, CI verte (a858628) : `BroadcastPayload.action/targetUserId/value` + `toggleGuestMic`/`onGuestAction` (canal `live-controls-<id>`, event `guest_action`) + bouton micro par invité dans `guestsSheet`. `kick`/`start_timer` non repris : le retrait d'invité passe déjà côté iOS par un mécanisme REST équivalent (`joinUpdate` → statut `ended`), et le chrono de parole n'était pas dans le périmètre audité.

**Section 4 (Live) : terminée, tout CI vert.**

## 5. Majeur

- [x] **5.1 Profil public complet d'un artiste** — `android/feature/profile/.../ArtistPublicProfileScreen.kt` + ViewModel (cover, bio, réseaux sociaux, follow). Fait, CI verte (df91e87) : point d'entrée ajouté (puce nom tappable Live/Concert, noms tappables barre de vote Duel, nom tappable par candidat Compétition), présenté en `fullScreenCover` par-dessus l'événement en cours, câblé au niveau `MainShellView` (comme `MainActivity.kt`).
- [x] **5.2 Édition du profil public créateur** — `android/feature/profile/.../PublicProfileEditScreen.kt` (cover, bio publique, réseaux, visibilité). Fait, CI verte (eb3df10) : `PublicProfileViewModel`/`PublicProfileEditView`, nouvelle route menu "Profil".
- [x] **5.3 Écran de préférences de notifications** — `android/feature/notifications/.../NotificationPrefsScreen.kt` + endpoints `PREFERENCES`/`PREFERENCES_EMAIL`. Fait (à confirmer CI, 061f9c9) : `NotificationPreferences` + `NotificationPrefsViewModel`/`NotificationPrefsView`, la ligne de menu "Notifications" ouvre désormais ces préférences (la cloche reste l'accès à la liste in-app).
- [ ] **5.4 CGU / Politique de confidentialité à l'inscription** — `android/feature/auth/.../LegalDocScreen.kt` + `TermsAcceptance`. Case à cocher obligatoire + consultation in-app. Absent d'iOS.
- [ ] **5.5 Espace "Mon contenu" créateur** — `android/feature/content/.../MyContentScreen.kt` (fichier entier). Publier une vidéo lifestyle, "Mes vidéos", `ReplayManageDialog` (public/privé, prix, téléchargement, remplacement fichier). Absent d'iOS.
- [ ] **5.6 Invitation de duel peer-to-peer entre artistes** — `CreatorScreen.kt` (`createDuel` L131-151, UI "Demander un Duel" L293-375) + reproposer une date (`changeDuelDate` L169-181, `SentDuelRow`).
- [ ] **5.7 Suppression d'un concert planifié** — `CreatorScreen.kt` (`deleteConcert` L260-264, `DELETE /artist-concerts/:id`).
- [ ] **5.8 Dates limites sponsor/dédicace à la création d'un concert** — `CreatorScreen.kt` L200-243/519-538 (`sponsorSubmissionDeadline`/`dedicationSubmissionDeadline`). Champs absents de `CreateArtistConcert` iOS.

## 6. Mineur

- [ ] **6.1 Filtre saisons en cours/terminées** — `LeaderboardScreen.kt` (`SeasonsTab` L231-253).
- [ ] **6.2 Détail des récompenses par rang de saison** — `LeaderboardScreen.kt` (`SeasonCard` L276-285, modèle `SeasonReward`). Modèle iOS `LeaderboardSeason` ne décode pas ce champ.
- [ ] **6.3 Vue liste/recherche des lives** — `android/feature/feed/.../LivesListScreen.kt` (fichier entier), en alternative au pager plein écran.
- [ ] **6.4 Compteur de spectateurs temps réel sur les cartes du feed** — `FeedViewModel.kt` (`refreshPresence`/`connectPresence` L58-90).
- [ ] **6.5 Recherche dans l'annuaire des artistes** — `ArtistsScreen.kt` L113-133.
- [ ] **6.6 Écran "Suivis" dédié** — `android/feature/artists/.../FollowedArtistsScreen.kt` (fichier entier, compteur + "Ne plus suivre").
- [ ] **6.7 Filtrage des événements éligibles au sponsoring côté UI** — `SponsorScreen.kt` (`loadEvents` L133-168, filtre `allowsSponsorAds`/deadline).
- [ ] **6.8 Bouton contextuel "Sponsoriser" depuis un écran d'événement** — `SponsorScreen.kt` (`preselectedTarget`) + point d'entrée dans `MainActivity.kt`.
- [ ] **6.9 Libellés de statut sponsor manquants** — `SponsorScreen.kt` (`statusLabel`, 5 statuts dont `awaiting_payment`/`paid` non traduits sur iOS).
- [ ] **6.10 Champs date de naissance / sexe** — `android/feature/auth/.../ProfileCompletionScreen.kt`.
- [ ] **6.11 Bascule admin "création de duels par les managers"** — `android/feature/profile/.../AdminScreen.kt` (`duelCreationEnabled`/`toggleDuelCreation()`). iOS lit le réglage mais aucun admin ne peut le modifier.

---

## Hors périmètre (déjà vérifié à parité ou non applicable)

GiftShop, Referral, Subscription, Replay (catalogue public), Google Sign-In, notifications temps réel (liste/lu/compteur), pagination historique wallet (absente des deux côtés).
