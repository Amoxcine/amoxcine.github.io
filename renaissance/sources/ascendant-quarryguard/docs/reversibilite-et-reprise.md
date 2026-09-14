# QuarryGuard : reversibilite et reprise

Date : 4 septembre 2026. Audit statique borne, actualise avec les essais main de 14:34:59 et 14:35:20, heure de Paris.

> Actualisation de lecture : le constat historique de perte d'owner ci-dessous
> a ete corrige depuis cette revue. Le code conserve l'UUID et un motif de
> quarantaine separe ; le selftest minimal de 14:59:06 teste le helper et la
> sauvegarde native. Voir [validation-phase2.md](validation-phase2.md) et
> [deploiement-operateur-phase2.md](deploiement-operateur-phase2.md) pour l'etat
> actuel. Les limites de reprise utile et de tickets partages demeurent.

## Decision et perimetre

**Poursuivre les tests isoles puis valider le protocole d'installation reversible ; aucune production autorisee ici.** Le redemarrage avec maintien de la suspension est maintenant prouve pour deux fixtures natives. La reprise effective du minage apres chargement et les chunks forces partages restent a valider.

Menace retenue : joueur Survie par les voies de jeu accessibles. Un administrateur qui forge arbitrairement un NBT n'est pas un adversaire a neutraliser par un nouveau framework d'identites. Les NBT anormaux sont des cas de robustesse/persistance ; les movers et clones ne deviennent des exigences de securite que pour les voies effectivement accessibles aux joueurs et retenues dans le pack. Aucun inventaire general de nouveaux mods n'a ete entrepris.

Cet audit n'a lance ni compilation, ni serveur, ni test. Il relit le code, les desassemblages locaux et les journaux existants ; seul ce rapport est produit. Les sauvegardes de production n'ont pas ete ouvertes ni modifiees.

## Preuves disponibles

| Preuve | Ce qu'elle demontre | Ce qu'elle ne demontre pas |
|---|---|---|
| [Suite initiale, PASS 14:18:50](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/results/runtime-20260904-121838.log:82) | Poses normales/avancees natives, droits FTB en direct, mutations de claims, aller-retour NBT intra-processus. | Pas de redemarrage dans cette ancienne suite. |
| [Restart prepare, PASS 14:34:59](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/results/runtime-runtime-restart-prepare-20260904-123447.log:70) | Deux poses natives, energie ajoutee, un tick natif, puis claim hostile ; tick bloque avec NBT identique ; sauvegarde et arret propres. | Pas une longue phase de minage ni toutes les phases/cibles actives. |
| [Restart check, PASS 14:35:20](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/results/runtime-runtime-restart-check-20260904-123509.log:60) | Nouvelle JVM, index reconstruit avec **2 claims** ; owner/area/state/energie preserves, claims hostiles retrouves ; **25 ticks natifs bloques par machine avec NBT identique** ; unclaim puis autorisation retrouvee. | Pas de tick utile apres l'unclaim, pas d'assertion des tickets partages, pas de comptage de blocs/loot produits apres reprise. |

Lecture du code de [RestartChecks.run](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/RestartChecks.java:25) : PID different exige L65-66 ; comparaison owner/area/state/energie L68-77 ; NBT identique compare avant/apres les 25 ticks **du second processus** L79-80. Ce n'est pas une assertion d'egalite de tout le NBT entre les deux JVM. L81-84 : unclaim, `mayWork=true`, puis suppression de la machine sans tick utile intermediaire. Les deux fixtures sont aux positions (800,64,800) et (848,64,800), donc pas dans le meme chunk.

Le helper [LabSupport.tick, L95-102](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/LabSupport.java:95) appelle le vrai ticker du bloc. La barriere de laboratoire exige fil serveur, zero joueur connecte, boucle locale, monde plat et `ready=true` ([requireLab, L43-53](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/LabSupport.java:43)). Les essais ne sondent donc pas tous les effets precedant `ServerStarted`. La preservation observee des champs et des ticks bloques est une preuve positive, pas une raison de requalifier tout le demarrage comme non teste.

