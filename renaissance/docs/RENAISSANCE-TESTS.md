# Ascendant : Renaissance RC1

Copie privee de qualification, Minecraft 1.21.1 / NeoForge 21.1.248 / Java 21.
Ne pas la fusionner avec le serveur public ni avec une ancienne sauvegarde.
Une RC sert a trouver les derniers problemes en jeu ; elle n'est pas une version
declaree sans bugs. La campagne des saisons suivantes n'est pas incluse.

## Ce que contient ce chapitre

- Preparation de l'equipement et de l'oxygene dans l'Overworld.
- Expedition en fusee vers la Lune, ressources et industrie locales.
- Construction d'un habitat dont l'air depend des machines et de leur energie.
- Vingt quetes reliees, comprenant un premier relais de combat volontaire.
- Retour en fusee possible avant le relais ; seule la quete de fin de chapitre
  demande de l'avoir vaincu.

## Restrictions actives

| Sujet | Regle de cette RC |
|---|---|
| Destinations | Lune seulement pour ce chapitre ; autres destinations reservees. |
| Voyage | Fusee native Ad Astra de premier niveau entre Overworld et Lune. |
| Teleportation lunaire | Refusee, meme entre deux points de la Lune. Waystones, Dislocators, rappels et voies de portails prises en charge sont concernes. |
| Portails narratifs | Aucun portail autorise dans cette premiere RC. |
| Ravitaillement automatique | Interdit entre la Lune, son orbite et les autres mondes, dans les deux sens. Aucun palier ne le reouvre. |
| Machines locales | Les circuits locaux restent utilisables. Certains appareils fondes sur un stockage global sont bloques sur la Lune, plutot que de laisser entrer un stock exterieur. |
| Bagages | Premier kit limite, controle avant le voyage. Le droit au premier kit est enregistre par joueur, pas renouvelle par reconnexion ou changement d'equipe. |
| Conteneurs | Les charges imbriquees ou non reconnues sont refusees ; aucun objet n'est confisque. Un objet refuse doit rester dans la base de depart. |
| Terrain et claims | Patch QuarryGuard RC3 integre pour la quarry prise en charge. Ce n'est pas une garantie universelle pour toutes les machines de tous les mods. |
| Apparitions | Pas d'apparitions naturelles de zombies/creepers sur la Lune ; cela n'interdit pas toutes les creatures, commandes ou spawners. |
| Habitat | Pas d'air gratuit permanent. Les murs restent necessaires contre les creatures deja presentes. |

Les protections sont limitees aux mods et versions identifies dans le manifeste.
Ajouter un nouveau mod de transport exige une nouvelle verification : la RC ne
peut pas garantir le comportement d'un mod absent de sa liste.

## Points a connaitre

- Certains equipements complexes, notamment les conteneurs de modules Draconic
  et certains conteneurs de sorts, ne sont pas encore reconnus par le controle
  des bagages. Un refus ne supprime pas leur contenu. Il ne faut pas retirer la
  protection pour contourner ce refus : remonter le nom de l'objet et le message.
- Le mode BLASTING du four Etrionic est neutralise dans cette copie, y compris
  hors de la Lune, pour eviter un probleme de duplication de la version retenue.
  Le bouton reste visible. Les alliages et les fours ordinaires restent utilisables.
- Le monde fourni est un nouveau monde prepare pour ce chapitre. Il ne reprend
  ni les inventaires, ni les tombes, ni les reseaux, ni la progression des joueurs
  des precedents tests. Ne pas importer un ancien train deja a cheval entre deux
  dimensions dans cette RC.
- Les mises a jour publiques Packwiz sont desactivees sur cette copie privee.
  Le controle HailWall de production n'est pas utilise pour ce test isole.

## Essais a faire en jeu

1. Ouvrir le monde prepare, verifier les deux chapitres Renaissance et preparer
   le scaphandre, son oxygene, la fusee et le kit indique par les quetes.
   Tester le depart en survie, sans privileges de teleportation.
2. Avant le depart, essayer un objet industriel interdit : le depart doit etre
   refuse sans perte de bagages. Retirer cet objet puis refaire le voyage normal.
3. Sur la Lune, construire et alimenter l'habitat, verifier l'air puis couper
   son alimentation. Sauvegarder, quitter et reprendre la partie.
4. Tester le relais indique par `/lunar_encounter site`, puis le retour en fusee.
   En groupe, verifier les inscriptions au combat et la recompense partagee.

Pour chaque probleme, conserver `logs/latest.log`, le message exact, le nom de
l'objet concerne et l'action qui a precede le probleme. Ne pas effacer le monde
ou les donnees de progression pour refaire un test.

## Limite de validation

Les essais techniques automatises ne remplacent pas un aller-retour avec un vrai
joueur, l'affichage du client, un combat complet et une partie multijoueur. Ces
essais de jeu constituent la reception de cette RC. Les mesures de memoire d'un
serveur de test sans joueurs ne constituent pas une estimation pour huit joueurs.
