# Audit anti-duplication — 27 novembre 2026

Base examinée : commit 0379abfab3f5adf166c04fd8942e3e658cadd753.
Les trois workflows de ce commit sont réussis. Aucun test de panne en jeu n'a été réalisé. Les risques de duplication sont des conclusions d'analyse statique, pas des exploits reproduits.

## Priorité P0 — intégrité des biens

| ID | Défaut ou risque | Source | Correction attendue |
|---|---|---|---|
| D01 | Coffre et inventaire joueur persistés indépendamment : risque de duplication ou perte après panne | LarpersFeatures.java:115, 1705, 2029 | Journal transactionnel des transferts, états avant/après et reçus persistants |
| D02 | Validation de succès dans features.yml, crédit dans data.yml, sauvegarde différée | LarpersFeatures.java:807-830 | Enregistrer identifiant unique et récompense dans le même commit économique |
| D03 | Quiz : crédit puis suppression de la réponse dans un autre fichier | LarpersFeatures.java:1973-1980 | Identifiant unique de tentative et paiement idempotent |
| D04 | Journal trade supprimé après Player.saveData() sans confirmation durable indépendante | SecureTradeManager.java:348-371 | Protocole de reprise durable et tests d'injection de panne |
| D05 | Même limite pour commerce | SecureTradeManager.java:394-415 | Même protocole, conserver les journaux finalisés pour audit |
| D06 | Les journaux YAML n'utilisent pas force(true) avant déplacement | SecureTradeManager.java:338-347 | Forcer la persistance et tester la récupération |
| D07 | Ouverture du coffre ajoute un espace après les deux-points, alors que les gestionnaires extraient le nom sans trim | LarpersFeatures.java:722,1710,2017,2033 | Identifier le coffre par un InventoryHolder stable |
| D08 | Certains clics depuis l'inventaire du joueur ne vérifient pas le propriétaire du coffre ni les emplacements verrouillés | LarpersFeatures.java:1705-1716 | Filtrer toutes les actions, y compris collecte globale et touches spéciales |
| D09 | YAML économique invalide chargé via loadConfiguration sans arrêt explicite de l'économie | LarpersCoin.java:85-86 | Chargement strict, sauvegarde et mise en sécurité en cas de corruption |
| D10 | YAML des fonctionnalités chargé de la même manière | LarpersFeatures.java:97-98 | Même protection |

## Priorité P1 — fraude économique et autres défauts

