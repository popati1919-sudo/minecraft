# Larpers Coin SMP 26.3

Plugin Paper pour le serveur **Larpers Coin SMP**, avec monnaie LPC et systèmes communautaires. Le dépôt contient le code source, les ressources, le script Windows et le workflow GitHub Actions.

## Cible
- Minecraft Java avec Paper 26.3 / Java 25.
- Compatibilité Bedrock prévue par **Geyser + Floodgate** installés séparément sur Paper; ce dépôt ne contient pas ces projets tiers.
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
- `/ah list`, `/ah sell <prix>`, `/ah buy <id>` — hôtel des ventes persistant.
- `/trade <joueur>`, `/trade accept`, `/trade item`, `/trade money <montant>`, `/trade confirm`, `/trade cancel` — échange avec double confirmation.

### Modération
- `/admin` — menu du staff.
- `/vanish`, `/invsee <joueur>`, `/freeze <joueur>`, `/staffchat <message>`.
- Accès contrôlé par `larpers.staff` et `larpers.admin` (OP par défaut).

## Données
Les soldes sont dans `plugins/LarpersCoin/data.yml`; clans, quêtes, quiz et hôtel des ventes sont dans `plugins/LarpersCoin/features.yml`.

## État et limites à connaître
Ce dépôt reste en développement et ne doit pas encore être considéré comme prêt pour la production. Les échanges, quêtes, niveaux/rôles de clans et achats boutique ont maintenant une première implémentation côté code, mais les tests automatisés et les essais sur un vrai serveur Paper 26.3 ne sont pas encore confirmés. Le menu staff possède deux pages d'actions rapides; le quiz dispose d'une banque configurable et d'un classement. Geyser/Floodgate et la compatibilité Bedrock doivent être testés sur un serveur dédié.

Consulter [docs/STATUS.md](docs/STATUS.md) pour le suivi des fonctions, [docs/CONFIGURATION.md](docs/CONFIGURATION.md) pour les prix/quiz/permissions et [CHANGELOG.txt](CHANGELOG.txt) pour les changements de la version 0.8.0.
