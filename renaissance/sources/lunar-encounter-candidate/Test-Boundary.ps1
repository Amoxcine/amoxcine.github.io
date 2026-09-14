[CmdletBinding()]
param([Parameter(Mandatory)][string]$Artifact,[Parameter(Mandatory)][string]$Output,[string]$JavaHome='C:/Users/avets/AppData/Roaming/PrismLauncher/java/java-runtime-delta')
$ErrorActionPreference='Stop'
New-Item -ItemType Directory -Path $Output -Force | Out-Null
$q=Join-Path $PSScriptRoot 'qualification'
$deps=Join-Path $PSScriptRoot 'dependencies'
$cp="$Output;$(Join-Path $deps '0084-asm-9.10.1.jar');$(Join-Path $deps '0090-asm-tree-9.10.1.jar')"
& (Join-Path $JavaHome 'bin/javac.exe') --release 21 -proc:none -cp $cp -d $Output (Join-Path $q 'CircuitBoundarySlice.java') (Join-Path $q 'NoCustomPayoutContractTest.java')
if($LASTEXITCODE -ne 0){throw 'Boundary extractor compilation failed'}
$old=Join-Path $PSScriptRoot 'dist/ascendant-lunar-encounter-0.1.1-candidate.jar'
if((Get-FileHash -LiteralPath $old).Hash -ne 'D5114D500B623FE92A8BB59DDD14990EB0828579D32823B87F7CDDF4507825A0'){throw 'Frozen 0.1.1 hash mismatch'}
& (Join-Path $JavaHome 'bin/java.exe') -cp $cp NoCustomPayoutContractTest $Artifact $old
if($LASTEXITCODE -ne 0){throw 'Custom physical payout regression'}
$native=Join-Path $Output 'circuit'
New-Item -ItemType Directory -Path $native -Force | Out-Null
$nativeCp="$native;$Artifact"
$stubs=@((Get-ChildItem (Join-Path $q 'circuit-stubs') -Recurse -Filter '*.java').FullName)
& (Join-Path $JavaHome 'bin/javac.exe') --release 21 -proc:none -cp $nativeCp -d $native $stubs (Join-Path $q 'NativeCircuitBoundaryTest.java') (Join-Path $q 'LunarDamageBoundaryTest.java')
if($LASTEXITCODE -ne 0){throw 'Boundary fixtures compilation failed'}
& (Join-Path $JavaHome 'bin/java.exe') -cp $cp CircuitBoundarySlice $Artifact $native
if($LASTEXITCODE -ne 0){throw 'Delivered bytecode extraction failed'}
& (Join-Path $JavaHome 'bin/java.exe') -Xverify:all -Xmx192m -cp $nativeCp fr.ascendant.lunar.encounter.NativeCircuitBoundaryTest
if($LASTEXITCODE -ne 0){throw 'Delivered circuit boundary failed'}
& (Join-Path $JavaHome 'bin/java.exe') -Xverify:all -Xmx192m -cp $nativeCp fr.ascendant.lunar.encounter.LunarDamageBoundaryTest
if($LASTEXITCODE -ne 0){throw 'Delivered damage boundary failed'}
