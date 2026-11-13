# Larpers Coin SMP 26.3

Plugin Paper pour le serveur **Larpers Coin SMP**, avec monnaie LPC et systèmes communautaires. Le dépôt contient le code source, les ressources, le script Windows et le workflow GitHub Actions.

## Cible
- Minecraft Java avec Paper 26.3 / Java 25.
- Compatibilité Bedrock empaquetée avec **Geyser + Floodgate**.
- Connexion Java non premium en mode hors-ligne avec **AuthMe** obligatoire (voir avertissement sécurité ci-dessous).
- Gradle 9.2.1.
- Aucune protection de terrain, aucun claim, aucun système VIP. Les commandes vanilla et sélecteurs vanilla ne sont pas remplacés.

## Compilation Windows
1. Installer un JDK 25.
2. Cloner ou télécharger ce dépôt.
3. Exécuter `BUILD-PLUGIN.bat`.
4. Le JAR est copié dans `plugins/LarpersCoin.jar`; le journal est dans `BUILD-PLUGIN.log`.

## Compilation automatique
Onglet **Actions** → workflow **Build LarpersCoin**. L'artefact s'appelle `LarpersCoin-Paper-26.3`.

## Commandes incluses

### Économie
- `/balance [joueur]` (`/bal`) — solde.
- `/pay <joueur> <montant>` — paiement entre joueurs connectés.
- `/baltop` — classement des fortunes.
- `/history` — tes 10 dernières transactions; `/history <joueur>` est réservé au staff.
- `/daily` — récompense quotidienne avec série.
- `/bounty` ou `/bounty list` — menu graphique de toutes les primes actives; bouton pour choisir un joueur en ligne et saisir le montant dans le chat.
- `/bounty <joueur> <montant>` — prime payée au joueur qui élimine la cible.
- `/eco give|take|set <joueur> <montant>` — administration.
- `/lottery buy [1-10]`, `/lottery pot`, `/lottery draw` — tickets, jackpot et tirage hebdomadaire.

### Clans
- `/clan create <nom>`, `/clan invite <joueur>`, `/clan join <nom>`, `/clan leave`.
- `/clan transfer <joueur>` et `/clan disband confirm` — transfert du leadership et dissolution contrôlée.
- `/clan audit` — journal des modifications du coffre (chef/officiers).
- `/clan` ouvre le menu graphique; `/clan info`, `/clan stats`, `/clan level`.
- `/clan bank [deposit|withdraw <montant>]`.
- `/clan allies request|accept|remove <clan>` — alliances bilatérales.
- `/clan role <joueur> <officer|member>` — rôle d'officier ou membre.
- `/clan chest` — coffre partagé.
- `/clan top`, `/clan leaderboard`.

### Activités et commerce
- `/quests` ouvre le menu des objectifs; `/quests claim`, `/quests weekly`, `/quests weekly claim` — objectifs automatiques quotidiens et hebdomadaires.
- `/quiz` — quiz Minecraft à choix multiples configurable dans `features.yml`; `/quiz top` — classement des scores.
- `/shop` ouvre la boutique graphique avec catégories, pages et quantités par clic (gauche=1, droit=16, Maj-clic=64); `/shop search <objet>` et `/shop buy <clé> [quantité]` — recherche et achats LPC; prix modifiables dans features.yml.
- `/ah` ou `/ah list` — hôtel des ventes graphique persistant avec barre de recherche (clic sur la boussole, puis saisie dans le chat), filtre vendeur/objet et bouton pour effacer la recherche.
- `/ah search <objet|vendeur>` et `/ah search clear` — recherche directe; `/ah sell <prix>`, `/ah buy <id>` — gestion des annonces.
- `/trade <joueur>`, `/trade accept`, `/trade item`, `/trade money <montant>`, `/trade confirm`, `/trade cancel` — échange avec double confirmation.

### Anti-triche et modération
- `/ac alerts` — activer/désactiver les alertes anti-triche pour le staff.
- `/ac status` — état du module et rappels des sanctions désactivées.
- `/ac alerts` — activer/désactiver ses alertes staff.
- `/ac info <joueur>`, `/ac top`, `/ac log`, `/ac reset <joueur>`, `/ac reload` — diagnostic, classement, journal récent, remise à zéro et rechargement.
- Détections configurables de vitesse anormale, montée verticale répétée et portée de frappe suspecte. Les violations sont sauvegardées dans `anticheat-violations.yml`; réglages dans `config.yml`. Les alertes ne constituent pas une preuve et aucune expulsion/bannissement automatique n'est appliqué.
- `/admin` — menu du staff.
- `/vanish`, `/invsee <joueur>`, `/freeze <joueur>`, `/staffchat <message>`.
- Accès contrôlé par `larpers.staff` et `larpers.admin` (OP par défaut).

## Accès Bedrock et comptes Java non premium
- Geyser-Spigot et Floodgate sont inclus dans le ZIP généré par GitHub Actions.
- Après le premier démarrage du serveur, arrête-le avec `stop`, puis lance `CONFIGURER-BEDROCK.bat` et redémarre. Geyser utilisera Floodgate (`auth-type: floodgate`).
- Lance `OUVRIR-PORTS-WINDOWS.bat` en administrateur pour autoriser les ports dans le pare-feu; redirige aussi **TCP 25565** et **TCP + UDP 19132** sur le routeur.
- `online-mode=false` permet les comptes Java non premium; AuthMe est inclus et doit rester activé. Les joueurs utilisent `/register motdepasse motdepasse` puis `/login motdepasse`.
- **Avertissement sécurité:** en mode hors-ligne, Mojang ne vérifie pas les noms Java. Sans authentification AuthMe correctement configurée, un joueur peut usurper le pseudo d'un autre, y compris celui d'un administrateur. N'accorde jamais OP avant d'avoir sécurisé l'inscription, utilise des mots de passe uniques et sauvegarde les données.
- Floodgate authentifie les joueurs Bedrock via son mécanisme de clé. Ne partage jamais les fichiers de clés Floodgate.

## Données
Les soldes sont dans `plugins/LarpersCoin/data.yml`; clans, quêtes, quiz et hôtel des ventes sont dans `plugins/LarpersCoin/features.yml`.

## État et limites à connaître
Ce dépôt reste en développement et ne doit pas encore être considéré comme prêt pour la production. Les échanges, quêtes, niveaux/rôles de clans et achats boutique ont maintenant une première implémentation côté code, mais les tests automatisés et les essais sur un vrai serveur Paper 26.3 ne sont pas encore confirmés. Le menu staff possède deux pages d'actions rapides; le quiz dispose d'une banque configurable et d'un classement. Le menu primes et la recherche AH sont ajoutés dans la version 1.0.2. Geyser/Floodgate/AuthMe sont empaquetés automatiquement; les connexions Bedrock et hors-ligne doivent encore être testées sur le serveur réel.

Consulter [docs/STATUS.md](docs/STATUS.md) pour le suivi des fonctions, [docs/CONFIGURATION.md](docs/CONFIGURATION.md) pour les prix/quiz/permissions et [CHANGELOG.txt](CHANGELOG.txt) pour les changements de la version 1.0.0.
