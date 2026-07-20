param(
    [string]$Branch = "",
    [switch]$SkipVerify
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

function Assert-ExitCode([string]$step) {
    if ($LASTEXITCODE -ne 0) {
        throw "$step failed with exit code $LASTEXITCODE."
    }
}

if (-not (Test-Path -LiteralPath (Join-Path $root ".git"))) {
    throw "Git repository has not been initialized."
}

Push-Location $root
try {
    $status = @(git status --porcelain=v1)
    Assert-ExitCode "Git status"
    if ($status.Count -gt 0) {
        throw "Working tree must be clean before publishing."
    }

    $tracked = @(git ls-files)
    Assert-ExitCode "Tracked file audit"
    $forbidden = @($tracked | Where-Object {
        ($_ -match '^\.env($|\.)' -and $_ -ne '.env.example') -or
        $_ -match '^(data|uploads|logs|tmp)/' -or
        $_ -match '(^|/)(target|node_modules|dist|coverage|__pycache__)/'
    })
    if ($forbidden.Count -gt 0) {
        throw "Sensitive or generated paths are tracked; publishing is blocked."
    }

    if (-not $Branch) {
        $Branch = (git branch --show-current).Trim()
        Assert-ExitCode "Current branch lookup"
    }
    if (-not $Branch) {
        throw "A named branch is required for publishing."
    }

    foreach ($remote in @("origin", "codeup")) {
        git remote get-url $remote | Out-Null
        Assert-ExitCode "Remote '$remote' lookup"
    }

    if (-not $SkipVerify) {
        powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "verify.ps1")
        Assert-ExitCode "Repository verification"
    }

    foreach ($remote in @("origin", "codeup")) {
        Write-Host "Publishing $Branch to $remote..." -ForegroundColor Cyan
        git push $remote "HEAD:refs/heads/$Branch"
        Assert-ExitCode "Push to $remote"
    }
} finally {
    Pop-Location
}

Write-Host "Published the same commit to origin and codeup." -ForegroundColor Green
