# Packages an already compiled guarded lab JAR. Never builds or starts Java.
[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^rc[1-9][0-9]*$')]
    [string]$CandidateLabel
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$build = Join-Path $PSScriptRoot 'build'
$pairPath = Join-Path $build 'build-pair.json'
$candidateDir = Join-Path $build 'candidate'
$output = Join-Path $candidateDir "ascendant-quarryguard-0.1.0-$CandidateLabel.jar"
$manifestPath = Join-Path $candidateDir "package-manifest-$CandidateLabel.json"
$metadata = 'META-INF/neoforge.mods.toml'
$prefix = 'fr/ascendant/quarryguard/'
$excludedPattern = '^fr/ascendant/quarryguard/(?:LabCommands|LabSupport|[^/$]+Checks)(?:\$[^/]+)?\.class$'
$utf8 = [Text.UTF8Encoding]::new($false, $true)

function Read-EntryBytes($Entry) {
    $stream = $Entry.Open()
    $memory = [IO.MemoryStream]::new()
    try { $stream.CopyTo($memory); return ,$memory.ToArray() }
    finally { $stream.Dispose(); $memory.Dispose() }
}

function Get-Sha256([byte[]]$Bytes) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($sha.ComputeHash($Bytes)).Replace('-', '') }
    finally { $sha.Dispose() }
}

function Assert-ByteExact([byte[]]$Before, [byte[]]$After, [string]$Name) {
    if ($Before.Length -ne $After.Length) { throw "Entry length changed: $Name" }
    for ($i = 0; $i -lt $Before.Length; $i++) {
        if ($Before[$i] -ne $After[$i]) { throw "Entry bytes changed: $Name at $i" }
    }
}

function Read-U2([IO.BinaryReader]$Reader) {
    return ([int]$Reader.ReadByte() * 256 + [int]$Reader.ReadByte())
}

function Get-ClassConstants([byte[]]$Bytes, [string]$Name) {
    $memory = [IO.MemoryStream]::new($Bytes, $false)
    $reader = [IO.BinaryReader]::new($memory)
    try {
        if ($Bytes.Length -lt 10 -or [BitConverter]::ToString($reader.ReadBytes(4)) -cne 'CA-FE-BA-BE') {
            throw "Invalid class header: $Name"
        }
        $minor = Read-U2 $reader
        $major = Read-U2 $reader
        if ($major -ne 65 -or $minor -ne 0) { throw "Expected non-preview Java 21 class: $Name" }
        $count = Read-U2 $reader
        for ($i = 1; $i -lt $count; $i++) {
            $tag = $reader.ReadByte()
            $skip = 0
            switch ($tag) {
                1 {
                    $length = Read-U2 $reader
                    $value = $reader.ReadBytes($length)
                    if ($value.Length -ne $length) { throw "Truncated constant: $Name" }
                    # Class identifiers/descriptors are ASCII, also in modified UTF-8.
                    [Text.Encoding]::UTF8.GetString($value)
                }
                { $_ -in 3, 4, 9, 10, 11, 12, 17, 18 } { $skip = 4 }
                { $_ -in 5, 6 } { $skip = 8; $i++ }
                { $_ -in 7, 8, 16, 19, 20 } { $skip = 2 }
                15 { $skip = 3 }
                default { throw "Unknown class constant tag $tag in $Name" }
            }
            if ($skip -gt 0 -and $reader.ReadBytes($skip).Length -ne $skip) {
                throw "Truncated class constant pool: $Name"
            }
        }
    } finally { $reader.Dispose(); $memory.Dispose() }
}

function Assert-NoLabReferences([string[]]$Constants, [string]$Name) {
    foreach ($value in $Constants) {
        if ($value -cmatch 'fr/ascendant/quarryguard/(?:LabCommands|LabSupport|[^/;.$\s]+Checks)(?:\$[^/;.\s]+)?(?:;|$)') {
            throw "Retained class references laboratory code: $Name -> $value"
        }
    }
}

