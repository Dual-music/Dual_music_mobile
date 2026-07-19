# Guide — Tester l'app sur un téléphone Android (débutant)

Ce guide explique, pas à pas, comment lancer l'app **Dual Music** sur un téléphone Android
depuis un **PC Windows**. Aucune expérience mobile requise.

> ℹ️ **iOS (iPhone) = impossible sans un Mac.** Xcode n'existe que sur macOS. Ce guide ne
> concerne donc que **Android** (PC Windows + téléphone Android).

> ✅ **Bonne nouvelle** : la première app de démo affiche seulement l'écran de marque
> (couleurs, boutons, cadeau animé). Elle **ne nécessite AUCUN backend** — tu peux la voir
> sur ton téléphone immédiatement.

Durée la première fois : **~1 heure** (surtout des téléchargements).

---

## Étape 1 — Installer Android Studio (sur le PC)

1. Va sur **https://developer.android.com/studio** → **Download Android Studio**.
2. Lance l'installateur, clique **Next** partout (options par défaut). Ça installe aussi le
   **JDK**, le **SDK Android** et **Gradle** — rien d'autre à installer.
3. Au premier lancement, l'assistant **Setup Wizard** apparaît → choisis **Standard** →
   **Next** → **Finish**. Il télécharge des composants (~10-20 min, patiente).

## Étape 2 — Récupérer le projet

Option simple (depuis Android Studio) :
1. Écran d'accueil → **Get from VCS** (ou *Clone Repository*).
2. **URL** : `https://github.com/Dual-music/Dual_music_mobile.git`
3. Connecte ton compte GitHub si demandé (le dépôt est **privé**).
4. ⚠️ **TRÈS IMPORTANT** : une fois cloné, ouvre **le sous-dossier `android`**, PAS la racine.
   → **File → Open →** va dans `Dual_music_mobile/`**`android`** → **OK**.

> Pourquoi `android/` et pas la racine ? Le projet contient aussi `ios/` et `shared-domain/`.
> Le "projet Android" (Gradle) est dans le dossier `android/`.

## Étape 3 — Laisser le projet se synchroniser

- En bas, une barre **Gradle Sync** tourne : téléchargement de Gradle 8.9 + dépendances
  (5-15 min la 1re fois). **Laisse finir.**
- S'il propose **Trust Project** → **Trust**.
- S'il propose d'installer un composant SDK manquant → **accepte**.

## Alternative — Tester sur un émulateur (sans téléphone)

Comme avec Flutter, Android Studio inclut un **émulateur** (téléphone virtuel sur le PC).
Tu peux donc tester **sans téléphone physique**. Deux façons de faire :

- **Option A** — sur ton **téléphone** (Étapes 4-5 ci-dessous) : meilleur pour la vidéo/les
  cadeaux (matériel réel).
- **Option B** — sur l'**émulateur** (ci-dessous) : pratique au quotidien, aucun téléphone requis.

### Créer et lancer un émulateur

1. Dans Android Studio : icône **Device Manager** (à droite) ou **Tools → Device Manager**.
2. **Create Device** → choisis un modèle (ex. **Pixel 7**) → **Next**.
3. Choisis une image système (ex. **API 35**, télécharge-la si besoin) → **Next** → **Finish**.
4. Clique le **▶** à côté de l'émulateur : un téléphone virtuel s'ouvre sur ton écran.
5. Ensuite, sélectionne cet émulateur dans le sélecteur d'appareil et clique **▶ Run** —
   l'app s'installe dedans.

### ⚠️ À savoir sur l'émulateur

- **Virtualisation requise** : l'émulateur a besoin que la **virtualisation soit activée
  dans le BIOS** (Intel VT-x / AMD-V). S'il refuse de démarrer, active-la dans le BIOS, ou
  active *« Plateforme d'hyperviseur Windows »* dans *« Activer/désactiver des
  fonctionnalités Windows »*.
