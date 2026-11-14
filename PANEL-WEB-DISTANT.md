# Panel web distant LarpersCoin

Le panneau est intégré au plugin Paper et démarre uniquement sur le SMP, sur `127.0.0.1:8765`. Le Lobby ne tente plus d'utiliser ce port. Le panneau n'ouvre aucun port Minecraft ou HTTP sur Internet; l'accès distant passe par Cloudflare Tunnel.

## 1. Démarrer le réseau

1. Lance `START-NETWORK.bat`.
2. Le terminal PowerShell demande le mot de passe du panneau de façon masquée.
3. Choisis un secret unique d'au moins 20 caractères. Il n'est pas écrit dans le dépôt ni dans un fichier; il reste dans l'environnement des processus jusqu'à l'arrêt du réseau.
4. Attends que le SMP démarre et affiche `Panel web actif sur 127.0.0.1:8765`.

Le mot de passe doit être ressaisi après un redémarrage complet de Windows ou du réseau. Une commande `/redemarrer` dans le superviseur conserve le secret du processus parent.

## 2. Démarrer le tunnel distant

Installe Cloudflare Tunnel (`winget install --id Cloudflare.cloudflared -e`), puis exécute `DEMARRER-PANEL-DISTANT.bat`. Le script vérifie d'abord que le panneau répond localement. Le terminal affiche ensuite une URL HTTPS temporaire `trycloudflare.com`. Ouvre cette adresse depuis ton téléphone ou ton ordinateur et connecte-toi avec le secret saisi à l'étape 1.

Garde la fenêtre du tunnel ouverte. L'adresse temporaire peut changer au prochain démarrage. Pour une adresse permanente, configure un tunnel Cloudflare nommé avec un domaine que tu possèdes et active Cloudflare Access devant le panneau.

## 3. Fonctions disponibles

- Tableau de bord et liste des joueurs connectés.
- Consultation des inventaires des joueurs en ligne (contenu, armure et main secondaire).
- Console de commandes du SMP avec les privilèges de la console.
- Historique local des connexions et commandes dans `plugins/LarpersCoin/panel-audit.log`.

## Dépannage

- Si le journal SMP affiche `Panel web désactivé`, relance le réseau avec `START-NETWORK.bat` et saisis un secret d'au moins 20 caractères.
- Si le journal affiche `Address already in use`, ferme l'ancien processus Paper/Java avant de relancer le réseau.
- Si le panneau local répond mais pas l'URL distante, vérifie que `cloudflared` reste ouvert.

## Sécurité

- Le serveur HTTP écoute exclusivement sur l'interface loopback `127.0.0.1`.
- N'expose jamais le port 8765 avec une redirection de port du routeur.
- Ne publie jamais les ports Paper 25566/25567.
- Les commandes sont exécutées avec les droits console: toute personne qui obtient l'accès au panneau peut administrer le SMP. Ne partage pas l'URL et le secret.
- Les sessions web expirent après 12 heures; déconnecte-toi sur les appareils partagés.
- Le tunnel temporaire est pratique pour tester. Pour un usage quotidien, préfère un tunnel nommé Cloudflare avec domaine personnel et une politique Cloudflare Access autorisant uniquement ton compte.
