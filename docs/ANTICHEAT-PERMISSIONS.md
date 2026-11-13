# Permissions anti-triche

L'anti-triche LPC distingue deux niveaux d'accès :

- **Administrateur** : accès complet à `/ac`, y compris `/ac reset <joueur>` et `/ac reload`.
- **Modérateur** : consultation avec `/ac status`, `/ac alerts`, `/ac info <joueur>`, `/ac top` et `/ac log`. Un modérateur ne peut ni réinitialiser les violations ni recharger la configuration.
- **Joueur** : aucun accès aux commandes anti-triche ni aux alertes.

Avec LuckPerms, attribuer le rôle modérateur à un joueur :

```
/lp creategroup moderator
/lp group moderator permission set larpers.anticheat.use true
/lp group moderator permission set larpers.anticheat.alerts true
/lp user Pseudo parent set moderator
```

Pour donner les droits administrateur :

```
/lp user Pseudo permission set larpers.admin true
```

Pour retirer le rôle modérateur :

```
/lp user Pseudo parent remove moderator
```

Les permissions `larpers.anticheat.bypass` et `larpers.anticheat.alerts` sont séparées : recevoir les alertes ne donne pas le droit de contourner les détections.
