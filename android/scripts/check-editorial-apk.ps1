param(
    [Parameter(Mandatory=$true)][string]$Apk,
    [Parameter(Mandatory=$true)][string]$PrivatePackage
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$apkPath = (Resolve-Path -LiteralPath $Apk).Path
$packagePath = (Resolve-Path -LiteralPath $PrivatePackage).Path
$package = Get-Content -LiteralPath $packagePath -Raw -Encoding UTF8 | ConvertFrom-Json
if ($package.schemaVersion -ne 1 -or $package.cards.Count -ne 78) { throw 'Invalid editorial package' }
# Exact-string scan for known long prose only. Does not certify all possible content.
$needles = @($package.cards + $package.phaseAdvice + $package.moonMood |
    ForEach-Object { $_ | Where-Object { $_ -is [string] -and $_.Length -gt 80 } } |
    Select-Object -Unique)
if ($needles.Count -eq 0) { throw 'No reference text to check' }
$archive = [System.IO.Compression.ZipFile]::OpenRead($apkPath)
$hits = [System.Collections.Generic.List[string]]::new()
$checked = 0
try {
    foreach ($entry in $archive.Entries) {
        if ($entry.Length -eq 0) { continue }
        $stream = $entry.Open()
        $buffer = [System.IO.MemoryStream]::new()
        try { $stream.CopyTo($buffer); $bytes = $buffer.ToArray() }
        finally { $stream.Dispose(); $buffer.Dispose() }
        $checked++
        $utf8 = [System.Text.Encoding]::UTF8.GetString($bytes)
        if ($utf8.Contains('Lru/astrosmap/app/editorial/AndroidEditorial;')) {
            $hits.Add($entry.FullName + ': legacy class')
        }
        # UTF-16 can begin on either byte alignment inside resources.arsc.
        $utf16 = [System.Text.Encoding]::Unicode.GetString($bytes)
        $utf16Odd = if ($bytes.Length -gt 1) { [System.Text.Encoding]::Unicode.GetString($bytes, 1, $bytes.Length - 1) } else { '' }
        foreach ($needle in $needles) {
            if ($utf8.Contains($needle) -or $utf16.Contains($needle) -or $utf16Odd.Contains($needle)) {
                $hits.Add($entry.FullName + ': known editorial text')
                break
            }
        }
    }
} finally { $archive.Dispose() }
[pscustomobject]@{
    APK = $apkPath
    SHA256 = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash
    CheckedEntries = $checked
    ReferenceTexts = $needles.Count
    Findings = @($hits.ToArray())
    Scope = 'Known long strings in UTF-8/UTF-16 and legacy class only; not a complete content audit'
} | ConvertTo-Json -Depth 3
if ($hits.Count -gt 0) { exit 1 }
