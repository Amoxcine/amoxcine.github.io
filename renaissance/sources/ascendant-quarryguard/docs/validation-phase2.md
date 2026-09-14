# QuarryGuard : validation complementaire

Date : 4 septembre 2026. Validation de laboratoire, a lire avec les journaux cites.

**Conclusion : correctif Java valide sur les scenarios de laboratoire ci-dessous,
non deploye. Pas encore une recette multijoueur ni une migration des anciennes
quarries.** Quatre agents ont contribue aux revues de securite, de performance,
de deploiement et de reseau. Le parent a integre les corrections et execute les
tests ; les agents n'ont pas lance de serveur concurrent.

## Binaire retenu

`integration/build/phase2-9dfa/ascendant-quarryguard-0.1.0-lab.jar`

Ce binaire de phase 2 est archive : le chemin non versionne dans `build/`
sert aux builds suivants. Voir [phase 3](validation-phase3.md) pour la suite.

```text
SHA-256 9DFAADAEF25779978FBAB5FF18885BD469CAF62460DB3D0FF6BC165620E497BA
```

La regression finale complete sur ce binaire passe a 15:27:01. Tous les
groupes sont executes dans la meme JVM, puis le monde est sauvegarde et le
serveur s'arrete avec le code 0, sans terminaison forcee.

- [Regression finale](../results/runtime-full-runtime-regression-baseline0-20260904-132627.log).
- [Binaire et parametres du lancement](../results/run-full-runtime-regression-baseline0-20260904-132627.json).

## Perimetre

Correctif Java cible serveur pour QuarryPlus 21.1.162 et FTB Chunks 2101.1.21,
sous Minecraft 1.21.1 / NeoForge 21.1.248. Aucun deploiement, push, changement
de Packwiz, du client ou du serveur des joueurs n'est effectue par ces essais.
Les tests destructifs sont limites aux mondes plats jetables du laboratoire.

## Resultats acquis

| Essai | Resultat | Portee |
|---|---|---|
| Suite native de regression, profil minimal | PASS a 14:59:06 | Poses normales/avancees, claim interieur, permissions et mutations FTB, persistance NBT, refus sans proprietaire. La conservation de l'UUID en quarantaine est verifiee par appel direct au helper, pas par un monde NBT corrompu charge de bout en bout. |
| Machines alimentees, profil minimal | PASS a 15:00:26 | Cadre, minage, stockage et ramassage natifs ; pause sous claim hostile et reprise apres unclaim. 133 ticks normale, 95 avancee ; 4 et 2 blocs effectivement mines. |
| Objets a la frontiere | PASS dans l'essai alimente | Un objet dont le centre est autorise mais la boite chevauche le claim voisin reste intact. La machine continue le travail permis et collecte les objets autorises. Deux modeles testes. |
| Paquets de geometrie malformee | PASS a 15:09:12, profil minimal | 16 variantes via codec et handler natifs, plus `setArea` direct, tailles 1/2 sur X/Z, deux parcours, origines positives/negatives. Aucun changement du NBT. Appel natif d'une colonne hors zone refuse avant modification de l'eau, de l'energie et du stockage ; quarantaine locale. |
| Regression dans le modpack complet | PASS a 15:12:07 | Tous les 145 JAR serveur conserves, plus le prototype. Poses et protections natives, NBT et droits FTB. Demarrage, sauvegarde et arret propres. |
| Machines alimentees dans le modpack complet | PASS a 15:13:38 | Meme resultat natif que le profil minimal, avec controle des objets chevauchant un claim voisin. |
| Geometrie dans le modpack complet | PASS a 15:14:59 | Memes 16 variantes et refus de colonne hors emprise. |
| Marqueur physique protege, regression finale | PASS a 15:27:01 | Normale et avancee : refus natif avant consommation, NBT et drops du marqueur intacts ; apres unclaim, pose autorisee et exactement un marqueur stocke. Les quatre types de liens natifs et leurs snapshots immuables sont testes separement. |
| Persistance entre deux JVM, phase precedente | PASS a 14:34:59 et 14:35:20 | Proprietaire, emprise, etat, energie et claims preserves ; 25 ticks bloques par machine apres redemarrage. Retour de l'autorisation apres unclaim, pas de minage utile post-redemarrage dans cette fixture. |
| Persistance entre deux JVM, binaire final et modpack complet | PASS a 15:29:50 puis 15:30:24 | Deux poses natives, sauvegarde et nouveau processus. Proprietaires, emprises et claims retrouves ; 25 ticks bloques par machine, etat et energie conserves. Autorisation retablie apres unclaim. Ne prouve pas un minage utile apres redemarrage ni la migration des anciennes machines. |

