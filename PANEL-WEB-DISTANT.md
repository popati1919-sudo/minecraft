# Panel web distant LarpersCoin

Le panel web est intégré au plugin Paper et écoute uniquement sur `127.0.0.1:8765`. Il n'ouvre aucun port Minecraft ou HTTP sur Internet. L'accès distant passe par Cloudflare Tunnel.

## 1. Définir le mot de passe administrateur

Sur le PC Windows qui héberge le SMP, ouvre PowerShell et exécute:

```powershell
[Environment]::SetEnvironmentVariable("LARPERS_PANEL_PASSWORD", "REMPLACE_PAR_UN_SECRET_LONG_DE_32_CARACTERES", "Machine")
```

Choisis un secret unique d'au moins 16 caractères (32 ou plus recommandé). Ne le mets jamais dans GitHub, dans un fichier du serveur ou dans une capture d'écran. Redémarre complètement le réseau Minecraft après avoir défini cette variable: Java ne lit les variables d'environnement qu'au démarrage.

Le panel est désactivé automatiquement si le secret est absent ou trop court.

## 2. Démarrer le tunnel distant

Installe Cloudflare Tunnel (cloudflared) sur le PC serveur, puis exécute `DEMARRER-PANEL-DISTANT.bat`. Le terminal affiche une URL HTTPS temporaire `trycloudflare.com`. Ouvre cette adresse depuis ton téléphone ou ton ordinateur, puis connecte-toi avec le secret défini à l'étape 1.

Le tunnel doit rester ouvert pour que le panel soit accessible. Cette URL temporaire peut changer au prochain démarrage. Pour une adresse permanente, configure un tunnel Cloudflare nommé avec un domaine que tu possèdes et active Cloudflare Access devant le panel.

## 3. Fonctions disponibles

- Tableau de bord et liste des joueurs connectés.
- Consultation des inventaires des joueurs en ligne (contenu, armure et main secondaire).
- Console de commandes du SMP avec les privilèges de la console.
- Historique local des connexions et commandes dans `plugins/LarpersCoin/panel-audit.log`.

## Sécurité

- Le serveur HTTP écoute exclusivement sur l'interface loopback `127.0.0.1`.
- N'expose jamais le port 8765 avec une redirection de port du routeur.
- Ne publie jamais les ports Paper 25566/25567.
- Utilise un secret unique et long, garde Windows et cloudflared à jour et protège le compte Cloudflare par MFA.
- Les commandes sont exécutées avec les droits console: toute personne qui obtient l'accès au panel peut administrer le SMP. Ne partage pas l'URL et le secret.
- Les sessions web expirent après 12 heures; déconnecte-toi sur les appareils partagés.

Le tunnel temporaire est pratique pour tester. Pour un usage quotidien, préfère un tunnel nommé Cloudflare avec domaine personnel et une politique Cloudflare Access autorisant uniquement ton compte.
