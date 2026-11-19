# Larry SMP SMP 26.3

Plugin Paper pour le serveur **Larry SMP SMP**, avec monnaie Larry et systèmes communautaires. Le dépôt contient le code source, les ressources, le script Windows et le workflow GitHub Actions.

## Cible
- Minecraft Java avec Paper 26.3 / Java 25.
- Compatibilité Bedrock et Education empaquetée avec **EduGeyser + EduFloodgate**.
- Connexion Java premium vérifiée par Mojang/Microsoft; Bedrock via Floodgate, sans AuthMe.
- Gradle 9.2.1.
- Aucune protection de terrain, aucun claim, aucun système VIP. Les commandes vanilla et sélecteurs vanilla ne sont pas remplacés.

## Compilation Windows
1. Installer un JDK 25.
2. Cloner ou télécharger ce dépôt.
3. Exécuter `BUILD-PLUGIN.bat`.
4. Le JAR est copié dans `plugins/LarpersCoin.jar`; le journal est dans `BUILD-PLUGIN.log`.

## Pack réseau prêt à générer (Velocity + Lobby + SMP)

Le workflow **Package reseau complet Larry SMP (Velocity + Lobby + SMP)** produit un ZIP séparé avec:
- Velocity public sur TCP 25565.
- Lobby Paper privé sur 127.0.0.1:25566.
- SMP Paper privé sur 127.0.0.1:25567.
- EduGeyser + EduFloodgate sur Velocity et EduFloodgate sur les backends.
- Clé de forwarding Velocity, scripts Windows de démarrage, configuration Bedrock et ouverture du pare-feu.

Dans GitHub, ouvre **Actions** puis **Package reseau complet Larry SMP (Velocity + Lobby + SMP)** et télécharge l'artefact `LarrySMP-Network`. Suis impérativement `LISEZ-MOI.txt` et exécute `CONFIGURER-RESEAU.bat` après le premier démarrage de Velocity. Les ports backend 25566/25567 ne doivent jamais être exposés à Internet.

Le pack est un déploiement initial: accepte l'EULA toi-même, configure la redirection des ports du routeur et teste une connexion Java premium, Java non premium et Bedrock avant l'ouverture publique.

## Compilation automatique
Onglet **Actions** → workflow **Build Larry SMP**. L'artefact s'appelle `LarrySMP-Paper-26.3`.

## Commandes incluses

### Économie
- `/balance [joueur]` (`/bal`) — solde.
- `/pay <joueur> <montant>` — paiement entre joueurs connectés.
- `/baltop` — classement des fortunes.
- `/history` — tes 10 dernières transactions; `/history <joueur>` est réservé au staff.
- `/bounty` ou `/bounty list` — menu graphique de toutes les primes actives; bouton pour choisir un joueur en ligne et saisir le montant dans le chat.
- `/bounty <joueur> <montant>` — prime payée au joueur qui élimine la cible.
- `/eco give|take|set <joueur> <montant>` — administration.
- `/lottery` — menu graphique pour tous: jackpot, nombre de tickets et achat de tickets.
- `/lottery buy <nombre>` — choisir le nombre de tickets (1 à 1 000) à 50 Larry chacun; le menu propose un achat personnalisé.
- `/lottery start [minutes]` — démarrer une loterie (1 à 1440 minutes), réservé aux administrateurs; annonce globale dans le chat et bannière dans la liste des joueurs.
- `/lottery draw` — terminer et tirer immédiatement, réservé aux administrateurs.

### Clans
- `/clan create <nom>`, `/clan invite <joueur>`, `/clan join <nom>`, `/clan leave`.
- `/clan transfer <joueur>` et `/clan disband confirm` — transfert du leadership et dissolution contrôlée.
- `/clan audit` — journal des modifications du coffre (chef/officiers).
- `/clan` ouvre le menu graphique; `/clan info`, `/clan stats`, `/clan level`.
- `/clan bank [deposit|withdraw <montant>]`.
- `/clan allies request|accept|remove <clan>` — alliances bilatérales.
- `/clan role <joueur> <officer|member>` — rôle d'officier ou membre.
- `/clan chest` — coffre partagé du clan (1 emplacement au départ); `/clan chest upgrade` augmente sa capacité contre des Larry.
- `/clan top`, `/clan leaderboard`.

### Activités et commerce
- `/quests` ouvre le menu des objectifs; `/quests claim`, `/quests weekly`, `/quests weekly claim` — objectifs automatiques quotidiens et hebdomadaires.
- `/top` — ouvre un menu de classements Larry (fortunes, clans et primes).
- `/ah` ou `/ah list` — hôtel des ventes graphique persistant avec barre de recherche (clic sur la boussole, puis saisie dans le chat), filtre vendeur/objet et bouton pour effacer la recherche.
- `/ah search <objet|vendeur>` et `/ah search clear` — recherche directe; `/ah sell <prix>`, `/ah buy <id>` — gestion des annonces.
- `/trade <joueur>`, `/trade accept`, `/trade item`, `/trade money <montant>`, `/trade confirm`, `/trade cancel` — échange avec double confirmation.
- `/shop` — boutique avec catégories, recherche intégrée depuis le menu des catégories et du catalogue, achats et prix de revente.
- `/sell` — ouvre le menu des piles vendables; cliquer sur une pile la vend. `/sell all` demande une confirmation avant de vendre tout l’inventaire admissible.

