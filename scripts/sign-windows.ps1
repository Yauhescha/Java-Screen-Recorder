param(
    [Parameter(Mandatory=$true)][string]$File,
    [string]$PfxPath = $env:JSR_SIGN_PFX,
    [string]$PfxPassword = $env:JSR_SIGN_PFX_PASSWORD,
    [string]$CertThumbprint = $env:JSR_SIGN_CERT_SHA1,
    [string]$TimestampUrl = "http://timestamp.digicert.com",
    [switch]$Required
)

$ErrorActionPreference = "Stop"

function Find-SignTool {
    $cmd = Get-Command signtool.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }

    $kits = Join-Path ${env:ProgramFiles(x86)} "Windows Kits\10\bin"
    if (Test-Path $kits) {
        $found = Get-ChildItem $kits -Recurse -Filter signtool.exe -File -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -match '\\x64\\signtool\.exe$' } |
            Sort-Object FullName -Descending |
            Select-Object -First 1
        if ($found) { return $found.FullName }
    }
    return $null
}

if (-not (Test-Path $File)) { throw "File to sign does not exist: $File" }
$SignTool = Find-SignTool
$HasPfx = $PfxPath -and (Test-Path $PfxPath)
$HasThumbprint = -not [string]::IsNullOrWhiteSpace($CertThumbprint)

if (-not $SignTool -or (-not $HasPfx -and -not $HasThumbprint)) {
    $message = "Code signing skipped. Install Windows SDK signtool and provide JSR_SIGN_PFX (+ JSR_SIGN_PFX_PASSWORD) or JSR_SIGN_CERT_SHA1."
    if ($Required) { throw $message }
    Write-Warning $message
    return
}

$args = @("sign", "/fd", "SHA256", "/td", "SHA256", "/tr", $TimestampUrl)
if ($HasPfx) {
    $args += @("/f", (Resolve-Path $PfxPath).Path)
    if (-not [string]::IsNullOrEmpty($PfxPassword)) { $args += @("/p", $PfxPassword) }
} else {
    $args += @("/sha1", $CertThumbprint)
}
$args += (Resolve-Path $File).Path

Write-Host "Signing: $File"
& $SignTool @args
if ($LASTEXITCODE -ne 0) { throw "signtool failed for $File" }

& $SignTool verify /pa /v (Resolve-Path $File).Path | Out-Host
if ($LASTEXITCODE -ne 0) { throw "Signature verification failed for $File" }
