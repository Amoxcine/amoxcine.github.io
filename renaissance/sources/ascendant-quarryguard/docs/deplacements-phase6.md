# QuarryGuard : deplacements Cardboard Box et Building Gadgets 2, phase 6

Date : 4 septembre 2026. Revue statique independante, strictement dans le
laboratoire. Aucun lancement Java, build, serveur, decompilation, changement
runtime, deploiement ou publication. Les deux JAR installes ont seulement ete
lus comme archives pour inventorier leurs classes et leurs tags de donnees.

**Suite du parent apres cette revue :** le bytecode manquant a ete extrait,
puis le candidat RC2 declare les blocs QuarryPlus comme non deplacables via
le tag commun NeoForge. Les chemins natifs Cardboard Box et le validateur de
cut BG2 refusent maintenant Quarry et Advanced Quarry. Voir le
[bilan de phase 6](validation-phase6-compatibilites.md). Le reste de ce
document conserve la revue et le protocole proposes avant cette mitigation.

## Verdict

**Compatibilite non certifiee et aucun contournement confirme.** Les preuves
locales etablissent le comportement de QuarryGuard et de QuarryPlus, mais les
desassemblages des chemins de Mekanism Cardboard Box et Building Gadgets 2
annonces par la phase 3 ne sont pas presents sous
`../refonte-ascendant-2026-09-04`. Sans les corps des methodes de capture,
suppression, restauration et file differee, un `MovementChecks.java` fiable
devrait deviner des API ou court-circuiter le vrai chemin. Il n'est donc pas
ecrit.

La lecture permet cependant de conclure ce qui suit :

- les tags installes ne refusent explicitement ni `quarryplus:quarry`, ni
  `quarryplus:adv_quarry`, ni `quarryplus:frame` pour ces deux outils ; ceci
  indique seulement l'absence de blocage declaratif, pas un succes de
  deplacement ;
- si le NBT complet est transporte, QuarryGuard conserve l'UUID du
  proprietaire et l'emprise absolue ; il ne les translate pas vers la
  destination et ne donne pas automatiquement la quarry au transporteur ;
- chaque travail de quarry est recontrole avec cet ancien proprietaire sur
  l'emprise absolue et sur le chunk actuel de la machine ; une perte de droit
  suspend donc le travail au prochain controle ;
- FTB controle une interaction sur la position du bloc cible. Ce controle ne
  remplace pas la garde de toute l'emprise, et les deux ne doivent pas etre
  confondus ;
- la garde des cadres ne controle que la propagation `FrameBlock.breakChain`.
  Elle suppose que le retrait ou remplacement initial a deja ete autorise par
  le chemin appelant. Une file Building Gadgets 2 qui survivrait a une
  revocation doit donc etre testee sur le vrai chemin ;
- enlever une quarry declenche aussi son `onRemove`, qui vide nativement son
  inventaire de modules si la block entity est encore visible. Capture NBT,
  drops de modules et restauration doivent etre compares ensemble pour
  exclure perte ou duplication.

## Perimetre epingle

| Composant | Version / empreinte observee |
|---|---|
| Minecraft / NeoForge | 1.21.1 / 21.1.248 d'apres la phase 4 |
| QuarryPlus | 21.1.162 d'apres la phase 4 |
| Mekanism | `Mekanism-1.21.1-10.7.19.85.jar`, SHA-256 `004DBC9F3106F4D192AEAA1EE1190DD16EC9CA8059ED3D093B80034F4C574F43` |
| Building Gadgets 2 | `buildinggadgets2-1.3.9.jar`, SHA-256 `ACAE3B628FA60AE8D34059B40C696EA282C01D20B14D32A506C4F85DD357B114` |
| `GuardHooks.java` lu | SHA-256 `9FAE12FECAF94B65E82B18564ED128DA488E82903F6C03C26CA0D2D7B57E13C0` |
| `FrameBlockMixin.java` lu | SHA-256 `B24C915F7B420CEA5C92E6E41F5C08FAAC31B159DD3FC5B51120E20EE93BC8C7` |
| `BlockItemPlacementMixin.java` lu | SHA-256 `23B3AA1EF4D8826E4A49930DDC0E2A28C18C83FFADA0E2E7A4375A3EEC759323` |
| manifeste mixins lu | SHA-256 `94C8D9190AFC92C98274CB76CE5402936D0F237365DA2432DE63E642AAF8AE21` |

