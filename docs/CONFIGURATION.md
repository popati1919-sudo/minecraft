# Configuration de LarpersCoin

Le plugin crée automatiquement `plugins/LarpersCoin/data.yml` et `plugins/LarpersCoin/features.yml` au premier démarrage.

Arrête le serveur avant de modifier les fichiers YAML, puis redémarre-le pour recharger les paramètres.

## Économie

- Récompense quotidienne: 60 Larry + 10 Larry par jour de série, jusqu'à 130 Larry.
- Quête quotidienne: 100 Larry lorsqu'un objectif est atteint.
- Quête hebdomadaire: 400 Larry lorsqu'un objectif est atteint.
- Ticket de loterie: 50 Larry; achat de 1 à 10 tickets pendant une loterie active.
- Coffre de clan: 1 emplacement au départ; améliorations successives à 250, 500, 1 000, 2 000, 4 000 et 8 000 Larry, jusqu'à 54 emplacements.
- Les échanges entre joueurs et l'hôtel des ventes constituent les moyens principaux de commerce; la boutique NPC et le quiz ont été retirés.

## Permissions

- `larpers.staff`: menu staff, vanish, invsee, freeze, staffchat et actions de modération.
- `larpers.admin`: commandes d'administration économique et actions réservées aux administrateurs.
- Les deux permissions sont attribuées aux opérateurs par défaut dans `plugin.yml`.

## Anti-triche v1.0

Le module crée automatiquement les valeurs manquantes dans `plugins/LarpersCoin/config.yml`. Tu peux les modifier puis exécuter `/ac reload`.

```yaml
anticheat:
  enabled: true
  alert-cooldown-seconds: 4
  evidence-limit: 50
  checks:
    speed:
      enabled: true
      max-ground-blocks-per-tick: 1.35
    fly:
      enabled: true
      upward-limit-per-tick: 0.72
      repeated-upward-ticks: 4
    reach:
      enabled: true
      max-distance: 4.45
```

Commandes staff: `/ac status`, `/ac alerts`, `/ac info <joueur>`, `/ac top`, `/ac log`, `/ac reset <joueur>`, `/ac reload`.

- `larpers.anticheat.alerts`: recevoir les alertes (OP par défaut).
- `larpers.anticheat.bypass`: exclure un joueur de toutes les détections (OP par défaut).
- Les compteurs sont enregistrés dans `anticheat-violations.yml`, avec sauvegarde périodique et à l'arrêt.
- Les journaux récents sont conservés en mémoire jusqu'au redémarrage.
- Les détections sont indicatives; elles ne déclenchent aucun kick ni bannissement.

## Sauvegardes

- `data.yml`: soldes, primes, loterie et historique économique (2000 opérations récentes au maximum).
- `features.yml`: clans, coffre partagé et son journal, quêtes, hôtel des ventes.
- Les données de fonctionnalités sont sauvegardées périodiquement et à l'arrêt du plugin. Le coffre de clan ouvert est également recopié lors des sauvegardes périodiques.
