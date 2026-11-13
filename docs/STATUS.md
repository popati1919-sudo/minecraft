# État du projet Larpers Coin SMP 26.3

Dernière mise à jour: 2026-11-13 — version 0.9.0.

## Implémenté dans le dépôt
- [x] Économie LPC persistante: soldes, paiements, classement, daily, primes, commandes admin et historique des 10 dernières transactions par joueur.
- [x] Loterie: tickets, jackpot persistant et tirage périodique.
- [x] Hôtel des ventes: mise en vente, liste et achat avec validation d'inventaire et paiement LPC.
- [x] Boutique LPC: GUI cliquable, catalogue, recherche et achats de 1 à 64 objets; prix stockés dans features.yml et modifiables.
- [x] Échanges joueurs: invitations, offres d'objets et de LPC, double confirmation, invalidation des confirmations après modification, contrôle du solde et de l'espace d'inventaire avant transfert. Les objets restent dans l'inventaire avant validation.
- [x] Quêtes quotidiennes et hebdomadaires: suivi automatique des blocs cassés, cultures mûres récoltées, créatures vaincues et poissons pêchés; récompense unique par période.
- [x] Clans: création, invitation, adhésion, départ, banque, coffre, XP et niveaux par activité, rôles leader/officer/member, alliances bilatérales sur demande et acceptation, statistiques et classements.
- [x] Quiz Minecraft à choix multiples avec GUI et récompense.
- [x] Menus GUI pour clans, quêtes, boutique et quiz; outils staff avec raccourcis invsee, vanish, freeze et staffchat.
- [x] Anti-triche initial: alertes staff pour vitesse anormale, vol/montée suspecte et portée de frappe; exemptions de téléportation/impulsion; compteurs de violations temporaires. Aucune sanction automatique.
- [x] Workflow GitHub Actions et script de compilation Windows.
- [ ] Compilation CI de la version 0.9.0 après ajout de l'anti-triche: à confirmer; la dernière compilation confirmée concerne le code antérieur (commit c081497fdb512fb0be74b798ab088c2638f3b5c5).

## Limites restantes
- [x] Boutique: GUI par catégories, navigation paginée, quantités par clic, prix configurables et contrôle de capacité d'inventaire. Stock illimité (pas de gestion de stock).
- [x] Clans: transfert du chef, dissolution avec confirmation, invitations UUID pouvant être acceptées hors ligne, rôles leader/officer/member et retraits de banque limités au chef/officier.
- [x] Coffre de clan: contenu persistant, verrou contre les ouvertures concurrentes, accès membres et journal des changements consultable par chef/officiers.
- [x] Quiz: banque de questions modifiable dans features.yml et classement des scores.
- [x] Menu admin: deux pages paginées avec sélecteurs de joueurs, vanish, freeze, raccourcis économie et loterie.
- [x] Écriture atomique de data.yml et features.yml: fichier temporaire, flush disque et remplacement atomique avec repli si le système de fichiers ne le permet pas.
- [ ] Journal transactionnel commun aux deux fichiers: les écritures individuelles sont protégées, mais une coupure entre deux fichiers distincts peut encore interrompre une opération inter-systèmes.
- [ ] Calibrage anti-triche sur serveur réel avec joueurs à latence variable: les seuils sont conservateurs et les alertes doivent être vérifiées par le staff.
- [ ] Essais fonctionnels en serveur Paper 26.3 réel: le build CI compile et produit le JAR, mais aucune session de jeu réelle n'a été exécutée ici.
- [ ] Validation Java/Bedrock via Geyser + Floodgate sur serveur dédié (requiert un serveur réel et des clients de test).

## Hors périmètre explicite
- Aucun claim / protection de terrain.
- Aucun rang VIP.
- Ne pas remplacer les commandes vanilla ni les sélecteurs vanilla.
- Geyser et Floodgate sont des plugins externes à installer séparément.

## Commandes récemment ajoutées
- /history [joueur] — historique économique (consultation d'un autre joueur réservée au staff).
- /trade <joueur>, /trade accept, /trade item, /trade money <montant>, /trade confirm, /trade cancel.
- /quests, /quests claim, /quests weekly, /quests weekly claim.
- /shop search <objet>, /shop buy <clé> [quantité].
- /clan role <joueur> <officer|member>.
- /clan transfer <joueur>, /clan disband confirm, /clan audit.
- /quiz top.
- /ac alerts, /ac info <joueur>, /ac reset <joueur> — diagnostic anti-triche réservé au staff.
- /clan allies request|accept|remove <clan>.
