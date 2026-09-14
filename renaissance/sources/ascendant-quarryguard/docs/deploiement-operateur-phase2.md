# QuarryGuard : protocole operateur de deploiement et de retrait, phase 2

**Actualisation du parent :** les preuves citees ci-dessous sont historiques.
Regression complete, charge comparee bornee et vrai redemarrage ont depuis
passe sur le binaire final dans la copie des 145 mods serveur. Voir
[validation-phase2.md](validation-phase2.md) pour les empreintes et limites.
Cela ne leve pas les reserves de migration et de recette multijoueur ; le
present protocole ne vaut toujours pas autorisation d'installation.

Date de lecture : 4 septembre 2026. Perimetre : documentation uniquement.

**Decision actuelle : aucune installation autorisee. Prototype experimental, non valide pour la production.** Ce document prepare une intervention future, soumise a une autorisation distincte et aux validations ci-dessous. Il ne donne pas l'ordre de lancer cette intervention maintenant.

Pour cette mission : lecture des references et journaux locaux, puis creation de ce seul document. Aucun Java, build, test ou serveur lance ; aucun JAR installe ; aucune modification de production, client, Packwiz ou GitHub ; aucun push. Le code et les tests continuent d'evoluer dans la tache parente : une preuve ancienne ne qualifie pas automatiquement leur nouvel etat ni un JAR portant le meme nom.

## 1. Ce qui est prouve, constate et encore a tester

### Preuves d'execution deja presentes, non rejouees ici

| Reference locale | Resultat acquis dans ce perimetre | Limite a conserver |
|---|---|---|
| [Suite native, PASS a 14:18:50][preuve-native] | Pose normale et avancee, refus avant consommation sur claim hostile interieur, droits FTB relus, mutations/transferts, persistance NBT dans le meme processus, refus du faux joueur. | Joueurs simules sans session reseau. Ni session multijoueur reelle, ni minage continu, ni mesure globale du modpack. |
| [Preparation restart, PASS a 14:34:59][preuve-prepare] et [controle restart, PASS a 14:35:20][preuve-restart] | Nouvelle JVM ; deux claims reconstruits ; proprietaire, emprise, etat et energie retrouves ; 25 ticks natifs bloques par machine avec NBT inchange pendant ces ticks ; autorisation retrouvee apres unclaim. | Deux fixtures, dans des chunks distincts. Pas de tick utile apres unclaim avant retrait des fixtures, pas de validation des forcages partages. Egalite de tout le NBT entre les JVM non verifiee. |
| [Essai alimente minimal, PASS a 14:55:11][preuve-powered-pass] | Cadre, minage, stockage et collecte natifs avec pause/reprise pour les deux variantes ; 228 ticks au total. | Acteurs hors ligne, pack minimal et meme processus. Ne prouve ni la reprise utile apres redemarrage, ni les sessions reelles, ni tous les modules du plein pack. |
| [Selftest minimal, PASS a 14:59:06][preuve-selftest-recent] et [assertions de LabChecks][labchecks] | Nouveau PASS minimal ; deux passages de cible invalide visibles au journal. Le helper `loadOwner` est appele directement, puis la sauvegarde native verifie owner conserve, motif present et travail refuse pour les deux variantes. | Ce controle du helper n'est pas un chargement natif complet du NBT invalide, ni une persistance de quarantaine sur disque entre JVM. Le nouveau selftest plein pack reste a executer. |
| [Demarrage plein pack a 14:56:18][preuve-full1] et [demarrage plein pack a 14:58:07][preuve-full2] | Demarrage atteint, puis QuarryGuard indique sa disponibilite ; serveur ensuite arrete, sans resultat de suite `QG-LAB-RESULT` dans ces journaux. | Selon le retour operateur, le runner a stoppe avant les tests pour un listener inattendu ; diagnostic en cours. Demarrage reussi ne signifie pas tests plein pack reussis ni confinement reseau valide. |

Les [essais alimentes de 14:44:40][preuve-powered1] et [14:53:16][preuve-powered2] restent des echecs historiques conserves, mais ne sont plus le dernier etat de la suite : le PASS de 14:55:11 leur succede. Le retour operateur attribue la correction de visibilite des `ItemEntity` a la promotion de quatre chunks de fixture en `TRACKED`, pas a un changement d'autorite. Le [code du banc alimente][poweredchecks] conserve puis restaure ces visibilites. Ne pas transposer cette manipulation de fixture aux permissions ou aux tickets du serveur joueur.

