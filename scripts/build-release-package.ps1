param(
    [string]$Repository = "Yauhescha/Java-Screen-Recorder",
    [string]$Notes = "Java Screen Recorder update",
    [switch]$RequireSignature
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectRoot

[xml]$pom = Get-Content (Join-Path $ProjectRoot "pom.xml")
$Version = [string]$pom.project.version
if ([string]::IsNullOrWhiteSpace($Version)) { throw "Could not determine version from pom.xml" }

$ManifestUrl = "https://github.com/$Repository/releases/latest/download/update.json"

Write-Host "Building Java Screen Recorder $Version"
Write-Host "Update channel: $ManifestUrl"

$params = @{
    SkipInstaller = $true
    UpdateManifestUrl = $ManifestUrl
}
if ($RequireSignature) { $params.RequireSignature = $true }
& (Join-Path $PSScriptRoot "build-windows.ps1") @params
if ($LASTEXITCODE -ne 0) { throw "Application build failed." }

$OutDir = Join-Path $ProjectRoot "release-out"
Remove-Item $OutDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$AppDir = Join-Path $ProjectRoot "dist\JavaScreenRecorder"
if (-not (Test-Path (Join-Path $AppDir "JavaScreenRecorder.exe"))) {
    throw "Packaged application was not found: $AppDir"
}

$ArchiveName = "JavaScreenRecorder-$Version-win-x64.zip"
$ArchivePath = Join-Path $OutDir $ArchiveName

# Compress-Archive with the application directory itself keeps JavaScreenRecorder/ as the ZIP root.
Compress-Archive -Path $AppDir -DestinationPath $ArchivePath -CompressionLevel Optimal -Force

& (Join-Path $PSScriptRoot "make-update-manifest.ps1") `
    -Package $ArchivePath `
    -Repository $Repository `
    -Version $Version `
    -Notes $Notes `
    -Output "release-out\update.json"
if ($LASTEXITCODE -ne 0) { throw "Manifest generation failed." }

Write-Host ""
Write-Host "Release package ready:"
Write-Host "  $ArchivePath"
Write-Host "  $(Join-Path $OutDir 'update.json')"
Write-Host ""
Write-Host "A code-signing certificate is optional. If JSR_SIGN_PFX or JSR_SIGN_CERT_SHA1 is configured,"
Write-Host "the launcher inside the ZIP is signed automatically."
