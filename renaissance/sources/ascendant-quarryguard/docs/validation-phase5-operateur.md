# QuarryGuard : outil operateur et candidat serveur, phase 5

Date : 4 septembre 2026, heures Europe/Paris.

**Avancee : outil d'attribution explicite implemente et recette fonctionnelle
finale reussie dans le laboratoire. Candidat sans banc de test prepare et
demarrage serveur verifie.**
Aucun deploiement, push GitHub, changement de client ou attribution d'une
machine appartenant aux joueurs.

## Perimetre

Cette phase traite le risque de bloquer les quarries anciennes sans moyen
propre de les rattacher a un proprietaire. Elle ne devine aucune identite.
L'operateur inspecte une position, propose un UUID, confirme l'apercu et
decide separement de la reprise. Le [guide operateur](outil-operateur-phase5.md)
decrit les commandes et leurs limites.

La pause d'attribution est persistante. Elle bloque le travail et les
reconfigurations de zone/configuration controlees. Elle ne constitue pas un
verrou universel des interfaces de stockage ou des outils de deplacement.
Les commandes changent uniquement les metadonnees de propriete et de pause,
pas le contenu, l'energie, l'emprise ou le checkpoint natif.

Chaque mutation impose d'abord un journal d'intention SNBT distinct avec
instantanes avant et prevu. Une annulation ne recharge pas cet instantane :
elle retire seulement l'attribution encore en pause. Apres reprise, aucune
annulation d'attribution ni restauration du monde n'est proposee par l'outil.

## Artefacts identifies

```text
Build : classes-20260904165826746

Laboratoire : ascendant-quarryguard-0.1.0-lab.jar
SHA-256 ADF080D2590A97A42697EF99B1F9BCBB39AA02A136A62F15FEB18842C2B24F7E

Candidat : ascendant-quarryguard-0.1.0-rc1.jar
SHA-256 73B3345D6EDA77908FCB8513C59E22614245752EB21E0918D207DB96D6941E27

Baseline sans protection : ascendant-lab-driver-NO-PROTECTION.jar
SHA-256 1F3C990999BF49BDA195E3F60180E03ECEBC2115707D80093D361A36882E3745
```

Le candidat est dans `integration/build/candidate/`. La baseline reste
strictement reservee au banc, jamais a installer sur un monde de joueurs.

Le packaging retire 32 classes du laboratoire, garde 28 classes runtime
identiques octet par octet et modifie seulement version, nom et description
du descripteur du mod. Les mixins obligatoires restent presents. Le
[manifeste du packaging](../integration/build/candidate/package-manifest.json)
relie les deux SHA-256 et conserve les empreintes des classes retenues.
Le chargement reflectif des commandes lab n'est demande que par un drapeau
JVM de test. Ce drapeau avec le candidat sans banc provoque une erreur
explicite ; il ne fait pas reapparaitre les tests dans le JAR.

### Demarrage du candidat sans banc

Le JAR RC1 identifie ci-dessus a ete charge dans le meme environnement
serveur complet, avec le drapeau laboratoire desactive. A 19:07:44,
`quarryguard status` indique `ready=true`. L'aide expose seulement status,
inspect, adopt, confirm, resume et cancel-adoption, sans commande de test.
Le serveur sauvegarde puis s'arrete normalement, sans terminaison forcee.

Preuves : [journal du candidat](../results/runtime-candidate-full-runtime-selftest-baseline0-20260904-170701.log)
et [identite du lancement](../results/run-candidate-full-runtime-selftest-baseline0-20260904-170701.json).
Le nom `selftest` dans ces fichiers est le libelle par defaut du lanceur :
ce lancement est un controle de demarrage, pas l'execution d'une suite de
tests sur le candidat. La recette fonctionnelle ci-dessous utilise le JAR
laboratoire dont les 28 classes runtime conservees sont identiques.
Le champ `serverExecuted=false` du manifeste de packaging decrit uniquement
l'etape de packaging, anterieure a ce demarrage.