Les mesures du [README][readme] concernent le noyau et le controle d'autorisation. Elles ne prouvent pas les temps de tick d'un serveur complet avec huit joueurs. La [preparation full-runtime][full-runtime] decrit une copie technique et un monde jetable neuf, pas une recette multijoueur ni une sauvegarde du monde existant.

### Constats statiques a la lecture actuelle

- [GuardHooks][hooks] refuse le travail si le proprietaire manque, si une quarantaine est presente ou si la protection n'est pas disponible. Les droits ne sont pas caches ; seule la geometrie l'est. Un claim hostile PUBLIC reste refuse par la politique stricte du prototype. Le statut OP seul n'est pas un bypass FTB.
- Le rapport [reversibilite et reprise][reversibilite] est **perime sur la perte d'owner : ce n'est pas un bug actuel a reprendre comme tel**. `loadOwner` restaure l'UUID puis conserve un motif distinct `ascendant_quarryguard_quarantine`; `saveOwner` ecrit les deux informations. Le controle helper et sauvegarde native est maintenant etaye par le selftest minimal ci-dessus. Restent a valider le chargement natif complet du cas invalide, la persistance de quarantaine entre JVM et le plein pack ; ce sont des limites de preuve, pas l'affirmation d'une perte d'owner persistante.
- Il n'existe pas de verrou global de migration, ni de commande d'adoption ou de levee de quarantaine dans les [commandes lues][commandes]. Un serveur sans joueurs peut faire travailler ses machines ; couper l'apport d'energie ne neutralise pas leur reserve interne.
- La suspension ordinaire n'efface pas le checkpoint natif et ne purge pas les chunks deja forces. Une autorisation retrouvee peut permettre la reprise au controle suivant. Ce n'est ni un arret manuel persistant, ni une garantie d'absence de tout effet dans les callbacks d'autres mods.

### A tester avant toute decision favorable

Le JAR exact retenu doit passer la recette de la section 6 : vraies connexions sans QuarryGuard cote client, revalidation du travail utile dans le plein pack et reprise utile apres redemarrage, conservation blocs/objets/energie, adoption en place, persistance disque de quarantaine, coexistence des forcages et restauration complete. Le demarrage seul, `ready=true` ou un PASS minimal ne suffisent pas. Une nouvelle preuve doit preciser son artefact, sa date et son perimetre ; les essais non effectues restent marques `NON TESTE`.

## 2. Lot serveur uniquement et versions epinglees

**Le futur correctif est un JAR Java cote serveur uniquement.** Si une livraison est autorisee, ajouter exclusivement le JAR QuarryGuard valide au dossier `mods` du serveur cible arrete. Ne pas l'ajouter au client Prism, ne pas le diffuser via Packwiz et ne pas copier le repertoire de laboratoire, ses mondes, configurations ou lanceurs sur le serveur des joueurs.

Le nom observe est `ascendant-quarryguard-0.1.0-lab.jar`, version declaree `0.1.0-lab`. Ce nom n'identifie pas un binaire immuable. L'empreinte du README est une reference historique, pas l'approbation d'un candidat ulterieur. Avant recette, le responsable doit figer le JAR, calculer son SHA-256, le relier aux sources et aux journaux d'essais, puis verifier cette meme empreinte lors de l'intervention. Aucun candidat n'est certifie par ce document.

| Composant | Version de reference a maintenir pour cette recette |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.248 |
| QuarryPlus / Additional Enchanted Miner | 21.1.162 |
| FTB Chunks | 2101.1.21 |
| FTB Teams | 2101.1.10 |
| FTB Library | 2101.1.35 |
| Architectury | 13.0.11 |
| ScalableCatsForce | 3.7.1-build-11 |
| Java | Microsoft OpenJDK 21.0.7, 64 bits |

Le [descripteur du mod][mods] impose exactement NeoForge, QuarryPlus, FTB Chunks et FTB Teams ; le tableau complete le gel operateur avec l'environnement du README. Inventorier aussi noms, versions et SHA-256 de tous les autres JAR et des bibliotheques de lancement. Ne pas mettre a jour une dependance pendant la meme intervention ; tout ecart doit passer une recette distincte, y compris un changement de Java.

