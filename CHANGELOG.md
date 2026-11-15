# Journal des modifications

## 0.6.0 — Socle initial dans GitHub
- Création de la structure Gradle Java 25.
- Ajout du workflow GitHub Actions pour compiler et publier un artefact JAR.
- Ajout du script Windows BUILD-PLUGIN.bat avec journal de compilation.
- Implémentation initiale des soldes LPC persistants, paiements, classement, récompenses quotidiennes, primes et loterie.
- Déclaration des commandes et permissions dans plugin.yml.

Cette version est une base de développement. Elle n'est pas encore certifiée par une compilation Paper 26.3 ni par un test sur serveur réel.

## 1.0.5 — Inscription, économie et menus Bedrock
- Simplification de /register: un seul mot de passe demandé, sans confirmation répétée.
- Retrait des commandes Quiz et Boutique; ajout du menu de classements /top.
- Récompense quotidienne ramenée à 60 LPC + 10 LPC par jour de série (maximum 130 LPC).
- Récompenses des quêtes ajustées à 100 LPC (quotidienne) et 400 LPC (hebdomadaire), avec objectifs hebdomadaires adaptés.
- Ticket de loterie ajusté à 50 LPC.
- Menus avec bordures grises discrètes; recherche de joueurs utilisable au clavier dans le chat Bedrock.
- Coffre de clan: 1 emplacement pour les nouveaux clans, améliorations payantes par paliers jusqu'à 54; conservation des anciens coffres déjà remplis.
- Ajout de /money comme alias de /balance.
- Build GitHub Actions et test réel sur Bedrock en attente.
