# QuarryGuard : compatibilites finales et essai client, phase 6

Date : 4 septembre 2026, heures Europe/Paris.

**Statut : controles automatises, essai avec un vrai client local et essai de
claim sur une copie coherente du monde reel termines. RC3 est pret pour une
proposition de deploiement, mais n'est pas encore installe sur le serveur
principal.** Aucun fichier du serveur distant, du client Prism, de Packwiz ou
de GitHub n'a ete modifie.

## Candidat exact

```text
ascendant-quarryguard-0.1.0-rc3.jar
SHA-256 2469A27765E1493FA0CA91083D72C9069BC0926E67A5E03345A17F72A3198787
```

Le [manifeste RC3](../integration/build/candidate/package-manifest-rc3.json)
retire 37 classes de test et conserve 28 classes runtime identiques octet par
octet au JAR de laboratoire. Le candidat ajoute aussi une ressource de donnees
standard, `c:relocation_not_supported`, pour les blocs QuarryPlus. Par rapport
a RC2, RC3 renvoie immediatement l'inventaire serveur au client lorsqu'une pose
est refusee, afin de corriger la prediction visuelle de consommation.

Demarrage RC3 sans drapeau ni commandes laboratoire : PASS a 21:11:58,
arret et sauvegarde normaux, code 0, aucune terminaison forcee.
[Journal](../results/runtime-candidate-full-runtime-selftest-baseline0-20260904-211158.log).

## Resultats ajoutes

| Controle | Resultat | Preuve |
|---|---|---|
| Regression complete RC3, dont onze cas de cadres | PASS 21:10:13 | [Journal](../results/runtime-full-runtime-regression-baseline0-20260904-211013.log) |
| Chargement partage d'un chunk, deux types | PASS 19:55:12 | [Journal](../results/runtime-full-runtime-shared-chunks-baseline0-20260904-175431.log) |
| Pump, XP et Filter sur Quarry normale | PASS 20:08:20 | [Journal](../results/runtime-full-runtime-modules-baseline0-20260904-180734.log) |
| Cardboard Box et validation Cut/Paste BG2 | PASS 20:12:26 | [Journal](../results/runtime-full-runtime-movement-baseline0-20260904-181149.log) |
| Client Prism `copie`, HailWall, Quarry et Advanced Quarry | PASS 22:00:03 | [Journal](../results/client-test-latest.log) |
| Claim hostile sur copie reelle, refus et inventaire synchronise RC3 | PASS 23:25:15 | [Journal](../results/runtime-real-world-copy-client-rc3-20260904-232523.log) |

La Cardboard Box est refusee avant consommation chez un adversaire par FTB,
et desormais aussi pour le proprietaire par le tag de non-deplacement. Le
validateur execute par la file Cut/Paste de Building Gadgets 2 refuse le meme
bloc. Dans les deux cas, la machine et son NBT complet restent inchanges.
Cela evite de transporter une emprise absolue, de perdre son proprietaire ou
de dupliquer des modules lors du remplacement de la block entity.

Le test des modules prouve un effet utile natif, puis 25 ticks sans mutation
sous claim adverse, et une reprise apres unclaim : Pump conserve exactement
les sources et fluides, XP conserve les orbes, Filter detruit l'or cible et
stocke le fer. Il porte sur la Quarry normale. Pump est refuse nativement par
l'inventaire de l'Advanced Quarry ; son equivalent est l'Advanced Pump. La
comptabilite XP/Filter de l'Advanced Quarry reste une reserve, car son pipeline
multi-etape exige un oracle distinct pour ne pas produire un faux resultat.

Pour le chargement de chunks, une suspension ne retire pas le drapeau global,
une tentative refusee n'en cree pas, et un drapeau externe preexistant reste
intact. Limite native observee : QuarryPlus n'effectue aucun comptage de
references entre deux machines du meme chunk. L'arret de celle qui possede le
drapeau peut donc le retirer pour l'autre. QuarryGuard ne tente pas de vider
ou de reconstruire globalement ces chargements.

## Echecs de conception ecartes

Deux lancements `modules` en echec sont conserves comme diagnostics, pas comme
defauts de QuarryGuard. Le premier demandait Pump a l'Advanced Quarry alors
que son inventaire le refuse. Le second exigeait une egalite immediate entre
bloc casse et stockage durant le pipeline multi-etape de l'Advanced Quarry.
Les deux serveurs ont ensuite ete arretes ; aucun de ces cas ne figure dans le
PASS final.

Un lancement sans test a aussi ete interrompu apres une commande `modules`
non enregistree. Il n'avait effectue aucune mutation de fixture. L'entree a
ete ajoutee, reconstruite, puis la vraie recette a passe.

## Validation client realisee

Le 4 septembre 2026, le client authentifie de l'instance Prism `copie` a
rejoint le serveur local sur `127.0.0.1:25585`. HailWall a accepte les 231 mods
annonces. Le joueur a utilise une Quarry puis une Advanced Quarry sans anomalie
visible. Le journal confirme plusieurs cycles natifs de chargement et retrait
de chunks entre 21:56:57 et 22:00:02, sans kick, crash ni erreur QuarryGuard.
La deconnexion a ete volontaire, puis le serveur a sauvegarde et termine
normalement. Le lanceur a restaure le JAR de laboratoire apres l'arret.

Quatre defauts du banc d'essai, distincts de QuarryGuard, ont ete corriges avant
ce PASS : detection de disponibilite fondee sur un ancien journal, ExtendedAE
absent de la copie serveur, liste blanche HailWall incomplete, puis profils FTB
Teams artificiels dans le monde automatise. Le test manuel utilise maintenant
`quarryguard-client-world`. La limite `rate-limit=10`, trop basse pour les
paquets de configuration QuarryPlus, a aussi ete alignee sur le vrai serveur,
qui etait deja configure avec `rate-limit=0`.

La copie coherente du monde reel a demarre et s'est arretee normalement. Les
sept anciennes quarries sans UUID ont ensuite ete attribuees a partir des blocs
Mekanism directement relies, reprises, puis controlees apres un second
redemarrage. Le dernier test de claim avec un vrai joueur a refuse une emprise
hostile sans poser le bloc, consommer l'objet ni supprimer le marqueur. RC3 a
aussi corrige la disparition visuelle temporaire de l'objet constatee avec RC2.
Voir le [bilan de phase 7](validation-phase7-copie-monde-reel.md).
