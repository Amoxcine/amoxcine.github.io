param(
    [string]$JavaBin = "$env:APPDATA/PrismLauncher/java/java-runtime-delta/bin",
    [ValidateRange(1, 3)][int]$Forks = 3,
    [switch]$TestsOnly
)
$ErrorActionPreference = 'Stop'
$classes = Join-Path $PSScriptRoot 'build/classes'
$results = Join-Path $PSScriptRoot 'results'
New-Item -ItemType Directory -Force -Path $classes, $results | Out-Null
$sources = @(
    Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src/main/java/fr/ascendant/quarryguard/core') -Filter '*.java'
    Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src/test/java/fr/ascendant/quarryguard/core') -Filter '*.java'
) | ForEach-Object { $_.FullName }
$compileOutput = @(& (Join-Path $JavaBin 'javac.exe') --release 21 -encoding UTF-8 -Xlint:all -Werror -d $classes @sources 2>&1)
$compileExitCode = $LASTEXITCODE
@(
    "Compiler: $JavaBin/javac.exe"
    'Options: --release 21 -encoding UTF-8 -Xlint:all -Werror'
    $compileOutput
    "Exit code: $compileExitCode"
) | Tee-Object -FilePath (Join-Path $results 'compile.txt')
if ($compileExitCode -ne 0) { throw 'Compilation failed' }
& (Join-Path $JavaBin 'java.exe') -Xms128m -Xmx512m -ea -cp $classes fr.ascendant.quarryguard.core.CoreTest 2>&1 | Tee-Object -FilePath (Join-Path $results 'tests.txt')
if ($LASTEXITCODE -ne 0) { throw 'Correctness tests failed' }
if (-not $TestsOnly) {
    for ($fork = 1; $fork -le $Forks; $fork++) {
        & (Join-Path $JavaBin 'java.exe') -Xms128m -Xmx512m -cp $classes fr.ascendant.quarryguard.core.Benchmark $results $fork 2>&1 | Tee-Object -FilePath (Join-Path $results "benchmark-$fork.txt")
        if ($LASTEXITCODE -ne 0) { throw "Benchmark fork $fork failed" }
    }
}
