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

function Send-Scenario {
    param([string]$ScenarioId, [bool]$FreshId = $false)
    $body = @{
        scenarioId = $ScenarioId
        freshId = $FreshId
    } | ConvertTo-Json
    $result = Invoke-RestMethod `
        -Method Post `
        -Uri "$SidecarUrl/api/v1/dispatch" `
        -ContentType "application/json" `
        -Body $body
    Assert-Equal $result.ackHttpStatus 202 "Ack for $ScenarioId"
}

Push-Location $repoRoot
try {
    Invoke-RestMethod -Uri "$SidecarUrl/health" | Out-Null
    Invoke-RestMethod -Uri "$BackendUrl/health" | Out-Null

    $sql = "SET FOREIGN_KEY_CHECKS=0; TRUNCATE TABLE override_log; " +
           "TRUNCATE TABLE policy_record; SET FOREIGN_KEY_CHECKS=1;"
    & docker compose exec -T -e "MYSQL_PWD=$DbPassword" mysql `
        mysql "-u$DbUsername" $DbName -N -B -e $sql
    if ($LASTEXITCODE -ne 0) {
        throw "Could not reset $DbName"
    }
    Invoke-RestMethod -Method Delete -Uri "$SidecarUrl/api/v1/dispatches" | Out-Null

    Send-Scenario "app-1234"
    Send-Scenario "app-1240"
    Send-Scenario "app-1242"
    4..20 | ForEach-Object { Send-Scenario "SIM-01" $true }
    Send-Scenario "app-1287"

    $deadline = (Get-Date).AddSeconds(30)
    do {
        Start-Sleep -Milliseconds 250
        $dispatches = Invoke-RestMethod -Uri "$SidecarUrl/api/v1/dispatches"
        $exact = @($dispatches | Where-Object {
            $_.applicationId -in @("app-1234", "app-1240", "app-1242", "app-1287")
        })
        $complete = $exact.Count -eq 4 -and
            @($exact | Where-Object { $null -eq $_.callbackStatus }).Count -eq 0
    } until ($complete -or (Get-Date) -ge $deadline)

    if (-not $complete) {
        throw "Timed out waiting for the four UC02 callbacks"
    }

    $expectedCallbacks = @{
        "app-1234" = "ACCEPTED"
        "app-1240" = "REJECTED"
        "app-1242" = "REJECTED"
        "app-1287" = "REFERRED"
    }
    foreach ($applicationId in $expectedCallbacks.Keys) {
        $exchange = $exact | Where-Object { $_.applicationId -eq $applicationId }
        Assert-Equal $exchange.callbackStatus $expectedCallbacks[$applicationId] `
            "Callback for $applicationId"
    }

    $maria = Invoke-RestMethod -Uri "$BackendUrl/cases/app-1234"
    Assert-Equal $maria.outcome "APPROVED" "app-1234 outcome"

    $sofia = Invoke-RestMethod -Uri "$BackendUrl/cases/app-1240"
    Assert-Equal $sofia.outcome "REJECTED" "app-1240 outcome"
    Assert-Equal $sofia.ruleResults[1].reasonCodes[0] `
        "POL_TAX_RESIDENCY_EXCLUDED" "app-1240 tax rule"

    $james = Invoke-RestMethod -Uri "$BackendUrl/cases/app-1242"
    Assert-Equal $james.outcome "REJECTED" "app-1242 outcome"
    Assert-Equal $james.ruleResults[0].registryChecked $true "app-1242 registryChecked"
    Assert-Equal $james.ruleResults[0].reasonCodes[0] `
        "POL_EXISTING_PRODUCT_HELD" "app-1242 product rule"

    $sampled = Invoke-RestMethod -Uri "$BackendUrl/cases/app-1287"
    Assert-Equal $sampled.outcome "REFERRED" "app-1287 outcome"
    Assert-Equal $sampled.machineOutcome "APPROVED" "app-1287 machineOutcome"
    Assert-Equal $sampled.ruleResults[3].position 21 "app-1287 sampling position"
    Assert-Equal $sampled.ruleResults[3].reasonCodes[0] `
        "POL_SAMPLED_FOR_REVIEW" "app-1287 sampling rule"

    Write-Output "UC02 sidecar E2E passed: 21 accepted applications and all four checkpoints."
} finally {
    Pop-Location
}