function Convert-CandidateMetadata([string]$Text) {
    $replacements = [ordered]@{
        '(?m)^version="0\.1\.0-lab"\r?$' = "version=`"0.1.0-$CandidateLabel`""
        '(?m)^displayName="Ascendant QuarryGuard \(laboratory\)"\r?$' = 'displayName="Ascendant QuarryGuard (server candidate, not production validated)"'
        "(?m)^description='''Experimental server protection for pinned QuarryPlus and FTB Chunks\. Not a validated production release\.'\x27\x27\r?$" = "description='''Server candidate for pinned QuarryPlus and FTB Chunks. Laboratory commands excluded. Not a validated production release.'''"
    }
    foreach ($pattern in $replacements.Keys) {
        $matches = [regex]::Matches($Text, $pattern)
        if ($matches.Count -ne 1) { throw "Unexpected laboratory metadata field: $pattern" }
        $match = $matches[0]
        $ending = if ($match.Value.EndsWith("`r")) { "`r" } else { '' }
        $Text = $Text.Remove($match.Index, $match.Length).Insert($match.Index, $replacements[$pattern] + $ending)
    }
    return $Text
}

# Refuse redirected output and stale candidates instead of touching another location.
foreach ($path in @($PSScriptRoot, $build, $pairPath, $candidateDir)) {
    if ((Test-Path -LiteralPath $path) -and ((Get-Item -LiteralPath $path -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw "Packaging path is a reparse point: $path"
    }
}
if ((Test-Path -LiteralPath $output) -or (Test-Path -LiteralPath $manifestPath)) {
    throw 'Candidate output already exists; preserve or remove it explicitly before repackaging.'
}
$pairBytes = [IO.File]::ReadAllBytes($pairPath)
$pair = $utf8.GetString($pairBytes).TrimStart([char]0xFEFF) | ConvertFrom-Json
if ($pair.guarded.name -cne 'ascendant-quarryguard-0.1.0-lab.jar' -or $pair.guarded.sha256 -notmatch '^[0-9a-fA-F]{64}$') {
    throw 'Invalid guarded artifact in build/build-pair.json'
}
$labPath = Join-Path $build $pair.guarded.name
if ((Get-Item -LiteralPath $labPath -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Lab JAR is a reparse point' }
$sourcePath = Join-Path $PSScriptRoot 'src/main/java/fr/ascendant/quarryguard/QuarryGuard.java'
$sourceBytes = [IO.File]::ReadAllBytes($sourcePath)
$sourceText = $utf8.GetString($sourceBytes)
if ($sourceText -cmatch '\b(?:[A-Za-z_$][A-Za-z0-9_$]*Checks|LabSupport)\b' -or
    $sourceText -notmatch 'AdoptionCommands\.register\(root\)' -or
    $sourceText -notmatch 'if\s*\(Boolean\.getBoolean\("ascendant\.quarryguard\.lab"\)\)' -or
    $sourceText -notmatch 'Class\.forName\("fr\.ascendant\.quarryguard\.LabCommands"\)' -or
    $sourceText -notmatch '\.getMethod\("register", LiteralArgumentBuilder\.class\)\.invoke\(null, root\)') {
    throw 'Runtime entry source is not isolated from laboratory commands'
}

$labStream = [IO.File]::Open($labPath, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::Read)
$lab = $null
try {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $labSha = [BitConverter]::ToString($sha.ComputeHash($labStream)).Replace('-', '') }
    finally { $sha.Dispose() }
    if ($labSha -ine $pair.guarded.sha256) { throw 'Guarded SHA256 mismatch; no candidate produced' }
    $labStream.Position = 0
    $lab = [IO.Compression.ZipArchive]::new($labStream, [IO.Compression.ZipArchiveMode]::Read, $true)
    $entries = [Collections.Generic.Dictionary[string, object]]::new([StringComparer]::Ordinal)
    $excluded = [Collections.Generic.List[string]]::new()
    $retained = [Collections.Generic.List[string]]::new()
    foreach ($entry in $lab.Entries) {
        $name = $entry.FullName
        if ($name -match '\\|^/|(^|/)\.\.?(/|$)' -or $entries.ContainsKey($name)) { throw "Unsafe or duplicate ZIP entry: $name" }
        if ($name -match '(?i)^META-INF/[^/]+\.(SF|RSA|DSA|EC)$') { throw 'Signed lab JAR cannot be repackaged without invalidating its signature' }
        $entries.Add($name, $entry)
        if ($name -cmatch $excludedPattern) { $excluded.Add($name) } else { $retained.Add($name) }
    }
    foreach ($name in @($metadata, 'ascendant-quarryguard.mixins.json', ($prefix + 'QuarryGuard.class'),
        ($prefix + 'GuardHooks.class'), ($prefix + 'AdoptionCommands.class'), ($prefix + 'AdoptionService.class'),
        ($prefix + 'LabCommands.class'), ($prefix + 'LabSupport.class'), ($prefix + 'AdoptionChecks.class'),
        ($prefix + 'AdoptionRestartChecks.class'))) {
        if (-not $entries.ContainsKey($name)) { throw "Missing lab entry (compile the complete lab first): $name" }
    }
    if (-not $excluded.Contains($prefix + 'AdoptionRestartChecks.class')) { throw 'AdoptionRestartChecks exclusion failed' }
    foreach ($root in @((Join-Path $PSScriptRoot 'src/main/java'))) {
        $root = [IO.Path]::GetFullPath($root)
        foreach ($source in Get-ChildItem -LiteralPath $root -Recurse -Filter '*.java') {
            $name = $source.FullName.Substring($root.Length + 1).Replace('\', '/') -creplace '\.java$', '.class'
            if (-not $entries.ContainsKey($name)) { throw "Source class missing from lab JAR: $name" }
        }
    }
    $config = $utf8.GetString((Read-EntryBytes $entries['ascendant-quarryguard.mixins.json'])) | ConvertFrom-Json
    if (-not $config.required -or $config.mixins.Count -eq 0) { throw 'Required protection mixins missing' }
    foreach ($mixin in $config.mixins) {
        $name = ($config.package + '.' + $mixin).Replace('.', '/') + '.class'
        if (-not $retained.Contains($name)) { throw "Protection mixin missing: $name" }
    }
    $classProofs = [Collections.Generic.List[object]]::new()
    foreach ($name in $retained) {
        if ($name.EndsWith('.class', [StringComparison]::Ordinal)) {
            $bytes = Read-EntryBytes $entries[$name]
            $constants = @(Get-ClassConstants $bytes $name)
            Assert-NoLabReferences $constants $name
            if ($name -ceq ($prefix + 'QuarryGuard.class')) {
                foreach ($required in @('fr.ascendant.quarryguard.LabCommands', 'fr/ascendant/quarryguard/AdoptionCommands',
                    'ascendant.quarryguard.lab', 'forName', 'getMethod', 'invoke')) {
                    if ($constants -cnotcontains $required) { throw "Stale runtime entry class: missing $required" }
                }
            }
            $classProofs.Add([ordered]@{ entry = $name; sha256 = Get-Sha256 $bytes })
        }
    }
    $originalMetadata = Read-EntryBytes $entries[$metadata]
    $candidateMetadata = $utf8.GetBytes((Convert-CandidateMetadata ($utf8.GetString($originalMetadata))))
    $header = $utf8.GetString($candidateMetadata)
    if ($header -notmatch '(?m)^\[\[mixins\]\]\r?$' -or
        $header -notmatch '(?m)^config="ascendant-quarryguard\.mixins\.json"\r?$') { throw 'Protection mixin header missing' }
    $buffer = [IO.MemoryStream]::new()
    try {
        $zip = [IO.Compression.ZipArchive]::new($buffer, [IO.Compression.ZipArchiveMode]::Create, $true)
        try {
            foreach ($name in $retained) {
                $original = $entries[$name]
                $entry = $zip.CreateEntry($name, [IO.Compression.CompressionLevel]::Optimal)
                $entry.LastWriteTime = $original.LastWriteTime
                $entry.ExternalAttributes = $original.ExternalAttributes
                $bytes = if ($name -ceq $metadata) { $candidateMetadata } else { Read-EntryBytes $original }
                $stream = $entry.Open()
                try { $stream.Write($bytes, 0, $bytes.Length) } finally { $stream.Dispose() }
            }
        } finally { $zip.Dispose() }
        $buffer.Position = 0
        $verify = [IO.Compression.ZipArchive]::new($buffer, [IO.Compression.ZipArchiveMode]::Read, $true)
        try {
            if ($verify.Entries.Count -ne $retained.Count) { throw 'Candidate entry count mismatch' }
            foreach ($name in $excluded) {
                if ($null -ne $verify.GetEntry($name)) { throw "Laboratory entry survived: $name" }
            }
            foreach ($name in $retained) {
                $entry = $verify.GetEntry($name)
                if ($null -eq $entry) { throw "Retained entry missing: $name" }
                $expected = if ($name -ceq $metadata) { $candidateMetadata } else { Read-EntryBytes $entries[$name] }
                $actual = Read-EntryBytes $entry
                Assert-ByteExact $expected $actual $name
                if ($name.EndsWith('.class', [StringComparison]::Ordinal)) {
                    Assert-NoLabReferences @(Get-ClassConstants $actual $name) $name
                }
            }
        } finally { $verify.Dispose() }
        $candidateBytes = $buffer.ToArray()
    } finally { $buffer.Dispose() }
    Assert-ByteExact $pairBytes ([IO.File]::ReadAllBytes($pairPath)) 'build-pair.json (concurrent change)'
    $manifest = [ordered]@{
        schemaVersion = 1
        createdUtc = [DateTime]::UtcNow.ToString('o')
        status = 'server-candidate-not-production-validated'
        buildId = $pair.buildId
        buildPairSha256 = Get-Sha256 $pairBytes
        lab = [ordered]@{ name = $pair.guarded.name; sha256 = $labSha }
        candidate = [ordered]@{ name = [IO.Path]::GetFileName($output); sha256 = Get-Sha256 $candidateBytes }
        runtimeSource = [ordered]@{ path = 'src/main/java/fr/ascendant/quarryguard/QuarryGuard.java'; sha256 = Get-Sha256 $sourceBytes }
        excludedEntries = @($excluded | Sort-Object)
        modifiedEntries = @($metadata)
        modifiedMetadataFields = @('version', 'displayName', 'description')
        retainedClasses = @($classProofs.ToArray())
        verification = [ordered]@{
            guardedSha256 = $true; runtimeSourceIsolation = $true; java21Headers = $true
            noLabClassReferences = $true; noLabEntries = $true; adoptionRestartChecksExcluded = $true
            allRetainedEntriesByteExactExceptMetadata = $true; protectionMixinsPresent = $true
            javaExecuted = $false; serverExecuted = $false; productionValidated = $false
        }
    }
    [IO.Directory]::CreateDirectory($candidateDir) | Out-Null
    $stream = [IO.File]::Open($output, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try { $stream.Write($candidateBytes, 0, $candidateBytes.Length) } finally { $stream.Dispose() }
    Assert-ByteExact $candidateBytes ([IO.File]::ReadAllBytes($output)) 'written candidate JAR'
    $manifestBytes = $utf8.GetBytes(($manifest | ConvertTo-Json -Depth 8))
    $stream = [IO.File]::Open($manifestPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try { $stream.Write($manifestBytes, 0, $manifestBytes.Length) } finally { $stream.Dispose() }
    [PSCustomObject]@{ Candidate = $output; Sha256 = $manifest.candidate.sha256; Manifest = $manifestPath; Excluded = $excluded.Count }
} finally {
    if ($null -ne $lab) { $lab.Dispose() }
    $labStream.Dispose()
}
