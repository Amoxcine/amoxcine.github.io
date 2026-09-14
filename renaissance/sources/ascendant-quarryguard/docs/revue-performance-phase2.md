# Revue ciblee du protocole de performance QuarryGuard - phase 2

**Actualisation du parent : revue historique du premier protocole.** Le banc
a depuis ete reecrit avec preuve du travail utile, fixtures equivalentes,
checkpoints, manifeste des deux binaires, controle de sortie et nettoyage
borne renforce. La comparaison finale passe dans le modpack complet ; voir
[resultats et limites](validation-phase2.md). Les mesures restent synthetiques,
sans huit joueurs, et ne restaurent pas tout le monde jetable (equipes/chunks).
Le verdict ci-dessous ne decrit donc pas le banc final.

Date : 2026-09-04. Revue statique du workspace demande. Aucun serveur, test, build, preparation, deploiement ou push lance. Aucun code modifie. Le test powered du parent reste independant et n'est ni interrompu ni certifie ici. Seul ce document est ajoute ; les recommandations ci-dessous ne sont pas appliquees.

## Verdict

Le banc appelle bien le ticker natif, mais son PASS ne prouve pas un minage actif pendant les fenetres mesurees. La baseline est, d'apres sa construction, le meme pilote sans enregistrement des mixins QuarryGuard ; elle n'est ni un serveur sans QuarryGuard, ni une comparaison de travail equivalent verifiee. Le nettoyage est borne a des positions de laboratoire mais incomplet sur echec et sur l'etat persistant. Aucun resultat de ce protocole ne valide une capacite de huit joueurs.

Priorites : corriger les oracles de progression et l'appariement des charges avant d'interpreter les timings ; rendre les fixtures recuperables avant de multiplier les essais. P1 signifie ici que l'inference de performance peut etre invalidee, pas qu'un incident de production a ete observe.

## Constats prioritaires

### 1. [P1] PASS possible sans travail utile pendant les mesures

Preuve : [LoadChecks, L55-82](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/LoadChecks.java:55). Apres 200 lots d'echauffement puis deux series de 200, l'unique oracle de travail est `state != FINISHED`. `remainingStone` est seulement affiche, sans valeur initiale ni comparaison par phase/machine. Le comptage inclut les supports et les bords du volume, pas uniquement les blocs interieurs extractibles. Un ticker annule par le garde, une machine desactivee, bloquee ou encore en preparation peut donc satisfaire cette assertion. Meme un champ `state` absent donne une chaine vide et passe ce test.

L'appel est reel : [LabSupport.tick, L95-101](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/LabSupport.java:95) obtient `EntityBlock.getTicker` puis appelle `ticker.tick`. [LoadChecks.batch, L91-95](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/LoadChecks.java:91) ajoute effectivement de l'energie. Cela ne suffit pas : le [ticker QuarryPlus archive, L179-238](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/refonte-ascendant-2026-09-04/quarry/evidence/quarry/com.yogpc.qp.machine.quarry.QuarryEntity.txt:179) comporte des retours anticipes et plusieurs phases de travail distinctes.

Correctif recommande : hors chronometrage, enregistrer pour chaque machine les blocs extractibles, stockage, etat/cible et energie avant/apres chaque fenetre. Exiger une progression utile positive dans chaque fenetre, des drops coherents avec les blocs retires et une energie effectivement consommee si `noEnergy` est desactive. Verifier l'identite de la machine, l'Area/minY, l'etat connu et, cote garde, zero refus inattendu. Ne pas imposer une extraction a chaque tick : les deplacements de tete sont du travail natif legitime. Reprendre les types d'oracles deja visibles dans [PoweredChecks, L242-268](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/PoweredChecks.java:242), sans presumer du resultat du test parent.

### 2. [P1] Stable et invalide ne comparent pas la meme tranche de travail

Preuve : [LoadChecks, L55-74](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/LoadChecks.java:55). Les memes machines consomment la fixture sans remise a l'etat initial : echauffement aux appels 1-200, stable aux appels 201-400, invalidation aux appels 401-600. Cadre, deplacement, minage et stockage peuvent avoir des couts differents. Une difference de percentiles entre les deux series ne permet donc pas d'isoler le cout de reconstruction du cache. Le nombre fixe d'appels d'echauffement ne demontre pas non plus la stabilisation de la compilation JVM.

