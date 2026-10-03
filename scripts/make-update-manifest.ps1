param(
    [Parameter(Mandatory=$true)][string]$Package,
    [Parameter(Mandatory=$true)][string]$Updater,
    [string]$Repository = "Yauhescha/Java-Screen-Recorder",
    [string]$Version = "",
    [string]$Notes = "Java Screen Recorder update",
    [string]$Output = "release-out\\update.json"
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectRoot

if (-not (Test-Path $Package)) { throw "Package not found: $Package" }
if (-not (Test-Path $Updater)) { throw "Updater not found: $Updater" }

if ([string]::IsNullOrWhiteSpace($Version)) {
    [xml]$pom = Get-Content (Join-Path $ProjectRoot "pom.xml")
    $Version = [string]$pom.project.version
}
if ([string]::IsNullOrWhiteSpace($Version)) { throw "Could not determine version from pom.xml" }

$packagePath = (Resolve-Path $Package).Path
$updaterPath = (Resolve-Path $Updater).Path
$packageName = Split-Path $packagePath -Leaf
$updaterName = Split-Path $updaterPath -Leaf
$baseUrl = "https://github.com/$Repository/releases/download/v$Version"
$packageUrl = "$baseUrl/$packageName"
$updaterUrl = "$baseUrl/$updaterName"
$packageHash = (Get-FileHash -Algorithm SHA256 $packagePath).Hash.ToUpperInvariant()
$updaterHash = (Get-FileHash -Algorithm SHA256 $updaterPath).Hash.ToUpperInvariant()

$outPath = Join-Path $ProjectRoot $Output
$outDir = Split-Path -Parent $outPath
if ($outDir) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }

# packageUrl/packageType/sha256 intentionally describe the tiny external updater.
# Recorder versions 0.10.x already understand these fields and can therefore
# bootstrap themselves onto the fixed updater without first installing 0.11.2 manually.
# The updater then reads payloadUrl/payloadSha256 and installs the portable ZIP.
$payload = [ordered]@{
    version = $Version
    packageUrl = $updaterUrl
    packageType = "exe"
    sha256 = $updaterHash
    payloadUrl = $packageUrl
    payloadSha256 = $packageHash
    payloadEntryExe = "JavaScreenRecorder/JavaScreenRecorder.exe"
    notes = $Notes
}
$payload | ConvertTo-Json -Depth 3 | Set-Content -Path $outPath -Encoding UTF8

Write-Host "Update manifest created: $outPath"
Write-Host "Version: $Version"
Write-Host "Updater: $updaterUrl"
Write-Host "Updater SHA-256: $updaterHash"
Write-Host "Payload: $packageUrl"
Write-Host "Payload SHA-256: $packageHash"
