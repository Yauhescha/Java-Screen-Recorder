param(
    [string]$Repository = "Yauhescha/Java-Screen-Recorder",
    [Parameter(Mandatory=$true)][string]$Output
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$Source = Join-Path $ProjectRoot "tools\PortableUpdater.cs"
if (-not (Test-Path $Source)) { throw "Updater source not found: $Source" }

$ManifestUrl = "https://github.com/$Repository/releases/latest/download/update.json"
$TempSource = Join-Path $env:TEMP ("JavaScreenRecorderUpdater-" + [Guid]::NewGuid().ToString("N") + ".cs")
$sourceText = [IO.File]::ReadAllText($Source)
$sourceText = $sourceText.Replace("@@MANIFEST_URL@@", $ManifestUrl.Replace("\", "\\").Replace('"', '\"'))
[IO.File]::WriteAllText($TempSource, $sourceText, (New-Object Text.UTF8Encoding($false)))

try {
    $cscCandidates = @(
        "$env:WINDIR\Microsoft.NET\Framework64\v4.0.30319\csc.exe",
        "$env:WINDIR\Microsoft.NET\Framework\v4.0.30319\csc.exe"
    )
    $csc = $cscCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $csc) { throw "The .NET Framework C# compiler (csc.exe) was not found." }

    $outDir = Split-Path -Parent $Output
    if ($outDir) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }

    & $csc /nologo /target:winexe /optimize+ /platform:anycpu `
        /reference:System.dll `
        /reference:System.Core.dll `
        /reference:System.Drawing.dll `
        /reference:System.Windows.Forms.dll `
        /reference:System.IO.Compression.dll `
        /reference:System.IO.Compression.FileSystem.dll `
        /out:$Output $TempSource
    if ($LASTEXITCODE -ne 0) { throw "Failed to compile JavaScreenRecorderUpdater.exe" }

    Write-Host "Portable updater created: $Output"
}
finally {
    Remove-Item $TempSource -Force -ErrorAction SilentlyContinue
}
