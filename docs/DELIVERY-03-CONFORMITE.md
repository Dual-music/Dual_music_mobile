# Livraison 03 — core-auth + Feature auth

Module 3 du prompt. ✅ = livré · ⚠️ = écart/à faire · 🔜 = ultérieur.

## Périmètre
Stockage sécurisé + refresh + biométrie (`core-auth`) et écrans/logique d'authentification
(`feature-auth`), iOS **et** Android.

## ⚠️ Correction de modèle vs le prompt initial
Le prompt disait « sign-in **phone + OTP** + Google + biométrie ». Le backend réel impose :
- **Auth primaire = email + mot de passe** (`/auth/login`, `/auth/register`).
- **OTP téléphone = vérification post-login** du numéro (`/auth/otp/phone/*`), pas un
  sign-in passwordless.
- **Google = redirect web** (`/auth/oauth/google`), pas d'échange idToken natif.

La livraison reflète **la réalité backend** (et non le prompt), ce qui est la bonne
décision d'ingénierie.

## Conformité

| Exigence | État | Où |
|---|---|---|
| Stockage jetons **Keychain** (iOS) | ✅ | `CoreAuth/KeychainTokenStore.swift` |
| Stockage jetons **EncryptedSharedPreferences + StrongBox** (Android) | ✅ | `core:auth/EncryptedTokenStore.kt` |
| `TokenStore`/`TokenRefresher` concrets branchés sur core-network | ✅ | `AuthRefresher` (iOS+Android) |
| Refresh `POST /auth/refresh` (hors cycle avec le client) | ✅ | client HTTP dédié |
| **Biométrie** FaceID / BiometricPrompt (retraits, PIN) | ✅ | `BiometricGate` (iOS+Android) |
| Connexion **email + mot de passe** | ✅ | `AuthRepository.login` + écrans |
| Inscription | ✅ | `AuthRepository.register` |
| Réhydratation session au démarrage (`/auth/me`) | ✅ | `AuthViewModel.bootstrap` |
| **OTP téléphone** (envoi + vérification) | ✅ | `sendPhoneOtp`/`verifyPhoneOtp` + écran OTP |
| Déconnexion (invalidation refresh + purge locale) | ✅ | `AuthRepository.logout` |
| Écrans avec **design system** (thème sombre, CTA dégradé) | ✅ | `SignInView`/`SignInScreen`, `OtpVerify*` |
| DTOs auth partagés | ✅ | `shared-domain/auth/AuthDtos.kt` |
| Messages d'erreur mappés sur les codes backend | ✅ | `friendlyMessage` (iOS+Android) |
| Code commenté professionnellement | ✅ | doc-comments partout |
| **Google Sign-In natif** | ⚠️ | entrée fournie (URL de démarrage) ; voir écart ci-dessous |
| Tests domain/data ≥ 80 % | 🔜 | à ajouter (mocks réseau) — patron déjà posé en shared-domain |

## ⚠️ Écart & besoin backend : Google natif
L'app fournit `googleStartURL()` (à ouvrir en `ASWebAuthenticationSession` / Custom Tab),
mais le flux **redirect web** actuel ne rend pas les jetons à l'app mobile proprement. Deux
options (au choix, à décider avant le module correspondant) :
1. **Recommandé** : ajouter côté backend `POST /auth/oauth/google/native` qui accepte
   l'`idToken` de Google Sign-In natif et renvoie une `AuthSession`. Les DTOs
   (`GoogleNativeRequest`) et la constante d'endpoint sont **déjà prêts** côté mobile.
2. Gérer un **redirect deep-link** (`dualmusic://auth/callback?...`) côté backend qui
   transmet les jetons à l'app.

## Backend — récapitulatif des ajouts à prévoir (cumulés)
- `POST /auth/oauth/google/native` (idToken) — **ce module**.
- Vérification de reçu **IAP/Play** créditant le wallet — module 6.
- Enregistrement de tokens **APNs/FCM** — module 8.

## Fichiers livrés
```
shared-domain/…/auth/AuthDtos.kt

ios/Packages/
├── CoreAuth/ (Package.swift + KeychainTokenStore, AuthRefresher, BiometricGate)
└── FeatureAuth/ (Package.swift + AuthModels, AuthRepository, AuthViewModel, SignInView, OtpVerifyView)

android/
├── core/auth/ (build.gradle.kts + EncryptedTokenStore, AuthRefresher, BiometricGate)
└── feature/auth/ (build.gradle.kts + AuthRepository, AuthViewModel, SignInScreen, OtpVerifyScreen)
```

## ⚠️ Note environnement
Non compilé ici (Windows). iOS à valider sur Xcode 16 ; Android via `./gradlew`.