Correctif recommande : recreer des fixtures equivalentes et repasser par la meme preparation native avant chaque regime ; comparer les checkpoints de progression. Repeter des paires garde/baseline dans plusieurs JVM avec ordre alterne. Garder les echantillons bruts, les phases et les compteurs de travail, pas seulement trois percentiles. Si les trajectoires divergent, publier ce resultat fonctionnel et ne pas calculer un surcout a travail egal.

### 3. [P2] La baseline retire bien les mixins declares, mais l'equivalence n'est pas garantie

Preuve : [build.ps1, L33-48](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/build.ps1:33) produit le JAR garde, retire exactement la section `[[mixins]]` du manifeste puis produit le pilote avec les memes classes. Ce n'est pas un simple bypass laissant les interceptions actives. Le fichier JSON et les classes de mixin restants ne constituent pas, a eux seuls, leur enregistrement. Le pilote conserve cependant les evenements et `GuardHooks.start`, donc la construction initiale de l'index : [QuarryGuard, L16-27](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/QuarryGuard.java:16).

Le traitement change aussi l'identite du faux joueur, les interceptions d'effets et l'initialisation de la machine, pas seulement une recherche d'index : [MiningNeoForgeMixin, L19-27](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/mixin/quarry/MiningNeoForgeMixin.java:19), [QuarryEntityMixin, L96-118](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/mixin/quarry/QuarryEntityMixin.java:96). C'est acceptable pour mesurer l'ensemble des mixins, mais uniquement si les effets natifs obtenus sont compares. La presence/absence d'owner verifiee par [LabSupport, L90-91](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/LabSupport.java:90) n'est pas cet oracle.

Enfin [run-lab.ps1, L19-21 et L53](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/run-lab.ps1:19) selectionne des noms fixes sans verifier que les deux JAR proviennent du meme build reussi. Un build interrompu apres le JAR garde peut laisser un ancien pilote. Le meme monde est reutilise, sans snapshot initial verifie ni refus d'une fixture restart encore preparee ; restaurer le JAR garde a la fin ne restaure pas le monde modifie sans protection.

Correctif recommande : identifier et verifier une paire d'artefacts par build et empreintes, enregistrer les versions/configurations et comparer Area, etats et quantite de travail entre bras. Pour les futurs essais autorises, partir d'un etat jetable identique ; refuser la baseline en presence de fixtures persistantes en attente. Garder le libelle precis « pilote commun sans mixins QuarryGuard ». Ne pas presenter ce delta comme le cout pur du core ou le cout total d'installation du mod.

### 4. [P2] La charge ne couvre pas les permissions vivantes et n'atteste pas l'invalidation

Preuve : [LoadChecks, L28-34 et L63-73](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/LoadChecks.java:28). Un seul owner pour 16 quarries normales, aucun claim cree dans leurs emprises, un seul claim lointain alterne. Dans un monde propre, l'ensemble des equipes couvertes reste vide : la boucle de verification des droits [GuardHooks, L330-347](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/GuardHooks.java:330) n'est pas exercee. Sur un monde reutilise, l'absence d'autres claims dans la charge n'est pas prouvee non plus.

La mutation distante doit invalider globalement les couvertures via la revision : [GuardHooks, L320-329](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/GuardHooks.java:320). Pourtant le banc ne verifie aucun delta de revision/reconstruction/cache par serie ; une regression d'invalidation peut produire un PASS et des timings artificiellement favorables. Les compteurs finaux sont cumulatifs, y compris pose et echauffement. Le temps direct de mutation FTB est explicitement exclu du chronometre.

Correctif recommande : ajouter des assertions de revision et de reconstructions apres mutation, ainsi que de cache stable apres echauffement. Separer les cas wilderness, claims propres/allies, refus et forte densite de claims/equipes ; declarer exactement le contenu du monde initial. Rapporter separement temps de mutation et premier lot apres mutation si l'objectif inclut leur impact total.