Les tags effectifs lus dans les 145 JAR serveur donnent :

- Mekanism `data/mekanism/tags/block/cardboard_blacklist.json` inclut
  `#c:relocation_not_supported`, les lits, les portes, le trial spawner et le
  vault. Son propre `data/c/tags/block/relocation_not_supported.json` ne cite
  aucune quarry QuarryPlus. `config/Mekanism/general.toml` a
  `cardboard_box.modBlacklist = []`.
- Building Gadgets 2 `data/buildinggadgets2/tags/block/deny.json` refuse les
  tetes de piston, la bedrock, les cadres de portail de l'End, les candle
  cakes, lits, portails et portes. Il ne cite aucune quarry ou frame.
- Le tag commun livre par Building Gadgets 2 ne refuse que son propre
  `buildinggadgets2:render_block`. Aucun autre JAR inventorie n'ajoute un bloc
  QuarryPlus a `c:relocation_not_supported`.

Ce resultat est un inventaire declaratif. Un refus code, un evenement annule,
une incompatibilite de block entity ou un echec de restauration restent
possibles.

## Findings par severite

### P1 conditionnel : revocation pendant une file BG2

Risque de securite a reproduire, pas exploit etabli. Les classes presentes
sont :

- `com.direwolf20.buildinggadgets2.common.network.handler.PacketCut` ;
- `PacketSendPaste`, `PacketRelativePaste` et `PacketUndo` dans le meme
  package ;
- `com.direwolf20.buildinggadgets2.common.events.ServerBuildList` et
  `ServerTickHandler` ;
- `com.direwolf20.buildinggadgets2.common.blockentities.RenderBlockBE` ;
- `com.direwolf20.buildinggadgets2.common.items.GadgetCutPaste` ;
- `com.direwolf20.buildinggadgets2.util.BuildingUtils`.

Leur bytecode desassemble n'est pas fourni dans les references locales. Il
manque donc le point exact ou les droits FTB sont controles, l'identite passee
aux evenements, le moment de la capture de NBT, et surtout un recontrole par
bloc au tick d'execution apres mise en file.

La consequence potentielle est precise. Si BG2 valide le joueur au paquet,
met le travail en file, puis remplace plus tard une frame apres revocation,
`FrameBlockMixin` ne rejette pas le remplacement initial. Ce remplacement
ouvre un nouveau `FrameCleanup` avec l'etat **courant** des claims. Sa politique
est volontairement fondee sur l'equipe du claim de la frame initiale et non
sur le joueur. Elle peut donc nettoyer les frames voisines de cette equipe
alors que le joueur n'aurait plus le droit de retirer la premiere frame.

Le correctif de phase 4 invalide un contexte deja capture quand un claim
change ; cela ne couvre pas une nouvelle propagation creee apres le changement
par une ecriture externe deja en file. La verification decisive doit porter
sur la mutation initiale BG2, pas seulement appeler
`GuardHooks.mayCleanFrame`.

Attendu de securite : apres revocation, aucun bloc source, render block,
cadre voisin, item, energie de gadget ou entree de file ne doit muter. Un
simple refus de la quarry a miner ne suffit pas.

### P1 conditionnel : inventaire de modules lors du boxing/cut

`QuarryBlock.onRemove` et `AdvQuarryBlock.onRemove` appellent
`Containers.dropContents` sur `moduleInventory` lorsque le type de bloc
change et que la block entity est encore accessible, puis deleguent a
`QpEntityBlock.onRemove`. Les methodes sont visibles dans :

- `com.yogpc.qp.machine.quarry.QuarryBlock.onRemove` ;
- `com.yogpc.qp.machine.advquarry.AdvQuarryBlock.onRemove`.

Si un outil capture le NBT complet puis remplace le bloc pendant que la block
entity source reste visible, les memes modules pourraient etre a la fois dans
le transport et sous forme de drops. Si la block entity est retiree avant
`onRemove`, ils pourraient au contraire ne pas tomber. L'ordre exact des
appels Mekanism/BG2 manque ; ni duplication ni perte n'est donc affirmee.

Le test doit utiliser au moins deux modules differents, une energie non nulle,
un contenu de stockage sentinelle et compter exactement : contenu source,
drops au sol, contenu transporte, contenu restaure et stockage destination.
Une comparaison du seul owner serait insuffisante.

### P2 : identite conservee sous condition de NBT complet

