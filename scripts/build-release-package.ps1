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

$AppDir = Join-Path $ProjectRoot "dist\\JavaScreenRecorder"
if (-not (Test-Path (Join-Path $AppDir "JavaScreenRecorder.exe"))) {
    throw "Packaged application was not found: $AppDir"
}

$ArchiveName = "JavaScreenRecorder-$Version-win-x64.zip"
$ArchivePath = Join-Path $OutDir $ArchiveName

# File entries only: compatible with old Java ZipInputStream updater versions.
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
if (Test-Path $ArchivePath) { Remove-Item $ArchivePath -Force }

$archive = [System.IO.Compression.ZipFile]::Open(
    $ArchivePath,
    [System.IO.Compression.ZipArchiveMode]::Create
)
try {
    Get-ChildItem -LiteralPath $AppDir -Recurse -File | ForEach-Object {
        $relative = $_.FullName.Substring($AppDir.Length).TrimStart([char[]]@([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar))
        $entryName = "JavaScreenRecorder/" + ($relative -replace '\\', '/')
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $archive,
            $_.FullName,
            $entryName,
            [System.IO.Compression.CompressionLevel]::Optimal
        ) | Out-Null
    }
}
finally {
    $archive.Dispose()
}

$UpdaterPath = Join-Path $OutDir "JavaScreenRecorderUpdater-$Version.exe"
& (Join-Path $PSScriptRoot "build-portable-updater.ps1") `
    -Repository $Repository `
    -Output $UpdaterPath
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $UpdaterPath)) {
    throw "Portable updater build failed."
}

# Sign the external updater too when a certificate is configured. This is optional.
& (Join-Path $PSScriptRoot "sign-windows.ps1") `
    -File $UpdaterPath `
    -Required:$RequireSignature

& (Join-Path $PSScriptRoot "make-update-manifest.ps1") `
    -Package $ArchivePath `
    -Updater $UpdaterPath `
    -Repository $Repository `
    -Version $Version `
    -Notes $Notes `
    -Output "release-out\\update.json"
if ($LASTEXITCODE -ne 0) { throw "Manifest generation failed." }

Write-Host ""
Write-Host "Release package ready:"
Write-Host "  $ArchivePath"
Write-Host "  $UpdaterPath"
Write-Host "  $(Join-Path $OutDir 'update.json')"
Write-Host ""
Write-Host "Users download the ZIP. Automatic updates use the small updater EXE."
