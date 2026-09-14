# QuarryGuard : geometrie, index et mesures isolees

Date : 4 septembre 2026. Perimetre de cette livraison : `quarryguard-lab/core/`
et ce rapport uniquement. Java 21 pur, sans dependance Minecraft. Aucun changement
de production, lancement du jeu, publication, commit ou push.

## 1. Decision

Retenir **un index creux par dimension, X trie puis Z trie**, avec deux vues
`TreeMap.subMap(min, true, max, true)`. Il ne parcourt jamais les chunks libres
d'une grande emprise, ne materialise pas le rectangle et retourne un temoin stable.
Ce n'est ni un range tree 2D augmente, ni une garantie `O(log N + K)`.

Trois strategies ont ete implementees et comparees : enumeration du rectangle
avec lookup hache, scan des claims de la dimension et index creux. L'index gagne
nettement sur les zones locales entourees de claims eloignes ; il peut perdre
contre un scan quand tous les claims sont inclus ou quand beaucoup de X candidats
ont des Z hors emprise. Les chiffres defavorables figurent ci-dessous.

Pas de bascule heuristique ni de buckets supplementaires : les trois strategies
suffisent a mesurer le compromis demande. Un index a buckets serait une autre
experience, non mesuree ici ; enumerer tous les buckets d'une immense zone libre
reintroduirait une dependance a sa surface. Le choix actuel privilegie une seule
representation, un contrat deterministe et un cout lie aux claims effectivement
stockes, pas un optimum universel.

## 2. Geometrie et preuve

Pour chaque axe, les blocs d'un chunk `c` sont `[16c, 16c+15]`. Pour un intervalle
ferme de blocs `[a,b]`, les chunks touches sont exactement
`[floor(a/16), floor(b/16)]`. La monotonie du plancher donne la couverture ; chaque
chunk intermediaire intersecte l'intervalle, donc il n'y a pas de chunk ajoute.
La projection du rectangle est le produit cartesien des deux intervalles.

`ChunkRect.fromBlocks` rejette les inversions **avant** conversion, puis utilise
`Math.floorDiv(coord, 16)`. Les differences sont calculees apres promotion en
`long`, et leur produit par `multiplyExact`. Un depassement est transforme en
`IllegalArgumentException` au constructeur. Ces operations reposent sur le
[contrat Java 21 de Math](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Math.html).

Exemples verifies :

- `-17 -> -2`, `-16 -> -1`, `-1 -> -1`, `15 -> 0`, `16 -> 1`.
- La bordure avancee `[-1,16] x [-1,16]` touche 9 chunks.
- 256 blocs touches : 16 chunks si alignes, 17 sinon.
- Toutes les coordonnees de blocs `int` se projettent sur `2^56` chunks au maximum.
- Un axe complet de coordonnees **chunks** `int` a une longueur de `2^32`.
- Le plan complet chunks a une aire de `2^64`, non representable en `long` : rejet.
- La bande de largeur `2^32` et profondeur `2^31-1` est acceptee ; une profondeur
  de `2^31` est rejetee. Aucun enroulement, saturation ou aire negative.

Pour l'index, chaque claim est conserve une fois, sous sa dimension exacte puis
ses deux coordonnees. La vue X retient exactement les lignes satisfaisant la borne
X ; la vue Z retient exactement les claims satisfaisant la borne Z de cette ligne.
Le produit des deux conditions est l'appartenance au rectangle. Si un predicat
refuse, le temoin est donc interieur et possede l'equipe stockee ; si tous passent,
aucun claim interdit de l'index n'est omis. L'ordre naturel signe de X puis Z
rend le premier temoin independant de l'ordre d'insertion.

Cette preuve suppose une photographie complete et coherente des claims et une
politique correctement fournie. Elle ne prouve pas la synchronisation avec FTB.

## 3. Complexite

Notations par dimension : `N` claims, `R` lignes X non vides, `n_x` claims dans
la ligne x ; pour une requete, `r` lignes candidates et `K` claims intersectes.
`A` est l'aire en chunks, `P` le cout d'une evaluation du predicat.

| Strategie | Requete sans refus precoce | Memoire |
|---|---|---|
| Lookup pour chaque chunk | `O(A + K*P)` attendu avec dispersion correcte | `O(N)` |
| Scan de la table de claims | `O(N + capacite + K*P)` | `O(N)` |
| X trie puis Z trie | `O(log(R+1) + r + somme_x log(n_x+1) + K*(1+P))` | `O(N+R)` |

La recherche par dimension ajoute un lookup hache. La condition de dispersion
et le cout d'iteration en taille plus capacite sont explicites dans la
[documentation HashMap](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashMap.html).
Le baseline utilise un hash melangeant les deux coordonnees pour ne pas penaliser
artificiellement les grilles denses par le hash lineaire par defaut d'un record.