Les mixins `QuarryEntityMixin` et `AdvQuarryEntityMixin` ajoutent
`ascendant_quarryguard_owner` dans `saveAdditional` via
`GuardHooks.saveOwner`, puis le restaurent a la fin de `loadAdditional` via
`GuardHooks.loadOwner`. Quarantaine et adoption suivent le meme principe.

Ainsi, un chemin qui transporte le `CompoundTag` complet conserve l'UUID A.
Le transporteur B ne devient pas proprietaire : `mayConfigure` exige A ou le
bypass. Si le champ owner manque, `loadOwner` le retire et `mayWork` refuse,
ce qui est fail-closed.

Deux exceptions doivent etre exercees et non supposees :

1. un outil peut ne transporter qu'un sous-ensemble ou des data components ;
2. un outil peut repasser par la pose normale du vrai `QuarryItem`.

Dans le second cas, `BlockItemPlacementMixin` et `confirmPlacement` peuvent
executer le preflight de pose et ecrire l'UUID du poseur B. Ce serait une
nouvelle pose autorisee, pas une preuve de conservation d'identite. Le corps
des outils est necessaire pour savoir quel chemin est reellement employe.

Il faut enfin separer identite et benefice hostile. Meme si la machine reste
a A, B peut potentiellement profiter d'une machine active deplacee en terrain
libre : elle continue a agir avec les droits de A si son ancienne emprise est
encore autorisee, et B peut tenter de recuperer ses sorties a la destination.
Ce n'est pas une attribution de droits dans QuarryGuard, mais c'est un risque
de vol par procuration. Il faut prouver que B ne peut ni declencher le travail,
ni l'alimenter utilement, ni extraire ses produits, ou accepter explicitement
ce choix de gameplay. Les chemins d'inventaire/capability externes ne sont pas
audites ici.

### P2 : emprise absolue, jamais translatee automatiquement

`QuarryEntity.fromClientTag` et `AdvQuarryEntity.fromClientTag` decodent le
champ `area` par `Area.CODEC`. Ce codec serialise `minX`, `minY`, `minZ`,
`maxX`, `maxY`, `maxZ` et la direction. `targetPos`, les iterateurs, la tete,
le stockage, les modules et le chunk loader contiennent egalement de l'etat
persistant selon le type.

`GuardHooks.loadOwner` valide l'emprise restauree et verifie qu'un
`targetPos` restaure reste dedans. Il ne translate aucune coordonnee selon la
nouvelle position du bloc. `mayWork` controle ensuite :

1. l'ancien UUID owner ;
2. tous les chunks de cette emprise absolue ;
3. le chunk de la nouvelle position de machine.

Une quarry deplacee de 64 blocs doit donc continuer a viser les coordonnees
d'origine, ou etre refusee. Affirmer qu'elle adopte naturellement une nouvelle
zone serait faux avec le code lu. L'avancee ne peut changer d'emprise que par
ses paquets proteges et dans un etat admis ; la normale n'a pas de translation
ajoutee par QuarryGuard.

### P2 : revocation de travail couverte, transport non couvert

Une mutation de claim met a jour l'index et sa revision par `ClaimsMixin` ; un
transfert de proprietaire passe par `ClaimOwnerMixin`. Le cache `Coverage`
memorise uniquement la geometrie et les IDs des equipes. `check` relit
`allowed` pour chaque equipe a chaque appel ; il ne met donc pas en cache une
permission de membre/allie/public. Les hooks de tick, de casse, d'ecriture,
de collecte et de chargement de chunks rappellent `mayWork`.

Conclusion statique : apres revocation des droits de A sur l'ancienne emprise
ou apres claim hostile du chunk destination, une machine restauree avec owner
A doit etre suspendue au prochain travail. Cette conclusion ne prouve pas que
le cut, le boxing, le paste ou l'unboxing eux-memes ont ete refuses. Ce sont
deux plans de protection differents.

### P3 : suppression des cadres et de la machine sont distinctes

Retirer une machine appelle `QuarryEntity.setRemoved` ou
`AdvQuarryEntity.setRemoved`, qui libere son `QuarryChunkLoader`. Le bytecode
lu ne montre pas de nettoyage de la structure de frames depuis ce chemin.
Le nettoyage en chaine audite appartient a `FrameBlock.onRemove ->
breakChain -> Level.removeBlock`.

