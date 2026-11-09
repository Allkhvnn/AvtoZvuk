[CmdletBinding()]
param(
    [ValidateRange(0, 300)][int]$DelaySeconds = 30
)

$ErrorActionPreference = 'Stop'
$taskName = 'AvtoZvuk Production Startup'
$script = (Resolve-Path (Join-Path $PSScriptRoot 'start-production.ps1')).Path
$command = "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$script`""
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $command
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
if ($DelaySeconds -gt 0) { $trigger.Delay = "PT${DelaySeconds}S" }
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Minutes 5) -MultipleInstances IgnoreNew
$principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Interactive -RunLevel Limited

Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings -Principal $principal -Force | Out-Null
Write-Host "Task '$taskName' installed for user $env:USERNAME with a ${DelaySeconds}-second delay."

