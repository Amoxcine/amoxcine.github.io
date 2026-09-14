# QuarryGuard : correction A du nettoyage des cadres

Date : 4 septembre 2026, heures Europe/Paris.

**Decision A validee par l'utilisateur et implementee dans le laboratoire.**
Le serveur des joueurs, le client, Packwiz et GitHub ne sont pas modifies.
Ce document ne donne pas d'autorisation de deploiement.

## Regle de jeu

Une frame est un bloc du cadre orange de la quarry. Casser une frame peut
declencher le nettoyage automatique des frames voisines, meme en diagonale.
Le correctif limite ce nettoyage selon le claim du premier bloc :

| Point de depart | Destination du nettoyage | Resultat |
|---|---|---|
| Hors de tout claim | Hors de tout claim, par un chemin autorise | Autorise |
| Hors de tout claim | N'importe quel claim, meme celui du joueur | Refuse |
| Claim de l'equipe A | Autres claims de A | Autorise |
| Claim de l'equipe A | Terrain non claim | Autorise |
| Claim de l'equipe A | Claim de B, meme PUBLIC ou allie | Refuse |
| Protection indisponible ou claim modifie pendant l'operation | Suite du nettoyage | Arretee |

La chaine ne saute pas par-dessus une portion interdite pour continuer de
l'autre cote. Les frames laissees dans un claim peuvent etre cassees depuis
ce claim, avec les droits FTB habituels. Le droit de casser le premier bloc
n'est pas change. Aucun joueur ou proprietaire de quarry n'est devine a
partir du claim. Les anciennes frames n'ont pas besoin d'une migration NBT.

## Statuts

| Element | Statut |
|---|---|
| Regle A | Valide par l'utilisateur |
| Implementation sur versions epinglees | Realisee, compilation Java 21 reussie |
| Onze scenarios natifs de cadres | Valide dans le laboratoire complet |
| Invalidation des autorisations entre deux controles | Valide par tests du helper avec vrais claims |
| Regression de pose, minage, collecte, marqueurs et cadres | Valide dans le laboratoire complet |
| Reprise et anciennes machines sur le nouveau binaire | Valide : quatre lancements successifs, deux redemarrages |
| Vrais clients, serveur a huit joueurs, charge prolongee | A tester |
| Installation en production | Reserve : pas encore validee ni effectuee |

## Binaire et preuves

```text
Build   classes-20260904162752354
JAR     ascendant-quarryguard-0.1.0-lab.jar
SHA-256 5A4603FAB6A27CD250AA0031B245D68084AF8756466755E419E21EF13B583158
```

Binaire, paire de construction et sources archives dans
`integration/build/phase4-5a46/`. Le JAR reste un prototype de laboratoire.

Profil : copie des 145 JAR serveur plus le prototype, Minecraft 1.21.1,
NeoForge 21.1.248, QuarryPlus 21.1.162, FTB Chunks 2101.1.21,
FTB Teams 2101.1.10, Java 21.0.7. Monde plat jetable, aucun joueur connecte,
plafond Java 4 Gio, une JVM a la fois, ports uniquement loopback.

| Essai | Resultat | Journal |
|---|---|---|
| Frames et autorite | PASS 18:29:05 | [Preuve](../results/runtime-full-runtime-frames-baseline0-20260904-162819.log) |
| Regression complete avec cadres | PASS 18:30:25 | [Preuve](../results/runtime-full-runtime-regression-baseline0-20260904-162938.log) |
| Preparation de reprise | PASS 18:31:19 | [Preuve](../results/runtime-full-runtime-restart-prepare-baseline0-20260904-163039.log) |
| Reprise utile, nouvelle JVM | PASS 18:32:06 | [Preuve](../results/runtime-full-runtime-restart-check-baseline0-20260904-163121.log) |
| Preparation anciennes machines | PASS 18:32:48 | [Preuve](../results/runtime-full-runtime-legacy-prepare-baseline0-20260904-163207.log) |
| Anciennes machines, nouvelle JVM | PASS 18:33:32 | [Preuve](../results/runtime-full-runtime-legacy-check-baseline0-20260904-163250.log) |

Les empreintes et parametres sont dans les fichiers `run-*.json` associes
aux journaux. Les six lancements utilisent exactement le meme binaire,
sauvegardent et quittent avec code 0, sans terminaison forcee. Les deux
manifestes persistants se terminent en `stage=checked`. Aucun serveur de
laboratoire n'est laisse en fonctionnement.

La reprise retrouve proprietaires, emprises, cibles, stockage et energie.
Apres retrait du claim adverse, la quarry normale mine un nouveau bloc en
4 ticks natifs et l'avancee en 1 tick, chacune avec un nouveau drop stocke,
sans recharge ni reinitialisation entre les processus. Les phases de
suspension conservent les donnees et le terrain.

Les six cas anciens (deux types de quarry, chacun sans proprietaire, avec
cible hors zone ou emprise invalide) conservent leur quarantaine et leurs
contenus. Deux attributions explicites de test sont annulees en restaurant
exactement le NBT. Aucune adoption automatique ni migration reelle n'a lieu.

### Onze scenarios natifs