Les dependances du descripteur portent `side="BOTH"` : cette declaration n'est pas une preuve de connexion reussie depuis un client sans QuarryGuard. Le serveur uniquement est le contrat a verifier en staging, pas une compatibilite reseau deja acquise. Ne pas contourner un refus de connexion en distribuant le prototype aux joueurs.

Garder les injections obligatoires : [configuration Mixin][mixins] avec `required=true`, `defaultRequire=1`, compatibilite Java 21. Au moindre echec de dependance ou d'injection, arreter la recette. Ne pas abaisser les exigences pour obtenir un demarrage, ni retirer automatiquement le garde pour rouvrir un serveur non protege.

## 3. Commandes et signaux de surveillance

Commandes constatees dans les sources a la date de lecture, a reconfirmer sur le candidat fige :

| Commande | Usage et restriction |
|---|---|
| `/quarryguard status` | Lecture d'etat, permission Minecraft de niveau 2 minimum. Dans la console serveur : `quarryguard status`. Ne migre aucune machine et ne fournit pas l'inventaire des orphelines. |
| `selftest`, `powered`, `loadtest`, `restart-prepare`, `restart-check` sous `quarryguard` | Commandes de banc ajoutees seulement si la propriete JVM `ascendant.quarryguard.lab` vaut `true`. Elles peuvent creer ou retirer des fixtures et modifier des claims. Jamais sur un monde joueur, ni sur sa copie de recette patrimoniale. |

Sur un futur serveur operateur et sur la copie patrimoniale, laisser la propriete lab absente ou fausse. Ne pas affaiblir les gardes de chemin, de monde plat ou d'absence de joueurs pour y executer le banc automatique. L'absence de ces sous-commandes est normale hors lab. Aucune commande `adopt`, `resume`, `repair`, `reset` ou `reload` de QuarryGuard n'est fournie par le code lu.

Avant ouverture, relever `ready=true` et `mutations=0` au repos, puis comparer le nombre et la localisation des claims attendus aux donnees FTB et au journal de demarrage. `status` n'affiche pas le nombre de claims ; le journal `QuarryGuard laboratory ready: ... claims` le donne. Une copie FTB incomplete peut etre lue comme vide : `ready=true` n'en garantit pas l'integrite.

Un `ready=false`, un compteur `mutations` durablement non nul au repos, une erreur FTB/Mixin ou une machine inexplicablement active impose l'arret de la recette et la conservation des journaux. Ne pas remettre un compteur a zero a la main. Une recharge interne FTB invalide le garde ; utiliser un arret/redemarrage normal apres diagnostic, pas un rechargement a chaud ou un appel manuel de `GuardHooks.start`.

Les compteurs `checks`, `denied`, les recherches de geometrie et `meanCheckUs` sont des indicateurs partiels, pas un audit exhaustif ni des MSPT. Les refus pour owner manquant ne se deduisent pas du seul compteur `denied`, et les avertissements ne sont pas repetes a chaque tick. Une absence de nouveau message ne prouve pas une reprise.

## 4. Sauvegarde obligatoire avant tout premier chargement

Ces operations ne devront etre effectuees que lors d'une maintenance future explicitement autorisee.

1. Nommer le responsable, fermer l'acces des joueurs et stopper proprement le serveur. Verifier la fin effective du processus et de la sauvegarde. Ne pas copier un monde en cours d'ecriture ni modifier un NBT pendant que le serveur le detient en memoire.
2. Creer un point de retour complet, date et immuable, **avant de charger le monde avec QuarryGuard**. Inclure toutes les dimensions et regions, entites, conteneurs, inventaires/joueurs, donnees de niveau et de chargement, FTB Teams/Chunks, `config`, `defaultconfigs`, `serverconfig`, datapacks/scripts pertinents, parametres de lancement et jeu complet de mods. Proteger les secrets et identites de cette archive ; aucun envoi externe.
3. Enregistrer le chemin absolu du snapshot, les empreintes des fichiers, les versions, l'heure et le nom du responsable. Inventorier les claims par dimension/equipe, pas seulement un total. Verifier que la copie est complete et restaurable dans un dossier distinct avant de la considerer comme point de retour.
4. Inventorier les quarries normales et avancees, y compris dans les chunks decharges, a partir de la copie arretee avec un lecteur structure valide. Pour chacune : dimension, position, type, UUID present/absent, motif de quarantaine, NBT brut et empreinte, emprise, etat, cible, stockage, modules, energie et `chunkLoader`. Ne pas deduire l'inventaire des seuls chunks visites en jeu. Aucun outil d'inventaire ou d'edition NBT n'est livre ici.
5. Relever separement les chunks forces globaux, les forceloads FTB et les autres sources connues, sans modifier les tickets. Un forceload FTB n'est pas un claim et les bits globaux ne representent pas necessairement tous les tickets FTB.
6. Faire accepter la fenetre de progression perdue en cas de restauration. Sans snapshot coherent et restauration repetee, ne pas demarrer l'intervention. Une copie de la seule region de quarry n'assure pas la coherence des loots, inventaires et claims.

