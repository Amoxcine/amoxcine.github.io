# QuarryGuard : validation sur copie du monde reel, phase 7

Date : 4 septembre 2026, heures Europe/Paris.

**Statut : validation terminee sur une copie coherente du monde reel. Le
demarrage, l'attribution des sept anciennes quarries, leur persistance apres
redemarrage et le refus d'une emprise hostile avec un vrai client sont valides.
Le candidat RC3 est pret pour une proposition de deploiement, sans constituer
une autorisation de modifier le serveur principal.**

Le backup source
`C:\Users\avets\Downloads\MineStrator-bd89b2b5-20260904_21` est reste en
lecture seule. Une copie complete et isolee a ete placee dans
`quarryguard-lab\real-world-runtime`. Les 16 992 fichiers et
19 983 443 262 octets ont ete recopies ; les empreintes de `world/level.dat`,
`server.properties`, `config/hailwall.json` et du JAR QuarryPlus correspondaient
avant le premier demarrage.

## Resultat du demarrage

Le candidat d'abord utilise pour la migration et le redemarrage etait RC2. Le
candidat final, apres correction de la synchronisation visuelle d'inventaire,
est :

```text
ascendant-quarryguard-0.1.0-rc3.jar
SHA-256 2469A27765E1493FA0CA91083D72C9069BC0926E67A5E03345A17F72A3198787
```

La copie a ete isolee sur `127.0.0.1:25587`, avec Voice Chat sur
`127.0.0.1:25588`, une whitelist d'une place, sans annonce LAN et avec le
MiniServ WebDisplays desactive. Le serveur a charge les 13 joueurs connus,
18 equipes, 216 claims, 20 chapitres et 275 quetes. QuarryGuard a annonce
`ready=true`. Deux lectures de statut ont donne `mutations=0`, `checks=0` et
`denied=0`, ce qui est attendu sans joueur connecte.

La consommation observee au repos apres chargement etait d'environ 2,93 Gio de
RAM residentielle et 3,00 Gio de memoire privee. Cette mesure courte ne remplace
pas un profil avec joueurs et quarries actives. Un retard unique de 42 ticks au
demarrage a ete journalise ; aucun retard continu, crash ou arret force n'a ete
observe.

`save-all flush` a sauvegarde toutes les dimensions, puis `stop` a termine avec
le code 0. Le [journal complet](../results/runtime-real-world-copy-smoke-20260904-223037.log)
conserve la preuve du premier lancement.

## Attribution des machines heritees

QuarryPlus ne conserve dans ces anciennes block entities aucun UUID de poseur
exploitable. QuarryGuard n'invente donc pas de proprietaire et suspend ces sept
machines sans detruire leur bloc, leur inventaire, leur energie ou leur emprise.
La commande `inspect` n'a ecrit aucune attribution pendant cette phase de
decouverte.

L'identite a ensuite ete retrouvee depuis les donnees natives des blocs
Mekanism relies aux machines. Cinq Quantum Entangloporters nommes par leur
utilisateur (`quarry 1`, `quary 2`, `quarry 3`, `quarry 4` et
`Creteil energie`) contenaient l'UUID de `Zermalebeluga`. Pour la paire distante,
le cable d'energie a ete suivi jusqu'au Quantum Entangloporter `FreeNRJ`, qui
contenait l'UUID de `kyrito8720`. Cette relation technique directe est plus
fiable que la simple proximite d'un claim.

| Dimension | Position | Proprietaire retenu | Etat apres reprise | Emprise X/Z |
|---|---:|---|---|---|
| `jamd:mining` | `-265 69 2389` | `kyrito8720` | `BREAK_BLOCK` | X `-446..-264`, Z `2390..2598` |
| `jamd:mining` | `-265 69 2388` | `kyrito8720` | `BREAK_BLOCK` | X `-264..-94`, Z `2388..2537` |
| `jamd:mining` | `-667 69 -857` | `Zermalebeluga` | `MOVE_HEAD` | X `-721..-661`, Z `-915..-857` |
| `jamd:mining` | `-238 69 -855` | `Zermalebeluga` | `FINISHED` | X `-247..-200`, Z `-897..-855` |
| `jamd:mining` | `-381 69 -846` | `Zermalebeluga` | `MOVE_HEAD` | X `-637..-381`, Z `-850..-589` |
| `jamd:mining` | `-377 69 -843` | `Zermalebeluga` | `MOVE_HEAD` | X `-381..-120`, Z `-843..-587` |
| `jamd:nether` | `-435 129 -840` | `Zermalebeluga` | `MOVE_HEAD` | X `-692..-429`, Z `-840..-584` |

