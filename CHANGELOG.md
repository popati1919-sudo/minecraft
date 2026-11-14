# Journal des modifications

## 1.0.5 — Compatibilité Bedrock et performances
- Regroupement des sauvegardes LPC et des données de fonctionnalités pour réduire les écritures synchrones.
- Loterie stockée en compteurs par joueur avec tirage pondéré et migration des anciens tickets.
- Menus Bedrock : quantité personnalisée de tickets et recherche de cibles de prime par pseudo.
- Sanctions automatiques de l'anticheat désactivées pendant la validation; détections mouvement/portée Bedrock neutralisées pour éviter les faux positifs.


## 0.6.0 — Socle initial dans GitHub
- Création de la structure Gradle Java 25.
- Ajout du workflow GitHub Actions pour compiler et publier un artefact JAR.
- Ajout du script Windows BUILD-PLUGIN.bat avec journal de compilation.
- Implémentation initiale des soldes LPC persistants, paiements, classement, récompenses quotidiennes, primes et loterie.
- Déclaration des commandes et permissions dans plugin.yml.

Cette version est une base de développement. Elle n'est pas encore certifiée par une compilation Paper 26.3 ni par un test sur serveur réel.
