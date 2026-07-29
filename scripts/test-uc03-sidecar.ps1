param(
    [string]$SidecarUrl = "http://localhost:9000",
    [string]$BackendUrl = "http://localhost:8080",
    [string]$DbName = "neo_02",
    [string]$DbUsername = "appuser",
    [string]$DbPassword = "apppass"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot

function Assert-Equal {
    param($Actual, $Expected, [string]$Label)
    if ("$Actual" -ne "$Expected") {
        throw "$Label expected '$Expected' but was '$Actual'"
    }
}

function Assert-Absent {
    param($Object, [string]$Property, [string]$Label)
    if ($Object.PSObject.Properties.Name -contains $Property) {
        throw "$Label unexpectedly exposed '$Property'"
    }
}

function Read-DbFingerprint {
    $sql = @"
SELECT CONCAT(
  (SELECT COUNT(*) FROM policy_record), '|',
  COALESCE((SELECT MAX(updated_at) FROM policy_record), 'EMPTY'), '|',
  (SELECT COUNT(*)
     FROM information_schema.columns
    WHERE table_schema = '$DbName'
      AND table_name IN ('policy_record', 'policy_config', 'override_log')
      AND (
        LOWER(column_name) LIKE '%applicant%'
        OR LOWER(column_name) LIKE '%full_name%'
        OR LOWER(column_name) LIKE '%date_of_birth%'
        OR LOWER(column_name) LIKE '%tax_residen%'
        OR LOWER(column_name) LIKE '%email%'
        OR LOWER(column_name) LIKE '%mobile%'
        OR LOWER(column_name) LIKE '%country_of_residence%'
      ))
);
"@
    $fingerprint = & docker compose exec -T -e "MYSQL_PWD=$DbPassword" mysql `
        mysql "-u$DbUsername" $DbName -N -B -e $sql
    if ($LASTEXITCODE -ne 0) {
        throw "Could not inspect $DbName"
    }
    return "$fingerprint".Trim()
}

Push-Location $repoRoot
try {
    Invoke-RestMethod -Uri "$SidecarUrl/health" | Out-Null
    Invoke-RestMethod -Uri "$BackendUrl/health" | Out-Null

    $dispatchBody = @{ scenarioId = "app-1240"; freshId = $false } | ConvertTo-Json
    $dispatch = Invoke-RestMethod `
        -Method Post `
        -Uri "$SidecarUrl/api/v1/dispatch" `
        -ContentType "application/json" `
        -Body $dispatchBody
    Assert-Equal $dispatch.ackHttpStatus 202 "app-1240 acknowledgement"

    $deadline = (Get-Date).AddSeconds(30)
    do {
        Start-Sleep -Milliseconds 250
        try {
            $case = Invoke-RestMethod -Uri "$BackendUrl/cases/app-1240"
            $decided = $null -ne $case.outcome
        } catch {
            $decided = $false
        }
    } until ($decided -or (Get-Date) -ge $deadline)
    if (-not $decided) {
        throw "Timed out waiting for app-1240 to reach a stable decision before read-only checks"
    }

    $before = Read-DbFingerprint
    1..2 | ForEach-Object {
        $view = Invoke-RestMethod -Uri "$BackendUrl/cases/app-1240/applicant"
        Assert-Equal $view.fullName "Sofia Ruiz" "Applicant name"
        Assert-Equal ($view.taxResidencies -join ",") "GB,US" "Tax residencies"
        Assert-Equal $view.countryOfResidence "GB" "Country of residence"
        Assert-Equal $view.productCode "CREDIT_CARD_STANDARD" "Product code"
        Assert-Equal $view.channel "WEB" "Channel"

        @(
            "identityDocument", "employment", "finances", "delivery", "consents",
            "email", "mobile", "nationality", "residentialStatus", "currentAddress",
            "monthsAtAddress", "dependants", "requestedCreditLimit"
        ) | ForEach-Object { Assert-Absent $view $_ "Applicant response" }
    }

    $after = Read-DbFingerprint
    Assert-Equal $after $before "Database fingerprint after two live reads"

    Write-Output "UC03 sidecar E2E passed: Sofia hydrated twice with a minimal response and no database change."
} finally {
    Pop-Location
}