Il faut donc tester separement : boxing/cut de la machine, boxing/cut d'une
frame, et paste qui remplace une frame. Un succes sur la machine ne valide pas
les cadres ; un refus FTB sur le bloc machine ne valide pas l'emprise.

## Reproduction native proposee

Ce protocole est destine au parent qui serialise les JVM. Il n'a pas ete
implemente ni execute ici. Le futur point d'entree peut etre
`MovementChecks.run(MinecraftServer)` uniquement apres lecture des corps de
methodes manquants. Il doit appeler le vrai chemin objet/paquet/file et le vrai
tick BG2 ; appeler directement `GuardHooks`, copier un `CompoundTag` ou poser
un `RenderBlockBE` a la main serait un mock et ne repondrait pas a la mission.

### Reservation spatiale

- Ancre reservee : `(7200,65,7200)`.
- Machine source proposee : `(7204,65,7215)`.
- Emprise source : X `7200..7208`, Y `65..69`, Z `7216..7224` ; couche de
  minage sentinelle a Y=64.
- Destination proposee : `(7268,65,7215)`, decalage X=+64.
- Une emprise erroneement translatee serait X `7264..7272`, Z `7216..7224` ;
  elle doit rester vide et intacte.
- Cadres differes proposes : `(7231,65,7248)`, `(7232,65,7248)` et
  `(7233,65,7248)`, places sur une frontiere de chunk.
- Volume d'observation minimal : X `7194..7286`, Y `58..75`, Z `7209..7254`,
  plus toute entite ou position que le bytecode BG2 revele avant codage.

Ces positions n'entrent ni dans les fixtures RestartChecks de
`6000/6064,65,5999`, ni dans le travail de tickets partages autour de 6800.
La reservation ne donne aucun droit de nettoyer un volume entier.

### Matrice minimale

Executer la matrice pour quarry normale puis avancee, et pour Cardboard Box
puis Cut/Paste BG2. Chaque scenario repart d'une fixture vierge.

| ID | Scenario | Assertions principales |
|---|---|---|
| M1 | A place/configure, B tente la capture dans claim A prive | Controle direct FTB refuse au meme bloc ; machine, NBT, cadres, modules, energie, sol et outil inchanges |
| M2 | A place en zone autorisee, B capture depuis un emplacement accessible puis restaure a +64 | Un seul owner attendu ; aire et cible restent absolues ; zone translatee intacte ; aucune perte/duplication de module, stockage, energie ou item |
| M3 | Comme M2, puis claim hostile a A sur ancienne emprise avant restauration | Restauration eventuelle fail-closed ; 25 ticks natifs sans travail, energie, collecte, frame, ticket ou terrain modifies ; unclaim natif puis reprise uniquement de l'ancienne emprise |
| M4 | Destination elle-meme claim hostile a A | Meme si l'ancienne emprise est autorisee, `mayWork` refuse via le chunk machine |
| M5 | Owner A conserve, machine active restauree en terrain libre par B | B ne peut configurer ; controler pourtant alimentation, sortie, ouverture, extraction et gains de B pendant un minage autorise dans l'emprise de A |
| M6 | BG2 Cut/Paste mis en file, puis revocation avant le tick de mutation | Aucun bloc source/destination, render block, frame, item ou energie ne change ; la file est annulee ou refusee sans effet tardif |
| M7 | M6 cible la premiere de trois frames connectees | Apres revocation, aucune des trois frames n'est supprimee ; verifier aussi face et diagonale |
| M8 | Cut/boxing d'une machine avec deux modules, energie et stockage sentinelles | Somme exacte avant/apres entre machine, transport et drops ; zero doublon, zero perte ; ancien chunk loader retire, aucun ticket orphelin |

Pour M2 et M3, comparer le NBT complet avant capture et apres restauration en
normalisant seulement les champs vanilla documentes comme positionnels par le
chemin de transport lu. Ne jamais retirer `area`, `targetPos`, owner,
quarantaine, adoption, etat, stockage, modules, energie ou chunk loader de la
comparaison pour fabriquer un PASS.

La preuve d'emprise doit contenir trois controles distincts : B ne peut pas
casser directement un bloc sentinelle du claim de A ; la machine peut ou non
travailler selon les droits de **A** ; la zone translatee reste intacte. Le
premier est FTB, les deux suivants sont QuarryGuard/QuarryPlus.

### Bornes et nettoyage