## Recette fonctionnelle

Copie complete des 145 JAR serveur plus le prototype, monde plat jetable,
4 Gio maximum, une JVM a la fois, aucune vraie connexion de joueur.

| Test final | Resultat | Preuve |
|---|---|---|
| Attribution, refus, configuration verrouillee | PASS 18:59:35 | [Journal](../results/runtime-full-runtime-adoption-baseline0-20260904-165853.log) |
| Preparation via commandes operateur | PASS 19:00:17 | [Journal](../results/runtime-full-runtime-adoption-prepare-baseline0-20260904-165937.log) |
| Attribution apres nouvelle JVM | PASS 19:01:02 | [Journal](../results/runtime-full-runtime-adoption-check-baseline0-20260904-170019.log) |
| Regression complete, dont onze cas de cadres | PASS 19:01:53 | [Journal](../results/runtime-full-runtime-regression-baseline0-20260904-170104.log) |
| Preparation de reprise utile | PASS 19:02:38 | [Journal](../results/runtime-full-runtime-restart-prepare-baseline0-20260904-170155.log) |
| Reprise utile, nouvelle JVM | PASS 19:03:22 | [Journal](../results/runtime-full-runtime-restart-check-baseline0-20260904-170240.log) |
| Preparation anciennes machines/quarantaine | PASS 19:04:06 | [Journal](../results/runtime-full-runtime-legacy-prepare-baseline0-20260904-170324.log) |
| Anciennes machines, nouvelle JVM | PASS 19:04:49 | [Journal](../results/runtime-full-runtime-legacy-check-baseline0-20260904-170408.log) |

Ces huit lancements portent sur ADF, avec sauvegarde et arret code 0 sans
terminaison forcee. Binaire, candidat et sources sont archives dans
`integration/build/phase5-adf0/`. Les trois manifestes de fixtures persistantes
sont termines en `stage=checked`.

### Refus et conservation

Les deux types de quarry sont charges d'objets, d'eau et d'energie avant les
tests. Sont verifies : commandes sans droits OP, autre operateur, jeton deja
consomme, autre dimension, meme NBT dans un nouvel objet de machine,
modification du NBT apres apercu, proprietaire apparu entre-temps,
quarantaine, emprise invalide, protection indisponible et claim adverse.

Les UUID choisis explicitement peuvent etre hors ligne et inconnus du cache
FTB. Aucun joueur ni aucune equipe ne sont crees a partir de cet UUID par
l'outil. Les acteurs simules du banc restent dans le monde jetable.

Les attributions confirmees resistent a 25 ticks natifs bloques, a une
reconstruction de block entity, a une reinitialisation du garde, puis a une
sauvegarde disque suivie d'une autre JVM. La comparaison inter-JVM porte
sur le NBT complet. La preparation et la phase de controle passent par le
vrai dispatcher Brigadier pour inspect/adopt/confirm/cancel-adoption/resume,
avec un refus de confirmation de niveau 0. Elles ne sont pas des connexions
reseau authentifiees.

Conservation verifiee : 7 diamants, 3 seaux d'eau, energie et donnees natives
non gerees par QuarryGuard. Annulation/reprise ne changent pas l'ensemble
des chunks forces globaux observe. Ce dernier controle ne couvre pas tous
les tickets internes de tous les mods.

### Correction issue de la revue

La premiere version 1884, archivee dans `integration/build/phase5-initial-1884/`,
passait ses tests d'attribution et persistance mais n'interdisait pas la
reconfiguration pendant la pause. Une revue statique independante a releve
ce manque dans `mayConfigure` et `maySetArea`.

Le binaire ADF et son candidat derive le corrigent. La recette ajoute les changements directs de
zone sur les deux variantes et deux paquets `AdvActionSyncMessage` via
codec/handler natifs, avec et sans synchronisation de zone. Leur contenu
reste sans effet tant que l'attribution est en pause. Les autorisations de
configuration sont retablies apres une reprise explicite. Le paquet provient
d'un acteur serveur simule portant l'UUID adopte, pas d'une vraie session.

