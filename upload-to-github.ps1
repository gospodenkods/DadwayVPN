[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$RepositoryUrl
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version 2.0

function Invoke-Git {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    & git @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Git command failed: git $($Arguments -join ' ')"
    }
}

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    throw "Git is not installed or is not available in PATH."
}

if (-not (Test-Path -LiteralPath ".github\workflows\build.yml")) {
    throw "Run this script from the project root directory."
}

if ($RepositoryUrl -notmatch '^https://github\.com/[^/]+/[^/]+(?:\.git)?$') {
    throw "RepositoryUrl must look like https://github.com/USER/REPOSITORY.git"
}

if (-not (Test-Path -LiteralPath ".git")) {
    Invoke-Git -Arguments @("init")
}

Invoke-Git -Arguments @("config", "core.autocrlf", "true")
Invoke-Git -Arguments @("add", "--all")

$stagedFiles = @(& git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) {
    throw "Unable to inspect staged files."
}

if ($stagedFiles.Count -gt 0) {
    Invoke-Git -Arguments @("commit", "-m", "Update Dadway VPN v8.2.0")
}
else {
    Write-Host "No file changes to commit." -ForegroundColor Yellow
}

Invoke-Git -Arguments @("branch", "-M", "main")

$remoteNames = @(& git remote)
if ($LASTEXITCODE -ne 0) {
    throw "Unable to inspect Git remotes."
}

if ($remoteNames -contains "origin") {
    Invoke-Git -Arguments @("remote", "set-url", "origin", $RepositoryUrl)
}
else {
    Invoke-Git -Arguments @("remote", "add", "origin", $RepositoryUrl)
}

Invoke-Git -Arguments @("push", "-u", "origin", "main")

Write-Host "Upload completed successfully." -ForegroundColor Green
Write-Host "Open GitHub, select Actions, then Build Dadway VPN v8.2.0 APK." -ForegroundColor Green
