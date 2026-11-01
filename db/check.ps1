$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$containerName = 'avtozvuk-db-check-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
$testPassword = [guid]::NewGuid().ToString('N')

function Invoke-Docker {
    & docker @args
    if ($LASTEXITCODE -ne 0) { throw "Docker command failed: $($args[0])" }
}

try {
    Invoke-Docker run --rm -d --name $containerName -e "POSTGRES_PASSWORD=$testPassword" `
        --mount "type=bind,source=$projectRoot/src/main/resources/db/migration,target=/migrations,readonly" `
        --mount "type=bind,source=$projectRoot/db/tests,target=/tests,readonly" postgres:17
    $ready = $false
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        & docker exec $containerName pg_isready -U postgres *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw 'PostgreSQL did not become ready' }
    Get-ChildItem "$projectRoot/src/main/resources/db/migration/V*.sql" | Sort-Object Name | ForEach-Object {
        Invoke-Docker exec $containerName psql -X -U postgres -v ON_ERROR_STOP=1 --single-transaction -f "/migrations/$($_.Name)"
    }
    Invoke-Docker exec $containerName psql -X -U postgres -v ON_ERROR_STOP=1 -f /tests/accounting_smoke.sql
}
finally {
    & docker stop $containerName | Out-Null
}
