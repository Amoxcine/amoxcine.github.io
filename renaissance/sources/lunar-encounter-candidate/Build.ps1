[CmdletBinding()]
param([string]$JavaHome='C:/Users/avets/AppData/Roaming/PrismLauncher/java/java-runtime-delta', [int]$Volume=100)
$ErrorActionPreference='Stop'
$build=Join-Path $PSScriptRoot ('build/'+[DateTime]::UtcNow.ToString('yyyyMMddHHmmssfff'))
$classes=Join-Path $build 'classes'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$deps=(Resolve-Path (Join-Path $PSScriptRoot '../renaissance-controller/build/dependencies')).Path
$mods='C:/Users/avets/AppData/Roaming/PrismLauncher/instances/Ascendant-Renaissance-PRE-RC/minecraft/mods'
$jars=@(Get-ChildItem $deps -Filter '*.jar' | Where-Object { [int]$_.Name.Substring(0,4) -lt 94 })
$jars+=@(Get-ChildItem $mods -Filter '*.jar' | Where-Object Name -Match '^(ftb-(teams|chunks|library)|PlayerRevive|CreativeCore)')
$sqlite=(Resolve-Path (Join-Path $PSScriptRoot '../encounter-prototype/dependencies/sqlite-jdbc-3.53.1.0.jar')).Path
if((Get-FileHash $sqlite).Hash -ne '28ACEECFCC9535645BD19FA988385703C7B89982C1506A6855F5942B4032ECA6'){throw 'SQLite hash mismatch'}
$originalJars=@($jars.FullName)+@($sqlite)
$localDeps=Join-Path $PSScriptRoot 'dependencies'
New-Item -ItemType Directory -Path $localDeps -Force | Out-Null
foreach($file in $originalJars) {
    $dest=Join-Path $localDeps ([IO.Path]::GetFileName($file))
    if(!(Test-Path -LiteralPath $dest) -or (Get-FileHash $dest).Hash -ne (Get-FileHash $file).Hash){Copy-Item -LiteralPath $file -Destination $dest}
}
$jars=@($jars | ForEach-Object {Get-Item -LiteralPath (Join-Path $localDeps $_.Name)})
$sqlite=Join-Path $localDeps ([IO.Path]::GetFileName($sqlite))
$cp=((@($jars.FullName)+@($sqlite)) -join ';').Replace('\','/')
$inputs=@((Get-ChildItem (Join-Path $PSScriptRoot 'src'),(Join-Path $PSScriptRoot 'integration') -Filter '*.java').FullName)
$lines=@('--release','21','-encoding','UTF-8','-proc:none','-classpath',('"'+$cp+'"'),'-d',('"'+$classes.Replace('\','/')+'"'))
$lines+=$inputs | ForEach-Object {'"'+$_.Replace('\','/')+'"'}
$argsFile=Join-Path $build 'javac.args'
[IO.File]::WriteAllLines($argsFile,$lines,[Text.UTF8Encoding]::new($false))
& (Join-Path $JavaHome 'bin/javac.exe') ('@'+$argsFile) 2>&1 | Tee-Object (Join-Path $build 'compile.log')
if($LASTEXITCODE -ne 0){throw 'Native compilation failed'}
Copy-Item -Path (Join-Path $PSScriptRoot 'resources/*') -Destination $classes -Recurse
Copy-Item -LiteralPath $sqlite -Destination (Join-Path $classes 'META-INF/jarjar')
$artifact=Join-Path $build 'ascendant-lunar-encounter-0.1.2-candidate.jar'
& (Join-Path $JavaHome 'bin/jar.exe') --create --file $artifact -C $classes .
if($LASTEXITCODE -ne 0){throw 'Packaging failed'}
$testDir=Join-Path $build 'tests'
New-Item -ItemType Directory -Path $testDir | Out-Null
$testCp="$artifact;$sqlite;$(Join-Path $localDeps '0093-slf4j-api-2.0.9.jar');$testDir"
$testSources=@((Get-ChildItem (Join-Path $PSScriptRoot 'test') -Filter '*.java').FullName)
& (Join-Path $JavaHome 'bin/javac.exe') --release 21 -encoding UTF-8 -proc:none -cp $testCp -d $testDir $testSources
if($LASTEXITCODE -ne 0){throw 'Test compilation failed'}
foreach($suite in @('RaidMachineTest','DurableRaidJournalTest','JournalSemanticTest','EncounterSafetyTest','JoinAdmissionTest','CircuitChoicesTest','SqliteJournalTest','LunarCandidateTest','SuccessOnlyLedgerTest')) {
    & (Join-Path $JavaHome 'bin/java.exe') "-Dorg.sqlite.tmpdir=$testDir" "-Dencounter.volume=$Volume" -Xmx192m -cp $testCp "fr.ascendant.lunar.encounter.$suite" (Join-Path $testDir $suite) 2>&1 | Tee-Object (Join-Path $build "$suite.log")
    if($LASTEXITCODE -ne 0){throw "$suite failed"}
}
$inputs+=$jars.FullName
& (Join-Path $PSScriptRoot 'Test-Boundary.ps1') -Artifact $artifact -Output (Join-Path $build 'boundary') -JavaHome $JavaHome 2>&1 | Tee-Object (Join-Path $build 'boundary.log')
$inputs+=@($sqlite)
$inputs | ForEach-Object {[pscustomobject]@{File=$_;SHA256=(Get-FileHash -LiteralPath $_).Hash}} | Export-Csv (Join-Path $build 'inputs-sha256.csv') -NoTypeInformation
[pscustomobject]@{artifact=$artifact;sha256=(Get-FileHash $artifact).Hash;compiledAgainstActualFtb=$true;testsPassed=$true;nativeRuntimeQualified=$false;releaseCandidate=$false} | ConvertTo-Json | Set-Content (Join-Path $build 'artifact.json')
Get-Content (Join-Path $build 'artifact.json')