- **Vidéo / cadeaux GPU** : l'émulateur est **parfait pour l'UI et les flux** (connexion,
  feed, navigation), mais **faible pour la vidéo live et les shaders GPU** (émulés, donc
  lents). Pour tester le **live et les cadeaux animés**, préfère le **téléphone physique**.
- **iOS** : il n'existe **aucun émulateur iOS sur Windows** (le simulateur iOS est réservé à
  macOS). Cette alternative ne concerne qu'Android.

> Si tu utilises l'émulateur, tu peux **sauter les Étapes 4 et 5** ci-dessous.

## Étape 4 — Préparer le téléphone

1. **Paramètres → À propos du téléphone** → tape **7 fois** sur **Numéro de build** →
   message *« Vous êtes développeur »*.
2. **Paramètres → Système → Options pour les développeurs** → active **Débogage USB**.
3. **Branche le téléphone au PC en USB.** Sur le téléphone, popup *« Autoriser le débogage
   USB ? »* → coche **Toujours autoriser** → **Autoriser**.

## Étape 5 — Lancer l'app

1. En haut d'Android Studio, dans le sélecteur d'appareil, ton téléphone apparaît
   (ex. *« Samsung SM-… »*). Sélectionne-le.
2. À côté, le sélecteur de module doit afficher **`app`**.
3. Clique le **▶ (triangle vert "Run")**.
4. Android Studio compile, installe et **lance l'app sur le téléphone**. 🎉

## ✅ À quoi ressemble le succès

Sur le téléphone : fond **sombre dégradé violet**, titre **« Dual Music »**, un **🎁 avec
halo violet animé**, une pastille **💎 1 250**, et des boutons (dégradé violet/rose, gris,
contour). → L'app native tourne sur ton appareil avec l'identité Dual Music.

---

## ⚠️ En cas d'erreur (c'est normal)

Ce code **n'a jamais été compilé** (développé sous Windows sans les outils mobiles). Il est
**possible** qu'Android Studio affiche des **erreurs en rouge** au premier build. Sur un
projet de cette taille, c'est attendu.

**Que faire :**
1. Regarde l'onglet **Build** (en bas) — copie le **texte rouge** de l'erreur.
2. Transmets-le pour correction.
3. Une fois corrigé côté dépôt : dans Android Studio, **Git → Update Project** (ou
   `git pull`), puis re-clique **▶ Run**.

On itère ainsi jusqu'à ce que ça compile.

---

## Dépannage rapide

| Problème | Solution |
|---|---|
| Le téléphone n'apparaît pas | Vérifie le câble USB (données, pas seulement charge), le **Débogage USB** activé, et l'autorisation acceptée sur le téléphone. |
| « Gradle Sync failed » | Vérifie la connexion internet ; **File → Sync Project with Gradle Files** pour relancer. |
| Android Studio a ouvert la mauvaise racine | Ferme le projet, **File → Open →** choisis bien le dossier **`android`**. |
| Composant SDK manquant | Accepte l'installation proposée, ou **Tools → SDK Manager** pour installer l'API 35. |

---
===================================================================================================================
# Partie 2 — Test end-to-end avec le backend branché
===================================================================================================================


Jusqu'ici l'app tournait seule. Pour tester les **vrais écrans** (connexion, feed, lives,
duels, portefeuille, cadeaux…), il faut **3 choses** :

