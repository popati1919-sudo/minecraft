# Audit de 40 points prioritaires — 28 novembre 2026

Base : 948bd231cd9d9d17295be2144d5a52f503f27bc2. Le workflow Build Larry SMP 46091999613 est réussi. Audit statique, aucun exploit reproduit ni test de panne en jeu. Critique désigne un risque potentiel d'identité, de données ou d'intégrité économique; haute désigne un défaut important ou conditionnel. Les points ne sont pas 40 exploits confirmés.

## Authentification
1. Critique — Velocity online-mode=false : les identités Java ne sont pas vérifiées par Mojang dans ce mode mixte. Concevoir une politique premium/crack explicite. (network-package.yml:194)
2. Critique — réservation initiale d'un pseudo non vérifié dans ce mode, y compris un pseudo premium. Ne pas assimiler l'identité offline à une identité premium. (LarpersAuth:213-218,251-255)
3. Critique — disparition de auth.yml traitée comme base vide. Distinguer premier démarrage d'une perte de fichier. (105-111)
4. Haute — sessions existantes conservées lorsque accountsReadable=false. Définir comportement fail closed. (182-200)
5. Haute — callback automatique différé utilise mustReRegister capturé à l'arrivée; vérifier révocation et session au moment de l'exécution. (141-171)
6. Haute — compteurs de verrouillage volatils; prévoir persistance avec expiration. (62-68,244-249)
7. Haute — FileChannel.lock bloquant sur thread serveur. Utiliser un délai borné, I/O hors thread principal. (384-399)
8. Haute — cache mtime/taille insuffisant pour détecter tout remplacement concurrent. Utiliser version ou empreinte sous verrou. (105-111,389)
9. Haute — contrôle des commandes au niveau PlayerCommandPreprocessEvent seulement, plusieurs exécuteurs distincts. Ajouter contrôle métier pour les opérations sensibles. (LarpersAuth:444; LarpersFeatures:onCommand)
10. Haute — contrôle des dégâts directs avant authentification incomplet pour les sources indirectes hors protection lobby. (LarpersAuth:onDamage; LarpersCoin:143-149)

## Transactions
11. Critique — journal corrompu lu avant filtrage du propriétaire, déconnexion de joueurs sans rapport. Index des participants, mise en quarantaine ciblée. (SecureTradeManager:428-442,484-492)
12. Haute — deux handlers de reprise indépendants, ordre des fichiers non trié. Ordonner par séquence durable globale. (423-442,480-492)
13. Critique — reprises LOWEST avant authentification MONITOR; attendre identité vérifiée avant livraison. (SecureTradeManager:423,480; LarpersAuth:421)
14. Critique — reprise parcourt fichiers existants mais ne vérifie pas les commits sans fichier actif ou archive. Réconciliation bilatérale. (SecureTradeManager:378-392,428-431,484-487)
15. Haute — recherche du fichier joueur retourne le premier répertoire existant et non le fichier valide correspondant au monde. Tester les layouts Paper 26.3 et configurables. (363-373)
16. Haute — archives completed et aborted sans politique de rétention/limite disque. (350-353,378-389)
17. Haute — clearCommerceCommit/clearTradeCommit ignorent l'échec de saveData. Marqueurs orphelins et incohérence de reprise. (LarpersCoin:368,392-394)
18. Critique — StaffWatch restaure et archive après saveData sans reçu relu sur disque. Ajouter protocole de reprise vérifié. (StaffWatch:188-194)
19. Critique — aucune barrière globale de transactions pendantes entre commerce, échanges et autres mutations; une erreur de livraison peut nécessiter blocage ciblé jusqu'à reprise. (SecureTradeManager:456-472,494-515)
20. Haute — force du fichier mais pas du répertoire après déplacement, repli non atomique. Risque conditionnel de perte de métadonnées lors d'une coupure. (LarpersCoin:160-178; SecureTradeManager:355-361)

## Économie et clans
21. Critique — création de clan annonce succès malgré échec save(); identifiant bancaire généré en mémoire peut devenir orphelin si une opération économique suit puis redémarrage. (LarpersFeatures:660-664)
22. Critique — transfert de chef/rôle et départ annoncent succès malgré échec save(); autorisations financières peuvent diverger de l'état durable. (686-699,725-732)
23. Haute — dissolution n'efface pas les entrées du registre économique; anciennes identités legacy réutilisables et données orphelines à réconcilier. (160,701-715; LarpersCoin:309-328)
24. Haute — migration coffre ignore les positions au-delà de 54 et les marqueurs gris identifiés par nom; archiver les anomalies au lieu de les éliminer. (162-175)
25. Haute — clic coffre différé ne conserve pas la pile initialement sélectionnée; peut transférer une autre pile si l'état change avant exécution. (188-210,1778-1787)
26. Haute — confirmation de vente d'une pile n'utilise pas de devis/snapshot; lit le contenu actuel du slot. (1986-1990)
27. Haute — choix d'un prix AH utilise l'objet actuellement en main sans snapshot de l'objet montré au début. (447,1979-1984)
28. Haute — modèle de recettes exclut les recettes spéciales, échanges villageois et sous-produits; arbitrages possibles non reproduits. (1595-1632)
29. Haute — compteurs de ventes et historique enregistrés après commit commerce dans fichier différent. Perte de statistiques et prix incorrects après interruption. (1680,1704-1718)
30. Haute — nouveaux reçus de récompenses/claims conservés sans limite; croissance du fichier monétaire et des sauvegardes. (LarpersCoin:289-307)

## Interface et stabilité
31. Haute — lecture des corps HTTP bornée en octets mais sans délai de lecture explicite; trois workers peuvent être occupés par des connexions lentes. (LarpersWebDashboard:113-117,205,315)
32. Haute — effacement des alertes peut s'exécuter après un timeout HTTP, sans annulation. (189-202)
33. Haute — suggestions expirées restent planifiées; longueur de query non bornée par application. (246-291)
34. Haute — token console conservé dans localStorage sans expiration; risque en cas de profil navigateur partagé. (555-558)
35. Haute — handler console enregistre les messages bruts des autres plugins, risque conditionnel de secrets ou commandes sensibles. (118-132)
36. Haute — menus économiques identifiés surtout par titre, pas par InventoryHolder; collision avec des inventaires tiers/customisés. (LarpersFeatures:1960-1990)
37. Haute — ouverture/fermeture des inventaires directement depuis InventoryClickEvent, comportement non garanti par Bukkit. (SecureTradeManager:227-233; LarpersFeatures:1967-1989)
38. Haute — chat de recherche peut traiter un événement déjà annulé; tâches différées sans validation systématique de session/connexion. (LarpersFeatures:2107-2200)
39. Haute — progression des blocs placés persistée avec saveSoon; panne et déplacements de blocs peuvent rendre l'historique incohérent. (1240-1245,1289-1313)
40. Haute — sérialisation YAML entière, fsync, lecture NBT et sauvegarde du joueur synchrones à chaque transfert; risque de lag sévère à charge élevée. (LarpersCoin:160-178; SecureTradeManager:394-409,456-478)

## Validation
Ajouter des tests d'intégration Paper pour identité premium/offline, authentification avant reprise, crash après chaque frontière de commit, journal manquant/corrompu, coffre et permissions, charge et latence des sauvegardes et du dashboard. Les tests unitaires précédents ne couvrent pas ces scénarios. Aucun fichier Java modifié lors de cet audit.