## Owner et adoption en place

### Stockage exact

La cle est `ascendant_quarryguard_owner`. [confirmPlacement, L161-175](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/GuardHooks.java:161) ecrit l'UUID du vrai poseur dans `machine.getPersistentData()` apres confirmation du plan de pose. [loadOwner/saveOwner, L240-261](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/GuardHooks.java:240) :
- retire l'owner des persistent data au chargement ;
- controle le `targetPos` eventuel ;
- ne relit l'UUID que depuis la **cle racine** du tag NBT recu ;
- recopie l'owner en memoire vers la racine du tag de sauvegarde.

Les injections sont a RETURN de `loadAdditional` et HEAD de `saveAdditional` : [normale, L149-158](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/mixin/quarry/QuarryEntityMixin.java:149), [avancee, L208-217](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/mixin/quarry/AdvQuarryEntityMixin.java:208). Modifier uniquement une copie imbriquee des persistent data ne constitue pas une migration fiable. La voie native est deja exercee par [LabChecks.reload, L330-342](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/LabChecks.java:330) et, pour la persistance disque, par RestartChecks.

**Une ancienne quarry sans owner est refusee**, meme hors claim : [mayWork, L197-213](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/GuardHooks.java:197). `mayConfigure` exige aussi un owner non nul, meme pour le bypass de l'operateur ([L215-221](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/GuardHooks.java:215)). Cliquer dessus ou etre OP n'est donc pas une commande d'adoption.

### Identite : decision humaine, pas inference depuis le claim

L'ancien rapport constate l'absence d'auteur natif utilisable et le fake player commun : [rapport initial, sections 6-9](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/refonte-ascendant-2026-09-04/quarry/rapport-quarryguard.md). Ni son UUID, ni le chef de party, ni le proprietaire du claim de la machine ou d'un claim partage ne permettent de retrouver le poseur.

Un operateur peut **valider explicitement un UUID de joueur** d'apres la connaissance du serveur et l'accord/documentation du joueur. Consigner UUID, machine (dimension/position/type), operateur, motif et NBT avant/apres. Il n'est pas necessaire d'inventer une preuve cryptographique ou un registre global de machine pour cette adoption manuelle. Identite non tranchee : ne rien attribuer, conserver la suspension. Un proprietaire legitime hors ligne n'est pas un orphelin.

Le prototype n'offre pas de commande metier d'adoption ; il offre les primitives NBT et les gardes. [Commandes actuelles](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/QuarryGuard.java:30) : `status`, et sous option lab `selftest`, `loadtest`, `restart-prepare`, `restart-check`. L'absence de commande dediee est une contrainte operatoire, pas un motif pour imposer un nouveau framework.

### Defaut concret : quarantaine qui perd l'owner

[loadOwner, L240-258](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/GuardHooks.java:240) retire l'UUID en memoire **avant** le controle de cible. Si le tag contient un `targetPos` indecodable, ou hors X/Z de l'area, le code journalise "invalid restored target" et retourne sans restaurer l'owner.

Le tag d'entree n'est pas modifie, mais une nouvelle sauvegarde dans un tag neuf n'ecrit plus l'UUID, puisque `saveOwner` ne l'ecrit que s'il existe en memoire. **L'attribution peut ainsi etre perdue au prochain enregistrement du monde.** C'est une consequence statique precise, pas encore un test execute. Elle compte pour une reprise/reparation de sauvegarde, meme sans joueur malveillant.

Mesure actuelle : exporter et conserver le NBT brut **avant tout chargement de migration**, puis ne pas reattribuer une machine rejetee pour cible invalide sans traiter la cause. Avant de promettre une quarantaine reversible, verifier/corriger la conservation de cette preuve ; un statut de refus distinct de l'identite suffit, aucun framework general n'est impose.