| ID | Défaut ou risque | Source | Correction attendue |
|---|---|---|---|
| D11 | Les blocs placés par le joueur sont exclus seulement du compteur de bûches, pas des autres compteurs de minage | LarpersFeatures.java:1261-1290 | Exclure les blocs replacés des récompenses de minage concernées |
| D12 | La quête kills peut inclure les morts de joueurs sans réutiliser l'anti-farm PvP | LarpersFeatures.java:1291-1318 | Séparer PvP et créatures, appliquer les règles anti-farm |
| D13 | Progression pêche sans ignoreCancelled | LarpersFeatures.java:1384-1389 | Écouter seulement les prises validées |
| D14 | Événement chat accepte potentiellement un message annulé; paiement non vérifié avant annonce | ChatEvents.java:284-298 | Vérifier autorisation, état et succès d'écriture |
| D15 | Amélioration du coffre débitée avant validation persistante du niveau | LarpersFeatures.java:692-715 | Enregistrer paiement et niveau ensemble |
| D16 | Les prix du marché sont harmonisés pour quelques matériaux mais pas toutes les transformations | LarpersFeatures.java:1527-1590 | Tester les recettes et cycles de conversion avec les prix réels; aucune boucle profitable confirmée |
| D17 | Copie de secours modo conservée après restauration, sans marque de consommation indépendante | StaffWatch.java:79-93,134-174 | Identifiant de session, archive et vérification de la restauration |
| D18 | Absence de contrôle anti-complicité spécifique sur les primes (transfert de prime, pas création d'argent démontrée) | LarpersCoin.java:843-858 | Politique anti-farm dédiée et journal des primes |
| D19 | Calcul monétaire encore basé sur double | LarpersCoin.java:180-223 | Unités entières de 0,0001 Larry avec limites numériques et migration contrôlée |
| D20 | Pas de nettoyage des journaux non commités dans la reprise | SecureTradeManager.java:374-429 | Réconciliation et rétention contrôlée des journaux |

## Validation requise
- Tests en instance isolée : conserver la quantité totale d'objets et les soldes attendus avant/après chaque transfert; simuler échecs d'écriture et redémarrages à chaque frontière de commit.
- Tester les coffres avec deux comptes et tous les types de clics; vérifier les slots verrouillés.
- Vérifier l'idempotence des récompenses et des reçus.
- Vérifier les recettes, arrondis et prix après actualisation du marché.
- Tester les parcours Java, Bedrock et Education après modifications.

Sources dans plugin-source/src/main/java/ca/larperscoin/smp/. Aucun fichier Java modifié pour cet audit.


## Correctifs implémentés après l'audit

| ID | Protection actuelle | Limite de validation |
|---|---|---|
| D01 | Coffre et inventaire modifiés via journal et registre économique; contrôle de conservation des quantités et métadonnées | Tester les arrêts forcés sur Paper |
| D02 | Récompenses monétaires de succès idempotentes dans le registre | Tester la migration des anciens succès |
| D03 | Identifiant du quiz par joueur et horodatage, crédit et validation atomiques | Tester la reprise d'un quiz après panne |
| D04 | Reçu PDC relu dans le fichier .dat avant archivage du journal trade | Tester la vraie sauvegarde Paper 26.3 |
| D05 | Même contrôle pour commerce et coffre | Tester la vraie sauvegarde Paper 26.3 |
| D06 | Écriture du journal avec FileChannel.force(true) avant déplacement | Panne matérielle et systèmes de fichiers non atomiques non garantis |
| D07 | InventoryHolder dédié, identifiant de clan indépendant du titre | Tester ouverture/fermeture et reconnexion |
| D08 | Clic gauche contrôlé sur pile entière, curseur vide, autres clics et drag bloqués | Tester Java, Bedrock, Education |
| D09 | Chargement strict de data.yml, arrêt si invalide | Tester des copies corrompues |
| D10 | Chargement strict de features.yml, validation avant démarrage | Tester des copies corrompues |
| D11 | Les blocs replacés ne donnent plus les récompenses de minage | Tester les blocs déplacés par piston et historiques existants |
| D12 | Quête kills réservée aux créatures; PvP séparé | Tester les catégories de créatures |
| D13 | Pêche annulée ignorée | Tester les plugins de protection |
| D14 | Événement chat autorisé en survie authentifiée, paiement confirmé avant annonce | Tester les autres plugins de chat |
| D15 | Niveau du coffre et solde sauvegardés dans le même registre | Tester les 7 niveaux |
| D16 | Plafonds de revente selon recettes connues et chaînes de fabrication | Recettes spéciales, échanges villageois et sous-produits non exhaustifs |
| D17 | Sauvegarde modo identifiée par session unique; archive de la restauration | Ancien snapshot corrompu : intervention manuelle |
| D18 | Délai de 24 h entre les mêmes joueurs pour récupérer une prime | Ne détecte pas tous les groupes de comptes complices |
| D19 | Unités entières de 0,0001 Larry, sauvegarde préalable et miroir décimal | Montant maximal 10 milliards Larry; vérifier les anciennes données |
| D20 | Journaux non commités de plus de 24 h isolés dans aborted | Archives conservées; politique de purge à choisir |

### Tests automatiques

Depuis plugin-source : `gradle clean build` ou `gradle antiDuplicationTest`.
Le test exécutable contrôle les opérations monétaires, 10 000 échanges de soldes pseudo-aléatoires reproductibles, les décisions de reprise, les limites de coffre, les types de clics autorisés, les reçus NBT, les recettes et la conservation des piles et métadonnées. Ce sont des tests unitaires des règles réutilisées en production, pas des sessions Minecraft simulées.

### Avant déploiement

1. Arrêter Lobby, SMP et Velocity, conserver une sauvegarde intégrale des mondes et des dossiers de plugins.
2. Mettre à jour les deux backends avec la même version. Ne pas revenir à l'ancien plugin après migration sans restaurer une sauvegarde cohérente des données et inventaires.
3. Tester dans une copie du serveur avec deux joueurs : déposer/retirer au coffre, échanger, acheter/vendre, récupérer une annonce, réclamer un succès, interrompre le serveur en laboratoire pendant une transaction. Vérifier conservation des objets et soldes.
4. Examiner les journaux et archives avant toute correction manuelle. Une transaction incohérente peut déconnecter le joueur pour empêcher un écrasement d'inventaire.
5. Ne pas considérer les duplications vanilla, les interactions avec des plugins tiers, les pannes disque ou tous les arbitrages du jeu comme définitivement éliminés par ces tests.

