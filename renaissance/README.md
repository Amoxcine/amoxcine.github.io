# Ascendant : Renaissance RC1

Minecraft 1.21.1, NeoForge 21.1.248, Java 21. Premiere RC lunaire, a tester sur une
instance et un monde separes. Ce n'est pas une mise a jour du serveur V1 existant.

## Installer

[Ouvrir la preversion Renaissance RC1](https://github.com/Amoxcine/amoxcine.github.io/releases/tag/renaissance-rc1).

- Client : importer `Ascendant-Renaissance-RC1.zip` dans Prism Launcher, puis
  ouvrir en solo le monde prepare `renaissance-rc1`.
- Serveur local Windows : extraire `Ascendant-Renaissance-RC1-Server.zip`, lancer
  `Demarrer-serveur.cmd`, puis se connecter a `127.0.0.1:25575` depuis cette RC.
  Le serveur est volontairement limite a la machine locale. Arret avec `stop`.
- Consulter [le guide des tests](docs/RENAISSANCE-TESTS.md) et
  [le guide du relais](docs/RELAIS-JOUEUR.md).

Pas de mise a jour automatique Packwiz vers le pack public. Ne pas ecraser les
anciennes sauvegardes. La copie publique remplace uniquement le mot de passe
residuel d'une interface web desactivee par `CHANGE_BEFORE_ENABLING` et rend les
chemins de sources des manifestes relatifs au depot. Ne pas activer cette
interface sans definir une authentification personnelle. Les fichiers de jeu,
les mods, le monde prepare et les restrictions sont ceux de la RC testee.

## Contenu

- Vingt quetes reliees : preparation, Lune, industrie locale, habitat et relais.
- Voyages en fusee, sans teleportation lunaire ni ravitaillement automatique
  interdimensionnel par les voies de transport prises en charge.
- Kit de premier voyage limite et protection contre les cargaisons cachees.
- Oxygene produit et distribue par les machines, pas d'air gratuit permanent.
- Premier combat volontaire et recompense FTB partagee ; QuarryGuard RC3 integre.
- 181 JAR client, 160 JAR serveur ; versions communes identiques.

Certains equipements complexes, notamment des conteneurs de modules Draconic et
de sorts, restent refuses au depart sans suppression de leur contenu. Le mode
BLASTING du four Etrionic est desactive pour eviter un probleme de la version
retenue ; les alliages et les fours ordinaires restent utilisables.

## Validation

Demarrage et redemarrage propres du monde neuf, scripts charges sans erreur,
site prepare et controle, 33 cas de bagages, 138 cas de rappel/tombe,
196 controles de portails/passages et 59 assertions sur les quetes.
[Details des essais et limites](docs/NATIVE_RESULTS.md).

Le vrai aller-retour en fusee, les interfaces client, un combat complet et les
recompenses en multijoueur restent a valider en jeu. Aucun test automatique ne
constitue une garantie universelle pour des mods ajoutes ensuite.

## Organisation du depot

| Dossier | Contenu |
|---|---|
| `client/` et `server/` | Configurations et scripts livres, quetes et donnees KubeJS. |
| `custom-mods/` | Sept binaires specifiques exactement figes pour cette RC. |
| `sources/` | Sources et tests des modules specifiques ; aucun cache de dependances tiers. |
| `prism-template/` | Metadonnees de l'instance privee. |
| `manifests/` | Inventaires et SHA256 des fichiers et des archives publiques. |
| `docs/` | Guides et compte rendu des essais. |

Les scripts de compilation conservent leur contexte de developpement et leurs
versions de dependances epinglees. Ils ne constituent pas encore une compilation
portable automatique : certains chemins et caches locaux doivent etre prepares.
Les anciens noms `candidate` sont preserves pour identifier les binaires testes.
Les helpers de laboratoire, caches, logs bruts et sauvegardes de joueurs ne sont
pas ajoutes au depot. Le monde neuf prepare et les mods tiers sont dans les ZIP
de la preversion, pas dans l'historique Git.

Le fichier de configuration sous `server/world-template/` ne construit pas le
relais a lui seul : utiliser le monde prepare fourni dans le ZIP.