## NBT, cibles et deplacements

### Ce qui est restaure

[QuarryEntity, sauvegarde/chargement L248-407](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/refonte-ascendant-2026-09-04/quarry/evidence/quarry/com.yogpc.qp.machine.quarry.QuarryEntity.txt:248) et [AdvQuarryEntity, L158-310](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/refonte-ascendant-2026-09-04/quarry/evidence/quarry/com.yogpc.qp.machine.advquarry.AdvQuarryEntity.txt:158) restaurent area, state, stockage, modules, chunkLoader et cible ; la normale conserve `head` et `skipped`, l'avancee `workConfig`. `digMinY` passe par les tags communs de synchronisation et l'energie par PowerEntity.

Le tag `targetPos` provient de **`targetIterator.getLastReturned()`**, pas de la serialisation complete de l'iterateur. Au chargement, un nouvel iterateur est construit depuis state/area/targetPos, et workConfig pour l'avancee. Voir [createTargetIterator normal](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/refonte-ascendant-2026-09-04/quarry/evidence/quarry/com.yogpc.qp.machine.quarry.QuarryEntity.txt:1615) et [createTargetIterator avance](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/refonte-ascendant-2026-09-04/quarry/evidence/quarry/com.yogpc.qp.machine.advquarry.AdvQuarryEntity.txt:723).

Le garde controle seulement le decodage et les bornes X/Z du `targetPos` present. Ce n'est pas un validateur universel du NBT. Ne pas ajouter naivement un test Y dans l'area des marqueurs : le minage normal descend sous cette area ; Y doit suivre la phase, `digMinY` et les iterateurs natifs. Une cible absente peut etre normale avant le debut du parcours.

Les reconfigurations autorisees via `setArea` vident les cibles/iterateurs prepares ([normale L96-119](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/mixin/quarry/QuarryEntityMixin.java:96), [avancee L119-140](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/mixin/quarry/AdvQuarryEntityMixin.java:119)). **Le chargement NBT affecte directement les champs**, sans passer par ce reset. Exemple de robustesse : [fromClientTag L312-332](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/refonte-ascendant-2026-09-04/quarry/evidence/quarry/com.yogpc.qp.machine.advquarry.AdvQuarryEntity.txt:312) appelle `AdvQuarryState.valueOf` avant l'injection `loadOwner` ; un state inconnu peut lever avant la quarantaine. Cela ne prouve aucune voie d'exploitation Survie ; traiter ce cas comme sauvegarde corrompue a isoler, pas comme nouveau blocage vital de securite.

### Movers et clones : portee exacte

Un NBT copie avec owner et cible X/Z coherente peut passer `loadOwner` puis `mayWork` si les droits de l'UUID couvrent l'area et la nouvelle position. Aucun lien de provenance ni test d'identite de l'instance attachee au monde n'est fait ; la fixture intra-processus autorise elle-meme une instance non attachee. **Le code ne garantit donc pas la suspension automatique de tout clone ou deplacement.**

Ce constat n'impose pas de nouvel identifiant par principe. Pour les voies de deplacement/clonage accessibles aux joueurs qui seront retenues : tester la conservation/reinitialisation effective de owner, area, cible et `chunkLoader.pos`, puis soit valider cette voie, soit l'exclure pour les quarries jusqu'a validation. Ne pas mettre en place une inference depuis le claim d'arrivee. Les copies administratives restent des actes operateur a revalider manuellement.

Risque concret supplementaire : le loader garde une **position absolue**. S'il reste lie a l'ancienne machine, sa suppression peut retirer le forcing de l'ancien chunk. Ce point demande un test de mover reel ou une fixture NBT equivalente, meme si l'owner est parfaitement legitime.

## FTB et caches au redemarrage

