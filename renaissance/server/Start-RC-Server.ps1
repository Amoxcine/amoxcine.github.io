#requires -Version 5.1
param([string]$JavaPath = '', [ValidateRange(4,12)][int]$MemoryGB = 6)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($JavaPath)) {
    $prismJava = Join-Path $env:APPDATA 'PrismLauncher/java/java-runtime-delta/bin/java.exe'
    if (Test-Path -LiteralPath $prismJava) { $JavaPath = $prismJava }
    else { $JavaPath = (Get-Command java -ErrorAction Stop).Source }
}
$probe = New-Object Diagnostics.Process
$probe.StartInfo.FileName = $JavaPath
$probe.StartInfo.Arguments = '-version'
$probe.StartInfo.UseShellExecute = $false
$probe.StartInfo.CreateNoWindow = $true
$probe.StartInfo.RedirectStandardError = $true
$null = $probe.Start()
$version = $probe.StandardError.ReadToEnd()
$probe.WaitForExit()
$probe.Dispose()
if ($version -notmatch 'version "21[.\"]') { throw "Java 21 requis. Version trouvee : $version" }
if ((Get-Content -LiteralPath 'server.properties') -notcontains 'server-ip=127.0.0.1') {
    throw 'Ce lanceur est reserve au serveur de test local.'
}
if ((Get-Content -LiteralPath 'eula.txt') -notcontains 'eula=true') { throw 'EULA non acceptee.' }
Write-Host 'Serveur prive Renaissance RC1. Connexion : 127.0.0.1:25575. Pour arreter : stop.'
& $JavaPath '-Xms1G' "-Xmx${MemoryGB}G" '@libraries/net/neoforged/neoforge/21.1.248/win_args.txt' 'nogui'
exit $LASTEXITCODE
