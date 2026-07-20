$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$python = if (Get-Command python -ErrorAction SilentlyContinue) {
    "python"
} elseif (Get-Command python3 -ErrorAction SilentlyContinue) {
    "python3"
} else {
    throw "Python 3 is required to run the repository Harness."
}

function Assert-ExitCode([string]$step) {
    if ($LASTEXITCODE -ne 0) {
        throw "$step failed with exit code $LASTEXITCODE."
    }
}

$env:PYTHONDONTWRITEBYTECODE = "1"

Write-Host "[1/9] Checking repository contracts and privacy boundaries..." -ForegroundColor Cyan
& $python (Join-Path $PSScriptRoot "check_harness.py")
Assert-ExitCode "Repository Harness"

Write-Host "[2/9] Running Harness unit tests..." -ForegroundColor Cyan
& $python -m unittest discover -s (Join-Path $root "tests") -p "test_*.py"
Assert-ExitCode "Harness unit tests"

Write-Host "[3/9] Validating the deterministic RAG dataset..." -ForegroundColor Cyan
& $python (Join-Path $PSScriptRoot "rag_eval.py") validate
Assert-ExitCode "RAG dataset validation"

Write-Host "[4/9] Scoring the RAG Harness smoke predictions..." -ForegroundColor Cyan
& $python (Join-Path $PSScriptRoot "rag_eval.py") score
Assert-ExitCode "RAG Harness smoke score"

Write-Host "[5/9] Validating the retrieval evaluation dataset..." -ForegroundColor Cyan
& $python (Join-Path $PSScriptRoot "retrieval_eval.py") validate
Assert-ExitCode "Retrieval dataset validation"

Write-Host "[6/9] Scoring the retrieval smoke predictions..." -ForegroundColor Cyan
& $python (Join-Path $PSScriptRoot "retrieval_eval.py") score
Assert-ExitCode "Retrieval smoke score"

Write-Host "[7/9] Running Spring tests when the backend exists..." -ForegroundColor Cyan
$backend = Join-Path $root "backend"
if (Test-Path -LiteralPath (Join-Path $backend "pom.xml")) {
    Push-Location $backend
    try {
        if ($env:OS -eq "Windows_NT" -and (Test-Path -LiteralPath "mvnw.cmd")) {
            & ".\mvnw.cmd" test
        } elseif (Test-Path -LiteralPath "mvnw") {
            & "./mvnw" test
        } else {
            & mvn test
        }
        Assert-ExitCode "Spring backend tests"
    } finally {
        Pop-Location
    }
} else {
    Write-Host "  Skipped: backend/pom.xml has not been created yet." -ForegroundColor DarkYellow
}

Write-Host "[8/9] Running Vue tests and build when the frontend exists..." -ForegroundColor Cyan
$frontend = Join-Path $root "frontend"
if (Test-Path -LiteralPath (Join-Path $frontend "package.json")) {
    Push-Location $frontend
    try {
        $npm = if ($env:OS -eq "Windows_NT") { "npm.cmd" } else { "npm" }
        if (-not (Test-Path -LiteralPath "node_modules")) {
            if (Test-Path -LiteralPath "package-lock.json") {
                & $npm ci
            } else {
                & $npm install
            }
            Assert-ExitCode "Frontend dependency install"
        }
        & $npm test
        Assert-ExitCode "Frontend tests"
        & $npm run build
        Assert-ExitCode "Frontend build"
    } finally {
        Pop-Location
    }
} else {
    Write-Host "  Skipped: frontend/package.json has not been created yet." -ForegroundColor DarkYellow
}

Write-Host "[9/9] Validating Docker Compose configuration when available..." -ForegroundColor Cyan
$composeFile = Join-Path $root "compose.yaml"
if ((Test-Path -LiteralPath $composeFile) -and (Get-Command docker -ErrorAction SilentlyContinue)) {
    & docker compose -f $composeFile config --quiet
    Assert-ExitCode "Docker Compose configuration"
} else {
    Write-Host "  Skipped: compose.yaml or Docker CLI is not available." -ForegroundColor DarkYellow
}

Write-Host "All available verification steps passed." -ForegroundColor Green
