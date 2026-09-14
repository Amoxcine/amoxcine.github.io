# QuarryGuard : attribution des anciennes quarries

Date : 4 septembre 2026.

**Outil valide par les essais fonctionnels du laboratoire. Ne pas installer sur le serveur
des joueurs a partir de ce guide.** Aucune machine reelle n'a ete attribuee.

[Resultats, binaire exact et reserves de livraison](validation-phase5-operateur.md).

## Pourquoi cet outil

Une ancienne quarry ne porte pas encore l'identite enregistree par
QuarryGuard. Le correctif la suspend par prudence. L'operateur peut maintenant
indiquer explicitement son proprietaire, sans casser la machine ni remplacer
son contenu, son energie, sa zone ou son point de travail.

L'UUID doit etre celui du joueur reel, verifie par l'operateur, par exemple
dans les journaux d'authentification du serveur. Le joueur peut etre hors
ligne. L'outil ne devine pas le proprietaire a partir du claim ou de l'equipe
et ne transforme pas une quarry deja attribuee en propriete d'un autre joueur.

## Procedure sur une copie de test

Les commandes exigent le niveau de permission Minecraft 2 minimum. Un joueur
ordinaire est refuse. Le JAR est destine au serveur, pas aux clients ; la
connexion d'un vrai client sans ce JAR reste a verifier.

Remplacer `X Y Z` par les coordonnees du bloc machine, `UUID` par l'identite
verifiee et `JETON` par la valeur affichee. Ces mots sont des emplacements,
pas des arguments a saisir litteralement.

1. Se placer dans la dimension de la quarry et charger son chunk normalement.
2. Inspecter : `/quarryguard inspect X Y Z`.
3. Preparer : `/quarryguard adopt X Y Z UUID`. Rien n'est encore modifie.
4. Verifier l'UUID affiche, puis saisir `/quarryguard confirm JETON` avec le
   meme operateur et dans la meme dimension, sous dix minutes.
5. La quarry reste en pause. Inspecter a nouveau, puis choisir une action :
   `/quarryguard resume X Y Z` pour autoriser le travail sous les controles
   de claims habituels ; ou `/quarryguard cancel-adoption X Y Z` pour annuler
   seulement cette attribution encore en attente.

Depuis une console, retirer le `/` initial. Pour une autre dimension, utiliser
le contexte `execute in <dimension> run quarryguard ...` pour chaque commande,
y compris la confirmation. Il n'y a ni scan de tous les chunks ni chargement
force automatique par cet outil.

Une confirmation consommee, expiree, perdue au redemarrage ou portant sur une
machine remplacee/rechargee doit etre preparee a nouveau. Une modification de
la machine depuis l'apercu provoque aussi un refus. Stabiliser ses entrees et
sorties puis refaire `adopt`, plutot que forcer une ancienne confirmation.

## Garanties et refus

| Situation | Resultat |
|---|---|
| Confirmation sans droits OP ou sous un autre operateur | Refusee |
| Confirmation du meme jeton deux fois | Refusee |
| Machine changee depuis l'apercu | Refusee avant attribution |
| Quarry deja attribuee | Aucun transfert de proprietaire |
| Emprise/cible invalide ou quarantaine | Pas d'adoption ni de levee de quarantaine |
| Confirmation reussie | Owner et pause d'attribution ajoutes, pas de reprise implicite |
| Arret puis redemarrage apres confirmation | Owner et pause conserves |
| Reconfiguration de zone/config pendant la pause | Refusee par les controles serveurs du prototype |
| Reprise avec claim non autorise ou protection indisponible | Refusee, pause conservee |
| Annulation avant reprise | Owner d'adoption et pause retires ; machine a nouveau sans owner, suspendue |
| Annulation apres reprise | Refusee : ce n'est pas un retour arriere du monde |

Les privileges FTB explicitement actives restent ceux de la politique
QuarryGuard existante. La commande de reprise ne cree aucun bypass et ne
change aucun claim. Le statut OP seul ne remplace pas les droits du
proprietaire adopte sur la zone de travail.

L'outil ne recharge jamais un ancien NBT pour annuler l'attribution : il
retire uniquement ses metadonnees. Cela evite d'ecraser des objets ou de
l'energie arrives entre-temps. La pause du travail n'est pas un verrou
universel des tuyaux, du stockage ou des outils de deplacement d'autres mods.

## Journal et sauvegardes

Avant chaque attribution, reprise ou annulation, un fichier SNBT unique est
ecrit sous `<monde>/quarryguard-audit/`. Il contient la position, la dimension,
l'operateur, l'heure, l'identifiant d'operation, le NBT avant et le NBT prevu.
Une erreur d'ecriture empeche la modification. Les anciennes entrees ne sont
pas ecrasees. Le serveur journalise ensuite l'application de l'action.

Le fichier est un **journal d'intention ecrit avant modification**, pas une
preuve autonome que le chunk a ete sauvegarde apres l'action. En cas de
coupure, comparer le monde effectivement recharge au journal et aux logs.
L'ecriture forcee du fichier a ete implementee ; une panne de courant ou de
disque n'est pas simulee dans ces tests. Aucune reprise automatique des
intentions n'est effectuee.

Conserver la sauvegarde complete du serveur avant une migration reelle.
Ce journal par machine ne remplace pas une sauvegarde coherente du monde,
des inventaires, des claims et des configurations.

## Limites de cette livraison

- Cet outil traite une machine connue par ses coordonnees. Il n'inventorie
  pas automatiquement les quarries de tous les chunks decharges.
- Les quarries aux donnees incoherentes restent suspendues : pas de commande
  pour supprimer arbitrairement leur quarantaine.
- Aucun ticket de chargement force n'est cree ou purge par l'attribution.
  Cela ne certifie pas toutes les interactions de chargement partage.
- Vrais clients, modules facultatifs, deplacements, charge multijoueur et
  restauration du monde existant restent des validations distinctes.
- Le candidat sans commandes de laboratoire n'est pas encore une version
  autorisee sur le serveur des joueurs.

Sources : [service operateur](../integration/src/main/java/fr/ascendant/quarryguard/AdoptionService.java),
[commandes](../integration/src/main/java/fr/ascendant/quarryguard/AdoptionCommands.java),
[tests de refus](../integration/src/main/java/fr/ascendant/quarryguard/AdoptionChecks.java),
[tests apres redemarrage](../integration/src/main/java/fr/ascendant/quarryguard/AdoptionRestartChecks.java).