[start/stop, L74-99](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/GuardHooks.java:74) remet a zero INDEX, TEAMS, COVERAGE et avertissements, charge les donnees de toutes les equipes via `getOrCreateData`, enumere les claims puis publie `ready=true`. [ServerStarted/Stopping, L22-27](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/QuarryGuard.java:22) fixe le cycle. **La reconstruction avec deux claims sauvegardes est maintenant testee**, sans appel manuel de `start` dans RestartChecks.

[check/allowed, L306-341](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/GuardHooks.java:306) cache uniquement la geometrie : dimension, rectangle, chunk machine, revision et ensemble des UUID d'equipes. Les droits restent relus : bypass de l'owner, rang membre/ALLY explicite, BLOCK_EDIT et equipe canonique valide. Un claim PUBLIC adverse reste refuse. Pas de resultat d'autorisation sauvegarde a reutiliser apres restart. Les Mixins [ClaimsMixin](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/mixin/ClaimsMixin.java:15) et [ClaimOwnerMixin](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/mixin/ClaimOwnerMixin.java:14) suivent register/unregister/transfert ; une mutation invalide la geometrie globalement.

Reserves precises, sans imposer un hot-reload :
- [TeamsLoadMixin](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/mixin/TeamsLoadMixin.java:12) invalide au debut de `TeamManagerImpl.load`, sans reouverture automatique. Le protocole doit retenir **arret/redemarrage normal**, pas rechargement a chaud. Le rapport initial suggerait LOADED comme barriere ; le [bytecode L271-327](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/research/ftb-evidence/dev.ftb.mods.ftbteams.data.TeamManagerImpl.txt:271) montre cet evenement avant l'enumeration des fichiers d'equipes.
- `mutations` augmente a HEAD et baisse a RETURN. Une exception peut le laisser non nul ; `status` l'expose et `available` refuse. Ne pas le remettre a zero a la main pour reprendre : examiner l'erreur et redemarrer avec les donnees FTB coherentes.
- `ready=true` ne garantit pas l'integrite d'une copie incomplete. [loadTeamData L138-173](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/refonte-ascendant-2026-09-04/quarry/evidence/chunks/dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl.txt:138) retourne une donnee vide si `SNBT.read` retourne null ; le garde ne connait pas le nombre attendu de claims. Comparer inventaire attendu, donnees FTB et erreurs de chargement. Ce controle d'installation suffit ici ; pas besoin d'inventer une nouvelle API FTB.
- La politique testee autorise wilderness. [Configuration lab](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/runtime/config/ftbchunks-world.snbt:48) a `no_wilderness=false`. Ne pas presenter le predicat comme toute la politique FTB, notamment les providers externes ou une configuration `no_wilderness` differente. Valider le contrat voulu sans modifier les permissions pour faire passer la migration.
- TEAMS retient des references jusqu'a stop ; `allowed` refuse les equipes invalides/non canoniques. Aucun droit durable supplementaire n'en decoule, mais les grands cycles reload/suppression restent hors preuve de ces petits essais.

## Forced chunks : ne pas les nettoyer

### Deux mecanismes distincts

[QuarryChunkLoader.Load L16-58](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/refonte-ascendant-2026-09-04/quarry/evidence/quarry/com.yogpc.qp.machine.misc.QuarryChunkLoader$Load.txt:16) utilise `ServerLevel.setChunkForced(x,z,true/false)`, sans proprietaire de ticket. [QuarryChunkLoader.of L46-63](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/refonte-ascendant-2026-09-04/quarry/evidence/quarry/com.yogpc.qp.machine.misc.QuarryChunkLoader.txt:46) renvoie `None` si le bit global du chunk est deja present dans `getForcedChunks()`, ou si le loader est desactive.