### Anti-triche et modération
- `/ac alerts` — activer/désactiver les alertes anti-triche pour le staff.
- `/ac status` — état du module et rappels des sanctions désactivées.
- `/ac alerts` — activer/désactiver ses alertes staff.
- `/ac info <joueur>`, `/ac top`, `/ac log`, `/ac reset <joueur>`, `/ac reload` — diagnostic, classement, journal récent, remise à zéro et rechargement.
- Détections configurables de vitesse anormale, montée verticale répétée et portée de frappe suspecte. Les violations sont sauvegardées dans `anticheat-violations.yml`; réglages dans `config.yml`. Les alertes ne constituent pas une preuve et aucune expulsion/bannissement automatique n'est appliqué.
- `/admin` — menu administrateur sur une seule page; `/admin reset <joueur>` réinitialise son mot de passe et l'expulse pour qu'il refasse `/register`; `/admin ip <adresse:port> <nom>` modifie les informations affichées dans TAB (administrateurs seulement).
- `/vanish`, `/invsee <joueur>`, `/freeze <joueur>`, `/staffchat <message>`.
- `/admin`, `/admin reset` et `/admin ip` sont réservés à `larpers.admin` (OP par défaut); les autres outils staff utilisent `larpers.staff`.

## Rangs visibles
- Les préfixes sont affichés dans le chat et la liste des joueurs: `[ADMIN]`, `[MOD]`, `[VIP]`, `[JOUEUR]`.
- Admin: OP ou permission `larpers.admin`; modérateur: `larpers.staff` ou `larpers.anticheat.mod`; VIP: `larpers.vip`.
- Les groupes LuckPerms peuvent attribuer ces permissions; le préfixe visuel est calculé par le plugin.

## Accès Bedrock et comptes Java non premium
- EduGeyser-Spigot et EduFloodgate sont inclus dans le ZIP généré par GitHub Actions; AuthMe n'est pas installé.
- Après le premier démarrage du serveur, arrête-le avec `stop`, puis lance `CONFIGURER-BEDROCK.bat` et redémarre. Geyser utilisera Floodgate (`auth-type: floodgate`).
- Lance `OUVRIR-PORTS-WINDOWS.bat` en administrateur pour autoriser les ports dans le pare-feu; redirige aussi **TCP 25565** et **TCP + UDP 19132** sur le routeur.
- Sur le réseau Velocity fourni, les joueurs Java créent leur mot de passe avec `/register <motdepasse>`, puis utilisent `/login <motdepasse>`; EduFloodgate reconnaît les joueurs Bedrock et Education.
- Le mode hors-ligne est utilisé uniquement derrière Velocity avec le forwarding moderne et les backends liés à `127.0.0.1`. N'expose jamais directement les ports Paper 25566/25567 sur Internet. Pour un serveur autonome, l'authentification par mot de passe LarpersCoin est activée par défaut.
- EduFloodgate authentifie les joueurs Bedrock et Education via sa clé. Ne partage jamais les fichiers de clés Floodgate.

## Données
Les soldes sont dans `plugins/LarpersCoin/data.yml`; clans, quêtes, coffre partagé et hôtel des ventes sont dans `plugins/LarpersCoin/features.yml`.

## État et limites à connaître
Ce dépôt reste en développement et ne doit pas encore être considéré comme prêt pour la production. Les échanges, quêtes, niveaux/rôles de clans et hôtel des ventes ont une première implémentation côté code, mais les tests automatisés et les essais sur un vrai serveur Paper 26.3 ne sont pas encore confirmés. Les menus Larry utilisent une bordure commune et des recherches par chat compatibles avec Bedrock. Le menu primes et la recherche AH sont ajoutés dans la version 1.0.2. La version 1.0.3 ajoute le menu de loterie administrable et les préfixes de rang dans le chat et le tab. EduGeyser/EduFloodgate sont empaquetés automatiquement; les connexions Bedrock et hors-ligne doivent encore être testées sur le serveur réel.

Consulter [docs/STATUS.md](docs/STATUS.md) pour le suivi des fonctions, [docs/CONFIGURATION.md](docs/CONFIGURATION.md) pour les permissions et [CHANGELOG.txt](CHANGELOG.txt) pour les changements de la version 1.0.0.


## Authentification intégrée (version de développement)

- Comptes Java crack : `/register <motdepasse>` à la première visite, puis `/login <motdepasse>`.
- Mots de passe stockés sous forme PBKDF2-HMAC-SHA256 avec sel individuel dans `plugins/LarpersCoin/auth.yml`.
- Cinq tentatives incorrectes déclenchent un blocage temporaire de 15 minutes.
- Les commandes et actions de jeu sont bloquées tant que le joueur n'est pas authentifié.
- Les comptes Bedrock sont reconnus par Floodgate; les autres joueurs utilisent l'authentification LarpersCoin.
- Le paquet configuré pour les comptes crack utilise `online-mode=false`; ne l'expose jamais directement derrière un proxy non sécurisé et ne partage pas les ports avec un serveur non fiable.

