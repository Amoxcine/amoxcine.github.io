# Ascendant QuarryGuard

Mod serveur NeoForge pour empecher QuarryPlus de construire, nettoyer ou miner
dans les claims FTB Chunks d'une autre equipe lorsque la machine est placee a
l'exterieur du claim.

## Etat du projet

- Minecraft 1.21.1
- NeoForge 21.1.248
- QuarryPlus 21.1.162
- FTB Chunks 2101.1.21
- FTB Teams 2101.1.10
- Java 21
- Dernier candidat valide : `0.1.0-rc3`

Le binaire exact valide est conserve dans
`dist/ascendant-quarryguard-0.1.0-rc3.jar`.

```text
SHA-256 2469A27765E1493FA0CA91083D72C9069BC0926E67A5E03345A17F72A3198787
```

Ne pas reconstruire un autre binaire sous le nom RC3. Toute modification doit
recevoir un nouveau numero de candidat et repasser les validations Minecraft.

## Structure

- `src/main/java` : noyau, integration NeoForge, commandes operateur et bancs
  d'essai Minecraft.
- `src/main/resources` : manifeste NeoForge, configuration Mixin et tag de
  securite des deplacements.
- `src/test/java` : tests et benchmarks autonomes du noyau spatial.
- `docs` : analyses, limites et comptes rendus de validation.
- `dist` : RC3 valide et son manifeste de paquetage.
- `build.ps1` : compilation du JAR de laboratoire avec les bibliotheques d'un
  serveur Ascendant local.
- `package-candidate.ps1` : retire les classes de laboratoire et produit un
  candidat serveur.
- `run-core-tests.ps1` : tests algorithmiques sans lancer Minecraft.

## Tester le noyau

Java 21 doit etre disponible dans `JAVA_HOME`, ou son dossier `bin` peut etre
fourni explicitement.

```powershell
.\run-core-tests.ps1 -TestsOnly
```

Exemple avec le Java fourni par Prism Launcher :

```powershell
.\run-core-tests.ps1 `
  -JavaBin "$env:APPDATA\PrismLauncher\java\java-runtime-delta\bin" `
  -TestsOnly
```

## Compiler l'integration NeoForge

Le dossier serveur utilise comme classpath doit contenir les versions exactes
listees plus haut, avec ses dossiers `libraries` et `mods`.

```powershell
.\build.ps1 `
  -ServerRoot "C:\chemin\vers\Ascendant-Server" `
  -JavaHome "$env:APPDATA\PrismLauncher\java\java-runtime-delta"
```

La compilation produit dans `build` un JAR protege de laboratoire, un pilote
sans protection pour les comparaisons et `build-pair.json`.

## Creer un futur candidat

Cette commande doit etre executee uniquement apres une compilation complete.
Utiliser un nouveau numero, par exemple RC4, puis refaire tous les tests avant
un deploiement.

```powershell
.\package-candidate.ps1 -CandidateLabel rc4
```

Le paquetage verifie que les classes de laboratoire sont absentes du candidat,
que les Mixins de protection sont presents et que les classes utilisent Java
21. Le resultat et son manifeste sont places dans `build/candidate`.

## Deploiement

QuarryGuard est un mod serveur. Arreter le serveur, sauvegarder le monde, placer
le JAR valide dans `mods`, puis redemarrer. La ligne `QuarryGuard ready` doit
apparaitre dans le journal. Les anciennes quarries sans proprietaire doivent
etre traitees avec la procedure d'adoption decrite dans `docs`.

Le dossier `docs` conserve aussi les limites connues : versions strictement
epinglees, invalidation globale apres mutation de claim et necessite de
revalider toute mise a jour de QuarryPlus ou FTB.

## Licence

MIT. Voir `LICENSE`.