FTB suit un domaine distinct avec `("ftbchunks", teamUUID)` : [updateChunkTickets L1971-1998](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/refonte-ascendant-2026-09-04/quarry/evidence/chunks/dev.ftb.mods.ftbchunks.data.ChunkTeamDataImpl.txt:1971). `ServerLevel.getForcedChunks()` n'enumerera donc pas necessairement tous les tickets FTB. Son cache [getForceLoadedChunks L437-504](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/refonte-ascendant-2026-09-04/quarry/evidence/chunks/dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl.txt:437) est distinct du cache QuarryGuard et filtre `isActuallyForceLoaded`. `ClaimedChunk.unload` retire le forceload FTB, **pas le claim** ([L268-303](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/refonte-ascendant-2026-09-04/quarry/evidence/chunks/dev.ftb.mods.ftbchunks.data.ClaimedChunkImpl.txt:268)).

### Contre-exemple partage

1. A demarre dans un chunk non force : `of` donne `Load`, qui met le bit global.
2. B demarre dans le meme chunk : `of` voit le bit et donne `None`.
3. A est retiree ou termine : son `Load` appelle `setChunkForced(...,false)`, sans consulter B. B perd cette source de maintien en charge, sauf autre source independante.

Preuve : [setRemoved normal L535-552](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/refonte-ascendant-2026-09-04/quarry/evidence/quarry/com.yogpc.qp.machine.quarry.QuarryEntity.txt:535), [avance L416-433](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/refonte-ascendant-2026-09-04/quarry/evidence/quarry/com.yogpc.qp.machine.advquarry.AdvQuarryEntity.txt:416) et [setState L609-659](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/refonte-ascendant-2026-09-04/quarry/evidence/quarry/com.yogpc.qp.machine.quarry.QuarryEntity.txt:609). Les transitions vers FINISHED restent autorisees par les Mixins meme si `mayWork` refuse : **finir n'est pas suspendre sans effets**. La presence d'un ticket FTB peut garder le chunk charge ; ne pas confondre retrait du bit et dechargement effectif.

[beforeForce L21-32](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/mixin/quarry/QuarryChunkLoaderLoadMixin.java:21) intercepte seulement une **nouvelle prise**. Il recherche la block entity a la position du loader, pas l'appelant ; il ne verifie pas qu'il s'agit de la meme quarry. L'unforce n'est pas intercepte. Un ancien `Load` restaure reste exploitable par `setRemoved`, meme si `enableChunkLoader=false` a ete change ensuite.

### Suspension et restart

Un refus du tick conserve l'etat natif, les cibles et les chunks deja forces ; il n'ecrit pas un etat de suspension persistant. Une permission retrouvee rend `mayWork` vrai et peut autoriser le tick suivant. **Les nouveaux essais prouvent le gel NBT bloque, pas une neutralite complete des tickets** : aucune assertion de forcing ne les accompagne, et les fixtures sont finalement retirees.

Le chargement natif restaure `state` et `chunkLoader` sans rejouer `setState`. Les chemins examines prennent le forcing lors d'une transition inactif -> actif, pas explicitement dans `loadAdditional`. Il reste a observer la reprise d'une machine active dependante du forcing sauvegarde, puis le cas d'un bit absent et celui d'un partage. Aucun desassemblage de la restauration interne des tickets ServerLevel ni scan des donnees de forcing du monde existant n'est fourni ici ; ne pas en deduire une garantie.

**Decision : ne pas toucher aux tickets pour attribuer un owner ou suspendre un orphelin.** Ne pas purger les forced chunks, appeler unforce FTB, forcer FINISHED ou casser pour "nettoyer". Un chunk sans source identifiee reste seulement un candidat orphelin ; une machine dechargee ou un autre ticket peut expliquer sa presence. Accepter temporairement le maintien en charge, ou ne pas deployer si ce cout est inacceptable. Une liberation selective exige une maintenance distincte avec provenance suffisamment etablie.

## Reserves avant installation