`TreeMap` donne des recherches/mutations logarithmiques. Les bornes inclusives
evitent un `max+1` qui deborderait a `Integer.MAX_VALUE`.
[Contrat TreeMap Java 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/TreeMap.html).
La formule de requete ci-dessus est notre analyse des deux parcours imbriques,
appuyee sur la recherche des bornes et les iterateurs par successeur dans
[l'implementation OpenJDK 21u](https://raw.githubusercontent.com/openjdk/jdk21u/master/src/java.base/share/classes/java/util/TreeMap.java).

Une insertion, un remplacement ou un retrait coute
`O(log(R+1) + log(n_x+1))`, hors dimension. Les lignes/dimensions vides sont retirees.
Le pire parcours peut visiter toutes les lignes meme avec `K=0` : l'ordre X/Z
est asymetrique. Avec un predicat constant, le pire cas est lineaire dans les
claims stockes, puisque `log(n_x+1) <= n_x` a une constante pres et `R <= N`.
Il n'est pas constant ni uniquement proportionnel au nombre de claims touches.
Les objets d'arbres et les vues ont un cout memoire/allocation non mesure en octets.

## 4. API et integration

Sources principales :

- [ChunkRect.java](../core/src/main/java/fr/ascendant/quarryguard/core/ChunkRect.java)
- [ClaimIndex.java](../core/src/main/java/fr/ascendant/quarryguard/core/ClaimIndex.java)

Package : `fr.ascendant.quarryguard.core`. Chemin a inclure au build principal :
`core/src/main/java`. Les baselines et executables restent sous `src/test/java`.

`put(String,int,int,UUID)` et `remove(String,int,int)` renvoient un booleen de
mutation effective. `queryDenied(String,ChunkRect,Predicate<UUID>)` renvoie
`Optional<ClaimIndex.Witness>`, avec `dim/x/z/team`. `clear()` supprime tout.
Dimensions non nulles/non blanches, autres references requises non nulles.

`revision()` commence a zero, augmente une fois par mutation effective, y compris
changement d'equipe et clear non vide. Une operation identique est un no-op.
Le clear ne remet jamais le compteur a zero ; un depassement du compteur refuse
la mutation avant changement. **Ce compteur ne represente pas les alliances,
membres, bypass, proprietaires de machines ou etat de chargement FTB.**

Le predicat est rappele pour chaque claim intersecte, meme si l'equipe a deja
ete vue. Ses exceptions remontent. Une tentative de mutation de l'index depuis
le predicat est refusee avant effet ; les lectures imbriquees restent possibles.
Toutes les operations doivent rester sur le meme thread ou sous une serialisation
externe. La detection de reentrance n'est pas un verrou multithread.

La collecte demandee par l'integration est supportee et testee :

```java
Set<UUID> owners = new HashSet<>();
index.queryDenied(dim, rectangle, team -> {
    owners.add(team);
    return true;
});
Set<UUID> coverage = Set.copyOf(owners);
```

Un cache **de couverture geometrique** doit inclure dimension, rectangle,
revision et identite/epoque de l'index. Un remplacement d'instance repart de zero
et doit invalider ce cache. Capture du compteur, collecte et utilisation doivent
etre coherentes avec les mutations. Chaque travail doit reevaluer les droits
actuels de chaque equipe collectee. Cette reduction par equipe n'est valide que
pour une regle reellement par equipe, pas pour un droit dependant de la position.
Le noyau ne contient aucun cache de couverture ni d'autorisation.

Verifier **separement** le chunk machine hors emprise comme un rectangle singleton,
et les effets sur les marqueurs exterieurs. Ne pas construire une enveloppe qui
englobe arbitrairement l'espace entre machine et emprise. Ownership, revision FTB
globale, disponibilite complete du gestionnaire et interceptions avant effets
restent a la charge de l'integration, comme dans le
[rapport precedent](../../refonte-ascendant-2026-09-04/quarry/rapport-quarryguard.md).

## 5. Correctness executee

Compilation `javac --release 21 -encoding UTF-8 -Xlint:all -Werror` reussie avec
Microsoft OpenJDK 21.0.7+6-LTS de Prism `java-runtime-delta/bin`.
Le premier essai confine ne pouvait pas ecrire les `.class`. La relance autorisee
a compile et execute les tests uniquement dans le laboratoire.

Resultat final dans [tests.txt](../core/results/tests.txt) :

```text
PASS: 915730 assertions; fixed seeds; 10000 exact-area cases;
2000 block-projection cases; 8000 mutation/query steps.
```

Couverture : negatives, maxima/minima `int`, bornes inclusives, inversion avant
arrondi, aire verifiee par oracle `BigInteger`, projection comparee a enumeration
de blocs bornee, claim central et aux quatre coins, zones immenses creuses,
dimension absente et multiple, claims exterieurs, 4096 claims denses tous permis,
premier/dernier refus, suppression/remplacement/clear/no-ops, compteur jusqu'au
debordement force en test, predicat vivant et exceptions, reentrance, collecte
distincte et invalidation de couverture, machine singleton, oracle independant
filtre/trie et comparaison des deux baselines apres mutations pseudo-aleatoires.
Les graines et bornes sont fixes ; aucun tirage n'enumere une grande aire non bornee.

Le nombre d'assertions est surtout constitue de verifications de couverture de
blocs ; ce n'est pas 915730 scenarios distincts ni une preuve de securite en jeu.

## 6. Protocole de performance

Machine locale : Windows 11 amd64, Intel Core i5-13600K, 20 processeurs logiques
vus par Java, Microsoft JDK 21.0.7. JVM `-Xms128m -Xmx512m`.
Mesures du 4 septembre 2026 entre 11:57:46 et 11:58:33 UTC environ.

- 3 processus JVM frais, 13 scenarios, 3 strategies dont 2 omises pour les aires
  geantes : 37 combinaisons mesurees par processus.
- Prechauffage de 150 ms minimum par combinaison, puis 5 repetitions de 513
  requetes individuelles : 2565 mesures par combinaison et processus.
- 64 requetes preconstruites cycliques, avec decalages deterministes dans les
  scenarios locaux ; les cas limites/denses complets repetent la meme emprise.
- Ordre des strategies tourne selon scenario et processus. Index construits
  avant mesure ; chaque jeu est valide contre les baselines avant chronometrage.
- `System.nanoTime` encadre chaque requete, sans generation de donnees, ecriture
  CSV ni compteurs dans la section chronometree. Les temoins sont consommes dans
  une somme publiee en variable volatile hors chronometrage.
- Percentile de rang `ceil(p*n)-1` sur les mesures triees. Temps bruts conserves.
  Les compteurs de predicat sont releves separement sur les memes requetes.
- Enumeration naive interdite **avant toute boucle** si aire > 65536 chunks,
  y compris si la dimension est absente. Les boucles utilisent des compteurs long.
- Budget de 90 secondes controle entre scenarios dans chaque processus ; jeux
  fixes bornes. Durees observees : 15.355, 14.993 et 15.664 s, soit 46.012 s.

Les exemples officiels JMH motivent la consommation du resultat et les processus
separes pour limiter les optimisations de code mort et la contamination des profils :
[DeadCode](https://raw.githubusercontent.com/openjdk/jmh/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_08_DeadCode.java),
[Forking](https://raw.githubusercontent.com/openjdk/jmh/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_12_Forking.java).
**Ce harness maison n'est pas JMH** : prechauffage court, profils encore partages
entre scenarios d'un processus, machine non dediee, allocations et GC non profiles,
predicat synthetique tres peu couteux. Pas de barre de confiance statistique.

L'horloge donne ici des echantillons quantifies par pas visibles de 100 ns et des
p50 a zero pour certaines requetes tres courtes. Ces zeros signifient **sous la
resolution exploitable**, pas cout nul. La difference de deux lectures consecutives
a elle-meme un p50 nul ; aucun overhead n'est soustrait.
[System.nanoTime ne garantit pas une resolution nanoseconde](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/System.html#nanoTime()).

## 7. Resultats

Chaque cellule contient `p50 / p95 / p99` en **microsecondes**. Pour chaque
percentile, on prend la mediane des trois valeurs par processus, et non un
percentile sur des donnees fusionnees. Les CSV gardent chaque processus et les
temps individuels. `~0` designe un zero quantifie non interpretable comme temps nul.

| Scenario | Naif | Scan claims | Index creux |
|---|---:|---:|---:|
| Dimension absente | ~0 / 0.1 / 0.1 | ~0 / 0.1 / 0.1 | ~0 / 0.1 / 0.1 |
| 20k eloignes, zone 16x16 | 1.2 / 1.5 / 2.3 | 135.3 / 154.5 / 204.8 | ~0 / 0.1 / 0.1 |
| 20k eloignes, zone 256x256 | 768.8 / 871.7 / 1206.2 | 133.8 / 151.7 / 177.8 | ~0 / 0.1 / 0.1 |
| 20032 claims, 31 touches tous permis | 777.3 / 873.3 / 1149.7 | 138.8 / 162.9 / 231.4 | 0.6 / 0.7 / 0.8 |
| 4096 denses, tous permis | 29.5 / 34.2 / 54.9 | 17.8 / 31.1 / 49.4 | 22.3 / 33.2 / 57.8 |
| Dense, fenetre 8x8 tous permis | 0.4 / 0.5 / 0.6 | 34.7 / 39.4 / 51.6 | 0.7 / 0.8 / 1.0 |
| Dense, premier X/Z refuse | ~0 / 0.1 / 0.1 | 8.4 / 9.9 / 11.0 | 0.1 / 0.1 / 0.1 |
| Dense, dernier X/Z refuse | 31.3 / 33.2 / 40.1 | 23.0 / 26.5 / 34.3 | 19.4 / 21.3 / 25.6 |
| 20k lignes X, aucun Z touche | 173.1 / 197.5 / 285.6 | 148.9 / 167.5 / 221.6 | 266.7 / 319.8 / 647.3 |
| Une ligne X, 20k Z, fenetres mixtes | 0.8 / 1.0 / 1.4 | 141.3 / 208.4 / 239.7 | ~0 / 0.1 / 0.2 |
| Aire 4000000004000000001, tous permis | OMIS : plafond | 188.7 / 205.5 / 253.1 | 347.8 / 418.5 / 740.3 |
| Axe X int complet, deux claims permis | OMIS : plafond | 0.1 / 0.1 / 0.1 | 0.1 / 0.1 / 0.1 |
| Huit dimensions, 9 a 16 touches | 0.8 / 1.0 / 1.2 | 4.8 / 5.3 / 13.1 | 0.3 / 0.4 / 0.5 |

Controles anti-resultats flatteurs :

- Dense tous permis : **4096 appels de predicat pour les trois strategies**,
  meme si les 4096 claims partagent une equipe. Le scan est plus rapide en mediane.
- Aire geante : **20032 appels**, sans refus precoce ; le scan gagne ici aussi.
- Bande X sans intersection Z : **0 appel**, mais 20000 lignes candidates pour
  l'index ; son cout ne se reduit donc pas au nombre d'appels du predicat.
- Premier/dernier signifient ordre X/Z. Le scan hache rencontre le refus apres
  respectivement 2483 et 3714 appels, l'index/naif apres 1 et 4096. Ne pas traiter
  ces cas comme des travaux identiques ni utiliser leur seul ratio pour conclure.
- Une ligne X / nombreux Z : les fenetres se decalent aussi en X et peuvent manquer
  completement la ligne ; compteurs 0 a 16. Ce scenario n'est pas un test de scan
  complet des 20000 claims autorises.

Variabilite : p50 de l'index dense tous permis entre 14.0 et 30.1 us selon JVM,
contre 13.2 a 24.2 us pour le scan. L'index creux local 31 claims est entre 0.4 et
0.8 us ; les petits ecarts ne justifient pas un reglage de seuil universel.
`build_ns` est un simple relevement de construction, non prechauffe/replique par
structure ; il ne constitue pas un benchmark fiable de mutations ou de memoire.

**Aucune mesure Minecraft, FTB, MSPT, debit serveur, cout de permission reel,
allocation par tick ou impact avec plusieurs machines n'a ete effectuee.**
Ne convertir aucun de ces chiffres en promesse zero MSPT.

## 8. Reproduction et livrables

Depuis `quarryguard-lab/core`, lancer `./run.ps1 -TestsOnly` ou
`./run.ps1 -Forks 3`. Possibilite de fournir `-JavaBin` pour un autre JDK 21.
Pas de telechargement de dependances ; les sorties restent dans ce dossier.

- [README et contrat d'integration](../core/README.md)
- [Script reproductible](../core/run.ps1)
- [Tests deterministes](../core/src/test/java/fr/ascendant/quarryguard/core/CoreTest.java)
- [Baselines naif/scan](../core/src/test/java/fr/ascendant/quarryguard/core/Baselines.java)
- [Harness de mesure](../core/src/test/java/fr/ascendant/quarryguard/core/Benchmark.java)
- [Tests executes](../core/results/tests.txt), [compilation sans diagnostic](../core/results/compile.txt)
- Syntheses : [JVM 1](../core/results/benchmark-1.csv), [JVM 2](../core/results/benchmark-2.csv), [JVM 3](../core/results/benchmark-3.csv)
- Mesures individuelles : [JVM 1](../core/results/samples-1.csv), [JVM 2](../core/results/samples-2.csv), [JVM 3](../core/results/samples-3.csv)
- Journaux : [JVM 1](../core/results/benchmark-1.txt), [JVM 2](../core/results/benchmark-2.txt), [JVM 3](../core/results/benchmark-3.txt)

Prochaine validation hors de ce perimetre : alimentation complete et synchronisee
depuis FTB, politique vivante, cycle de vie des caches geometriques et mesures
sur une copie du serveur. Le noyau ne revendique pas ces validations.
