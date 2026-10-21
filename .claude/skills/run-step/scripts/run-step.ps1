<#
.SYNOPSIS
  Checks out a workshop branch, (re)starts the Docker stack and verifies it.

.PARAMETER Branch
  raw | step1 ... step7 | master | full branch name (e.g. step3-streaming)

.PARAMETER Fresh
  Delete chat-db data first (chat history); accounts stay.

.PARAMETER AiCheck
  Also send one real message to Gemini (uses quota of the free tier).

.PARAMETER NoCheckout
  Run whatever is checked out now.

.EXAMPLE
  .\run-step.ps1 step3
  .\run-step.ps1 master --Fresh --AiCheck
#>
param(
    [string]$Branch      = "",
    [switch]$Fresh,
    [switch]$AiCheck,
    [switch]$NoCheckout
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Die   { param($msg) Write-Error "ERROR: $msg"; exit 1 }
function Info  { param($msg) Write-Host "==> $msg" }
function Pass  { param($msg) Write-Host "  v $msg" }   # checkmark substitute
function Fail  { param($msg) Write-Host "  x $msg"; $script:FAILED = $true }

# --- locate repo root ---------------------------------------------------------------------------
$ROOT = git rev-parse --show-toplevel 2>$null
if ($LASTEXITCODE -ne 0) { Die "not inside the workshop git repository" }
Set-Location $ROOT

# --- resolve and check out the branch -----------------------------------------------------------
if (-not $NoCheckout) {
    if (-not $Branch) { Die "branch is required (raw, step1 ... step7, master)" }
    $exists = git show-ref --verify --quiet "refs/heads/$Branch" 2>$null; $exists = ($LASTEXITCODE -eq 0)
    if (-not $exists) {
        $matches = git for-each-ref --format='%(refname:short)' "refs/heads/$Branch-*" 2>$null
        $matches = @($matches | Where-Object { $_ })
        if ($matches.Count -eq 1) {
            $Branch = $matches[0]
        } else {
            # fall back to remote
            Info "Branch '$Branch' not found locally, trying remote..."
            $fetched = $false
            git fetch -q origin $Branch 2>$null
            if ($LASTEXITCODE -eq 0) {
                git checkout -q --track "origin/$Branch"
                if ($LASTEXITCODE -eq 0) { $fetched = $true }
            }
            if (-not $fetched) {
                $rmatches = @(git for-each-ref --format='%(refname:short)' "refs/remotes/origin/$Branch-*" 2>$null |
                    Where-Object { $_ } | ForEach-Object { $_ -replace '^origin/', '' })
                if ($rmatches.Count -eq 1) {
                    git fetch -q origin $rmatches[0]
                    git checkout -q --track "origin/$($rmatches[0])"
                    if ($LASTEXITCODE -ne 0) { Die "git checkout $($rmatches[0]) failed" }
                } else {
                    $all = (git for-each-ref --format='%(refname:short)' refs/heads) -join ' '
                    Die "unknown branch '$Branch'. Available locally: $all"
                }
            }
        }
    }
    $current = git rev-parse --abbrev-ref HEAD
    if ($current -ne $Branch) {
        $dirty = git status --porcelain
        if ($dirty) { Die "uncommitted changes in the working tree; commit or stash them before switching to ${Branch}:`n$dirty" }
        Info "Checking out $Branch"
        git checkout -q $Branch
        if ($LASTEXITCODE -ne 0) { Die "git checkout $Branch failed" }
    }
}
$Branch = git rev-parse --abbrev-ref HEAD

# --- .env ---------------------------------------------------------------------------------------
if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    Info "Created .env from .env.example (set GEMINI_API_KEY in it for step1 and later)"
}
function Get-EnvValue { param($key)
    $line = Select-String -Path ".env" -Pattern "^$key=" | Select-Object -Last 1
    if ($line) { return ($line.Line -split "=", 2)[1] } else { return "" }
}

# What this branch contains
$HAS_AI = -not (Select-String -Path "chat-service\src\main\resources\application.properties" -Pattern "^spring.ai.model.chat=none" -Quiet)
$KEY     = Get-EnvValue "GEMINI_API_KEY"
$KEY_SET = $KEY -and $KEY -ne "PUT_YOUR_GEMINI_API_KEY_HERE"

# --- start the stack ----------------------------------------------------------------------------
$PROJECT = if ($env:COMPOSE_PROJECT_NAME) { $env:COMPOSE_PROJECT_NAME } else { (Split-Path $ROOT -Leaf).ToLower() }

if ($Fresh) {
    Info "Deleting chat-db data (chat history)"
    docker compose rm -sf chat-service chat-db 2>$null
    $vol = docker volume ls -q --filter "label=com.docker.compose.project=$PROJECT" --filter "label=com.docker.compose.volume=chatdata"
    if ($vol) { docker volume rm $vol 2>$null }
}

Info "Building and starting on $Branch (first build can take a few minutes)"
$job = Start-Job -ScriptBlock {
    param($root)
    Set-Location $root
    docker compose up -d --build 2>&1
} -ArgumentList $ROOT
$start = Get-Date
while ($job.State -eq 'Running') {
    $elapsed = [int]((Get-Date) - $start).TotalSeconds
    Write-Host -NoNewline "`r  building... ${elapsed}s"
    Start-Sleep 5
}
Write-Host "`r                        `r" -NoNewline
$log = Receive-Job $job
Remove-Job $job
if ($LASTEXITCODE -ne 0 -or $job.State -eq 'Failed') {
    Write-Host ($log | Select-Object -Last 30)
    Die "docker compose up failed"
}