## Comparaison de charge bornee

Deux processus successifs, memes 16 quarries normales en terrain libre,
200 lots d'echauffement puis 200 mesures par phase. Dans chaque phase
mesuree : 800 blocs mines, les 16 machines avancent. Le parent compare les
fichiers bruts : checkpoints avant/apres et energie consommee identiques
entre baseline et garde, pas seulement entre phases d'un meme processus.

| Temps d'un lot de 16 tickers, ms | p50 | p95 | p99 |
|---|---:|---:|---:|
| Baseline, claims stables | 0,2451 | 5,6480 | 6,7545 |
| Garde ADF, claims stables | 0,3567 | 6,1572 | 8,8165 |
| Baseline, phase avec mutations | 0,1978 | 3,6994 | 5,4952 |
| Garde ADF, phase avec mutations | 0,2116 | 3,6332 | 5,3729 |

La phase stable ne recalcule pas la geometrie. La seconde effectue
200 mutations et 3 200 recherches avec le garde. Les mesures incluent le
remplissage d'energie, mais excluent les mutations elles-memes, la preparation,
les controles de resultat et le nettoyage. Ce ne sont pas les MSPT du
serveur. Une seule paire de processus ne permet pas d'attribuer tout l'ecart
au correctif, de promettre une absence de ralentissement ou de certifier
huit joueurs. Le cout de suppression d'une tres grande chaine de cadres,
les allocations/GC et la generation restent hors de cette mesure.

- Baseline PASS 19:06:10 : [journal](../results/runtime-full-runtime-loadtest-baseline1-20260904-170520.log),
  [donnees](../results/load-baseline-30484-1788541570454.json).
- Garde PASS 19:06:59 : [journal](../results/runtime-full-runtime-loadtest-baseline0-20260904-170612.log),
  [donnees](../results/load-guarded-14220-1788541619307.json).

Les commandes OP ne s'executent pas periodiquement : apercu, copies NBT et
ecriture forcee de l'audit sont des operations ponctuelles. Leur latence sur
le stockage du serveur distant n'est pas mesuree ici.

## Limites et prochaine etape

- Toujours aucun test avec un vrai client sans QuarryGuard, ni avec huit
  joueurs. Demarrage serveur et bytecode identique ne prouvent pas le reseau.
- Les modules facultatifs, deplacements de machines et chargements forces
  partages du modpack restent a tester dans leurs usages reels.
- Les commandes n'inventorient pas toutes les anciennes quarries de tous
  les chunks decharges ; les positions et proprietaires reels restent a etablir.
- Pas de panne de disque/coupure de courant injectee, ni attente reelle de
  dix minutes pour l'expiration d'un jeton. Le journal est une intention,
  pas une sauvegarde transactionnelle de l'ensemble du monde.
- Une copie restauree et verifiee du monde des joueurs est necessaire avant
  toute migration reelle. Aucun snapshot du serveur distant n'est fabrique
  ou presente comme disponible par cette phase.
- Le candidat reste sans autorisation de production. L'outil d'adoption ne
  leve pas les autres reserves et ne justifie aucune mise a jour annexe.

Verification des installations : 145 JAR serveur et 168 JAR client inchanges
par rapport a l'inventaire initial. Depot local Git sans modification
signalee, avec l'avertissement habituel de lecture de l'exclusion globale.
Cette comparaison ne constitue pas un inventaire de tous les fichiers.

## Cloture du laboratoire

Dernier controle du noyau : compilation Java 21 avec avertissements traites
en erreurs reussie, puis 915 730 assertions reussies. Ce sont des tests Java
purs, pas une mesure de serveur : [compilation](../core/results/compile.txt),
[resultats](../core/results/tests.txt).

Apres les essais, aucun processus Java du laboratoire ni ecoute TCP 25585
ou point de terminaison UDP 25586 ne subsiste. Les serveurs de test sont
arretes. Aucun serveur de joueurs n'a ete lance, arrete ou modifie.