| Priorite | Reserve concrete | Critere de sortie |
|---|---|---|
| Necessaire | Reprise **utile** non couverte : RestartChecks s'arrete a mayWork=true. | Apres nouvelle JVM/unclaim, ticks de cadre/minage et conservation blocs/loot/stockage/energie sur les deux machines, depuis une cible deja engagee. |
| Necessaire | Forcing partage : A=Load/B=None et suppression/FINISHED peuvent retirer le bit commun. | Tester/traiter ce cas, ou exclure explicitement cette utilisation ; prouver l'absence de chirurgie des tickets pendant adoption/suspension. |
| Necessaire pour quarries existantes | Adoption manuelle et retour arriere pas encore repetes. | Inventaire, UUID valide par operateur, essai de modification owner seule et restauration complete reussie. Pas de framework d'identites exige. |
| A corriger/verifier pour quarantaine reversible | Cible invalide -> owner non restaure -> prochaine sauvegarde sans owner. | Test natif sur tag neuf ; conserver identite et motif distinctement, ou maintenir le cas hors reprise avec NBT brut sauvegarde. |
| Conditionnelle | Movers/clones accessibles en Survie et checkpoints natifs inhabituels. | Tester seulement les voies retenues ; exclure celles non validees. NBT admin forge = robustesse, pas menace vitale ajoutee. |

La reprise **bloquee** apres restart et le rebuild initial avec deux claims ne sont plus des blocages sans preuve. Les autres limites deja documentees sur callbacks/modules et charge complete ne sont pas certifiees par cet audit de reprise, ni etendues en nouvelles recherches.

## Protocole de migration et rollback

### 1. Preparer le point de retour

1. Sur copie complete arretee, conserver un snapshot immuable avant QG et une copie de travail distincte. Pour toute future installation autorisee, refaire un snapshot apres arret propre, joueurs exclus ; ne jamais appliquer maintenant a la production.
2. Inclure toutes les dimensions/regions, entites/conteneurs/inventaires joueurs, level data, donnees de chargement, FTB Teams/Chunks, configs et serverconfig, plus versions/empreintes des mods. Une seule region de quarry ne permet pas de remettre les loots et claims en coherence.
3. Inventorier aussi les quarries dechargees : dimension, position, type, owner present/absent, NBT brut et hash, area/state/targetPos, storage/modules/energie et chunkLoader. Separer bits globaux, forceload FTB et autres sources connues ; **aucune suppression**. Ne pas traiter l'absence de fichier de forcing comme une autorisation d'en creer.
4. Comparer les donnees FTB attendues apres demarrage de la copie ; une copie amputee de claims n'est pas du wilderness legitime. Conserver configs de droits, pas de bypass global de commodite.

### 2. Adopter une ancienne quarry sans la casser

1. Seules les machines sans owner, natives et coherentes, sont candidates a cette operation. Avec le garde present, elles sont refusees. Une machine deja attribuee peut reprendre automatiquement : pour l'examiner sans effets, rester hors ligne ou disposer d'un verrou effectivement teste. Couper seulement son energie entrante n'est pas un verrou ; le stock interne demeure.
2. L'operateur valide le joueur par UUID, enregistre le motif et la machine exacte. Claim partage/party/fake player ne sont jamais des sources d'attribution. En cas de doute, laisser la machine en place sans owner, suspendue.
3. Sur la copie arretee, sauvegarder le tag original puis ajouter **seulement la cle owner racine** via un outil NBT structure verifie. Ne changer ni state, targetPos, area, chunkLoader, ni inventaires. Ne pas retoucher un fichier pendant que le serveur le possede en memoire. Aucun tel outil/commande de migration n'est livre par cet audit.
4. Pour repeter cette operation via le banc isole, les primitives existent : `tag.copy()`, `putUUID`, `loadWithComponents`, `saveWithFullMetadata`. Appliquer l'essai sur une fixture controlee, sur le fil serveur et sans tick intermediaire ; si une instance attachee est modifiee, marquer `setChanged`. Comparer les champs natifs et les forcages avant/apres. Ce n'est pas une invitation a appeler le selftest sur un monde joueur.
5. Si `loadOwner` rejette la cible, arreter l'adoption ; garder l'original et traiter le defaut d'owner ci-dessus. Ne pas reinjecter l'UUID directement en memoire pour contourner le rejet.
6. Au redemarrage, reevaluer les droits de cet UUID sur toute l'area et le chunk de la machine. Un owner valide peut rester interdit. Ni `mayConfigure` ni un bypass de l'operateur ne doivent le rendre artificiellement autorise.

