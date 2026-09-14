# Resultats natifs de l'integration lunaire

Serveur dedie jetable : lunar-native-lab, aucun joueur reel connecte pendant ces
essais. Les anciennes sauvegardes, la PRE-RC Prism et la production sont intactes.
Les identifiants ci-dessous sont ceux des fichiers runtime-bootstrap/*.log et JSON.

## Base et environnement

- 211409-418 : demarrage, Lune accessible, datapack d'apparitions charge, arret propre.
- 211612-754 : 10 lectures natives PASS (dimension sans air, biome et recettes).
- 212908-808 : ajout de Schematic Energistics manquant a l'intersection initiale,
  chargement du script habitat ; ce chargement ne validait pas encore son action.
- 213521-276 et 214002-632 : ECHECS des premiers essais d'evenement, alias KubeJS
  dimension puis type d'entite. Les deux ont ete corriges. Arrets propres, pas de
  joueur reel ni d'entite ajoutee ; ne pas presenter ces essais comme reussis.
- 214220-929 : filtre habitat, 8 cas via le bus NeoForge PASS. Air API temporaire
  present/absent/retire, naturel/generation/commande/spawner, passif et Overworld.
  Air temporaire retire ; ceci n'est PAS un test du distributeur alimente.

## Logistique et quarry

214220-929 : 10 commandes strictes, passed=true, exitCode=0, forced=false,
cleanExit=true. Les chunks forces pour les essais ont ete liberes.

- QuarryGuard RC3 exact, SHA256
  `2469A27765E1493FA0CA91083D72C9069BC0926E67A5E03345A17F72A3198787`,
  reprend le binaire valide du projet D:/Programmation/Projets/ascendant-quarryguard.
  Versions natives correspondantes : QuarryPlus21.1.162, FTB Chunks2101.1.21,
  FTB Teams2101.1.10. Readiness observee ; aucun nouveau test hostile de claim ici.
- Logistique0.1.0, SHA256
  `EFE98C6D60C38D6ACE59DC8482FBE43116677A8DBAC3AB0F0DB8F1AF44FAE5E5`,
  explicitement activee par fichier de configuration.
- Fixture f014e12e-461b-45c0-92b1-7f6eb1ccc13a, 26 blocs nouveaux, centres
  Overworld et Moon (8,310,8), conservee dans le laboratoire.
- Ponts AE2 reellement formes/alimentes et partenaires enregistres : liaison
  interdimensionnelle coupee dans les deux sens.
- QE : frequence native sous-jacente partagee, getter lunaire ferme et conteneurs
  items/fluides/chimiques/energie/chaleur fermes sur les six faces.
- QIO/Powah : decisions de garde sur endpoints observes, pas de transfert natif
  de stock/energie qualifie par ce premier diagnostic.
- Regle independante des grants confirmee ; aucun grant historique modifie.

## Equipements et inventaires

215604-689 : 7 commandes strictes, passed=true, arret propre sans terminaison forcee.
Extra restrictions0.1.0, SHA256
`50FEC7DD7732CDABE5C9EA113B3F8D60E432A63CC36A2CB0F4FAC2D9F2A35B10`,
configuration active. Classes cibles chargees dans le loader natif, dont l'interface
AE2AddonLib ; pas seulement relues sous forme de bytecode.

16 assertions natives PASS avec profils FakePlayer nouvellement crees et non
connectes (aucun inventaire de joueur reel utilise) :

- Validite des menus EnderChest refusee sur Moon, controle positif Overworld.
- Ender Collection Draconic : refus unitaire et en lot, quantites et energie
  conservees ; insertion positive de 4 diamants dans le coffre fictif Overworld.
- Building Gadgets, capacite liee : refus Moon/Overworld dans les deux sens,
  puis extraction locale positive sur chacun des deux mondes.
- Deux coffres de fixture conserves aux centres (80,310,8), chacun avec 31 diamants
  de test. Chunks forces ensuite liberes. Ne pas reutiliser comme fixture neuve.
- Appel direct de cellule spatiale sur Moon refuse, composants et quantite de
  cellule inchanges. Pas de test complet d'un Spatial IO Port dans cet essai.
- Script de refus precoces de sorts/creation Nether charge ; ses evenements
  specifiques n'ont pas encore ete declenches par cet essai.

## Ressources locales

221402-646 : 5 commandes strictes, passed=true, exitCode=0, forced=false.
43 assertions natives PASS sur 16 nouveaux chunks lunaires : les 16 features
et les deux recettes payantes sont enregistrees. Les palettes contiennent
notamment charbon, cuivre, redstone, zinc, osmium, pierre, andesite et gres,
ainsi que les autres minerais natifs observes dans le journal.
La commande native locate trouve une meteorite AE2 a (1568,1744), a 147 blocs
du point teste. Cela prouve sa localisation, pas encore la recolte des presses.
Les 16 chunks forces ont ete liberes et le serveur sauvegarde puis arrete.
Les essais 215943-163 et 220345-461 avaient echoue dans le helper JavaScript
(lecture des Holder de biome), pas dans la generation. Le helper utilise
desormais les registres/palettes et la localisation native separee.
Aucun ancien chunk de la sauvegarde utilisateur n'a ete regenere.

## Sorts et machines alimentees

221823-171 : resultat global FAILED (attente COMPLETE_PASS expiree), mais arret
de nettoyage propre, exitCode=0, forced=false. Ne pas annoncer ce protocole PASS.

- 36 assertions sur le vrai bus NeoForge PASS : sept sorts de transport/coffre
  Iron et deux effets Ars refuses sur Moon et Moon orbit, controles Overworld
  et sorts non concernes preserves. Aucun sort reellement execute par un joueur.
- Helper jetable uniquement, SHA256
  `BDD50795B248905716EFFA722BA862DBD05D402085713CB15923324955D5FEF7`.
- Fixture `1d8ca1c9-a299-43f0-9046-e8d54658819e`, origine Moon (2,200,2),
  103 nouveaux blocs dans un volume verifie vide, conservee avec son manifeste.
  Entrees par les slots natifs, energie finie explicitement injectee pour TEST.
- Laiton : deux lingots Create, cuivre/zinc consommes, 8 000 FE depenses.
- Acier : un lingot **Mekanism** et 2 000 FE depenses. Almost Unified change
  la sortie effective ; le helper attendait le lingot Ad Astra et refuse donc
  COMPLETE_PASS. Correction du test a partir de la recette chargee requise.
- Distributeur : eau consommee, seau vide rendu, air present dedans et absent
  dehors. Apres extraction TEST unique de l'energie, air retire par le tick natif,
  sans ecriture d'air par le helper ; 40 mB d'oxygene restaient disponibles.
- Un chunk Moon [0,0] reste force car le protocole a abandonne les commandes
  suivantes. A liberer au prochain essai. Pas de reapprovisionnement de la fixture.

### Essai corrige

222504-001 : 9 commandes strictes PASS, arret propre, exitCode=0, forced=false.
Nouveau helper `DD08AA23744347EDC2092F3D20D13446F69BAB8F0994E2D7C346F72AACD2E423`,
nouvelle fixture `4d428f44-4865-457e-b012-27e0cceb188f` a Moon (2,220,2).
Aucun ancien lot reapprovisionne. COMPLETE_PASS=true : sortie acier issue de
la recette effective capturee, exactes quantites et depenses d'energie, air
alimente puis retire nativement. La recette de compression accepte cet acier ;
six recettes aval acceptent la plaque attendue (lectures d'ingredients seulement,
pas une execution du compresseur). Le chunk [0,0] a ete libere.
Les 36 cas de sorts repasses avec succes.

Ce boot charge aussi extra restrictions0.1.1
`387845AE8F70522C2954568C078B066BFEF42D732C2D50C3DCABC5FE58A033B9`
et recall0.1.0 `BE0BFD9D6BE1092BFFA38FFA5EE06C27BBF52205A797FB4F19214AE6189249B9`,
tous deux actifs. Leurs nouvelles routes ne sont pas toutes exercees par ce test.
Les six ajouts de parite serveur de server-parity-additions.json ont demarre
ensemble ; cela ne prouve pas encore leurs interfaces reseau avec un vrai client.

## Distribution commune et cuisson Etrionic

223347-602 : 15 commandes strictes PASS, exitCode=0, forced=false, cleanExit=true.
Les 39 JSON sont charges depuis kubejs/data ; plus de datapack lunaire ajoute
directement au monde du laboratoire. Le garde Etrionic
`097DE85FB38B7ACDB9F5E834B47D8AD5D861197E25D6AB5F3454F85C46DE232E`
est actif. Helper non livrable
`84234F7BD454F48AB0D35CFD3C21E8C34AC1D68A704C0E03BAFA2944C2A00F60`.

- Fixture BLASTING `daaaef1e-f16a-4210-ac91-bb322875c0f1`, Moon (2,240,2) :
  quatre fers bruts dans quatre slots natifs, 10 000 FE, 159 ticks observes,
  159 vetoes, slots et FE inchanges, aucune sortie. BLASTING_VETO_PASS=true.
- Nouvelle fixture ALLOYING (18,220,2) : COMPLETE_PASS=true pour acier, laiton
  et air alimente puis retire. Les anciennes fixtures sont inchangees.
- Nouveau lot de 16 chunks [200..203,200..203] : 43 assertions ressources PASS.
  Meteorite native localisee a (3440,3104), pas de recolte simulee des presses.
- Les 18 chunks forces pour ces essais ont ete liberes avant sauvegarde/arret.

Le garde neutralise seulement la cuisson Etrionic, dans toutes les dimensions
de cette RC isolee. Il ne corrige pas le code fournisseur et ne cache pas le
bouton natif. Les autres fours restent utilisables ; ALLOYING reste native.

## Portee

Ces preuves sont des tranches de qualification, pas une validation globale RC.
Restent notamment le vrai aller-retour en fusee et le fret, les menus d'un joueur
connecte, les liens energetiques d'armure, les autres portails/rappels, le cycle
industriel alimente, le premier combat et l'integration complete des quetes.
Les essais ulterieurs doivent citer leurs propres hashes et logs, sans heriter
silencieusement du resultat d'un binaire precedent.

## Addendum : essais termines 224711 a 230733

Ajout documentaire depuis les quatre couples existants
`lunar-native-lab/runtime-bootstrap/smoke-20260913-<identifiant>.log.json` et `.log`.
Les preuves precedentes, notamment 223347-602, restent inchangees. Le run
231616, encore en cours lors de cette demande, n'est ni lu ni qualifie ici.

Les QUATRE protocoles ci-dessous ont `passed=false`, `strictMode=true`,
`exitCode=0`, `cleanExit=true`, `forced=false`. Un arret de nettoyage propre ne
transforme pas leur sequence incomplete en PASS : le `stop` planifie a ete saute,
puis le controleur a envoye son arret de nettoyage. Les commandes sautees ne sont
pas des tests executes, meme si leur module a correctement demarre.

| Run | Commandes envoyees / prevues | Etape bloquante |
| --- | --- | --- |
| 224711-913 | 1 / 9 | Cargo : classe du seau de carburant refusee. |
| 225518-908 | 3 / 17 | Recall QA : premier controle Robit divergent. |
| 225914-988 | 3 / 14 | Helper de survey : refus annonce avant resultat natif tardif. |
| 230733-203 | 6 / 16 | Cargo : composant Apotheosis de pioche refuse. |

### 224711-913 : cargo 0.0.3 refuse le kit

- Le journal charge travel0.0.3 ; `lunar_cargo fixture` echoue sur
  `CARGO_CHANGED_BUCKET_CLASS @ fixture:6 [ad_astra:fuel_bucket]` (log ligne1140).
- Le JSON constate `STEP_RESULT_MISSING_BEFORE_ACK index=0`, puis abandon.
  L'absence de `commandErrors` dans ce JSON ne supprime pas le FAIL cargo explicite
  affiche au niveau INFO par le serveur. Aucun bilan natif de 27 cas PASS.
- Recall, survey/site et forceload prevus ensuite sont sautes. Aucun site construit
  ni chunk nouvellement force par ces commandes dans cet essai.
- L'erreur Tectonic `fabric:overlays` ligne881 est deja presente en baseline ;
  elle n'est pas le motif d'abandon cargo. Note separee :
  `lunar-survival-candidate/TECTONIC_METADATA_AUDIT.md`. Tectonic reste conserve.

### 225518-908 : chargement extra positif, recall incomplet

- `lunar_extra_qa load` PASS : cibles/hooks charges dans le runtime natif pour
  extra0.1.1, productionSHA
  `387845AE8F70522C2954568C078B066BFEF42D732C2D50C3DCABC5FE58A033B9`.
  Cela ne remplace pas `lunar_extra_qa run`, saute plus loin dans le protocole.
- `lunar_recall_qa check` PASS, mais `cases=0` : disponibilite/controle initial,
  pas un cycle de rappel ou de recuperation valide.
- `lunar_recall_qa all` FAIL, run `9efd5f07-5f46-439e-b22c-027d5ab61903`,
  `completed=0`, premier cas Robit Overworld -> Overworld, `denied=false`.
  Le hash NBT avant/apres differe. Les scalaires affiches restent identiques
  (Robit12345 FE, portable2000 FE, stocks/cooldown affiches inchanges) : conserver
  l'echec de comparaison sans inventer une perte d'objets ou d'energie demontree.
- Audit quetes, tests extra comportementaux, site et forceload suivants sautes.
  Logs de preuve : lignes1192,1198,1205-1207.

### 225914-988 : quetes structurelles PASS, survey global FAILED

- Audit quetes natif : `checks=59 quests=20 tasks=27 progressWrites=0
  realCraftTest=false`. Structure/lecture des taches validees, pas de progression
  accordee, craft reel ou parcours d'un joueur connecte.
- 25 chunks Moon [30,30]..[34,34] marques forces. Survey sans ecriture :
  centre(512,108,512), minSurface98, terrainAccess(482,108,480), 4225 colonnes.
- Le helper annonce `Native encounter preflight refused` et le controleur abandonne.
  Ensuite seulement, le journal affiche le resultat natif `Preflight PASS` pour
  4144 ajouts exclusivement dans l'air (lignes1187-1192). Ce resultat tardif est
  une preuve partielle de preflight, pas un PASS retroactif du helper/protocole.
  Aucune construction du site n'a ete executee dans cette sequence.
- La liberation des 25 chunks a ete sautee ici ; elle sera explicitement executee
  dans 230733. Cargo et tests extra suivants sautes : aucun resultat cargo27PASS.

### 230733-203 : site construit/valide, cargo Apotheosis FAILED

- Encounter0.1.2 : `healthy=true`, site `lunar_relay_01`, centre(512,108,512),
  `runs=0`. Preflight natif PASS puis `build_site confirm` reussi : **4144 blocs
  ajoutes dans l'air, aucun bloc remplace**. `validate` confirme geometrie/claims
  actuellement valides. Ce sont des preuves du site, PAS d'un combat, de vagues,
  de recompenses ou d'une completion joueur.
- Les **25 chunks** Moon [30,30]..[34,34] sont explicitement liberes avant cargo.
  Le journal demande de desactiver `allowSiteBootstrap` au prochain redemarrage ;
  aucune nouvelle construction ne doit reutiliser ce site comme volume vierge.
- Travel0.0.4 est charge, mais `lunar_cargo fixture` echoue sur
  `CARGO_APOTH_GEM_PAYLOAD @ fixture:0 [minecraft:diamond_pickaxe]` (ligne1183).
  JSON : `STEP_RESULT_MISSING_BEFORE_ACK index=5`, puis commandes restantes sautees.
  **Pas de 27 cas cargo PASS** et pas de qualification du vrai vol/fret.
- Tests extra comportementaux et nouvel audit quetes suivants non executes.
  Les precedentes preuves de quetes restent attribuees a leur propre run.
  Preuves site/cleanup : JSON steps0..4 et log lignes1161-1179.

### Limites pour le packaging parent

- Conserver separement les PASS de 223347 (industrie/O2/guard), le PASS structurel
  quetes de 225914 et les PASS site de 230733 ; aucun ne valide cargo ou recall.
- Ne pas promouvoir travel0.0.3/0.0.4 en cargo qualifie sur ces essais. Un correctif
  compile ulterieur doit avoir sa propre preuve native terminee et son hash fige.
- Ne pas inclure les helpers QA, commandes de seed ou bootstrap de site actif
  comme fonctionnalites normales de la RC. Packaging et decisions d'activation
  restent au parent ; cet ajout documentaire n'installe ni ne modifie ces modules.
- Les versions citees proviennent des logs de chaque run. Ne pas substituer le
  hash d'un JAR courant du laboratoire a un binaire historique non capture ici.
  Aucun resultat du run231616 incomplet n'est anticipe dans ce bilan.

## Addendum : 231616 et 231911 maintenant termines

Lecture des couples `runtime-bootstrap/smoke-20260913-231616-699.log.json/.log`
et `smoke-20260913-231911-741.log.json/.log` apres leur arret. La reserve du
precedent addendum concernait le moment ou 231616 etait encore en cours ; aucun
resultat historique n'est reecrit. Seul ce document est modifie.

### Rappel court : Tectonic conserve

L'erreur de section `fabric:overlays`/`tectonic:config` existe deja en 211409 et
223347, avant 224711. Le log211409 liste ensuite Tectonic parmi les datapacks
actives. Le JAR3.0.26 et la configuration du lab sont identiques a PRE-RC,
`mod_enabled=true`. La condition est enregistree cote NeoForge ; le lecteur de
metadata rejette la section Fabric, pas le pack entier. Aucune nouvelle
desactivation identifiee ; pas de nouveau test comparatif du relief Overworld.
Preuves detaillees : `lunar-survival-candidate/TECTONIC_METADATA_AUDIT.md`.
Ne pas retirer Tectonic pour faire disparaitre ce message.

### 231616-699 : sept assertions Ars PASS, puis frame Iron FAIL

Resultat global **FAILED** : JSON `passed=false`, `strictMode=true`, 4/10 commandes
envoyees, `exitCode=0`, `cleanExit=true`, `forced=false`. Nettoyage par arret apres
abandon ; les commandes de liberation, audit quetes et sauvegarde planifiee sont
sautees. Ne pas assimiler l'arret propre a la reussite de `lunar_extra_qa run`.

- Les trois commandes initiales ont force le chunk **[5,0]** (bloc88,8) dans
  Overworld, Moon et Moon orbit. Les preflights QA de volumes vides reussissent.
- Sept lignes `PASS ARS minecraft:overworld -> ad_astra:moon` (log1222-1228) :
  refus du scroll avec compte/composants/position/menu preserves ; drop inchange ;
  refus avant mise en file ; liaison du portail inchangee ; warp de tuile existante
  inchange ; teleportation terminale inchangee ; creation de portail refusee.
  Ce sont sept assertions de cette direction, pas sept matrices de dimensions
  completes ni des actions d'un joueur reel.
- Puis echec explicite : `IRON minecraft:overworld -> ad_astra:moon frame before
  cooldown/position/items` (log1230), `FAIL command=run`. L'assertion frame Iron
  n'est pas qualifiee ; aucune conclusion inventee sur l'objet ou l'etat exact
  modifie au-dela du message disponible. Les scenarios suivants sont interrompus.
- Les **trois force-loads [5,0] restent inscrits a la fin de cet essai**, leurs
  commandes de retrait etant sautees. Un arret du serveur ne les supprime pas.

### 231911-741 : Recall QA0.1.1, deux domaines positifs et echec Mek

QA chargee : `ascendant_lunar_recall_qa` **0.1.1-lab-only** (log444).
Run QA `811c8993-1a73-442f-8bd1-1ca717d1f454`. Les domaines independants ont ete
executes malgre le premier echec, puis le bilan a echoue : `Failed domains=[mek]`,
`completed=78`. JSON global **FAILED**, 1/7 commandes envoyees, `exitCode=0`,
`cleanExit=true`, `forced=false`. L'arret de nettoyage est confirme, pas suppose.

- `scroll` : **32** controles completes, `DOMAIN_RESULT scroll status=PASS`
  (log1207-1208). Refus natifs DeathScroll testes et controles autorises limites
  au garde quand le journal dit `gate-only` ; `trips=0`.
- `claim` : **46** controles completes, `DOMAIN_RESULT claim status=PASS`
  (log1255-1256). Evenements/chemins de refus GraveComponent testes ; controles
  locaux/hors Lune `gate-only` sans claim force ; `trips=0`.
- `mek` : FAIL des le Robit Overworld -> Overworld, `denied=false`,
  `completed=0 trips=0`. L'EXACT_DIFF (log1172) isole
  `$["nbt"]["robit"]["NeoForgeData"]` : **absent -> {}**.
  Energie, stocks et cooldown affiches restent identiques. Le diagnostic transmis
  par le parent/worker est une initialisation paresseuse sans rapport avec une
  perte de stock, et un correctif du comparateur QA est en cours. Le diff natif
  est conserve ; ni le correctif ni le domaine Mek ne sont declares PASS ici.
- Le motif attendu configure par le parent etait `PASS all cases=138` (JSON
  step0), signale errone. C'est un probleme distinct du vrai `RECALL QA FAIL` :
  corriger ce pattern ne ferait pas reussir l'essai, qui echoue explicitement sur
  Mek avant le bilan global. **78 controles partiels, pas 138 PASS.**
- Les retraits des trois force-loads [5,0] sont encore sautes dans cette sequence.
  Ils restent donc a liberer a la cloture des deux journaux examines. Aucune
  liberation ulterieure n'est anticipee ; aucun nouveau forceload n'est ajoute ici.

Pour le packaging : conserver Ars7, scroll32 et claim46 comme preuves partielles
avec leurs limites. Ne pas annoncer extra-run ou recall-all qualifies. Conserver
le suivi des trois force-loads jusqu'a une preuve de retrait explicite par le
parent ; aucun serveur, inventaire, fixture ou correctif n'a ete modifie ici.

## Addendum : 232628-734 Recall corrige et nettoyage PASS

Verification du JSON et du log termines
`runtime-bootstrap/smoke-20260913-232628-734.log.json/.log` : **passed=true**,
`strictMode=true`, **7/7 etapes succeeded**, `exitCode=0`, `cleanExit=true`,
`forced=false`, aucune failureReason. Sauvegarde explicite puis stop planifie
reussis, contrairement aux arrets de nettoyage des deux essais precedents.

- Helper **Recall QA0.1.2-lab-only**, SHA256 complet verifie dans le lab et
  correspondant au prefixe fourni par le parent :
  `2A6839FA8BA6FE2F8B867AE6C48FF8FE8CDE89068CC6B102CD0E1173288F1365`.
- **Le module Recall de production reste inchange**, version0.1.0 candidate,
  SHA256 `BE0BFD9D6BE1092BFFA38FFA5EE06C27BBF52205A797FB4F19214AE6189249B9`,
  identique a la preuve precedente. La correction concerne le helper QA ; aucune
  extension de la politique de rappel ou nouvelle autorisation de transport.
- Run QA `87fdd082-aa9d-44f0-b7a2-c013e00db9f2` : **138 controles natifs PASS**,
  repartis en Mek60, scroll32 et claim46. Trois DOMAIN_RESULT PASS observes
  (log1327-1328,1361-1362,1409-1412). Les anciens echecs restent documentes.
- Portee exacte du bilan : `native boundary probes only, not block/portable
  packet or live recovery qualification`. `trips=0` dans chaque domaine. Ce PASS
  ne prouve pas un voyage/retour reel, un paquet joueur portable/bloc ou une
  recuperation complete en jeu ; il ne valide pas non plus le cargo Apotheosis.
- **Les trois force-loads [5,0] ont bien ete retires**, successivement dans
  Overworld, Moon et Moon orbit (JSON steps1..3). Le reliquat de 231616/231911 est
  donc resolu par une preuve explicite, sans modifier leurs anciens resultats.
- Quetes : **59 controles PASS**, 20 quetes, 27 taches, `progressWrites=0`,
  `realCraftTest=false` (step4). Pas de progression artificiellement accordee.

Packaging : conserver Recall0.1.0 et sa portee de production ; exclure le helper
QA0.1.2 des fonctionnalites livrees. Cet ajout modifie seulement NATIVE_RESULTS,
sans installation, boot, correctif ou action sur les chunks par ce worker.

## Addendum : 232835 partiel et 233045 cargo33 PASS

Couples JSON/log termines lus dans runtime-bootstrap :
`smoke-20260913-232835-605` et `smoke-20260913-233045-754`.
Les historiques precedents, dont les echecs cargo0.0.3/0.0.4, restent inchanges.

### 232835-605 : extra QA0.1.1 avance, controle Create positif en echec

Resultat global **FAILED**, 4/9 commandes envoyees, `exitCode=0`,
`cleanExit=true`, `forced=false`. Les retraits de chunks planifies ont ete sautes,
avant arret de nettoyage. Ce run avait de nouveau force [5,0] dans Overworld,
Moon et Moon orbit, apres leur liberation lors de 232628.

- Helper `lunar-extra-qa-0.1.1-lab-only`. Les refus Ars et Iron avances affichent
  PASS, notamment le frame Iron Overworld -> Moon qui bloquait l'essai precedent.
  Le journal comprend maintenant controles NO_OPERATION, vraie paire liee,
  selection/insertion du candidat et preservation des stocks/composants.
- Controles hors Lune positifs Ars/Iron executes : scroll consommant une unite,
  teleportation terminale, evenement de portail mis en file puis retire par QA,
  cooldown du frame Iron et traitement natif du candidat. Le FakePlayer supprime
  le mouvement reseau du scroll ; ne pas annoncer un trajet de joueur connecte.
- Create : controles negatifs Moon/Moon orbit PASS avant provider/creation de
  voie/liaison, voie d'entree conservee, callback/dispatch hors Lune executes et
  pas de variation de compte graphe/train pendant les operations refusees.
- Echec du controle positif suivant (log1430-1433) :
  `CREATE OFFWORLD native track creation and reciprocal binding positive
  (controlled same-world exit)`. Le fixture de voie/liaison positive n'est donc
  pas valide. Le parent signale sa correction par le worker ; **ce log ne prouve
  pas un bug du module de production** et aucun correctif ulterieur n'herite d'un
  PASS. Ne pas presenter l'ensemble `lunar_extra_qa run` comme reussi.

### 233045-754 : travel0.0.5, fixture cargo33 et nettoyage valides

JSON confirme **passed=true**, `strictMode=true`, **6/6 etapes succeeded**,
`exitCode=0`, `cleanExit=true`, `forced=false`, aucune failureReason.

- Travel **0.0.5 candidate**, SHA256 fourni par le parent puis verifie sur le JAR
  correspondant du lab :
  `A416643D2ABE2E94348B86FEA7C1FABCB43140A08B511C2C5F199B0D655A649F`.
- `lunar_cargo fixture` : **CARGO NATIVE FIXTURE PASS 33 cases**. Kit reel plus
  quatre pieces de combinaison acceptes ; matieres premieres repetees/sac
  industriel refuses ; stacks du fixture inchanges. Aucune mutation du joueur
  ou du monde par ce diagnostic. Ce sont 33 cas natifs sur ce binaire, pas une
  requalification retroactive des anciens lots de 27 cas en echec.
- Les **trois force-loads [5,0]** recrees par 232835 ont ete retires dans
  Overworld, Moon et Moon orbit (steps1..3), puis `save-all flush` et `stop`
  ont reussi. Nettoyage de ce reliquat maintenant explicitement demontre.
- Limite : fixture cargo positif, mais pas un aller-retour reel en fusee, un
  veto de vol avec recuperation du joueur, une reprise de session interrompue ou
  une approbation universelle des equipements/modules. L'echec extra/Create
  precedent reste independant de ce PASS cargo.

Pour l'assemblage parent : rattacher la preuve cargo33 au hash0.0.5 ci-dessus,
conserver les limites extra QA et exclure les helpers de qualification du pack
joueur. Aucune modification de production, installation ou execution ici ; seul
NATIVE_RESULTS est complete apres verification de l'arret des deux runs.

## Addendum : 233426-869, premier runtime propre RC1

Preuve distincte du lab historique : fichiers termines
`lunar-clean-runtime/runtime-bootstrap/smoke-20260913-233426-869.log.json/.log`.
JSON : **passed=true, strictMode=true, 9/9 etapes succeeded, exitCode=0,
cleanExit=true, forced=false**, aucune failureReason. Les helpers QA ne sont pas
livres dans RC1 ; le manifeste scelle contient six scripts JS et 39 JSON data.
Le log confirme 2/2 startup scripts et 4/4 server scripts, sans erreur/warning.

- Encounter0.1.2 healthy=true, runs=0 (log1163). Moon (512,108,512) : preflight
  4144 ajouts air-only, puis construction de 4144 blocs sans remplacement et
  validation geometrie/claims PASS (1171,1175,1180).
- Les 25 chunks Moon [30,30]..[34,34] ont ete liberes (1184), puis sauvegarde
  explicite et stop reussis. Pas de reliquat de force-load pour cette operation.
- **Cargo33 PASS** sur ce runtime propre (1188) : kit+4 pieces de combinaison
  acceptes, nouvel apport brut/sac industriel refuse, stacks inchanges.
- **Pas de qualification combat**, vagues, recompenses ou joueur reel ; pas
  d'aller-retour fusee. Cette preuve ne remplace pas les limites des fixtures.

Etat suivant ce run, lu sans modification : le serverconfig du monde propre
`renaissance-rc1/serverconfig/lunar-encounter.properties` contient maintenant
`allowSiteBootstrap=false`. La source `lunar-qualification/kubejs-web-disabled.json`
et le `kubejs/config/web_server.json` du runtime propre ont `enabled=false`, sans
champ d'authentification. Selon le parent, cela remplace le defaut web KubeJS
active avec authentification aleatoire ; aucune valeur d'auth n'est reproduite.
Ce durcissement est **posterieur au run**, son redemarrage de preuve est attendu.
Le COMPLETE/manifeste SHA256 `5D26FD95231D106940504914414996B4C81E39E0F71DCDC8F5919EB485074E05`
consulte ne reference pas encore cet overlay web : ne pas attribuer sa couverture
au seal initial ni annoncer le nouveau seal avant verification parent.

## Addendum : 233723-945, extra QA0.1.2 termine PASS

JSON/log termines de `lunar-native-lab/runtime-bootstrap/smoke-20260913-233723-945` :
**passed=true, strictMode=true, 9/9 etapes succeeded, exitCode=0,
cleanExit=true, forced=false**, aucune failureReason.

- Helper `lunar-extra-qa-0.1.2-lab-only`, nonshipping. Module de production
  extra restrictions **0.1.1 inchange**, SHA256 annonce par le marqueur natif
  `387845AE8F70522C2954568C078B066BFEF42D732C2D50C3DCABC5FE58A033B9`
  (log1440), identique au manifeste RC1.
- `PASS LOAD_TOTAL targets=26 injectors=38 accessor=1` (1208) est une preuve de
  transformation, **pas 38 tests comportementaux supplementaires**.
- `PASS BEHAVIOR_TOTAL checks=196; no real player used; no spell event-bus
  duplication` puis `PASS command=run` (1439-1440). Ars/Iron : refus lunaires,
  conservation des stocks/composants et controles natifs hors Lune positifs.
- Create : refus Moon/orbit avant creation/liaison, voie d'entree conservee,
  controles de callback/provider et absence de variation graphe/train lors des
  refus. Le controle positif precedemment en echec passe maintenant : creation
  native des voies et liaison reciproque, **sortie controlee dans le meme monde**
  (1431-1432). Provider original restaure, fixtures nettoyes dans les trois mondes
  (1433-1436). L'ancien echec 232835 reste historique, pas requalifie retroactivement.
- Migration preflight : trains=0, straddling=0, lunarDirectedEdges=0, aucune
  suppression (1437-1438). Ce monde vide de trains ne prouve pas une migration
  de trains existants, un trajet complet ou un transport de joueur reel.
- Les trois chunks [5,0] OW/Moon/orbit ont ete liberes (1444,1448,1452), puis
  sauvegarde et arret propres. Aucun helper ajoute au payload par cette preuve.

Seuls les documents sont mis a jour ici. Le redemarrage final du runtime propre
apres durcissement web/bootstrap reste une preuve separee encore attendue.

## Addendum : 233916-260, redemarrage propre durci PASS

JSON/log termines verifies dans
`lunar-clean-runtime/runtime-bootstrap/smoke-20260913-233916-260.log.json/.log` :
**passed=true, strictMode=true, 7/7 etapes succeeded, exitCode=0,
cleanExit=true, forced=false**, aucune failureReason. Cette preuve clot l'attente
du redemarrage indiquee dans les deux addenda precedents.

- Configuration du monde propre relue : `allowSiteBootstrap=false`. Apres
  redemarrage, encounter healthy=true, runs=0, meme site (512,108,512), puis
  geometrie/claims valides sans modification du terrain (log1145,1153).
  Aucun nouveau build, combat, vague ou recompense n'est qualifie.
- Les 25 chunks Moon [30,30]..[34,34] charges pour la validation ont ete liberes
  (1157). Cargo33 PASS (1161), sauvegarde explicite puis stop reussis.
- KubeJS : **2/2 startup et 4/4 server scripts**, chacun 0 erreur/0 warning
  (718,962). Le fichier web du runtime conserve `enabled=false` apres restart.
  KubeJS a ajoute une authentification locale au fichier genere ; sa valeur
  n'est pas reproduite ici et n'appartient pas a la source overlay sans auth.
  Ne pas distribuer ce fichier runtime genere a la place de la source.

Etat de livraison : parent effectue encore le preflight final des hashes et de
l'historique joueur vide avant ZIP/installation Prism. Aucun ZIP, installation
ou historique joueur vide n'est certifie par ce run. Le manifeste initial
`5D26FD...` relu ne contient toujours pas l'overlay web ; sa revision et la
livraison effective restent a confirmer separement. RC testee par ces tranches,
pas qualification gameplay complete. Aucune action runtime realisee par ce worker.