1. Terrain libre vers claim adverse, contact par face ; controle du refus FTB direct.
2. Meme tentative en diagonale ; controle du refus FTB direct.
3. Terrain libre vers claim du joueur : nettoyage refuse.
4. Deux claims de la meme equipe : nettoyage conserve.
5. Claim de son equipe vers terrain libre : nettoyage conserve.
6. Terrain libre vers terrain libre : nettoyage conserve.
7. Claim de son equipe vers claim d'une autre equipe en mode PUBLIC : refuse.
8. Chaine de 19 frames, un chunk adverse entre deux zones libres : aucune traversee.
9. Liquide adjacent au premier cadre : arret natif conserve.
10. Liquide adjacent au cadre suivant : arret natif conserve.
11. Premier cadre remplace par de la pierre : pierre preservee, voisins autorises nettoyes.

Les acteurs serveur simules sont en survie, sans bypass. La destruction
utilise `ServerPlayer.gameMode.destroyBlock` et le vrai `FrameBlock.onRemove`.
Le cas de remplacement utilise `Level.setBlock`. Frames et liquide sont
places par les fixtures, pas construits par deux quarries de vrais joueurs.
Les claims prives et PUBLIC sont reels. L'alliance n'est pas un scenario
distinct execute : la politique ne consulte aucun droit d'alliance.

Les tests separes d'autorite exercent les vrais claim/unclaim : creation
d'un claim apres capture, changement de proprietaire destination, retrait
du claim source, changement puis retour a l'etat initial, mutation en cours,
protection arretee/invalidee et nouvelle initialisation. Un ancien contexte
ne retrouve jamais son autorisation. Ce sont des appels au helper, pas une
injection de changement de claim au milieu d'une suppression native.

## Correction technique

- `GuardHooks.FrameCleanup` capture la dimension/instance de monde, la
  position initiale immuable, l'equipe du claim initial ou l'absence de claim,
  la revision des claims et une generation d'autorite.
- `FrameBlockMixin` encadre le vrai `breakChain`. Il filtre la recherche des
  voisins : une destination interdite ne rejoint jamais la chaine.
- Le retrait effectif est aussi intercepte. Il relit l'autorite, conserve
  les frames voisines d'un liquide et refuse de retirer un autre type de
  bloc, notamment la pierre remplacant le premier cadre.
- Toute mutation de claim invalide l'operation en cours, meme eloignee. Une
  nouvelle operation devra repartir des droits a jour. Ce choix prudent
  peut laisser des frames a nettoyer, mais ne prolonge pas une autorisation.
- Le contexte local au fil d'execution et le verrou natif anti-recursion
  sont restaures dans des `finally`, y compris en cas d'exception.
- Les injections sont obligatoires et ciblent la version exacte du mod.
  Une mise a jour incompatible doit echouer au chargement, pas desactiver
  silencieusement la protection.

Le correctif de cadres n'ajoute aucun balayage periodique des machines.
Il suit le parcours natif des frames connectees, avec 26 voisins par frame
visitee et des consultations FTB supplementaires. Il n'enumere pas tous les
blocs d'un volume. Sa complexite de parcours reste lineaire dans la taille
de la chaine exploree, hors cout interne des API ; une tres grande chaine
peut toujours couter du temps et de la memoire sur le fil serveur.

**Aucun surcout chiffre du nettoyage n'est mesure ici.** Les mesures de
phase 2 concernent un autre binaire et d'autres operations ; elles ne
constituent ni un benchmark de cette correction ni une mesure a huit joueurs.

Une relecture independante du contexte d'autorisation et du mixin n'a releve
aucun defaut avere dans ce perimetre. C'est une revue statique, pas une preuve
supplementaire d'execution ; exceptions et reentrance ne sont pas injectees
dans les essais et les hooks externes ne sont pas audites exhaustivement.

## Historique et limites

Le [FAIL de phase 3](compatibilite-phase3.md) reste une preuve du defaut avant
correction. Son diagnostic A693 est archive dans
`integration/build/frame-reproducer-a693/`. Il ne contient pas ce correctif.

Le champ de cette correction est `quarryplus:frame`. Elle ne certifie pas
tous les chemins de suppression de tous les mods, ni `SoftBlock`, les outils
de deplacement, les modules facultatifs ou les callbacks tiers. Elle ne
change ni `customPlayer` ni les options de claims du serveur.

Verification des installations : 145 JAR serveur et 168 JAR client,
empreintes identiques a l'inventaire initial, sans ajout/suppression. Etat
Git local sans changement signale ; avertissement de lecture de l'exclusion
globale Git. Cette comparaison de JAR n'est pas un audit de tous les fichiers.

## Sources locales

- [Politique et contexte](../integration/src/main/java/fr/ascendant/quarryguard/GuardHooks.java).
- [Interception native](../integration/src/main/java/fr/ascendant/quarryguard/mixin/quarry/FrameBlockMixin.java).
- [Matrice native](../integration/src/main/java/fr/ascendant/quarryguard/FrameChecks.java).
- [Tests d'invalidation](../integration/src/main/java/fr/ascendant/quarryguard/FrameAuthorityChecks.java).
- [Preuves de persistance precedentes](validation-phase3.md).