Preuves :

- [Regression](../results/runtime-runtime-selftest-baseline0-20260904-125855.log).
- [Machines alimentees et frontiere](../results/runtime-runtime-powered-baseline0-20260904-130014.log).
- [Preparation restart](../results/runtime-runtime-restart-prepare-20260904-123447.log).
- [Verification restart](../results/runtime-runtime-restart-check-20260904-123509.log).
- [Preparation restart finale, modpack complet](../results/runtime-full-runtime-restart-prepare-baseline0-20260904-132917.log).
- [Verification restart finale, modpack complet](../results/runtime-full-runtime-restart-check-baseline0-20260904-132952.log).
- [Binaire du restart final](../results/run-full-runtime-restart-check-baseline0-20260904-132952.json).

## Correction du banc de test

Les premiers essais alimentes ont echoue car les objets ajoutes dans une zone
sans joueur n'etaient pas visibles aux requetes natives d'entites. Ce n'etait
pas une preuve de defaut du ramassage QuarryPlus ou de la protection.

Le test promeut temporairement le suivi des entites dans ses quatre chunks via
`PersistentEntitySectionManager.updateChunkStatus(..., TRACKED)`, puis restaure
leur visibilite initiale. Une assertion exige que chaque objet de test soit
visible avant de continuer. Aucune permission n'est contournee et aucun etat
interne de minage n'est force pour obtenir le succes. Les ticks de la machine
sont natifs, mais appeles synchroniquement depuis une commande de laboratoire :
ce n'est pas une session multijoueur ou un cycle complet de ticks du monde.

Le banc verifie l'absence de changement de NBT, energie, inventaire, blocs et
objets pendant les ticks refuses. Il verifie aussi les comptes de drops mines
et l'absence de modification de blocs hors de son emprise. Le nettoyage ne
restaure que les positions possedees par la fixture ; les equipes FTB hors ligne
restent dans le monde jetable.

## Copie du modpack complet

Les 145 JAR serveur, plus le prototype, demarrent ensemble et la suite de
regression passe sur ce profil. Les trois premiers lancements ont ete
interrompus avant assertions par le controle reseau du lanceur. Le dump Java
a revele `LanServerPinger` ; la configuration NeoForge copiee activait
`advertiseDedicatedServerToLan=true`. Ce reglage est maintenant desactive
dans la copie et le preparateur uniquement. Le wildcard UDP a disparu :
Minecraft ecoute sur 127.0.0.1:25585 et Voice Chat sous l'adresse loopback
mappee `::ffff:127.0.0.1:25586`. Aucun port wildcard n'est autorise par le
lanceur. Aucun mod n'a ete retire pour obtenir ce resultat.

Le premier arret de diagnostic a necessite de terminer le processus apres
sauvegarde ; les suivants ont termine proprement. Ces essais interrompus
restent des echecs de protocole archives, pas des PASS retroactifs.

