# Configuration de LarpersCoin

Le plugin crée automatiquement `plugins/LarpersCoin/data.yml` et `plugins/LarpersCoin/features.yml` au premier démarrage.

Arrête le serveur avant de modifier les fichiers YAML, puis redémarre-le pour recharger les paramètres.

## Prix de la boutique

Les prix sont exprimés en LPC par unité. Les clés correspondent aux identifiants internes du catalogue.

```yaml
shop:
  prices:
    diamant: 250.0
    fer: 25.0
    or: 40.0
    émeraude: 100.0
    redstone: 10.0
    lapis: 12.0
    obsidienne: 35.0
    pain: 5.0
    pomme: 4.0
    bois: 8.0
    pierre: 2.0
    terre: 1.0
    elytra: 5000.0
    netherite: 1500.0
```

Le menu `/shop` sépare les objets par catégories. Clic gauche achète 1 unité, clic droit 16 et Maj-clic 64. Les achats vérifient le solde et l'espace disponible avant le débit.

## Banque de questions du quiz

La banque est créée automatiquement si elle est absente. Ajoute tes questions sous `quiz-bank`. Chaque question doit avoir une question, une réponse qui correspond exactement à l'un des choix et entre 2 et 6 choix.

```yaml
quiz-bank:
  diamants:
    question: "À quelle couche trouve-t-on souvent des diamants?"
    answer: "profonde"
    choices:
      - "surface"
      - "profonde"
      - "dans le Nether"
  ender:
    question: "Quel objet permet d'activer un portail de l'End?"
    answer: "oeil de l'Ender"
    choices:
      - "perle de l'Ender"
      - "oeil de l'Ender"
      - "poudre de blaze"
```

Une bonne réponse donne 50 LPC et ajoute un point au classement `/quiz top`. Le délai entre deux questions est de 60 secondes.

## Permissions

- `larpers.staff`: menu staff, vanish, invsee, freeze, staffchat et actions de modération.
- `larpers.admin`: commandes d'administration économique et actions réservées aux administrateurs.
- Les deux permissions sont attribuées aux opérateurs par défaut dans `plugin.yml`.

## Sauvegardes

- `data.yml`: soldes, primes, loterie et historique économique (2000 opérations récentes au maximum).
- `features.yml`: clans, coffre partagé et son journal, quêtes, scores/questions du quiz, prix de boutique et hôtel des ventes.
- Les données de fonctionnalités sont sauvegardées périodiquement et à l'arrêt du plugin. Le coffre de clan ouvert est également recopié lors des sauvegardes périodiques.