1. faire **tourner le backend** sur le PC,
2. le rendre **joignable depuis le téléphone** (via l'IP locale du PC),
3. **pointer l'app** vers cette adresse.

> 💡 **Pourquoi une IP et pas `localhost` ?** Pour le téléphone, `localhost` = *lui-même*,
> pas le PC. Il faut donc l'**IP du PC sur le Wi-Fi** (ex. `192.168.1.42`).
> Exception : l'**émulateur** utilise l'adresse spéciale `10.0.2.2` (déjà configurée par
> défaut) — si tu testes sur émulateur, tu n'as **rien à changer** dans l'app.

**Pré-requis absolu : le téléphone et le PC doivent être sur le MÊME réseau Wi-Fi.**

---

## Étape A — Faire tourner le backend (sur le PC)

Le backend a besoin de **Node.js ≥ 20** et d'une base **MySQL**. (Redis est **optionnel** :
`REDIS_OPTIONAL=true` — sans Redis, l'app utilise un repli intégré, aucun souci.)

1. **Installer Node 20+** : https://nodejs.org (choisis « LTS »). Vérifie dans un terminal :
   ```bash
   node -v   # doit afficher v20.x ou plus
   ```
2. **MySQL 8.0+ OBLIGATOIRE.** Le backend utilise la collation `utf8mb4_0900_ai_ci` et des
   fonctions fenêtre (`ROW_NUMBER/OVER`) — **exclusives à MySQL 8.0+** (MySQL 5.7 et XAMPP
   échouent avec `Unknown collation: 'utf8mb4_0900_ai_ci'`). Choisis **un** des deux chemins
   ci-dessous (🐳 **Docker recommandé** : isolé, n'écrase pas un MySQL existant), puis
   continue au point 3.

> ### 🐳 Chemin Docker — recette complète (validée)
>
> Pré-requis : **Docker Desktop** installé et **lancé** (sinon erreur *« check if the daemon
> is running »* → ouvre Docker Desktop et attends qu'il soit prêt).
>
> On lance un MySQL 8 **isolé** sur le port **3307** (pour ne pas entrer en conflit avec un
> éventuel MySQL déjà présent sur 3306).
>
> **a. Démarrer le conteneur** (crée aussi la base `duel_music`, mot de passe root = `root`) :
> ```powershell
> docker run --name dualmysql8 -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=duel_music -p 3307:3306 -d mysql:8.0
> ```
>
> **b. Attendre qu'il soit prêt** (~30 s la 1re fois). Répète jusqu'à voir
> `ready for connections` :
> ```powershell
> docker logs dualmysql8 --tail 5
> ```
> ⚠️ **N'enchaîne pas trop tôt** : tant que le conteneur affiche *« Starting temporary
> server »* / *« InnoDB initialization »*, il **refuse les connexions** (tu obtiendrais
> `Access denied`). Attends bien la ligne `ready for connections`.
>
> **c. Configurer le `.env`** du backend avec ces valeurs **exactes** :
> ```
> DB_HOST=127.0.0.1
> DB_PORT=3307
> DB_NAME=duel_music
> DB_USER=root
> DB_PASSWORD=root
> ```
> (En Docker, la base `duel_music` existe déjà → **`npm run db:create` est inutile**, passe
> directement à `db:reset` au point 4.)
>
> **Gérer le conteneur ensuite** : `docker stop dualmysql8` pour l'éteindre,
> `docker start dualmysql8` pour le rallumer — **les données sont conservées**. (Pour repartir
> de zéro : `docker rm -f dualmysql8` puis relance la commande **a**.)

> ### 💽 Chemin installeur (alternative)
>
> Installe *MySQL Community Server 8.0* (https://dev.mysql.com/downloads/mysql/), démarre-le,
> et note le mot de passe **root**. Dans le `.env` : `DB_PORT=3306` (défaut),
> `DB_PASSWORD=<ton_mot_de_passe>`. Ici, `npm run db:create` **est** nécessaire (point 4).

3. Ouvre un terminal dans le dossier **`Dual_music_backend`** et installe les dépendances :
   ```bash
   cd Dual_music_backend
   copy .env.example .env      # (Windows) — si le .env n'existe pas encore
   npm install
   ```
   Vérifie dans **`.env`** : `DB_HOST=127.0.0.1`, `DB_NAME=duel_music`, `PORT=4000`, et le
   `DB_PORT`/`DB_PASSWORD` selon ton chemin (Docker = `3307`/`root` ; installeur = `3306`/ton mdp).
4. **Remplir la base** (migrations + procédures stockées + réglages/catalogue) :
   ```bash
   npm run db:create     # 💽 installeur uniquement (🐳 Docker : SAUTE cette ligne)
   npm run db:reset      # migrations + procédures + seed — ~4 min la 1re fois
   ```
   ✅ Succès = `All stored procedures applied.` puis le seed sans erreur.
   ℹ️ Le seed installe les **réglages + le catalogue de cadeaux/prix**, mais **aucun compte** :
   tu créeras ton compte via l'**inscription** dans l'app.
5. **Démarrer le serveur** :
   ```bash
   npm run dev
   ```
   ✅ Succès = la ligne `Dual Music API listening on http://localhost:4000`.

**Vérifie dans un navigateur sur le PC** : ouvre `http://localhost:4000/api/v1/health`
(ou la page d'accueil de l'API). Si tu obtiens une réponse JSON, le backend tourne. 👍

> ⚠️ **Laisse ce terminal ouvert** : tant que `npm run dev` tourne, le backend est vivant.
> Le fermer = couper le backend.

---

## Étape B — Rendre le backend joignable depuis le téléphone

### B.1 — Trouver l'IP locale du PC

Dans un terminal Windows :
```bash
ipconfig
```
Cherche la ligne **« Adresse IPv4 »** de ta carte Wi-Fi (ex. `192.168.1.42`).
**C'est cette adresse** que le téléphone utilisera. Note-la.

### B.2 — Autoriser le port 4000 dans le pare-feu Windows

Par défaut, Windows **bloque** les connexions entrantes : le téléphone ne pourra pas
joindre le PC tant que le port 4000 n'est pas ouvert. Dans un terminal **PowerShell en
administrateur** :
```powershell
New-NetFirewallRule -DisplayName "Dual Music API 4000" -Direction Inbound -LocalPort 4000 -Protocol TCP -Action Allow
```
(Alternative manuelle : *Pare-feu Windows Defender → Paramètres avancés → Règles de trafic
entrant → Nouvelle règle → Port → TCP 4000 → Autoriser*.)

### B.3 — Vérifier depuis le téléphone

Sur le **navigateur du téléphone** (connecté au même Wi-Fi), ouvre :
```
http://<IP_DU_PC>:4000/api/v1/health
```
(ex. `http://192.168.1.42:4000/api/v1/health`). Si tu vois la réponse JSON → **le
téléphone joint le backend** 🎉. Sinon, voir le dépannage plus bas (Wi-Fi / pare-feu / IP).

---

## Étape C — Pointer l'app vers le backend

1. Dans Android Studio, ouvre le fichier
   [MainActivity.kt](../android/app/src/main/kotlin/com/dualmusic/app/MainActivity.kt).
2. Cherche la ligne (vers le **haut du fichier**) :
   ```kotlin
   private const val API_BASE_URL = "http://10.0.2.2:4000"
   ```
3. Remplace `10.0.2.2` par **l'IP du PC** trouvée à l'étape B.1 :
   ```kotlin
   private const val API_BASE_URL = "http://192.168.1.42:4000"   // ← ton IP
   ```
   > ⚠️ Garde `http://` et `:4000`. **N'ajoute PAS** `/api/v1` : l'app l'ajoute déjà elle-même.
   > 📱 **Émulateur** : laisse `10.0.2.2` (ne change rien).
4. **Relance l'app** : **▶ Run**. Android Studio recompile et réinstalle avec la nouvelle adresse.

> 🔒 **Le HTTP en clair est déjà autorisé** dans l'app (`usesCleartextTraffic="true"` dans
> le manifeste) — pas de config TLS à faire pour un test local.

---

## Étape D — Tester les vrais parcours

Une fois l'app relancée et connectée au backend, teste dans cet ordre :

1. **Inscription** (email + mot de passe). ⚠️ Le `db:seed` ne crée **aucun compte** (il
   installe seulement les réglages + le catalogue de cadeaux/prix) — **crée donc un compte**
   via l'écran d'inscription. Ensuite tu pourras te reconnecter avec.
2. **Feed** des lives / **catalogues** (duels, concerts, compétitions) → les listes doivent
   se remplir depuis le backend.
3. **Portefeuille** : solde + historiques.
4. **Temps réel** : ouvre un live/duel → le **chat** et les **cadeaux** transitent par
   Socket.IO (même adresse `API_BASE_URL`).
5. **Création avec upload** (espace créateur / sponsoring) : choisir une image/vidéo →
   l'upload passe par `presign → PUT → confirm`.

> 🐛 **En cas de bug** : ouvre l'onglet **Logcat** (en bas d'Android Studio), filtre par
> `Dual` ou par le niveau **Error**, et copie le texte rouge. C'est la source n°1 pour
> diagnostiquer (erreur réseau, 401, parsing JSON…).

---

## Dépannage — connexion backend ↔ téléphone

| Symptôme | Cause probable | Solution |
|---|---|---|
| L'app affiche *« impossible de joindre le serveur »* / timeout | Mauvaise IP, ou pas le même Wi-Fi | Re-vérifie `ipconfig` (IPv4 **Wi-Fi**), et que téléphone **et** PC sont sur le **même** réseau. |
| Le test navigateur B.3 échoue depuis le téléphone | Pare-feu Windows bloque le port | Rejoue la règle pare-feu (B.2). Vérifie aussi que le backend tourne toujours (`npm run dev`). |
| Ça marche sur émulateur mais pas sur téléphone | L'app pointe encore sur `10.0.2.2` | Mets l'**IP du PC** dans `API_BASE_URL` (Étape C). `10.0.2.2` **ne marche que sur émulateur**. |
| L'IP du PC change après un redémarrage | IP attribuée dynamiquement par la box | Refais B.1 + C, ou réserve une IP fixe pour le PC dans la box. |
| Réseaux d'entreprise / Wi-Fi public | *Isolation des clients* activée (le PC et le téléphone ne peuvent pas se voir) | Utilise un **partage de connexion** (hotspot) depuis le téléphone, ou un Wi-Fi domestique. |
| `Unknown collation: 'utf8mb4_0900_ai_ci'` au `db:migrate` | MySQL **< 8.0** (5.7 / XAMPP / MariaDB) | Installe **MySQL 8.0+** (voir Étape A.2, option Docker recommandée). Cette collation n'existe qu'en MySQL 8. |
| `db:reset` échoue | MySQL éteint, mauvaise version, ou identifiants `.env` faux | Démarre MySQL 8, vérifie `DB_PORT` (3307 si Docker), corrige `DB_USER`/`DB_PASSWORD` dans `.env`. |
| Connexion refuse les identifiants | Aucun compte (le seed n'en crée pas) | **Inscris-toi** d'abord dans l'app, puis connecte-toi avec ce compte. |

---

## Récapitulatif express (une fois tout installé)

```bash
# 1. PC — démarrer le backend
cd Dual_music_backend
npm run dev                     # → http://localhost:4000

# 2. PC — connaître son IP
ipconfig                        # → Adresse IPv4, ex. 192.168.1.42
```
```kotlin
// 3. App — MainActivity.kt
private const val API_BASE_URL = "http://192.168.1.42:4000"
```
```
# 4. Android Studio — ▶ Run (téléphone branché, même Wi-Fi, port 4000 ouvert)
```

---

## Fonctions **non** testables en E2E local (rappel)

Ces fonctions dépendent de **consoles externes** et ne peuvent pas être validées par ce
test local — elles seront ajoutées **après** :

- **Notifications push (FCM)** → nécessite un projet **Firebase** + `google-services.json`.
- **Recharge de crédits / achat d'abonnement (Google Play Billing)** → nécessite la **Play
  Console** (compte à 25 $) + produits configurés.
- **Connexion Google native (OAuth)** → nécessite un client OAuth (Firebase/Google Cloud) +
  empreinte SHA-1.

Le reste de l'app (le cœur) est **entièrement testable** dès maintenant avec ce guide.
