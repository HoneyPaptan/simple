$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
$Repo = "HoneyPaptan/simple"
$Base = if ($env:INSTALL_BASE_URL) { $env:INSTALL_BASE_URL } else { "https://github.com/$Repo/releases/latest/download" }

if (-not ($Base.StartsWith("https://") -or $Base.StartsWith("http://127.0.0.1:") -or $Base.StartsWith("http://127.0.0.1/"))) {
    throw "INSTALL_BASE_URL must start with https:// or http://127.0.0.1"
}

$Arch = switch ($env:PROCESSOR_ARCHITECTURE) {
    "AMD64" { "amd64" }
    "ARM64" { "arm64" }
    default { throw "unsupported cpu: $($env:PROCESSOR_ARCHITECTURE)" }
}

$Local = $env:LOCALAPPDATA
if (-not $Local) { throw "LOCALAPPDATA is not set" }
$BinDir = Join-Path $Local "simpled\bin"
$Dest = Join-Path $BinDir "simpled.exe"
$BinName = "simpled-windows-$Arch.exe"
$Tmp = Join-Path $env:TEMP ("simpled-" + [guid]::NewGuid().ToString())
New-Item -ItemType Directory -Force -Path $Tmp | Out-Null
try {
    Invoke-WebRequest -Uri "$Base/SHA256SUMS" -OutFile (Join-Path $Tmp "SHA256SUMS")
    Invoke-WebRequest -Uri "$Base/$BinName" -OutFile (Join-Path $Tmp $BinName)
    Push-Location $Tmp
    $line = Select-String -Path SHA256SUMS -Pattern " $BinName$"
    if (-not $line) { throw "checksum line missing" }
    $expected = ($line.Line -split "\s+")[0]
    $hash = (Get-FileHash $BinName -Algorithm SHA256).Hash.ToLower()
    if ($hash -ne $expected.ToLower()) { throw "checksum mismatch" }
    Pop-Location
    New-Item -ItemType Directory -Force -Path $BinDir | Out-Null
    cmd /c "schtasks /End /TN simpled >nul 2>&1"
    Get-Process simpled -ErrorAction SilentlyContinue | Where-Object { $_.Path -eq $Dest } | Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Milliseconds 500
    Copy-Item (Join-Path $Tmp $BinName) $Dest -Force
    $userPath = [Environment]::GetEnvironmentVariable("Path", "User")
    if ($userPath -notlike "*$BinDir*") {
        [Environment]::SetEnvironmentVariable("Path", "$userPath;$BinDir", "User")
        $env:Path = "$env:Path;$BinDir"
    }
    & $Dest install
    $hc = Get-Command hc -ErrorAction SilentlyContinue
    $hcPath = if ($hc) { $hc.Source } else { Join-Path $env:LOCALAPPDATA "Programs\herdr-core\hc.exe" }
    if (-not (Test-Path $hcPath)) {
        Write-Host "Installing herdr-core, which runs your agents"
        powershell -NoProfile -ExecutionPolicy Bypass -Command "irm https://releases.herdr.dev/core/install.ps1 | iex"
    }
    if (Test-Path $hcPath) { & $hcPath server start *> $null }
    Write-Host ""
    Write-Host "Next step: run simpled pair and scan the QR code in the simple app on your phone."
}
finally {
    Remove-Item -Recurse -Force $Tmp -ErrorAction SilentlyContinue
}
