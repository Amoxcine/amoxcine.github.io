# Dependency graph

The graph reads the Packwiz metafiles and the matching NeoForge JARs already installed on a client or server. It writes `mods.csv`, `dependencies.csv`, and `dependencies.dot` to the chosen output directory. Solid arrows mean required dependencies; dashed arrows mean optional dependencies.

```powershell
$jars = @('C:\path\to\client\mods', 'C:\path\to\server\mods')
.\tools\build-mod-dependency-graph.ps1 -PackRoot . -JarDirectories $jars -OutputDirectory .\dependency-graph
dot -Tsvg .\dependency-graph\dependencies.dot -o .\dependency-graph\dependencies.svg
.\tools\scan-mod-references.ps1 -GraphDirectory .\dependency-graph -JarDirectories $jars
```

The graph covers declared NeoForge dependencies and Kotlin language loaders. `scan-mod-references.ps1` checks additional code and content references for selected libraries. A node with no incoming arrows is a review candidate, not proof that its JAR can be removed. Check missing JARs, code references, configuration, and world content before changing Packwiz.