Les deux machines a Z `2388/2389` partagent une frontiere d'emprise. Les deux
machines a X `-381/-377` partagent egalement une frontiere. Elles ont ete
attribuees et controlees individuellement.

## Adoption, reprise et redemarrage

Chaque machine a suivi la procedure en deux etapes `adopt`, `confirm`, puis la
commande distincte `resume`. Les quatorze intentions d'ecriture correspondantes
(sept adoptions et sept reprises) existent dans
`world/quarryguard-audit/`. Deux confirmations erronees sur la machine du
Nether ont ete refusees avant mutation et n'ont donc modifie aucun bloc.

Un second demarrage complet a ensuite recharge les 216 claims et les sept
machines. Chaque `inspect` a retrouve le bon UUID, `attribution en pause=false`,
aucune quarantaine et le meme etat QuarryPlus. Aucun message `owner missing`
ni aucune erreur QuarryGuard n'apparait apres le demarrage complet. Le seul
forceload temporaire utilise pour inspecter le Nether a ete retire ; la commande
de verification a confirme qu'aucun chunk n'y restait force par cette operation.

Pendant l'activite des machines, les compteurs ont progresse de 46 178 a
57 464 controles en 17 secondes, sans refus. Le second point mesure
`geometryQueries=6`, `geometryCacheHits=57458` et `meanCheckUs=0.628`. Une
mesure plus longue avant ce redemarrage avait atteint 63 815 controles,
63 808 acces au cache, sept calculs de geometrie et 0,497 microseconde par
controle, toujours sans refus. Ces valeurs prouvent un cout faible dans cette
copie, pas une garantie de MSPT sous huit joueurs.

Le second `save-all flush` et l'arret ont termine normalement avec le code 0.
Le [journal d'adoption et de redemarrage](../results/runtime-real-world-copy-adoption-restart-20260904-224850.log)
porte l'empreinte SHA-256
`0E5DF622149FECF12BB28F3ECC68FAA576D1C441CF0C33CBFCE6CF3F4B786074`.
Le `level.dat` du backup source conserve son empreinte initiale
`545EBA71C7A1262D86E83A5C77E54BD9DBEFC7EF607AB3BA23D8A140CEBCECEA` :
seule la copie de laboratoire a ete modifiee.

## Essai final avec un vrai joueur

Le joueur `akoukiko`, connecte avec l'instance Prism `copie`, a d'abord valide
une pose libre. Une seconde Quarry a ensuite ete placee depuis le terrain libre
avec un Flexible Marker dont l'emprise traversait le claim prive de l'equipe
`Sneaxx`, chunk `(363, -86)`. Le contournement FTB du joueur etait desactive.

RC2 refusait correctement cette seconde pose : aucun bloc Quarry n'etait cree,
le marqueur et son NBT restaient intacts et l'unique objet Quarry restait dans
l'inventaire serveur. Le client masquait toutefois temporairement l'icone de
l'objet jusqu'au clic sur sa case, car il avait predit la consommation du
`BlockItem`. RC3 force l'envoi immediat de l'inventaire serveur apres ce refus,
sans changer la decision d'autorisation.

Le meme scenario a ete rejoue avec RC3. Le message de refus est apparu et
l'icone est restee visible immediatement, sans interaction supplementaire. Les
controles serveur apres l'essai ont confirme : une Quarry toujours presente,
position cible toujours vide, Flexible Marker toujours present avec les bornes
X `5798..5833` et Z `-1370..-1366`. Le compteur est passe de trois a six refus,
avec 423 387 controles, 13 recherches de geometrie, 423 374 acces au cache et
0,271 microseconde moyenne par controle au dernier point.

La zone temporaire a ete supprimee, le joueur replace a sa position initiale,
toutes les dimensions sauvegardees et le serveur arrete normalement. Le
[journal du test client RC3](../results/runtime-real-world-copy-client-rc3-20260904-232523.log)
porte l'empreinte SHA-256
`0BEEAE6B8CEF6D362E57DF78D8AEA7F9A194D4D216C286994B57FFB852963AA6`.
Aucun fichier du serveur principal, de Packwiz, du client distribue ou de
GitHub n'a ete modifie.
