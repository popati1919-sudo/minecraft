# Larpers Coin SMP 26.3

Plugin Paper SMP avec une économie unique **Larpers Coin (LPC)**.

## Cible technique
- Paper Minecraft 26.3
- Java 25
- Gradle 9.2.1
- Bedrock visé via Geyser + Floodgate (tests de compatibilité à faire)

## Compilation Windows
1. Installer un JDK 25.
2. Télécharger ou cloner ce dépôt.
3. Lancer `BUILD-PLUGIN.bat`.
4. Le JAR compilé est copié dans `plugins/LarpersCoin.jar`.
5. Le journal est enregistré dans `BUILD-PLUGIN.log`.

## Compilation GitHub
Ouvrir l'onglet **Actions**, lancer ou consulter **Build LarpersCoin**, puis télécharger l'artefact `LarpersCoin-Paper-26.3`.

## Commandes du socle actuellement implémentées
- `/balance [joueur]` — consulter son solde; consulter un autre joueur nécessite les permissions staff.
- `/pay <joueur> <montant>` — transfert vers un joueur en ligne.
- `/baltop` — classement des 10 plus grosses fortunes.
- `/daily` — récompense quotidienne avec série.
- `/bounty <joueur> <montant>` — prime sur un joueur en ligne, payée au tueur.
- `/lottery buy [1-10]`, `/lottery pot`, `/lottery draw` — tickets et jackpot.
- `/eco give|take|set <joueur> <montant>` — administration (OP ou permission admin).

## Installation
1. Installer le serveur Paper correspondant à la version ciblée.
2. Compiler et placer `LarpersCoin.jar` dans le dossier `plugins/`.
3. Démarrer le serveur et vérifier la console.
4. Les données sont conservées dans `plugins/LarpersCoin/data.yml`.

## Portée actuelle
Le dépôt contient le premier socle économique. Les clans, les quêtes avancées, le quiz, l'hôtel des ventes, les menus graphiques et les outils de modération restent à développer. Consultez [docs/STATUS.md](docs/STATUS.md).

**Règles du projet :** aucun claim, aucune protection de terrain par parcelle, aucun VIP. Les commandes vanilla ne sont pas remplacées.
