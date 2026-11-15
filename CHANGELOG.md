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

## 1.0.6 — Administration, authentification et lobby
- /admin tient maintenant sur une seule page; /admin reset <joueur> révoque son ancien mot de passe, expulse le joueur et impose une nouvelle inscription.
- Base auth.yml partagée entre Lobby et SMP, y compris détection automatique pour les anciennes installations réseau.
- /admin ip <adresse:port> <nom> permet aux administrateurs de modifier les informations affichées dans TAB; TAB affiche aussi le ping.
- Récompense quotidienne de connexion supprimée.
- Loterie: achat personnalisé de 1 à 1 000 tickets.
- Coffre de clan: un emplacement initial et amélioration payante directement depuis le menu.
- Recherche par pseudo dans les listes de joueurs, les primes et l'hôtel des ventes.
- Lobby entièrement vide, sans plateforme; vol autorisé dans le lobby d'authentification.
- Distances de rendu/simulation réduites et options G1GC pour diminuer la charge.


## 1.0.7
- Ajout d'un dashboard web administrateur protégé par jeton.
- Réinitialisation des comptes disponible uniquement aux administrateurs autorisés.
- Anti-triche en mode alertes uniquement : Fly, Speed, Reach et détection statistique X-Ray; aucun kick ou ban automatique.


## 1.0.8
- Ajout de GrimAC avec alertes et logs uniquement; configuration sans kick/ban automatique.
- Ajout de ViaVersion et ViaBackwards sur les backends Paper pour la compatibilité des versions Java.
- Dashboard web: console du serveur en direct et exécution de commandes réservées au détenteur du jeton administrateur.
