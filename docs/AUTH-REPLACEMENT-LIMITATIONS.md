# Authentification LPC sans FastLoginPlus

## Ce que fait cette branche

- Le plugin Paper garde les commandes `/register` et `/login` pour les identités offline.
- Floodgate reste reconnu automatiquement sur Paper.
- L'ancien appel à PlaceholderAPI/FastLoginPlus est retiré du code Paper.
- Le compagnon Velocity `LarpersCoinVelocity` ajoute la commande `/lpcproxy status` et journalise le mode du proxy.
- Le build CI compile et publie séparément les deux JAR.

## Limite de sécurité importante

Velocity possède un réglage `online-mode` global au proxy, pas un mode différent par joueur. En mode `online-mode=false`, un pseudo Java ne prouve pas que le joueur possède le compte Microsoft correspondant. Une simple recherche pseudo -> UUID ne prouve pas la propriété du compte et ne doit jamais permettre de contourner `/login`.

En conséquence, ce remplacement n'active l'authentification automatique Java que lorsque le backend reçoit un UUID premium authentifié par un proxy configuré en mode online et protégé par le forwarding moderne. Avec le paquet actuel LPC (Velocity en mode offline pour autoriser les joueurs crack), les joueurs Java Premium et Crack restent soumis à l'authentification LPC jusqu'à ce qu'une vraie intégration de login hybride soit ajoutée ou qu'une stratégie d'entrée distincte soit adoptée.

Ne définis jamais `auth.proxy-authenticated: true` sur un Lobby accessible directement ou non protégé. Le SMP ne peut utiliser ce réglage que s'il est strictement privé derrière le Lobby et le proxy, avec forwarding moderne et secret identique.

## Commande de diagnostic

Dans la console Velocity ou en jeu avec permission :
`/lpcproxy status`

Cette commande affiche le mode online/offline réel du proxy. Elle ne prétend pas authentifier un joueur et ne remplace pas la vérification Microsoft.