**Pas besoin d'un schema PENDING/machineId obligatoire.** Un registre operateur d'intervention et la cle existante suffisent a tester l'adoption en place dans ce perimetre. Il faut en revanche reconnaitre l'absence de mode global migration : ne pas promettre un demarrage d'inventaire inerte de toutes les machines deja attribuees.

### 3. Reprise et alternatives

1. Avant activation, lever les reserves utiles/forcing sur le laboratoire, puis sur la copie du monde existant dans son environnement de test propre. Les gardes de chemin du banc plat ne doivent pas etre affaiblis pour y lancer les fixtures destructives.
2. Sur la copie, reprendre une machine choisie a la fois dans une fenetre d'observation controlee ; toute autre machine attribuee chargee peut travailler, donc ne pas pretendre a une isolation par la seule alimentation.
3. Verifier premiere cible utile, effets de cadre/minage/fluides, conservation du stockage/modules/energie, et forcages a chaque phase. Comparer au **dernier checkpoint natif**, pas eternellement a la cible initiale de migration. Un iterator qui revisite une position n'est pas une duplication tant que ses effets ne sont pas doubles.
4. **Casser/reposer n'est pas la voie par defaut.** `setRemoved` peut unforce et les `saveToItem` natifs ne recopient que les composants, pas une garantie de checkpoint complet : [normal saveToItem](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/refonte-ascendant-2026-09-04/quarry/evidence/quarry/com.yogpc.qp.machine.quarry.QuarryEntity.txt:519), [avance saveToItem](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/refonte-ascendant-2026-09-04/quarry/evidence/quarry/com.yogpc.qp.machine.advquarry.AdvQuarryEntity.txt:400). Une pose neuve attribue le nouveau poseur et reselectionne la zone. Cette alternative exige un essai de drops/modules/enchantements/stockage/energie, l'acceptation de la perte de progression et la resolution du partage de forcing.
5. Un orphelin sans identite reste suspendu en place. Pour un deplacement volontaire, valider explicitement la nouvelle zone et le traitement du loader absolu par la voie retenue ; pas de reattribution depuis le claim d'arrivee.

### 4. Rollback complet, hors ligne

1. En cas d'echec : arret propre, archive de l'etat en echec et des logs ; aucune casse, aucune purge de tickets.
2. Restaurer ensemble monde complet, FTB, configurations et jeu de mods du meme snapshot. Retirer QG dans cet ensemble restaure. Ne pas fusionner anciens blocs avec inventaires/loots recents ou claims d'une autre date.
3. Controler empreintes et donnees attendues avant le demarrage d'une **copie de restauration**, puis repeter le controle des machines et du forcing. Garder le snapshot original immuable.
4. Le rollback abandonne toute progression depuis le snapshot. Faire accepter cette fenetre avant l'installation ; sans snapshot coherent, rester arrete plutot que tenter une reparation selective improvisee.

Retirer seulement le JAR en gardant le monde modifie n'inverse ni les effets de minage, ni la perte d'owner en quarantaine, ni les forceloads. La suspension calculee par les Mixins disparait avec le JAR : une ancienne machine encore active peut repartir. La compatibilite d'un tag supplementaire ignore par QuarryPlus n'est donc pas un rollback de gameplay. Le retour a la version sans garde retrouve egalement son ancien niveau de protection ; garder les joueurs exclus pendant la verification.

## Tests courts pour main

**Disponibles maintenant :** `RestartChecks.run(server, true/false)`, commandes lab `restart-prepare`/`restart-check` et parametre `-Test` du [lanceur actuel](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/run-lab.ps1:1). Le PASS existant ne doit pas etre reraconte comme un test manquant. Les cinq extensions ci-dessous restent a automatiser ; leurs noms sont des propositions, pas des commandes ajoutees.