Cette fonctionnalité doit encore réussir la compilation CI et être testée en jeu avant d'être considérée prête pour la production.


## Dashboard web administrateur (1.0.7)

Le SMP démarre un dashboard intégré sur `127.0.0.1:8765` (HTTP local uniquement). Pour un accès public HTTPS, démarre le serveur puis lance `DEMARRER-DASHBOARD-HTTPS.bat` dans le dossier SMP/serveur: le script lance un tunnel Cloudflare et affiche une URL `https://...trycloudflare.com`. Garde cette URL et le jeton admin secrets; le tunnel reste actif tant que la fenêtre est ouverte. Le script télécharge `cloudflared.exe` depuis les publications officielles Cloudflare. Le jeton administrateur est généré au premier démarrage dans `plugins/LarpersCoin/web-admin.yml`; ne le publie jamais et ne l'envoie pas dans un salon public.

Le dashboard affiche les joueurs connectés, leur ping, les alertes anti-triche et permet à un administrateur de réinitialiser un compte. La réinitialisation révoque le compte, expulse le joueur connecté et l'oblige à refaire `/register`. La commande en jeu `/admin reset <pseudo>` reste réservée à `larpers.admin` ou OP.

Pour accéder au dashboard depuis Internet en HTTPS, démarre le serveur puis lance `DEMARRER-DASHBOARD-HTTPS.bat`. Le script ouvre un tunnel Cloudflare et affiche une URL publique `https://...trycloudflare.com`. Il utilise le dashboard local `127.0.0.1:8765`, sans exposer ce port sur le routeur ni modifier `web-admin.bind`. Garde l’URL et le jeton secrets; le tunnel reste actif tant que la fenêtre est ouverte.

## Anti-triche sans bannissement automatique

Les détections Speed, Fly, Reach et X-Ray suspect ajoutent des alertes et des éléments de preuve consultables par le staff avec `/ac log`, `/ac top` et dans le dashboard. Aucun seuil ne déclenche de kick ou de ban automatique. Les statistiques X-Ray sont heuristiques et doivent être vérifiées manuellement avant toute sanction.


## Compatibilité Java et anti-triche (1.0.8)

Les ZIP générés installent automatiquement:
- **ViaVersion 5.12.2** et **ViaBackwards 5.12.2** sur Lobby et SMP (et sur le serveur autonome), pour permettre aux clients Java de différentes versions prises en charge de rejoindre le serveur Paper 26.3.
- **GrimAC** sur le SMP (et sur le serveur autonome). GrimAC est compatible Paper et fournit les contrôles de mouvement, combat et paquets. Grim exempte les joueurs EduGeyser/EduFloodgate de ses contrôles; les joueurs Bedrock ne doivent donc pas être jugés sur les mêmes détections Java.
- Le fichier `plugins/GrimAC/punishments.yml` est préinstallé avec uniquement `[alert]` et `[log]`: **aucun kick ou ban automatique** n'est configuré. Les alertes doivent être vérifiées par un membre du staff avant toute sanction.

## Console web en direct

Le dashboard du SMP (ou du serveur autonome) affiche les dernières lignes du journal et actualise les données toutes les 2 secondes. Il permet aussi d'envoyer une commande comme si elle était saisie dans la console serveur. Toutes les commandes sont exécutées avec les droits console: garde donc le jeton privé.

Le dashboard écoute sur `http://127.0.0.1:8765` en local. Pour l’accès extérieur HTTPS, utilise `DEMARRER-DASHBOARD-HTTPS.bat`; ne publie jamais directement le port 8765 en HTTP. Le jeton est dans `plugins/LarpersCoin/web-admin.yml`.


## Minecraft Education Edition

Le réseau utilise **EduGeyser + EduFloodgate** sur Velocity. Le paquet autonome utilise leurs versions Spigot. Les joueurs Minecraft Education peuvent rejoindre le même serveur que les joueurs Java et Bedrock.

- Au premier démarrage, EduGeyser affiche une **Connection ID** stable dans la console. Les joueurs la saisissent dans l'écran de connexion multijoueur de Minecraft Education.
- Pour créer un code d'invitation lié à une école, lance `/edu joincode add` depuis la console Velocity et connecte un compte Microsoft 365 Education.
- L'inscription du serveur dans la liste intégrée à Education nécessite les droits Global Admin du tenant Microsoft 365.
- Redirige **TCP et UDP 19132** vers Velocity. EduGeyser utilise NetherNet sur les deux protocoles.
- Vérifie que la version installée de Minecraft Education figure dans les versions prises en charge par EduGeyser.

Documentation: [EduGeyser](https://edugeyser.org/) · [Méthodes de connexion](https://edugeyser.org/wiki/geyser/education/connection-methods/) · [EduFloodgate](https://edugeyser.org/wiki/geyser/education/edufloodgate/).
