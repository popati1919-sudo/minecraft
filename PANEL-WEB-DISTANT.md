# Panel web distant LarpersCoin

Le panneau est integre au plugin Paper et demarre uniquement sur \`127.0.0.1:8765\` sur le SMP. L'acces distant passe par Cloudflare Tunnel.

## 1. Demarrer le reseau

1. Lance \`START-NETWORK.bat\`.
2. Au premier lancement seulement, PowerShell demande le mot de passe du panneau de facon masquee.
3. Le secret doit contenir au moins 20 caracteres. Il est chiffre par DPAPI/Windows pour le compte Windows courant et conserve dans \`%LOCALAPPDATA%\\LarpersCoin\\panel-password.dpapi\`. Le fichier ne contient pas le mot de passe en clair.
4. Aux lancements suivants, le secret chiffre est charge automatiquement sans nouvelle saisie.
5. Attends le message \`SMP est pret\` et le demarrage du panneau web.

Pour changer le mot de passe, arrete le reseau, supprime \`%LOCALAPPDATA%\\LarpersCoin\\panel-password.dpapi\`, puis relance \`START-NETWORK.bat\`: un nouveau mot de passe sera demande. Le secret reste disponible pendant les redemarrages \`/redemarrer\`.

Le superviseur lance directement les processus Java, conserve leur vraie entree console, attend que Paper affiche \`Done (\`, puis configure LuckPerms. En cas d'arret, le journal \`<serveur>/logs/superviseur-console.log\` contient la sortie et les erreurs.

## 2. Demarrer le tunnel distant

Installe Cloudflare Tunnel (\`winget install --id Cloudflare.cloudflared -e\`), puis execute \`DEMARRER-PANEL-DISTANT.bat\`. Le script verifie que le panneau repond localement et ouvre ensuite un tunnel HTTPS temporaire. Garde la fenetre ouverte.

## 3. Fonctions disponibles

- Tableau de bord et liste des joueurs connectes.
- Consultation des inventaires des joueurs en ligne (contenu, armure et main secondaire).
- Console de commandes du SMP avec les privileges de la console.
- Historique local des connexions et commandes dans \`plugins/LarpersCoin/panel-audit.log\`.

## Securite

- Le serveur HTTP ecoute exclusivement sur \`127.0.0.1\`.
- N'expose jamais le port 8765 ni les ports backend Paper 25566/25567 sur Internet.
- Les commandes du panneau utilisent les droits console; ne partage pas le secret.
- Pour changer le mot de passe, supprime le fichier DPAPI local comme indique plus haut.
