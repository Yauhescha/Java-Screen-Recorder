param(
    [string]$FfmpegUrl = "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip"
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$ResourceDir = Join-Path $ProjectRoot "src\main\resources\native\windows-x64"
$TargetExe = Join-Path $ResourceDir "ffmpeg.exe"
$TargetProbe = Join-Path $ResourceDir "ffprobe.exe"

New-Item -ItemType Directory -Force -Path $ResourceDir | Out-Null

if ((Test-Path $TargetExe) -and (Test-Path $TargetProbe)) {
    Write-Host "Embedded FFmpeg/FFprobe already exist in: $ResourceDir"
    exit 0
}

$TempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("java-screen-recorder-ffmpeg-" + [Guid]::NewGuid())
$ZipFile = Join-Path $TempRoot "ffmpeg.zip"
$ExtractDir = Join-Path $TempRoot "extract"
$ShaFile = Join-Path $TempRoot "ffmpeg.zip.sha256"

try {
    New-Item -ItemType Directory -Force -Path $TempRoot, $ExtractDir | Out-Null

    Write-Host "Downloading FFmpeg..."
    Invoke-WebRequest -Uri $FfmpegUrl -OutFile $ZipFile -UseBasicParsing

    # Gyan publishes SHA-256 next to the archive. Validate when available.
    try {
        Invoke-WebRequest -Uri ($FfmpegUrl + ".sha256") -OutFile $ShaFile -UseBasicParsing
        $ShaText = (Get-Content $ShaFile -Raw).Trim()
        $Expected = ($ShaText -split '\s+')[0].ToUpperInvariant()
        $Actual = (Get-FileHash -Algorithm SHA256 $ZipFile).Hash.ToUpperInvariant()
        if ($Expected -and $Expected -ne $Actual) {
            throw "FFmpeg SHA-256 mismatch. Expected $Expected, got $Actual"
        }
        Write-Host "FFmpeg SHA-256 verified."
    }
    catch {
        if ($_.Exception.Message -like "*mismatch*") { throw }
        Write-Warning "Could not download/verify the published SHA-256. Continuing with the HTTPS download."
    }

    Write-Host "Extracting FFmpeg archive..."
    Expand-Archive -Path $ZipFile -DestinationPath $ExtractDir -Force

    $Found = Get-ChildItem -Path $ExtractDir -Recurse -Filter "ffmpeg.exe" -File | Select-Object -First 1
    $FoundProbe = Get-ChildItem -Path $ExtractDir -Recurse -Filter "ffprobe.exe" -File | Select-Object -First 1
    if (-not $Found) { throw "ffmpeg.exe was not found inside the downloaded archive." }
    if (-not $FoundProbe) { throw "ffprobe.exe was not found inside the downloaded archive." }

    Copy-Item $Found.FullName $TargetExe -Force
    Copy-Item $FoundProbe.FullName $TargetProbe -Force
    Write-Host "Embedded FFmpeg prepared: $TargetExe"
    Write-Host "Embedded FFprobe prepared: $TargetProbe"
}
finally {
    if (Test-Path $TempRoot) {
        Remove-Item $TempRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}
