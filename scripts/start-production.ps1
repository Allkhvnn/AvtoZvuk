[CmdletBinding()]
param(
    [ValidateRange(10, 300)][int]$StartupTimeoutSeconds = 120
)

$ErrorActionPreference = 'Stop'
$projectDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$environmentFile = Join-Path $projectDirectory '.env'
$jarFile = Join-Path $projectDirectory 'build\libs\kz-0.0.1-SNAPSHOT.jar'
$logDirectory = Join-Path $projectDirectory 'build\runtime'
$javaCandidates = @()
$javaCandidates += 'C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot\bin\java.exe'
if ($env:JAVA_HOME) { $javaCandidates += (Join-Path $env:JAVA_HOME 'bin\java.exe') }
$javaCandidates = @($javaCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -Unique)

function Import-EnvironmentFile([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing $Path. Copy .env.example to .env and set DB_PASSWORD."
    }
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^\s*#' -or [string]::IsNullOrWhiteSpace($line)) { continue }
        if ($line -notmatch '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { throw "Invalid line in ${Path}: $line" }
        [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
    }
}

function Test-DockerReady {
    & docker info *> $null
    return $LASTEXITCODE -eq 0
}

function Wait-Until([scriptblock]$Condition, [string]$FailureMessage) {
    $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    do {
        if (& $Condition) { return }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw $FailureMessage
}

Import-EnvironmentFile $environmentFile
if (-not $env:DB_PASSWORD) { throw 'DB_PASSWORD is empty in .env.' }
if (-not $env:DB_PORT) { $env:DB_PORT = '55432' }
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker was not found in PATH.' }
if (-not $javaCandidates) { throw 'Java 21 was not found. Set JAVA_HOME or install Eclipse Temurin 21.' }
if (-not (Test-Path -LiteralPath $jarFile)) { throw 'Production JAR is missing. Run .\gradlew.bat bootJar first.' }

if (-not (Test-DockerReady)) {
    $dockerDesktop = 'C:\Program Files\Docker\Docker\Docker Desktop.exe'
    if (-not (Test-Path -LiteralPath $dockerDesktop)) { throw 'Docker Desktop is not running and could not be found.' }
    Start-Process -FilePath $dockerDesktop
    Wait-Until { Test-DockerReady } 'Docker Desktop did not become ready in time.'
}

Push-Location $projectDirectory
try {
    & docker compose up -d db
    if ($LASTEXITCODE -ne 0) { throw 'Could not start PostgreSQL.' }
    Wait-Until {
        $status = & docker compose ps db --format '{{.Health}}' 2>$null
        $LASTEXITCODE -eq 0 -and $status -eq 'healthy'
    } 'PostgreSQL did not become healthy in time.'
} finally {
    Pop-Location
}

try {
    $response = Invoke-WebRequest -Uri 'http://localhost:8080/' -UseBasicParsing -TimeoutSec 3
    if ($response.StatusCode -eq 200) {
        Write-Host 'AvtoZvuk is already running at http://localhost:8080/'
        exit 0
    }
} catch {}

[IO.Directory]::CreateDirectory($logDirectory) | Out-Null
$env:DB_URL = "jdbc:postgresql://localhost:$($env:DB_PORT)/avtozvuk"
$process = Start-Process -FilePath $javaCandidates[0] `
    -ArgumentList '-jar', $jarFile `
    -WorkingDirectory $projectDirectory `
    -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $logDirectory 'application.log') `
    -RedirectStandardError (Join-Path $logDirectory 'application-error.log') `
    -PassThru

Wait-Until {
    if ($process.HasExited) { throw "Backend exited with code $($process.ExitCode). Check build\runtime logs." }
    try {
        (Invoke-WebRequest -Uri 'http://localhost:8080/' -UseBasicParsing -TimeoutSec 3).StatusCode -eq 200
    } catch { $false }
} 'Backend did not become ready in time.'

Write-Host "AvtoZvuk started (PID $($process.Id)): http://localhost:8080/"

