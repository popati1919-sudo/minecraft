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
