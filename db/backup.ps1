[CmdletBinding()]
param(
    [string]$BackupDirectory,
    [ValidateRange(1, 3650)][int]$RetentionDays = 30,
    [string]$ContainerName
)

$ErrorActionPreference = 'Stop'
if (-not $BackupDirectory) { $BackupDirectory = Join-Path $PSScriptRoot '..\backups' }
$database = 'avtozvuk'
$databaseUser = 'avtozvuk'

function Invoke-Docker([string[]]$Arguments) {
    & docker @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Docker exited with code ${LASTEXITCODE}: docker $($Arguments -join ' ')" }
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker was not found in PATH.' }
if (-not $ContainerName) {
    $containers = @(& docker ps --filter 'label=com.docker.compose.service=db' --format '{{.Names}}')
    if ($LASTEXITCODE -ne 0) { throw 'Could not list Docker containers.' }
    $containers = @($containers | Where-Object { $_ })
    if ($containers.Count -ne 1) { throw "Expected one running database container, found $($containers.Count). Pass -ContainerName explicitly." }
    $ContainerName = $containers[0]
}

$resolvedDirectory = [IO.Path]::GetFullPath($BackupDirectory)
[IO.Directory]::CreateDirectory($resolvedDirectory) | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$fileName = "avtozvuk-$stamp.dump"
$containerFile = "/tmp/$fileName"
$targetFile = Join-Path $resolvedDirectory $fileName

try {
    Invoke-Docker -Arguments @('exec', $ContainerName, 'pg_dump', '-U', $databaseUser, '-d', $database, '-Fc', '--no-owner', '--no-acl', '-f', $containerFile)
    & docker exec $ContainerName pg_restore -l $containerFile | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'The created archive failed validation.' }
    Invoke-Docker -Arguments @('cp', "${ContainerName}:$containerFile", $targetFile)
} finally {
    & docker exec $ContainerName rm -f $containerFile 2>$null
}

if (-not (Test-Path -LiteralPath $targetFile) -or (Get-Item -LiteralPath $targetFile).Length -eq 0) { throw 'The archive is missing or empty.' }
$hash = Get-FileHash -LiteralPath $targetFile -Algorithm SHA256
"$($hash.Hash.ToLowerInvariant())  $fileName" | Set-Content -LiteralPath "$targetFile.sha256" -Encoding ascii

$cutoff = (Get-Date).AddDays(-$RetentionDays)
Get-ChildItem -LiteralPath $resolvedDirectory -File -Filter 'avtozvuk-*.dump' |
    Where-Object LastWriteTime -lt $cutoff |
    ForEach-Object {
        Remove-Item -LiteralPath $_.FullName
        $checksum = "$($_.FullName).sha256"
        if (Test-Path -LiteralPath $checksum) { Remove-Item -LiteralPath $checksum }
    }

Write-Host "Backup created: $targetFile"
Write-Host "SHA256: $($hash.Hash.ToLowerInvariant())"

