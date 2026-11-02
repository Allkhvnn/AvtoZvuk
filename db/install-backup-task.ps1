[CmdletBinding()]
param(
    [string]$BackupDirectory,
    [ValidatePattern('^([01]\d|2[0-3]):[0-5]\d$')][string]$DailyAt = '02:00',
    [ValidateRange(1, 3650)][int]$RetentionDays = 30
)

$ErrorActionPreference = 'Stop'
if (-not $BackupDirectory) { $BackupDirectory = Join-Path $PSScriptRoot '..\backups' }
$taskName = 'AvtoZvuk PostgreSQL Backup'
$script = (Resolve-Path (Join-Path $PSScriptRoot 'backup.ps1')).Path
$directory = [IO.Path]::GetFullPath($BackupDirectory)
$command = "-NoProfile -ExecutionPolicy Bypass -File `"$script`" -BackupDirectory `"$directory`" -RetentionDays $RetentionDays"
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $command
$trigger = New-ScheduledTaskTrigger -Daily -At $DailyAt
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Hours 2)
$principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Interactive -RunLevel Limited
Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings -Principal $principal -Force | Out-Null
Write-Host "Task '$taskName' installed: daily at $DailyAt."
Write-Host "Backup directory: $directory"
Write-Host 'Docker Desktop and the interactive user session must be running.'

