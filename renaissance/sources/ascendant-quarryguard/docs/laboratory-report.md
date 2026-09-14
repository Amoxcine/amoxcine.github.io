# Ascendant : QuarryGuard, laboratoire de protection des claims

Date : 4 septembre 2026.

**Statut : candidat RC3 valide par les essais automatises, un vrai client local,
une copie coherente du monde reel, un redemarrage apres adoption des sept
anciennes quarries et un refus de claim hostile avec inventaire client
synchronise. Il est pret pour une proposition de deploiement.**

**Derniere avancee : le test final avec un vrai joueur a refuse une Quarry dont
l'emprise traversait un claim adverse, sans pose, consommation ni perte du
marqueur ; RC3 garde aussi l'icone de l'objet visible immediatement.** Le
[bilan de phase 7](research/validation-phase7-copie-monde-reel.md) decrit les
preuves de cette validation finale. Le [bilan de phase 6](research/validation-phase6-compatibilites.md)
conserve les preuves de compatibilite. La recette de phase 5 est detaillee
dans le bilan ci-dessous. L'option A reste celle validee par l'utilisateur :
onze scenarios natifs de cadres ont passe en phase 4 et sont rejoues. Le
[defaut de phase 3](research/compatibilite-phase3.md) reste archive comme
preuve avant correction, pas comme defaut encore reproduit sur ce candidat.

## Conclusion

La solution retenue combine un index spatial des claims et un cache de geometrie.
Elle ne balaie pas tous les blocs du volume pour retrouver les claims. Les
autorisations restent relues lors des actions natives controlees ; un test
constant de coordonnees empeche aussi une destination de sortir de l'emprise.
Une ancienne autorisation ne doit pas continuer apres son retrait.

Cette approche est techniquement realisable avec les versions installees. Des essais automatiques et des mesures existent deja. Ils ne permettent pas de promettre une absence totale d'impact sur un serveur complet avec huit joueurs.

Aucun changement n'a ete applique au serveur Ascendant, au client Prism, a Packwiz ou a GitHub. Ce dossier contient uniquement le laboratoire.

**Dernier bilan : [validation-phase7-copie-monde-reel.md](research/validation-phase7-copie-monde-reel.md).**
Le [bilan de phase 5](research/validation-phase5-operateur.md) conserve la
recette de l'outil d'adoption.
Le [guide operateur](research/outil-operateur-phase5.md) decrit inspection,
apercu, confirmation, pause persistante, annulation avant reprise et reprise
explicite. Il ne vaut pas autorisation d'installer le candidat en production.
La regle A protege les frontieres lors du nettoyage automatique des cadres,
y compris en diagonale. La [phase 3](research/validation-phase3.md) conserve
les preuves de persistance precedentes, rejouees sur le candidat de phase 4.
Reprise utile apres redemarrage, conservation des sauvegardes anciennes et
quarantaine, attribution manuelle reversible sur fixtures : PASS dans le
modpack complet. Un vrai client local est maintenant couvert ; le monde et les
machines de production restent hors de ces preuves. Les mesures et corrections precedentes figurent dans
[validation-phase2.md](research/validation-phase2.md).
La section 4 ci-dessous conserve la mesure initiale avec sa date ; elle ne fige
pas l'empreinte d'un futur candidat. Les revues des agents sont archivees avec
leurs constats, qui doivent etre lus a la date de leur revision.

## 1. Probleme traite

Un joueur pose une QuarryPlus en dehors d'un claim adverse, mais ses marqueurs definissent une zone qui traverse ce claim. Les protections de la seule position de la machine ne suffisent pas a proteger la construction du cadre et les autres operations de la machine.

Le prototype controle l'emprise complete, y compris les chunks interieurs et la position de la machine. Il refuse la pose normale avant consommation si cette emprise est interdite. Une machine deja posee est suspendue si une autorisation devient invalide.

Les autres sujets de refonte du modpack, notamment Apotheosis et l'equilibrage des boss, ne sont pas modifies ici.

## 2. Algorithme retenu

### Projection exacte en chunks

Un chunk couvre 16 blocs sur chaque axe. Pour une emprise fermee `[minX,maxX] x [minZ,maxZ]`, les bornes en chunks sont :