## 5. Anciennes quarries : refus ferme et quarantaine

| Etat observe | Consigne operateur |
|---|---|
| Owner absent | Laisser la machine en place, suspendue par defaut, meme hors claim. Aucune adoption automatique. |
| Owner connu mais joueur hors ligne | Ce n'est pas une orpheline. Verifier les droits de cet UUID ; la connexion du proprietaire n'est pas une condition d'identite. |
| Owner connu, emprise interdite ou garde indisponible | Maintenir la suspension ; diagnostiquer droits, emprise ou index. Ne pas accorder de bypass pour faire passer la recette. |
| Cible restauree invalide ou marqueur de quarantaine present | Isoler le cas et conserver le NBT brut. Un owner present n'annule pas le refus. Ne pas effacer le marqueur pour forcer la reprise. |
| Attribution contestee, deplacement ou clone non valide | Garder hors reprise. Ne pas attribuer selon le claim d'arrivee ou supposer qu'un clone sera automatiquement suspendu. |

La politique est **fail-closed** : si l'identite ou la protection manque, on refuse le travail. Elle n'implique pas une destruction. L'absence d'owner provoque un refus calcule ; elle n'ecrit pas necessairement le marqueur persistant de quarantaine utilise pour une cible invalide.

### Adoption future, manuelle et en place

1. Traiter seulement une machine native sans owner et dont les donnees sont coherentes, d'abord sur copie arretee. L'operateur valide explicitement un UUID de joueur avec une justification tracee. Le proprietaire du claim, le chef d'equipe et le faux joueur partage ne prouvent pas qui a pose la quarry. Identite non tranchee : ne rien attribuer.
2. Consigner machine, UUID retenu, operateur, motif, date, snapshot et NBT avant intervention. La cle attendue est l'UUID NBT racine `ascendant_quarryguard_owner`, pas une chaine de nom ni seulement une copie imbriquee dans les persistent data.
3. En l'absence de commande metier, exiger un outil NBT structure prealablement valide sur copie. Ajouter uniquement l'owner racine ; ne changer ni emprise, etat, cible, inventaire, energie ou loader. Ne pas retirer `ascendant_quarryguard_quarantine`. Si l'outil ou la procedure n'est pas valide, l'adoption reste bloquee, sans improvisation en production.
4. Comparer les champs natifs avant/apres, puis verifier sauvegarde, redemarrage, droits et absence de changement de forcage sur la copie. Une cible rejetee arrete l'adoption ; ne pas reinjecter l'UUID en memoire pour contourner le rejet. Conserver la preuve acquise du helper puis de la sauvegarde native avec owner et motif ; la completer par un chargement natif complet et une persistance disque entre redemarrages sur le candidat retenu.
5. Reprendre seulement apres la recette utile. Un owner legitime peut rester interdit si son emprise croise un claim adverse. Le retrait d'un claim ou le retour d'une alliance peut faire repartir une machine attribuee ; ne pas faire ces changements pendant une inspection supposee inerte.

Ne pas casser/reposer pour migrer par defaut : risque de perte de progression et effets natifs sur drops, modules ou forcage. Ne pas forcer l'etat `FINISHED`, purger les forced chunks, ni appeler un unforce FTB pour "nettoyer" une suspension. Deux machines peuvent dependre du meme chunk ; retirer la source de A peut affecter B. Si le cout des chunks conserves est inacceptable, reporter le deploiement au lieu de les purger. Toute liberation selective releve d'une maintenance distincte et justifiee.

## 6. Recette staging avec de vraies sessions