### 5. [P2] Le nettoyage perd les poses partielles et laisse de l'etat persistant

Preuve : [LabSupport.place, L75-92](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/LabSupport.java:75) pose le support avant plusieurs operations/assertions susceptibles d'echouer. [LoadChecks, L41-44](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/LoadChecks.java:41) ne journalise machine et positions qu'apres le retour du helper. Si la verification de l'owner echoue apres une pose effective, le `finally` ignore cette machine et son support.

Le [finally du loadtest, L83-87](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/LoadChecks.java:83) remet les positions connues a l'air, sans inventaire des entites/effets voisins ni assertion finale de restauration. Les equipes aleatoires creees par [LabSupport.actor, L56-59](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/LabSupport.java:56) ne sont pas supprimees. Le monde reutilise accumule donc au moins des donnees d'equipes et des chunks generes, meme en cas de succes. `GuardHooks.afterPlace()` nettoie seulement le contexte de placement, pas toutes les fixtures.

Correctif recommande : valider et enregistrer les positions avant la premiere mutation, rendre la pose localement reversible sur exception et verifier la restauration. Inventorier les entites et effets dans un volume borne avant travail ; ne retirer que les creations attribuables au test. Journaliser l'equipe/claim effectivement possedes. Pour l'etat global, preferer une fixture jetable par essai a une purge generale ; ne pas effacer arbitrairement les tickets ou donnees d'autres tests.

### 6. [P2] Restart : reprise utile non testee et echec partiel non recuperable

Preuve : [RestartChecks, L43-63](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/RestartChecks.java:43). La preparation execute un seul tick alimente avant blocage, sans prouver une cible/minage actif. Si la deuxieme fixture echoue, la premiere reste modifiee sans manifeste ecrit : celui-ci n'est sauvegarde qu'en L88. Aucun rollback ou journal intermediaire ne couvre cette branche.

Lors du controle, [L67-86](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/RestartChecks.java:67) compare owner, Area, energie et etat entre JVM ; l'egalite de tout le NBT concerne seulement les 25 ticks bloques dans la seconde JVM. Apres unclaim, `mayWork=true` est suivi de la suppression immediate, sans tick utile. Si la premiere machine est supprimee puis la seconde echoue, le manifeste reste `prepared` et une relance echoue sur la premiere machine manquante.

Correctif recommande : preparer un checkpoint natif actif borne, comparer aussi cible/stockage pertinents entre JVM, puis attester extraction et conservation des drops apres unclaim avant suppression. Journaliser chaque fixture et chaque etape de nettoyage de facon recuperable, sans effacer les preuves en cas d'echec. Les [PASS historiques prepare](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/results/runtime-runtime-restart-prepare-20260904-123447.log:70) et [check](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/results/runtime-runtime-restart-check-20260904-123509.log:69) restent des preuves de persistance/suspension pour leur version, pas de reprise du minage. Un powered PASS sans redemarrage ne comblerait pas a lui seul cette lacune.

### 7. [P2] Le lanceur peut accepter un processus termine en erreur apres PASS

Preuve : [run-lab.ps1, L107-110](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/run-lab.ps1:107). `ExitCode` est affiche mais ne participe pas a l'echec final. Si les assertions PASS sont imprimees puis la sauvegarde/l'arret echoue avec un code non nul, `sent=true`, `sawResult=true` et `forced=false` suffisent a accepter le run. C'est particulierement genant pour une preparation de restart dont la sauvegarde fait partie du protocole.

Correctif recommande : exiger `ExitCode == 0` et une confirmation de sauvegarde/arret propre pour le protocole inter-JVM. Conserver les logs en cas d'erreur. Ce controle peut etre ajoute sans changer le fonctionnement du garde.

## Ce que mesure le chronometre

