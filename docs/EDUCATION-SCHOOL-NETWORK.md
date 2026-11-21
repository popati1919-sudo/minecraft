# Minecraft Education depuis un réseau scolaire HTTPS

## Objectif

Permettre à Minecraft: Education Edition, depuis l'école, de rejoindre le réseau Larry SMP hébergé à la maison, sans modifier Velocity, Lobby ou SMP.

## Ce que signifie le test Java réussi

Le fait que Minecraft Java ait rejoint `<adresse>:25565` prouve que le réseau scolaire autorise au moins cette connexion TCP vers le serveur. Cela ne prouve pas que l'UDP est autorisé.

- Java Edition utilise TCP pour le port de jeu (dans ce réseau : TCP 25565).
- EduGeyser utilise NetherNet/WebRTC pour Education Edition.
- EduGeyser utilise un port TCP pour la signalisation HTTP(S), et un port UDP distinct pour le trafic de jeu. Le trafic UDP reste nécessaire après la signalisation.

Le port TCP 25565 de Velocity ne peut donc pas, à lui seul, transporter une session Education. Déplacer uniquement la signalisation EduGeyser sur TCP 25565 ou TCP 443 ne corrige pas un blocage UDP.

## Architecture Larry SMP

```
Minecraft Education (école)
        |
        | signalisation TCP / HTTPS
        | trafic NetherNet / UDP requis
        v
EduGeyser (entrée Education/Bedrock)
        |
        v
Velocity TCP 25565
        |
        +--> Lobby
        +--> SMP (Paper)
```

Garder EduGeyser comme entrée séparée devant Velocity. Ne pas remplacer le port Java 25565 et ne pas exposer directement Paper.

## Vérifications à faire avant de modifier les ports

1. Depuis l'école, confirmer avec le service informatique si le réseau bloque tout UDP sortant, ou seulement certains ports.
2. Demander si le trafic WebRTC/TURN officiel de Microsoft est autorisé, notamment TCP distant 443 vers les services TURN Microsoft, ainsi que les flux UDP nécessaires.
3. À la maison, vérifier que le pare-feu Windows et le routeur transmettent le port UDP configuré pour EduGeyser, ainsi que son port TCP de signalisation.
4. Vérifier les ports réellement annoncés par EduGeyser dans son journal de démarrage. Ne pas supposer que le port 25565 TCP est aussi le port UDP.

## Demande à transmettre à l'administrateur réseau

« Minecraft Java fonctionne depuis ce réseau vers mon serveur en TCP 25565, mais Minecraft Education via EduGeyser échoue avec “Fin du flux”. Pouvez-vous vérifier les règles WebRTC/NetherNet et indiquer si l'UDP sortant est bloqué ? Si une exception est possible, merci de confirmer les destinations et ports autorisés pour Minecraft Education/EduGeyser, y compris les services TURN Microsoft sur TCP 443 et les flux UDP nécessaires. Je ne demande pas de désactiver le pare-feu ni de contourner le filtrage. »

## Limite actuelle et solution supportable

La documentation publique actuelle d'EduGeyser indique que TCP sert à la signalisation et UDP transporte le trafic NetherNet. Elle ne documente pas de mode de jeu TCP-only. Par conséquent, une configuration du seul serveur Larry SMP ne peut pas rendre fiable une connexion Education si l'école bloque absolument tout UDP.

La voie supportable est une exception réseau approuvée par l'école pour les flux nécessaires. Si aucune exception UDP n'est possible, il faut utiliser un réseau ou un accès distant explicitement autorisé par l'établissement; ne pas annoncer un faux mode « HTTPS uniquement » qui ne transporte pas le jeu.