Preparer, apres autorisation distincte, un serveur de staging isole : copie complete du pack et copie coherente du monde arretee, dans des chemins distincts de la production et du snapshot. Verifier qu'aucun lien de repertoire ne pointe vers un monde ou des donnees de production. Limiter les acces aux testeurs et verifier tous les ports/services, y compris les ports annexes des mods. Ne pas exposer la copie publiquement ni desactiver l'authentification pour simplifier les essais.

Utiliser de vrais clients du pack sans le JAR QuarryGuard et des sessions reseau authentifiees : au moins proprietaire, allie et joueur adverse pour les droits, puis jusqu'a huit joueurs simultanes pour la charge cible. Tester en Survie, sans OP ou bypass de commodite ; reserver leur verification a un scenario explicite. La copie full-runtime deja preparee est un environnement de banc, pas cette recette patrimoniale ; ses limites et ses fixtures ne doivent pas etre contournees.

Pour chaque scenario et pour les deux types de quarry, conserver : JAR/SHA-256, versions/configs, snapshot initial, participants et roles, dimension/positions, actions, etat avant/apres, journaux et captures, verdict `PASS`, `FAIL` ou `NON TESTE`. Un test exclu doit avoir un motif et une restriction d'usage effectivement applicable, pas un PASS implicite.

| Scenario | Action a observer en vraie session | Critere de reussite |
|---|---|---|
| Connexion serveur uniquement | Connexion, reconnexion et interface de la quarry avancee sans QuarryGuard sur les clients. | Pas d'exigence du JAR client, erreur reseau ou desynchronisation persistante. |
| Pose et claims | Terrain libre, propre equipe, allie, adversaire PUBLIC/PRIVATE ; claim strictement interieur, frontieres et coordonnees negatives ; claim du chunk machine. | Autorisation conforme ; refus avant consommation dans le chemin normal ; bloc cible et marqueurs preserves. Toute anomalie tardive de pose est consignee, pas supposee remboursee. |
| Droits en cours de travail | Retirer alliance/appartenance/bypass, changer confidentialite, ajouter/transferer/retirer un claim via les voies FTB accessibles. | Suspension aux controles suivants, sans droit conserve par le cache ni effets interdits observes ; reprise seulement apres droit retabli. Verifier aussi proprietaire hors ligne. |
| Travail utile | Machines alimentees, cadre, minage, fluides/modules utilises et objets au sol aux frontieres ; reconfiguration avancee par owner, tiers et joueur eloigne. | Pas d'effet hors droits, de double loot ou de disparition inexpliquee ; progression, stockage et energie conformes aux operations natives. Absence d'erreur d'interface ne suffit pas. |
| Arret et reprise | Sauvegarder depuis une cible deja engagee, stopper puis redemarrer avec claims persistants et droit retire ; retablir le droit et observer les premiers effets utiles. | Emprise/owner/checkpoint conserves, suspension maintenue, puis cadre/minage effectif sans effets doubles ; compteurs blocs/loot/energie compares au dernier checkpoint natif. `mayWork=true` seul n'est pas une reprise utile. |
| Orphelines et quarantaine | Cas sans owner, adoption manuelle documentee, cible invalide et quarantaine sauvegardee puis rechargee. | Orpheline immobile meme en terrain libre ; pas d'attribution arbitraire ; owner et motif conserves ; aucun effacement des autres donnees. |
| Forcage partage | Sur fixture dediee, A prend le forcing, B dans le meme chunk le reutilise ; suspension, restart, fin/retrait controle de A ; variante avec ticket FTB distinct. | Decrire ce qui maintient B chargee et verifier l'absence de purge a l'adoption/suspension. Si la coexistence echoue, traiter le defaut ou exclure cet usage de facon verifiable avant toute livraison. Un simple bit preexistant donnant le loader None aux deux machines ne teste pas ce cas. |
| Deplacement retenu | Chaque mover/clone effectivement accessible en Survie que l'on souhaite autoriser. | Owner, zone, cible et position absolue du loader coherents ; droits de destination reevalues. Une fixture NBT seule ne valide pas le mod de deplacement ; exclure les voies non validees. |
| Pack complet et charge | Copies distinctes du meme checkpoint avec puis sans correctif, parcours equivalents, huit joueurs, plusieurs machines petites/grandes, generation et rafales de claims. | Relever MSPT p50/p95/p99, pics, memoire/GC/allocations et debit utile sur une duree convenue avant essai. Fixer et respecter des seuils avant mesure ; ne pas les inventer apres coup. Aucune comparaison hostile sans garde sur le monde joueur. |
| Restauration | Repeter la section 8 sur un dossier distinct, depuis le snapshot immuable. | Monde, inventaires, claims, etats machine et forcages correspondent au point de retour ; aucun monde efface ou reinitialise. |