Reprendre `LabSupport.requireLab` avant toute mutation : serveur vide,
loopback, monde plat jetable, fil serveur et garde prete. Refuser avant la
creation d'acteurs si une position, un support, une block entity, une entite
ou un claim de la fixture n'est pas dans l'etat vierge attendu.

Le futur test doit tenir un manifeste durable distinct, par exemple
`quarryguard-movement-phase6-test.properties`, ecrit avant la premiere
mutation. Il doit enregistrer monde/dimension, scenario, positions exactes,
states initiaux, IDs des block entities, UUID/equipes, identite objet des
claims crees, entites item/render creees, contenus d'outils et phase courante.

Nettoyage autorise uniquement en succes, ou par une routine de recuperation
explicite apres inspection :

- enlever seulement les blocs encore egaux aux states poses par la fixture ;
- enlever seulement les block entities dont type et donnees sentinelles
  correspondent au manifeste ;
- enlever seulement les entites dont UUID a ete enregistre par la fixture ;
- retirer seulement un claim cree par la fixture si l'objet claim et son
  equipe sont encore identiques ;
- verifier ensuite air/absence de block entity et absence des entites
  enregistrees ;
- conserver les equipes FTB hors ligne, comme les bancs existants ;
- sur etat inattendu, refuser le nettoyage et garder le manifeste comme
  preuve. Aucun balayage ou remise a zero du volume reserve.

Chaque attente BG2 doit etre bornee par le nombre reel de ticks de sa file,
avec un plafond explicite derive du bytecode lu. Les phases de suspension de
quarry peuvent reprendre la borne existante de 25 ticks. Aucun sleep mural,
appel direct de helper ou mutation NBT ne doit remplacer l'action native.

## Evidence manquante avant codage

Produire, avec le parent qui centralise les operations Java, les
desassemblages correspondant exactement aux deux SHA-256 ci-dessus :

1. Mekanism `BlockCardboardBox`, `ItemBlockCardboardBox`,
   `TileEntityCardboardBox`, `attachments.BlockData` et leurs appels de
   sauvegarde/restauration, evenement de casse/pose et blacklist ;
2. BG2 `PacketCut`, `PacketSendPaste`, `PacketRelativePaste`,
   `ServerBuildList`, `ServerTickHandler`, `RenderBlockBE`, `GadgetCutPaste`,
   `BuildingUtils` et tout helper transitif qui effectue `setBlock`, retire la
   source, restaure une block entity ou consulte les droits ;
3. la methode exacte qui cree et consomme la file, son lien avec le joueur
   initiateur et le comportement si ce joueur se deconnecte ou perd ses
   droits ;
4. l'ordre capture NBT / retrait block entity / changement de state /
   `onRemove` / apparition des drops / restauration ;
5. le traitement de `c:relocation_not_supported`, du deny tag BG2 et des
   evenements NeoForge/FTB a chaque etape.

Sans ces elements, coder contre les seuls noms de classes risquerait de
tester une reconstruction artificielle et de produire un faux PASS. Le JAR
seul prouve que les classes existent ; il ne prouve ni leurs signatures, ni
leur ordre d'appel, ni leur politique de revocation.

## Sources locales lues

- `integration/src/main/java/fr/ascendant/quarryguard/GuardHooks.java` ;
- mixins quarry sous `integration/src/main/java/fr/ascendant/quarryguard/mixin/quarry/` ;
- `integration/src/main/resources/ascendant-quarryguard.mixins.json` ;
- `research/compatibilite-phase3.md`, `validation-phase3.md` et
  `validation-phase4-cadres.md` ;
- bytecode texte QuarryPlus sous
  `../refonte-ascendant-2026-09-04/quarry/evidence/quarry/`, notamment
  `QuarryBlock`, `AdvQuarryBlock`, `QuarryEntity`, `AdvQuarryEntity`, `Area`
  et `FrameBlock` ;
- bytecode texte FTB `dev.ftb.mods.ftbchunks.FTBChunks`, dont `blockBreak` et
  `blockPlace`, sous les memes references ;
- JAR et configurations copies dans `full-runtime`, lus sans les modifier.

Les constats de phase 3 disant que les outils transportent du NBT et que BG2
est differe sont conserves comme pistes historiques. Faute de leurs preuves
desassemblees dans l'arborescence demandee, ils ne sont pas promus ici en
certification ou en exploit reproduit.