1. **Reprise utile** : prolonger [RestartChecks L81-84](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/RestartChecks.java:81) avant suppression : apres unclaim, `LabSupport.tick` jusqu'a premier effet utile borne, snapshots bloc/stockage/energie/target et compteurs de loot. Ajouter un checkpoint A avec cible engagee (pas seulement un tick initial), puis nouvelle JVM ; variantes cadre/minage normale et avancee. `LabSupport.tick` est un helper package-private du banc, les API publiques dessous sont `EntityBlock.getTicker` et `BlockEntityTicker.tick`. Ne pas appeler des `setState/startQuarryWork/serverTick` QuarryPlus package-private comme API publique.
2. **Forcing partage et restart** : snapshots copies de `ServerLevel.getForcedChunks()` + etat FTB `ClaimedChunk.isForceLoaded/isActuallyForceLoaded`. Variante A prend `QuarryChunkLoader.of(...).makeChunkLoaded(...)`, B recoit None ; refus de droits doit laisser les bits, retrait/FINISHED de A doit exposer le risque. Variante bit preexistant avant A ; variante `ChunkTeamData.forceLoad` (simulation false) pour ticket FTB distinct. Deux machines et retrait uniquement dans une fixture jetable. Un simple test avec bit preexistant donnerait None a A et raterait le defaut A=Load/B=None.
3. **Adoption + quarantaine** : reutiliser `saveWithFullMetadata`/`loadWithComponents` sur copie de tag. Cas owner absent puis UUID operateur valide : droits relus, champs natifs et forcing inchanges ; cible X/Z invalide encodee avec `BlockPos.CODEC` : refus, puis sauvegarde native dans tag neuf et assertion de conservation de l'UUID d'origine. Cette derniere attente peut echouer actuellement et doit rester un defaut, pas un PASS de refus. Aucun besoin de multiplier les UUID admin forges pour valider la menace Survie.
4. **Deplacement retenu** : tester la voie mover/clone reellement accessible choisie par main, ou fixture equivalente `EntityBlock.newBlockEntity`, `setLevel`, `loadWithComponents` pour isoler la persistance. Positions ancienne/nouvelle et dimension, owner/area/target/loader avant-apres, bits anciens/nouveaux apres retrait. Revalidation des droits a l'arrivee ; pas d'assertion PENDING automatique inexistante. Une fixture NBT seule ne certifie pas le comportement du mod mover.
5. **Restauration et droits** : preparer un owner membre/allie/bypass, retirer son droit avant sauvegarde/arret (API deja utilisees dans [LabChecks L225-267](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/src/main/java/fr/ascendant/quarryguard/LabChecks.java:225)), verifier refus apres restart sans effacer/reconstruire manuellement l'index. Puis repeter le rollback complet dans une copie distincte sans QG et comparer NBT natifs, inventaires, claims et forcing au snapshot. Pour reload FTB, `TeamManagerImpl.load` doit laisser le garde ferme jusqu'au restart normal ; ne pas appeler `GuardHooks.start` pour masquer un defaut de cycle.

Conserver le manifeste inter-JVM deja utilise par RestartChecks (UUID via tags, positions, claims, PID), l'etendre avec checkpoint utile et forcing. Les assertions avant `ServerStarted` demanderaient une instrumentation de test distincte car `LabSupport.requireLab` exige deja ready ; elles ne sont pas couvertes par un appel manuel a mayWork apres demarrage.

**Conclusion :** preuve acquise de persistance et de suspension au restart ; preuve de reprise utile et coexistence des forcages encore manquante. Preparer l'adoption manuelle en place et le rollback complet, sans toucher aux tickets ni inferer l'owner d'un claim partage. Aucun code de production ni lancement ajoute par cet audit.