Les callbacks de casse/loot/fluides/voisinage et les modules peuvent avoir des effets intermediaires. Les scenarios retenus doivent couvrir ceux du pack utilise ; ne pas promettre une atomicite universelle. Conserver les configurations FTB et la politique wilderness de reference : `no_wilderness` ou des fournisseurs de permissions differents demandent une validation specifique, pas une modification des droits pour faire passer les tests.

## 7. Deroulement futur d'un deploiement autorise

1. Avant la maintenance, obtenir l'accord explicite sur le JAR identifie par SHA-256, les resultats de recette, la politique des anciennes machines, les restrictions restantes et la perte de progression possible. Un FAIL non traite sur un usage prevu interdit la livraison. Aucun de ces accords n'est acquis ici.
2. Fermer l'acces, stopper proprement et refaire le snapshot complet de la section 4. Comparer l'inventaire reel de mods, claims et machines avec celui de la recette ; tout ecart pertinent impose de reevaluer celle-ci.
3. Serveur arrete, ajouter uniquement le JAR valide, sans doublon de QuarryGuard ni mise a jour annexe. Garder ses dependances epinglees. Ne pas activer les commandes lab. Ne pas adopter en masse au premier demarrage ; conserver la liste des machines sans owner.
4. Demarrer avec acces joueur ferme, lire les erreurs, le nombre de claims charges et `quarryguard status`. Attention : les machines deja attribuees peuvent travailler des ce demarrage. Une fenetre "sans effets" exige le serveur arrete ou un verrou dont l'efficacite a ete demontree, pas seulement une whitelist fermee.
5. Effectuer les controles limites prevus par la recette avec les seuls testeurs autorises. Verifier suspensions attendues, absence de reprise illicite, absence de perte/duplication et charge. Toute divergence ferme la validation et declenche la procedure d'incident ; pas de purge, bypass ou hot-reload de rattrapage.
6. Une ouverture aux joueurs necessite une decision explicite du responsable apres ces controles et un suivi des alertes et performances. Archiver le dossier d'intervention et garder le snapshot. La reussite de ces etapes ne vaut pas certification generale de compatibilite avec de futures versions.

## 8. Retrait et retour arriere sans effacer le monde

### Retirer le JAR seulement : effets et limites

Cette option doit etre choisie explicitement ; ce n'est pas un rollback des donnees.

| Ce que le retrait change au prochain demarrage | Ce qu'il ne repare pas |
|---|---|
| Les gardes de pose, de travail, de reconfiguration et de collecte ajoutes par les Mixins ne s'appliquent plus. La commande QuarryGuard disparait. | Aucun bloc mine, cadre pose, fluide modifie ou effet de voisinage n'est annule. |
| Une quarry au checkpoint natif actif peut repartir, meme si QuarryGuard la suspendait pour owner absent, claim adverse ou quarantaine. | Ni loot, inventaire, module, energie ou progression consommee ne sont restaures. |
| On revient au comportement de QuarryPlus et aux protections FTB restantes, sans garantie que celles-ci couvrent l'emprise comme QuarryGuard. | Aucun claim, droit ou ticket de chargement n'est reconstruit ou nettoye automatiquement. Le risque initial peut revenir. |
| Les tags ajoutes ne sont plus interpretes par QuarryGuard absent. | Ni une attribution perdue, ni une cible incoherente, ni une sauvegarde corrompue ne sont reparees. La conservation de tags inconnus apres sauvegarde sans garde puis reinstallation reste a tester. |

La compatibilite de chargement sans le JAR doit etre repetee sur copie. Ne pas promettre qu'un tag ignore restera sur disque apres les sauvegardes natives. Ne pas effacer manuellement les tags "pour desinstaller" : conserver la preuve d'identite et de quarantaine avant tout chargement sans garde.

Pour un retrait seul futur : acces ferme, arret propre, sauvegarde complete de l'etat courant et journaux ; isoler uniquement le JAR QuarryGuard hors du dossier de mods charge, en conservant son hash et tous les autres mods. Redemarrer d'abord une copie de verification. Ne pas retirer QuarryPlus, FTB ou leurs dependances ; ne pas lancer le monde sans ses mods de contenu. Ne pas rouvrir automatiquement le serveur non protege. Si les machines ne peuvent pas etre maintenues inactives de facon validee, conserver le serveur arrete.

