# Reprise native apres redemarrage : patch phase 3

Actualisation du parent : compile et execute avec succes dans le modpack
complet a 17:19:49 et 17:20:24. Positions finales X=6004/6068, Y=65, Z=5999,
emprises X=6000..6008 / 6064..6072, Z=6000..6008. Nouveau manifeste
`quarryguard-restart-phase3-test.properties`, pour conserver la preuve de
phase 2 intacte. Voir [validation-phase3.md](validation-phase3.md). Les
coordonnees et le nom de manifeste ci-dessous decrivent la proposition
initiale de l'agent, pas les positions finalement executees.

Statut : code relu statiquement, **non compile et non execute par cet intervenant**.
Compilation et essais dans deux JVM reserves au parent. Aucun build, serveur,
script, commande, autre classe, deploiement ou push modifie/lance ici.

## Contrat et preuve attendue

`RestartChecks.run(MinecraftServer, boolean)` conserve sa signature.
La preparation place nativement une quarry normale et une avancee, configure
une zone 9x9 avec 49 blocs d'or interieurs a Y=64 et le minimum de minage a 64.
L'avancee reprend le workConfig initial de PoweredChecks : demarrage immediat,
cadre actif, parcours non chunk-by-chunk. Ce seul chargement de configuration
se fait en WAITING, avant tout tick ; aucun etat de travail, owner ou iterateur
n'est fabrique. La normale et l'avancee doivent atteindre le minage natif,
extraire au moins un bloc et stocker exactement autant de drops, avec une
consommation d'energie positive et du minerai restant.

La preparation pose ensuite les claims FTB hostiles via leur API native,
verifie 25 ticks refuses par machine et journalise les checkpoints.
Le controle exige une autre incarnation de JVM (PID et instant de lancement),
les memes owner, area, cible serialisee, etat, energie, stockage, configuration,
modules et blocs. Les deux fixtures sont precontrolees avant le premier unclaim.
Il exige encore 25 ticks refuses par machine avec NBT complet, energie,
inventaire, terrain et volume externe inchanges. Puis chaque claim est retire
nativement : de nouveaux blocs doivent etre mines et leurs drops stockes en
quantite exacte, avec baisse d'energie. Aucune recharge, reconfiguration,
recreation ou injection de cible/etat ne se produit apres redemarrage.

Chaque boucle de minage est bornee a 2000 ticks natifs / 3 secondes.
Les comptes avant/apres, deltas de drops, energie depensee et nombre de ticks
figurent dans le journal et le resultat de commande. Aucun PASS d'execution
n'est revendique par ce rapport.

## Emprises et journal

- Normale : machine (12004,65,11999), area X=12000..12008, Z=12000..12008.
- Avancee : machine (12068,65,11999), area X=12064..12072, Z=12000..12008.
- Chaque fixture possede exactement 488 positions : machine, support, volume
  ferme de son area de Y=64 a 69. Les claims cibles sont (750,750) et (754,750).
- Le volume d'effets inclut la marge de collecte native de PoweredChecks ;
  ses blocs doivent etre de l'air sans block entity avant toute pose, pour
  **les deux machines**. Les chunks de pose par defaut sont aussi precontroles.
  La visibilite temporaire des entites reprend PoweredChecks et est restauree
  en finally. Aucune entite n'est creee ou supprimee par le test.
- Anciennes positions (800/848,64,800) et fixtures Legacy du parent vers
  X=4500..4820, Z=4500 jamais visitees/nettoyees par ce patch.

Le fichier monde `quarryguard-restart-test.properties` reste le point d'entree,
avec schema `native-mining-restart-3`. Il enregistre monde/dimension, liste
exacte des positions et air initial, UUID owner/equipe hostile, claims,
progression, NBT et blocs des checkpoints. Ecriture temporaire, force disque,
puis remplacement atomique ; pas de repli par troncature du manifeste.
La reservation est durable avant la premiere mutation de blocs. Les phases
de claim/unclaim et de nettoyage sont journalisees avant leur mutation.

**Ancien manifeste incompatible : refus avant mutation, meme stage=checked.**
Les anciens manifestes actuellement presents dans runtime/full-runtime sont
donc a examiner et archiver separement par le parent avant les nouveaux essais.
Le patch ne les migre pas, ne les ecrase pas et ne deduit pas de droits de
nettoyage de leurs anciennes coordonnees.

En succes, les deux checkpoints sont reverifies avant nettoyage des seules
positions connues et journalisees ; controle d'air/absence de block entity
ensuite. Aucun unregisterClaim global, effacement d'entites ou nettoyage du
volume externe. Les equipes hors ligne restent dans le laboratoire.
En echec apres reservation, stage=prepared reste actif avec phase=failed et
le point d'echec ; aucun nettoyage automatique d'un etat incertain. Le journal
permet l'inspection et la recuperation manuelle bornee, pas une reprise
automatique d'un essai partiel ni un PASS retroactif. Ne pas relancer prepare
sur ces fixtures sans examen. Un crash peut laisser le dernier journal durable,
sans la mention failed ; une phase autre que ready/complete reste refusee.

## Hypotheses et limites

Versions et comportements natifs repris de validation-phase2.md, LabSupport,
PoweredChecks, LoadChecks, GuardHooks et des desassemblages QuarryPlus locaux.
Lecture reflective du stockage uniquement ; la visibilite d'entites est celle
du banc console existant. Pas de simulation de clients, d'objets ramasses,
de quarantaine legacy ou de crash disque. Les nouvelles positions doivent
reellement etre vierges ; aucun terrain sale n'est remis a zero pour obtenir
un PASS. Les ticks sont natifs mais synchrones sur le thread serveur, sans
avancer le temps du monde comme une session joueur. Le parent doit sauvegarder
et arreter proprement entre prepare/check, comme dans le protocole precedent.
Les comparaisons de cible/stockage rechargees restent a confirmer dans les
JVM centralisees ; un ecart sera un echec, pas une normalisation de l'etat.

Fichiers edites exclusivement via apply_patch :
`integration/src/main/java/fr/ascendant/quarryguard/RestartChecks.java` et
`research/reprise-phase3.md` sous quarryguard-lab.