- [PASS complet](../results/runtime-full-runtime-selftest-baseline0-20260904-131129.log).
- [Ports conformes](../results/listeners-full-runtime-selftest-baseline0-20260904-131129.json).
- [Dump revelant l'annonce LAN](../results/threads-full-runtime-selftest-baseline0-20260904-131002.txt).

Plusieurs messages de compatibilite/recettes deja presents dans le jeu de mods
sont visibles au demarrage. Ils ne constituent ni un audit complet du modpack,
ni une preuve qu'ils sont causes par QuarryGuard.

## Charge native comparee

Le premier loadtest n'avait pas d'oracle suffisant pour prouver le travail
utile ; son PASS n'est pas utilise pour conclure sur les performances. Le
protocole corrige recree deux fixtures identiques de 16 quarries normales en
terrain libre. Chacune recoit 200 lots d'echauffement et 200 lots mesures.
Le regime avec invalidation applique une mutation de claim eloigne avant
chaque lot. L'index n'est pas reconstruit manuellement.

Chaque machine doit miner et consommer de l'energie dans la fenetre mesuree.
Le delta de blocs interieurs extraits doit egaler le delta de drops stockes.
Les deux regimes doivent terminer avec les memes etats, cibles, quantites
et consommation d'energie. Les echantillons bruts et checkpoints sont sauves
en CSV/JSON. Les poses partielles sont journalisees avant mutation et le
nettoyage des blocs verifies est controle.

Les deux profils ont passe le test avec et sans les mixins : 800 blocs mines
par fenetre et 16 machines actives. Le garde effectue zero recherche spatiale
pendant les mesures stables et 3 200 apres les 200 mutations, soit une par
machine et par mutation. L'equivalence des checkpoints est aussi comparee
entre bras avec et sans garde, en dehors du serveur.

La paire finale utilise le binaire retenu ci-dessus et la baseline de SHA-256
`633C27B8FB9D872AD1F6D51DE6C3C038A75E5FEEE4128040591E91048FE04CCF`,
build commun `classes-20260904132613861`. Ordre : baseline puis garde, une
JVM par essai, modpack complet et plafond de 4 Gio. Les deux essais passent
a 15:28:39 et 15:29:15, puis sauvegardent et quittent avec le code 0, sans
arret force. Comparaison structuree des fichiers JSON : `before`, `after`
et `energySpent` sont identiques entre bras pour chacun des deux regimes.

Temps en millisecondes pour un lot de 16 tickers, 200 lots par ligne,
percentiles par rang superieur :

| Regime | Interceptions QuarryGuard | p50 | p95 | p99 |
|---|---|---:|---:|---:|
| Stable | Absentes | 0,2469 | 5,6036 | 8,2822 |
| Stable | Actives | 0,2810 | 5,2486 | 6,5802 |
| Invalidation avant chaque lot | Absentes | 0,1489 | 2,0890 | 2,4715 |
| Invalidation avant chaque lot | Actives | 0,1696 | 2,2621 | 3,5072 |

Ce petit echantillon montre du travail equivalent et le comportement attendu
du cache. Les variations de queue, parfois plus basses avec le garde, ne
prouvent ni une acceleration ni un surcout reproductible.

- [Mesures brutes sans interceptions](../results/load-baseline-8424-1788528519864.json).
- [Mesures brutes avec interceptions](../results/load-guarded-19364-1788528555913.json).
- [Lancement baseline et empreintes](../results/run-full-runtime-loadtest-baseline1-20260904-132805.json).
- [Lancement protege et empreintes](../results/run-full-runtime-loadtest-baseline0-20260904-132841.json).

Le pilote baseline conserve l'initialisation et les commandes du laboratoire,
mais aucun des mixins QuarryGuard n'est enregistre. C'est une comparaison des
interceptions du correctif, pas du cout complet d'installation ni du seul
index. Les deux JAR sont lies par un manifeste de build et verifies avant
lancement. Une sauvegarde de restart encore preparee interdit la baseline.

Reservations de mesure : terrain libre, quarries normales, un acteur hors
ligne, temps du jeu non avance par la boucle, 200 echantillons par regime.
Le cout inclut la recharge et le ticker natif, pas la mutation de claim,
les oracles ou la preparation. L'ordre stable puis invalidation reste fixe
et la JVM continue de s'echauffer. Ni le p99 de ces lots ni leur difference
ne sont un p99 de ticks serveur, une capacite a huit joueurs, ou une preuve
statistique d'un pourcentage de surcout reproductible.

## Corrections de securite

La revue de securite a identifie une emprise trop petite qui permettait a un
parcours natif de sortir de la zone declaree. Le garde exige maintenant un
interieur non vide (au moins trois blocs sur X et Z, calcul en `long`) et
verifie les destinations effectives en temps constant avant les effets vises.
Une zone invalide envoyee par paquet est refusee sans invalider la protection
de toutes les machines. Une destination de travail incoherente met seulement
sa machine en quarantaine. Les tests de paquets ci-dessus ne passent pas par
une vraie connexion reseau ; ils decodent et executent le handler natif.

La seconde correction autorise aussi les positions physiques des marqueurs,
independamment de leur zone selectionnee. Elle controle chaque chunk distinct
retire par le lien natif, avant pose, confirmation, lecture des drops et
retrait. Le type de lien inconnu est refuse. Les acces reflectifs aux records
QuarryPlus sont limites aux liens des versions epinglees et ne tournent pas
dans la boucle de minage. Cela ne cree pas une transaction universelle entre
la lecture des drops et leur retrait si un autre mod intervient au milieu.

## Etat des dossiers existants

Nouvelle comparaison SHA-256 : les 145 JAR serveur et les 168 JAR client
correspondent a l'inventaire initial, sans ajout ni suppression. Le depot
`D:\Programmation\Projets\amoxcine.github.io` ne contient aucun changement
signale par `git status --short` ; une restriction de lecture du fichier
d'exclusion global Git a toutefois produit un avertissement. Aucun push
n'a ete effectue. Les copies jetables et leurs journaux restent au laboratoire.

Pas de garantie de zero impact serveur. Les controles chauds mesures ne sont
pas des MSPT de serveur avec huit joueurs. Restent notamment les sessions
reelles, les modules facultatifs, les callbacks tiers en cours d'operation,
les voies de deplacement de machines et les tickets de chunks forces partages.
Le prototype n'est pas autorise a etre installe sur le serveur des joueurs.