### Retour a l'etat anterieur : restauration coherente

1. A l'incident, fermer l'acces et stopper proprement. Conserver l'etat en echec complet et ses journaux dans une archive distincte avant toute restauration. Ne rien casser en jeu, ne pas supprimer de regions ni de tickets.
2. Selectionner le snapshot d'avant installation avec son manifeste. Verifier sa disponibilite et ses empreintes. Restaurer dans **un nouveau dossier de serveur et de monde**, jamais par suppression ou fusion dans l'original. Controler les chemins absolus resolus et l'absence de liens vers l'ancien dossier avant toute ecriture.
3. Restaurer ensemble le monde complet, toutes les dimensions, inventaires/entites, FTB, configurations, datapacks/scripts et ensemble de mods du meme checkpoint. Si le snapshot est anterieur a QuarryGuard, ce JAR doit etre absent du lot restaure. Ne pas melanger anciens blocs et loots recents, anciens claims et nouvelles appartenances, ou anciennes machines et forcages d'une autre date.
4. Tester cette restauration en copie isolee, avec les acces limites, et comparer machines, progression, inventaires, claims et sources de chargement au snapshot. Garder l'original et l'archive d'incident intacts ; ne pas reinitialiser le monde, supprimer `level.dat`, regenerer des chunks ou utiliser une purge de tickets comme reparation.
5. Apres validation et autorisation, pointer le service arrete vers le dossier restaure verifie, sans ecraser l'ancien monde. Documenter l'etat abandonne et la fenetre de progression perdue. Le serveur sans garde retrouve son niveau de protection anterieur ; l'ouverture aux joueurs reste une decision distincte.
6. Sans sauvegarde coherente ou avec une restauration en echec, rester arrete et conserver les preuves. Retirer le JAR ou reinstaller une version precedente ne remplace pas la restauration. Une reparation selective eventuelle demande une mission distincte.

## 9. Dossier a remettre au responsable

- Version et SHA-256 du JAR candidat, manifeste complet des dependances et configurations, lien vers les sources correspondantes.
- Chemins absolus du snapshot immuable, de la copie de recette, de l'archive d'incident si necessaire et de la restauration validee ; empreintes et verification de coherence.
- Inventaire des quarries/claims/forcages, registre des UUID adoptes et liste des machines maintenues en quarantaine ou hors reprise.
- Tableau des scenarios et preuves, y compris echecs et `NON TESTE`, vraies sessions et mesures de charge ; restrictions explicitement acceptees et applicables.
- Responsable, fenetre d'intervention, conditions d'arret, decision de deploiement/retrait/restauration et accord sur la progression abandonnee.

**Etat de sortie de cette mission : protocole redige seulement. Aucune autorisation de production, aucune installation et aucune nouvelle preuve d'execution.**

[readme]: C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/README.md
[hooks]: C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/GuardHooks.java
[commandes]: C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/QuarryGuard.java
[labchecks]: C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/LabChecks.java
[poweredchecks]: C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/PoweredChecks.java
[mods]: C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/resources/META-INF/neoforge.mods.toml
[mixins]: C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/resources/ascendant-quarryguard.mixins.json
[reversibilite]: C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/research/reversibilite-et-reprise.md
[full-runtime]: C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/research/full-runtime-notes.md
[preuve-native]: C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/results/runtime-20260904-121838.log
[preuve-prepare]: C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/results/runtime-runtime-restart-prepare-20260904-123447.log
[preuve-restart]: C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/results/runtime-runtime-restart-check-20260904-123509.log
[preuve-powered1]: C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/results/runtime-runtime-powered-baseline0-20260904-124429.log
[preuve-powered2]: C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/results/runtime-runtime-powered-baseline0-20260904-125305.log
[preuve-powered-pass]: C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/results/runtime-runtime-powered-baseline0-20260904-125500.log
[preuve-selftest-recent]: C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/results/runtime-runtime-selftest-baseline0-20260904-125855.log
[preuve-full1]: C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/results/runtime-full-runtime-selftest-baseline0-20260904-125545.log
[preuve-full2]: C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/results/runtime-full-runtime-selftest-baseline0-20260904-125735.log
