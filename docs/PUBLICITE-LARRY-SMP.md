# Publicité Larry SMP — format vertical (30 secondes)

Objectif : TikTok, YouTube Shorts et Instagram Reels. Captures réelles du serveur de test, sans annoncer de fonctionnalités non validées.

## Plan de tournage

| Temps | Capture | Texte |
|---|---|---|
| 0–4 s | Survol d'une base ou du spawn, caméra fluide | UN NOUVEAU SMP |
| 4–9 s | /shop, /sell all, /money | DEVIENS LE PLUS RICHE |
| 9–14 s | Groupe devant une base, /clan et coffre | CRÉE TON CLAN |
| 14–22 s | /bounty, deux contributions sur la même cible, duel PvP, annonce du paiement | FAIS MONTER LA PRIME |
| 22–26 s | /lottery et jackpot | TENTE DE GAGNER LE JACKPOT |
| 26–30 s | Logo et adresse | LARRY SMP — larry-smp.ca — JAVA + BEDROCK |

Vérifier le domaine et la connexion publique avant publication. Ne pas présenter Education comme fonctionnel avant validation.

## Voix off

« Et si chaque joueur avait un prix ? Bienvenue sur Larry SMP. Construis ta base, vends tes ressources, fais grandir ton clan et place des primes sur tes rivaux. Les primes s'additionnent, et le serveur conserve l'argent jusqu'à ce qu'un joueur admissible élimine la cible. À toi de devenir le plus riche. Rejoins Larry SMP ! »

## Enregistrement et montage

- OBS Studio : enregistrer Minecraft en 1920 x 1080 à 60 images/s, en gardant le sujet au centre, ou directement en 1080 x 1920.
- F1 pour les panoramas sans interface, F5 pour les scènes à la troisième personne. Réafficher l'interface pour les commandes.
- Filmer les scènes avec deux ou trois comptes de test, pas sur l'économie publique. Ne pas montrer les mots de passe ou le dashboard administratif.
- Importer les clips dans CapCut ou DaVinci Resolve, projet 9:16, 30 s. Couper au rythme d'une musique autorisée pour la publication. Ajouter le texte et les sous-titres.
- Export MP4 H.264, 1080 x 1920, 60 FPS, 12–20 Mbit/s.

## Démonstration des primes

Sur une copie de test, deux joueurs placent respectivement 100 et 250 Larry sur la même cible via /bounty <pseudo> <montant>. Le total affiché est 350 Larry. Chaque contribution est débitée puis conservée dans le registre serveur data.yml, avec la sauvegarde du solde. Aucun objet monétaire n'est remis à la cible. Les contributions peuvent continuer sans limite de nombre ni expiration automatique, sous le plafond technique du registre (10 milliards Larry). Un kill admissible verse le total au vainqueur et remet la prime à zéro. Le délai anti-farm de 24 heures par paire reste actif. Vérifier le paiement et la persistance après redémarrage avant de filmer.