# --- wait until everything is healthy -----------------------------------------------------------
$endpoints = @(
    @{ Name="auth-service"; Url="http://localhost:8081/actuator/health" },
    @{ Name="chat-service"; Url="http://localhost:8082/actuator/health" },
    @{ Name="chat-ui";      Url="http://localhost:3001/" }
)
Info "Waiting for services"
$deadline = (Get-Date).AddSeconds(300)
foreach ($ep in $endpoints) {
    while ($true) {
        try {
            $r = Invoke-WebRequest -Uri $ep.Url -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
            if ($r.StatusCode -lt 400) { break }
        } catch {}
        if ((Get-Date) -ge $deadline) {
            docker compose ps
            Write-Host "--- last log lines of $($ep.Name):" -ForegroundColor Red
            docker compose logs --tail 40 $ep.Name
            Die "$($ep.Name) did not become ready ($($ep.Url))"
        }
        Start-Sleep 3
    }
}

# --- smoke checks -------------------------------------------------------------------------------
$API    = "http://localhost:3001/api"
$HDR    = @{ "Content-Type" = "application/json" }
$script:FAILED = $false

function Http-Status { param($uri, $headers)
    try { $r = Invoke-WebRequest -Uri $uri -Headers $headers -UseBasicParsing -ErrorAction Stop; return $r.StatusCode }
    catch [System.Net.WebException] { return [int]$_.Exception.Response.StatusCode }
    catch { return 0 }
}
function Extract-Field { param($json, $field)
    if ($json -match "`"$field`"`s*:`s*`"([^`"]*)`"") { return $Matches[1] } else { return "" }
}

$EMAIL = "run-step-$(Get-Date -UFormat %s)@check.local"
try {
    $reg   = Invoke-RestMethod -Uri "$API/auth/register" -Method POST -Headers $HDR `
               -Body (ConvertTo-Json @{ email=$EMAIL; fullName="Run Step Check"; password="run-step-check" })
    $TOKEN = $reg.accessToken
} catch { $TOKEN = "" }

$AUTHHDR = $HDR + @{ "Authorization" = "Bearer $TOKEN" }

function Cleanup {
    if ($TOKEN) {
        try { Invoke-WebRequest -Uri "$API/chat/history" -Method DELETE -Headers $AUTHHDR -UseBasicParsing -ErrorAction SilentlyContinue | Out-Null } catch {}
    }
    docker compose exec -T auth-db psql -U auth -d auth -qc "DELETE FROM app_user WHERE email='$EMAIL';" 2>$null | Out-Null
}

try {

Info "Checks"
if ($TOKEN) { Pass "register/login via auth-service" } else { Fail "registration failed" }
$hs = Http-Status "$API/chat/history" $AUTHHDR
if ($hs -eq 200) { Pass "chat history endpoint" } else { Fail "GET /api/chat/history (status $hs)" }

function Invoke-Chat { param($msg)
    # Use curl.exe (ships with Windows 10+) for streaming SSE
    $body = '{"message":"' + $msg + '","timeZone":"UTC"}'
    $stream = curl.exe -sN --max-time 150 -X POST "$API/chat" `
        -H "Content-Type: application/json" `
        -H "Authorization: Bearer $TOKEN" `
        -d $body 2>$null
    return $stream
}
function Parse-Answer { param($stream)
    $ev = ""; $out = ""
    foreach ($line in ($stream -split "`n")) {
        $line = $line.TrimEnd("`r")
        if ($line -match "^event:(.*)") { $ev = $Matches[1].Trim() }
        elseif ($line -match '^data:(.*)') {
            $data = $Matches[1]
            if ($data -match '"text"\s*:\s*"([^"]*)"') {
                $out += $(if ($ev -eq "error") { "[error] " } else { "" }) + $Matches[1]
            }
        } elseif ($line -match '^\{') {
            if ($line -match '"detail"\s*:\s*"([^"]*)"') { $out += "[http] " + $Matches[1] }
        }
    }
    return ($out -replace '\s{2,}', ' ').Substring(0, [Math]::Min(200, $out.Length))
}

if (-not $HAS_AI) {
    $reply = Parse-Answer (Invoke-Chat "hello")
    if ($reply -like "AI is not connected yet*") { Pass "chat echo (no AI on this step): $reply" }
    else { Fail "unexpected chat reply: $reply" }
} elseif (-not $KEY_SET) {
    Fail "GEMINI_API_KEY is not set in .env - the chat will answer 'Gemini API key is not configured'"
} elseif ($AiCheck) {
    $start = Get-Date
    $reply = Parse-Answer (Invoke-Chat "Reply with exactly: OK")
    $secs  = [int]((Get-Date) - $start).TotalSeconds
    if ($reply -and $reply -notlike "[error]*" -and $reply -notlike "[http]*") {
        Pass "Gemini answered in ${secs}s: $reply"
    } else {
        Fail "Gemini call failed: $reply"
    }
} else {
    Pass "Gemini key is set (real chat call skipped to save quota; use -AiCheck)"
}

} finally { Cleanup }

# --- report -------------------------------------------------------------------------------------
Write-Host ""
$short = git rev-parse --short HEAD
Info "Branch $Branch ($short)"
docker compose ps --format '{{.Service}}: {{.Status}}' | ForEach-Object { Write-Host "  $_" }
Write-Host ""
Write-Host "  Chat UI: http://localhost:3001"
Write-Host "  Hard-refresh the browser (Ctrl+Shift+R) after switching branches."
if ($script:FAILED) { exit 1 } else { exit 0 }