```text
cxMin = floor(minX / 16)
cxMax = floor(maxX / 16)
czMin = floor(minZ / 16)
czMax = floor(maxZ / 16)
```

Le plancher est important aux coordonnees negatives : le bloc -1 est dans le chunk -1, pas dans le chunk 0. Les bornes sont inclusives. On ne teste pas seulement les quatre coins ou le perimetre, car cela raterait un claim situe au milieu.

### Index spatial creux

Les claims sont classes par dimension, puis par X et par Z dans deux niveaux d'arbres tries. Une recherche utilise uniquement les lignes et claims candidats. Elle n'enumere pas tous les chunks vides d'un immense rectangle.

Le choix repose sur les garanties de recherche des [TreeMap de Java 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/TreeMap.html). La demonstration et les complexites exactes figurent dans [l'etude algorithmique](research/algorithmes.md). Ce n'est pas un algorithme universellement constant ni une garantie `O(log N + K)`.

### Cache de geometrie, pas de permissions

Pour chaque machine chargee, on conserve les identifiants des equipes dont les claims croisent son emprise. Cette liste est recalculee lorsque les claims ou l'emprise changent. Entre ces changements, on ne refait pas la recherche spatiale.

A chaque controle, les droits effectifs sont relus pour ces equipes : appartenance, alliance, confidentialite et contournement explicitement active dans FTB. Une modification de droits prend donc effet sans attendre un delai de cache.

Le controle chaud coute approximativement `O(1 + T)` appels de verification, ou `T` est le nombre d'equipes distinctes concernees, hors cout interne des API FTB. Il reste execute sur le fil principal du serveur pour ne pas consulter un monde ou des permissions en cours de mutation depuis un autre fil.

L'index est mis a jour sur les operations reelles de claim, unclaim et transfert d'equipe. En cas d'index indisponible ou de proprietaire inconnu, la machine est suspendue au lieu de traiter la zone comme libre.

## 3. Mesures algorithmiques

**915 730 assertions automatiques reussies** sur le noyau Java : coordonnees negatives, limites numeriques, projections, mutations, dimensions et comparaison avec une methode de reference.

Trois processus Java independants, 13 scenarios, echauffement et distributions p50/p95/p99. Ce sont des microbenchmarks synthetiques, pas une mesure du serveur en charge. Valeurs ci-dessous en microsecondes, mediane des percentiles des trois processus.

| Scenario sans refus anticipe | Methode | p50 | p95 | p99 |
|---|---|---:|---:|---:|
| 20 032 claims, 31 dans l'emprise | Enumeration des chunks | 777,3 | 873,3 | 1 149,7 |
| Meme scenario | Scan des claims | 138,8 | 162,9 | 231,4 |
| Meme scenario | Index retenu | 0,6 | 0,7 | 0,8 |
| 4 096 claims denses dans l'emprise | Scan des claims | 17,8 | 31,1 | 49,4 |
| Meme scenario dense | Index retenu | 22,3 | 33,2 | 57,8 |
| 20 000 lignes X candidates, Z hors zone | Scan des claims | 148,9 | 167,5 | 221,6 |
| Meme cas defavorable | Index retenu | 266,7 | 319,8 | 647,3 |

L'index gagne nettement pour une petite emprise parmi beaucoup de claims eloignes. Il peut perdre contre un scan dans les cas denses ou defavorables. Le cache limite la frequence de ces recherches, mais ne supprime pas leur cout.

Les CSV, distributions completes, compteurs, code et protocole sont dans [core/](core/README.md) et [research/algorithmes.md](research/algorithmes.md).

## 4. Essais initiaux dans Minecraft

**Suite terminee avec succes le 4 septembre 2026 a 14:18:50, heure de Paris.** Le serveur de laboratoire a ensuite sauvegarde et termine avec le code 0, sans arret force.

Tests effectues pour la quarry normale et la quarry avancee :

- Pose native refusee avec un claim adverse strictement interieur a l'emprise, loin de la machine et des coins ; aucune consommation de l'objet, aucun remplacement du bloc cible et marqueur conserve.
- Pose native autorisee en terrain libre, claim personnel, claim de son equipe et claim d'allie explicite autorise ; consommation d'un seul objet et proprietaire correctement enregistre.
- Ajout et retrait de claim reflettes par le controle sans reconstruction manuelle de l'index.
- Transfert reel du claim entre equipes reflete par le controle.
- Ajout/retrait d'alliance, appartenance et confidentialite relus sans recalcul de geometrie ; aucun droit conserve a tort par le cache.
- Activation puis retrait du contournement FTB pris en compte.
- Sauvegarde et restauration NBT natives du proprietaire et de l'emprise ; machine sans proprietaire refusee.
- Pose par faux joueur refusee, meme avec l'UUID du proprietaire.

Les acteurs sont des joueurs serveur simules, en survie, sans connexion reseau. Ils utilisent la vraie methode de pose Minecraft et les vrais marqueurs QuarryPlus, pas une imitation de leur logique. Ce test ne remplace pas une session multijoueur ni un fonctionnement continu de machines alimentees.

| Mesure du controle chaud dans le moteur | Resultat |
|---|---:|
| Echauffement | 5 000 appels |
| Appels chronometres | 10 000 |
| Claims dans l'emprise pour cette mesure | 1 claim personnel |
| Mediane | 500 ns = 0,5 microseconde |
| p95 | 1 000 ns = 1,0 microseconde |
| p99 | 1 700 ns = 1,7 microseconde |
| Recalcul de geometrie pendant ces appels chronometres | Aucun |

La suite complete comporte 15 074 controles, 48 recherches de geometrie et 15 024 utilisations du cache. Ce sont des compteurs de chemins differents, pas trois nombres qui doivent s'additionner : certains refus ou contournements sont traites avant le cache.

Preuve : [journal du lancement reussi](results/runtime-20260904-121838.log). [Notes et perimetre exact des essais](research/runtime-test-notes.md).

Un premier essai avait echoue sur le joueur simule sans connexion utilise par le banc de test : la quarry avancee envoyait un paquet d'interface a ce joueur. La fixture a ete corrigee avec un destinataire silencieux sans reseau, sans contourner la pose native. [Journal du premier echec](results/runtime-20260904-121509.log). Cet echec n'est pas comptabilise comme une reussite.

Dans cette suite initiale, la persistance a ete testee par un aller-retour NBT
dans le meme processus. La phase 2 ajoute un vrai redemarrage avec maintien de
la suspension, puis des essais alimentes distincts. Ne pas confondre ces deux
preuves avec un minage utile apres redemarrage. Les mesures initiales ne
comprennent ni huit joueurs ni le modpack complet.

## 5. Politique du prototype

| Situation | Comportement implemente |
|---|---|
| Terrain non claim | Autorise si la protection est disponible |
| Claim personnel ou equipe dont le proprietaire est membre | Autorise selon les droits FTB |
| Allie explicite | Autorise seulement si le mode de protection le permet |
| Claim adverse, meme en mode PUBLIC | Refuse par la politique stricte QuarryGuard |
| Contournement FTB explicitement actif | Respecte ; le simple statut OP ne suffit pas a lui seul |
| Droits retires apres la pose | Suspension aux controles suivants |
| Ancienne quarry sans proprietaire identifie | Suspension, sans destruction de la machine |
| Pose par un faux joueur | Refusee |
| Reconfiguration avancee | Proprietaire ou contournement FTB, a 8 blocs maximum |
| Recharge interne des equipes sans cycle normal de demarrage | Protection indisponible, machines suspendues jusqu'au redemarrage |

Ces restrictions sont des choix de prudence du laboratoire, pas une modification deja imposee au gameplay du serveur. Aucun changement de `customPlayer` ni des options de claims de production n'a ete fait.

### Nettoyage automatique des cadres : A

La table precedente concerne les actions de la machine et son proprietaire.
Le nettoyage des frames ne fournit pas cette identite : sa regle distincte,
validee par l'utilisateur, repose sur le claim du premier cadre. Depuis un
claim de A, il peut nettoyer en terrain libre et dans les claims de A, mais
pas chez B, meme allie ou PUBLIC. Depuis le terrain libre, il n'entre dans
aucun claim, meme celui du joueur. Une portion interdite bloque aussi le
passage vers la suite de la chaine. Le droit FTB de casser le premier bloc
n'est pas modifie. [Preuves et limites](research/validation-phase4-cadres.md).

## 6. Limites et conditions avant production

1. **Performances globales non mesurees.** Une comparaison bornee de 16 quarries actives avec/sans interceptions passe maintenant sur le modpack complet, a travail equivalent. Restent generation, joueurs reels, temps de tick, allocations et memoire en charge prolongee. Les temps de lots du laboratoire ne sont pas des MSPT de serveur.
2. **Invalidation globale.** Le prototype invalide la geometrie de toutes les machines apres une mutation de claim, meme eloignee. Une rafale de changements suivie de nombreuses grosses quarries peut concentrer les recalculs. Une invalidation par dimension ou zone ne sera ajoutee qu'avec les tests de coherence correspondants.
3. **Pas de transaction generale entre mods.** Les callbacks de casse, loot, fluide, modules et voisins peuvent intervenir au milieu d'une operation. Les points controles ne constituent pas une preuve d'atomicite de tous ces effets. Les essais de mutations pendant ces callbacks et d'absence de duplication restent a faire.
4. **Echec tardif de pose.** Si un autre traitement modifie les donnees entre le controle initial et la confirmation, l'objet machine peut deja etre pose. Le prototype ne promet pas un remboursement ou un retour arriere universel dans cette situation.
5. **Chargement force existant.** Une suspension ne retire pas automatiquement les chunks deja forces par une ancienne quarry. Les liberer sans perturber une autre machine exige une gestion d'appartenance des tickets ; pas de suppression globale des tickets.
6. **Migration.** Les quarries anciennes sans proprietaire ne sont pas adoptees automatiquement. La phase 5 ajoute un outil OP explicite avec confirmation, pause persistante et annulation des seules metadonnees avant reprise. Sur la copie reelle, les sept proprietaires ont ete deduits de blocs Mekanism directement relies, puis les adoptions ont persiste apres redemarrage. L'outil ne peut toutefois pas inventer cette preuve pour une autre machine, n'inventorie pas tous les chunks decharges et ne valide pas les machines deplacees ou clonees par un autre mod. Voir le guide operateur avant une recette sur copie du monde existant.
7. **Interactions.** La phase 2 couvre des paquets malformes via leur codec et
   handler natifs, ainsi que du travail alimente et la collecte aux frontieres.
   Le client reel, Quarry et Advanced Quarry ont passe le controle local de
   phase 6, puis le monde reel a passe son demarrage isole et son essai de claim
   en phase 7. Les
   mutations synchrones de marqueurs, certains modules avances et
   le fonctionnement prolonge restent distincts de ces essais bornes.
8. **Versions exactes.** Les hooks sont lies aux versions listees ci-dessous. Une mise a jour de QuarryPlus ou FTB necessite une nouvelle validation. Ne pas desactiver les controles d'injection pour faire demarrer une version incompatible.

**Decision de livraison : RC3 a termine les validations prevues et peut etre
propose pour installation. Ne pas modifier le serveur des joueurs sans accord
explicite et sans appliquer la procedure operateur de migration.**

### Prochaine etape recommandee

Conserver cet algorithme. La copie complete a passe la regression, la
comparaison de charge bornee de phase 2, le minage utile apres redemarrage, les
tests legacy, le client local, l'adoption persistante des sept machines et le
test final de claim hostile. La prochaine etape est une installation encadree
sur le serveur principal avec sauvegarde complete, puis une surveillance des
MSPT pendant une charge representative. Mesurer notamment plusieurs grandes
emprises, la generation et une rafale de changements de claims.

Si les recalculs provoquent des pics, commencer par reduire la portee des invalidations et mesurer a nouveau. Un traitement differe sous budget par tick n'est acceptable que si les machines concernees restent suspendues jusqu'a leur revalidation ; il ne faut pas prolonger une ancienne autorisation pour gagner du temps.

Il n'y a plus de choix technique ni de proprietaire a trancher pour le candidat
RC3. La prochaine action necessite l'accord explicite de l'utilisateur :
preparer et executer le deploiement sur le serveur principal.

## 7. Versions et isolation

| Composant | Version du laboratoire |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.248 |
| QuarryPlus / Additional Enchanted Miner | 21.1.162 |
| FTB Chunks | 2101.1.21 |
| FTB Teams | 2101.1.10 |
| FTB Library | 2101.1.35 |
| Architectury | 13.0.11 |
| ScalableCatsForce | 3.7.1-build-11 |
| ExtendedAE, ajout au serveur de test pour correspondre au client | 1.21-2.2.35-neoforge |
| Java | Microsoft OpenJDK 21.0.7, 64 bits |

Le profil minimal utilise `quarryguard-lab/runtime/`, un monde plat distinct et un plafond Java de 2 Gio. Le profil complet utilise `quarryguard-lab/full-runtime/` et 4 Gio. Minecraft ecoute seulement `127.0.0.1:25585`, Voice Chat seulement le loopback sur 25586 ; l'annonce LAN NeoForge et MiniServ sont desactives dans la copie. Les bibliotheques sont lues depuis le serveur existant, mais mods, configurations, logs et sauvegardes du laboratoire sont distincts. Les serveurs sont arretes a la fin des essais.

Verification initiale : les 145 JAR du serveur et les 168 JAR du client avaient
les memes empreintes SHA-256 que l'inventaire pris avant cette phase. Pour le
controle client de phase 6, ExtendedAE a ensuite ete ajoute uniquement a la
copie serveur du laboratoire afin de correspondre a l'instance Prism `copie`.
Le depot GitHub local et les installations de production n'ont pas ete modifies.

Empreinte du candidat serveur final sans banc de test :

```text
ascendant-quarryguard-0.1.0-rc3.jar
SHA-256 2469A27765E1493FA0CA91083D72C9069BC0926E67A5E03345A17F72A3198787
```

Le chemin `integration/build/candidate/` contient ce candidat et son manifeste
`package-manifest-rc3.json`. Les anciens binaires restent archives : RC1 et son
binaire de laboratoire dans `phase5-adf0/`, phase 4 dans `phase4-5a46/`, phase 3 dans `phase3-ef26/`,
reproducteur du defaut A693 dans `frame-reproducer-a693/`. Les binaires
EF26 et A693 ne contiennent pas la correction des cadres de phase 4.

## 8. Fichiers et reproduction

- [Algorithmes et mesures](research/algorithmes.md).
- [Invalidation FTB et droits](research/ftb-invalidation.md).
- [Points d'interception QuarryPlus et reserves](research/quarry-hooks.md).
- [Tests Java et protocole](core/README.md).
- [Code du prototype](integration/src/main/java/fr/ascendant/quarryguard/GuardHooks.java).
- [Essais natifs](integration/src/main/java/fr/ascendant/quarryguard/LabChecks.java).
- [Validation finale de phase 2](research/validation-phase2.md).
- [Validation finale de phase 3](research/validation-phase3.md).
- [Correction A et validation de phase 4](research/validation-phase4-cadres.md).
- [Outil operateur et candidat de phase 5](research/validation-phase5-operateur.md).
- [Compatibilites finales et essai client de phase 6](research/validation-phase6-compatibilites.md).
- [Validation sur copie du monde reel de phase 7](research/validation-phase7-copie-monde-reel.md).
- [Protocole operateur, sans autorisation de deploiement](research/deploiement-operateur-phase2.md).
- `results/` : sorties de chaque lancement, y compris les essais en echec.

Pour developpeur, uniquement dans ce laboratoire avec ses dependances locales :

```powershell
.\quarryguard-lab\integration\build.ps1
.\quarryguard-lab\integration\prepare-runtime.ps1
.\quarryguard-lab\integration\run-lab.ps1
# Sur la copie complete deja preparee :
.\quarryguard-lab\integration\run-lab.ps1 -Profile full-runtime -MaxHeapGiB 4 -Test regression
.\quarryguard-lab\integration\run-lab.ps1 -Profile full-runtime -MaxHeapGiB 4 -Test loadtest
.\quarryguard-lab\integration\run-lab.ps1 -Profile full-runtime -MaxHeapGiB 4 -Test loadtest -Baseline
```

Ces scripts ne mettent pas a jour Packwiz ni le serveur des joueurs. Le test automatique exige explicitement le nom et le chemin du monde jetable, le mode plat, l'ecoute locale et l'absence de joueurs connectes. Il ne doit pas etre transpose a un monde existant.
