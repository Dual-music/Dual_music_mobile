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

## Et après ? (aperçu)

Cette démo = **écran de marque uniquement**. Les vrais écrans arriveront **module par
module** :
1. **Connexion** (email + mot de passe, OTP, Google, biométrie).
2. **Feed de lives** (scroll vertical style TikTok).
3. **Live** (vidéo LiveKit + chat + cadeaux animés).

Pour ces écrans, il faudra faire **tourner le backend** sur le PC et le rendre **joignable
depuis le téléphone** (via l'IP locale du PC, ex. `http://192.168.x.x:4000`, car le
téléphone ne connaît pas `localhost`). Un guide dédié sera fourni à ce moment-là.
