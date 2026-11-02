[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$BackupFile,
    [switch]$ConfirmDatabaseReplacement,
    [string]$SafetyBackupDirectory,
    [string]$ContainerName
)

$ErrorActionPreference = 'Stop'
if (-not $SafetyBackupDirectory) { $SafetyBackupDirectory = Join-Path $PSScriptRoot '..\backups\before-restore' }
if (-not $ConfirmDatabaseReplacement) { throw 'Restore replaces the current database. Repeat with -ConfirmDatabaseReplacement.' }
$source = (Resolve-Path -LiteralPath $BackupFile).Path
if ([IO.Path]::GetExtension($source) -ne '.dump') { throw 'Expected a PostgreSQL archive with the .dump extension.' }

if (-not $ContainerName) {
    $containers = @(& docker ps --filter 'label=com.docker.compose.service=db' --format '{{.Names}}') | Where-Object { $_ }
    if ($LASTEXITCODE -ne 0 -or $containers.Count -ne 1) { throw 'Could not identify one database container. Pass -ContainerName.' }
    $ContainerName = $containers[0]
}

Write-Host 'Creating a safety backup of the current database...'
& (Join-Path $PSScriptRoot 'backup.ps1') -BackupDirectory $SafetyBackupDirectory -RetentionDays 90 -ContainerName $ContainerName
if ($LASTEXITCODE -ne 0) { throw 'Safety backup failed. Restore stopped.' }

$containerFile = "/tmp/restore-$([guid]::NewGuid().ToString('N')).dump"
try {
    & docker cp $source "${ContainerName}:$containerFile"
    if ($LASTEXITCODE -ne 0) { throw 'Could not copy the archive into the container.' }
    & docker exec $ContainerName pg_restore -l $containerFile | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'The archive is damaged or unsupported.' }
    & docker exec $ContainerName psql -U avtozvuk -d postgres -v ON_ERROR_STOP=1 -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='avtozvuk' AND pid <> pg_backend_pid();"
    if ($LASTEXITCODE -ne 0) { throw 'Could not terminate active connections.' }
    & docker exec $ContainerName dropdb -U avtozvuk --if-exists avtozvuk
    if ($LASTEXITCODE -ne 0) { throw 'Could not drop the current database.' }
    & docker exec $ContainerName createdb -U avtozvuk avtozvuk
    if ($LASTEXITCODE -ne 0) { throw 'Could not create a clean database.' }
    & docker exec $ContainerName pg_restore -U avtozvuk -d avtozvuk --no-owner --no-acl --exit-on-error $containerFile
    if ($LASTEXITCODE -ne 0) { throw 'Restore failed. Use the safety backup.' }
} finally {
    & docker exec $ContainerName rm -f $containerFile 2>$null
}

Write-Host "Database restored from: $source"
Write-Host 'Restart the backend so it reconnects to PostgreSQL.'

