param(
    [Parameter(Mandatory=$true)][string]$Package,
    [string]$Repository = "Yauhescha/Java-Screen-Recorder",
    [string]$Version = "",
    [string]$Notes = "Java Screen Recorder update",
    [string]$Output = "release-out\update.json"
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectRoot

if (-not (Test-Path $Package)) { throw "Package not found: $Package" }

if ([string]::IsNullOrWhiteSpace($Version)) {
    [xml]$pom = Get-Content (Join-Path $ProjectRoot "pom.xml")
    $Version = [string]$pom.project.version
}
if ([string]::IsNullOrWhiteSpace($Version)) { throw "Could not determine version from pom.xml" }

$packagePath = (Resolve-Path $Package).Path
$packageName = Split-Path $packagePath -Leaf
$packageUrl = "https://github.com/$Repository/releases/download/v$Version/$packageName"
$hash = (Get-FileHash -Algorithm SHA256 $packagePath).Hash.ToUpperInvariant()

$outPath = Join-Path $ProjectRoot $Output
$outDir = Split-Path -Parent $outPath
if ($outDir) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }

$payload = [ordered]@{
    version = $Version
    packageUrl = $packageUrl
    packageType = "zip"
    sha256 = $hash
    entryExe = "JavaScreenRecorder/JavaScreenRecorder.exe"
    notes = $Notes
}
$payload | ConvertTo-Json -Depth 3 | Set-Content -Path $outPath -Encoding UTF8

Write-Host "Update manifest created: $outPath"
Write-Host "Version: $Version"
Write-Host "Package: $packageUrl"
Write-Host "SHA-256: $hash"