- Chaque echantillon est la duree d'un lot sequentiel de 16 appels : recharge energetique, recherche du ticker par le helper, ticker natif et interceptions eventuelles. Ce n'est pas le temps du seul garde. Le libelle actuel reconnaissant la recharge et excluant setup/nettoyage/mutation est utile et doit etre conserve.
- Les appels se font dans une commande synchrone du thread serveur, pas dans 600 tours complets du serveur. Ils ne font pas avancer eux-memes `gameTime`, les autres machines, les entites ou les files d'effets differees. Le [fournisseur de temps energetique natif, L346-354](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/refonte-ascendant-2026-09-04/quarry/evidence/quarry/com.yogpc.qp.machine.PowerEntity.txt:346) utilise justement `getGameTime`. Cela ne prouve pas une inactivite du ticker, mais interdit d'assimiler cette boucle a une cadence reelle de jeu.
- Le garde chronometre lui-meme chaque controle avec deux `nanoTime` et des compteurs : [GuardHooks, L312-335](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/GuardHooks.java:312). Le delta inclut cette instrumentation du prototype ; `meanCheckUs` n'est ni un percentile ni une mesure du ticker complet.
- Les indices 99/189/197 de [LoadChecks.percentiles, L99-101](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/LoadChecks.java:99) sont corrects pour le nearest-rank sur 200 valeurs. Pas de correctif d'index a recommander. En revanche p99 repose ici sur le 198e echantillon trie, dans une serie courte et correlee ; sa precision n'est pas etablie.

## Core et huit joueurs

Le [benchmark core](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/core/src/test/java/fr/ascendant/quarryguard/core/Benchmark.java:17) annonce correctement un harnais exploratoire hors Minecraft/JMH. Ses validations croisees, graines fixes, rotation d'ordre, consommation des resultats et echantillons bruts sont de bons garde-fous. Les [tests core](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/core/src/test/java/fr/ascendant/quarryguard/core/CoreTest.java:23) couvrent geometrie, mutations, revisions et oracle independant. Aucune erreur concrete du core n'est identifiee dans cette revue ciblee ; les tests n'ont pas ete reexecutes.

Attention a la transposition : [ClaimIndex.queryDenied, L80-100](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/core/src/main/java/fr/ascendant/quarryguard/core/ClaimIndex.java:80) peut s'arreter au premier refus et appelle le predicat par claim. L'integration reconstruit au contraire toutes les equipes couvertes avec un predicat toujours vrai, puis verifie les droits par equipe ; en cache chaud, elle evite cette recherche. Un scenario core `dense-denied-first` ne mesure donc pas directement le chemin de refus integre. Les durees `build_ns` sont des constructions ponctuelles, pas une distribution du cout des mutations FTB/reconstructions.

Le controle [LabSupport.requireLab, L43-52](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/LabSupport.java:43) exige zero joueur connecte. Les 16 machines d'un seul acteur fictif ne representent pas huit joueurs. Ni les chunks explores, reseau, entites, automatisations, sauvegardes et pauses memoire, ni les quarries avancees et leurs configurations ne sont representes par ce loadtest. Le profil full-runtime ajoute les mods, pas cette activite.

Conclusion autorisee apres correction : cout local de lots natifs comparables, pour les phases/configurations et claims mesures. Conclusion interdite : « supporte huit joueurs », « gagne X MSPT » ou conversion directe d'un p99 de lot en p99 serveur. Une validation ulterieure a huit joueurs demanderait une charge representative sur des ticks serveur complets, des mesures MSPT et pauses, des debits de travail utiles et plusieurs paires de runs comparables. Aucun de ces essais n'est lance ou programme ici.

## Suite minimale recommandee

1. Ajouter les oracles de progression par phase et les assertions de cache/revision, en dehors des zones chronometrees.
2. Comparer des fixtures et artefacts apparies ; conserver timings bruts et quantites de travail, separer les regimes.
3. Fermer les trous de nettoyage et de reprise du protocole restart ; verifier le code de sortie du processus.
4. Garder les conclusions limitees au laboratoire. Integrer ensuite le resultat powered du parent comme preuve fonctionnelle distincte, sans le transformer en benchmark ni en preuve de reprise inter-JVM.

Fichier ajoute par cette revue : ce document uniquement. Aucun petit correctif recommande n'a ete applique.
