# QuarryGuard : cadres et interactions de mods

Date : 4 septembre 2026. Revue statique par agent, reproduction ciblee par le
parent. Historique du defaut avant correction, sans modification de production.
L'utilisateur a ensuite valide A : [correction et essais de phase 4](validation-phase4-cadres.md).
Les verdicts ci-dessous distinguent lecture de code et execution.

## Defaut reproduit : suppression de cadres a travers un claim

Priorite P2, protection des cadres incomplete. QuarryPlus 21.1.162 utilise
`FrameBlock.onRemove -> breakChain -> Direction26.DIRECTIONS -> removeBlock`.
Les cadres secs relies, y compris diagonalement, sont parcourus puis retires
sans evenement de casse joueur pour chaque destination. Les intercepteurs
de minage de la quarry ne couvrent pas cette suppression.

Dans la copie des 145 mods serveur, avec le garde actif :

1. Trois blocs natifs `quarryplus:frame` sont installes dans un volume vide.
2. Le premier est en terrain libre ; les deux suivants dans le claim prive
   d'une autre equipe. Acteur serveur Survie, sans bypass, connexion simulee.
3. `actor.gameMode.destroyBlock` sur le cadre protege est refuse par FTB ;
   tous les cadres restent presents. Ce controle exclut une protection FTB
   simplement desactivee dans le banc.
4. Le meme appel sur le cadre en terrain libre reussit. Les deux cadres
   proteges disparaissent par la propagation native.
5. Le cas est reproduit avec contact par face, puis en diagonale. Les seules
   positions de la fixture et son claim sont nettoyes a la fin.

Le test exige la conservation des cadres proteges : **FAIL a 17:29:54**.
Ce FAIL demontre le defaut, ce n'est pas un PASS de protection. La JVM
sauvegarde et quitte proprement avec code 0 ; le lanceur signale ensuite
l'echec des assertions. Aucun crash du serveur n'est observe.

- [Preuve complete](../results/runtime-full-runtime-frames-baseline0-20260904-152913.log).
- [Binaire du diagnostic](../results/run-full-runtime-frames-baseline0-20260904-152913.json).
- [Reproducteur](../integration/src/main/java/fr/ascendant/quarryguard/FrameChecks.java).
- [Bytecode natif lu](../../refonte-ascendant-2026-09-04/quarry/evidence/quarry/com.yogpc.qp.machine.misc.FrameBlock.txt).

SHA-256 du diagnostic :
`A693F58478A177C4F90BDFA523CF2C020EC823D41599C118005F8B85700C9AAB`.
Il ajoute le test, pas un correctif. Le binaire EF26 archive de phase 3 ne
contient pas non plus de protection de cette propagation.

Limite : les blocs natifs sont places par la fixture, pas par deux vraies
quarries dans cette reproduction. L'appel de casse et la propagation sont
natifs, mais il n'y a pas de connexion reseau reelle. Le resultat concerne
les cadres ; il ne demontre pas la destruction de coffres ou le vol de loot.

## Choix de gameplay : A valide

Le chemin de suppression automatique ne fournit pas l'identite du joueur
ou de la machine responsable. Une simple verification des droits du joueur
n'est donc pas disponible pour tous les declencheurs.

**A, recommandation : conserver un nettoyage automatique limite par equipe
de claim.** La chaine peut rester en terrain libre ou dans les claims de
l'equipe du point de depart, mais ne traverse pas un claim d'une autre
equipe. Si elle part du terrain libre, elle n'entre dans aucun claim, meme
celui du joueur qui a casse le premier cadre. Les cadres restants peuvent
etre casses depuis leur propre claim, avec les droits FTB habituels.

**B : desactiver completement le nettoyage automatique en chaine.** Chaque
bloc doit etre casse individuellement. Plus simple a mettre en oeuvre, mais
moins pratique pour nettoyer une grande installation.

Faisabilite A : implementee et testee sur le code epingle apres validation
utilisateur. L'exploration et le retrait effectif sont controles. Les droits
sont revalides ; une protection indisponible refuse le nettoyage. Le controle
agit lors de la suppression des cadres, pas a chaque tick de toutes les
machines. Aucun chiffre de performance n'est promis. Les anciennes frames
n'ont pas besoin d'etre attribuees individuellement. Voir le rapport de
phase 4 pour les scenarios effectivement executes et leurs limites.

## Autres observations de l'agent

Ces points sont des constats statiques transmis par l'agent, pas des essais
runtime supplementaires du parent :

- `SoftBlock.ChainBreakTask` possede un chemin similaire ; une jonction
  interclaim produite normalement reste non demontree. Pas un second exploit
  etabli par les essais ci-dessus.
- Mekanism Cardboard Box transporte et restaure des block entities et passe
  des controles locaux de casse. Building Gadgets 2 Cut/Paste fait aussi du
  transport de NBT via `RenderBlockBE`, avec traitement differe. Les anciens
  owner et emprise absolue peuvent etre conserves ; le garde controle toujours
  les droits actuels et le chunk courant de la machine. Aucun contournement
  de minage prive etabli. Revocation pendant la file BG2 encore a tester.
- QuarryPlus Mover transfere des enchantements entre objets, pas des block
  entities. Les quarries declarent `PushReaction.BLOCK`, refuse par le chemin
  standard Create inspecte. Aucun clone exploitable demontre.
- Pump, Bedrock, XP et Filter sont actives dans la configuration examinee,
  RepeatTick desactive. Aucun nouveau franchissement ordinaire etabli ; le
  filtrage peut supprimer volontairement des produits. Conservation exacte
  modules/XP/energie et voies de transport restent a exercer en runtime.

Ces reserves ne doivent ni effacer les PASS de persistance obtenus, ni etre
transformees en certification de tous les modules du pack.
