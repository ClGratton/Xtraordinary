param()

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$safePath = $repoRoot -replace '\\', '/'
$safeDirectory = "safe.directory=$safePath"

function Invoke-RepositoryGit {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $output = & git -c $safeDirectory @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed: $($output -join [Environment]::NewLine)"
    }
    return @($output)
}

$branch = (Invoke-RepositoryGit -C $repoRoot branch --show-current | Select-Object -First 1).Trim()
if ([string]::IsNullOrWhiteSpace($branch)) {
    throw 'Canonical builds require a named Git branch; detached HEAD is not allowed.'
}

$status = @(Invoke-RepositoryGit -C $repoRoot status --porcelain=v1 --untracked-files=all)
if ($status.Count -gt 0) {
    throw "Canonical builds require a clean committed source tree. Commit and push these paths first:`n$($status -join [Environment]::NewLine)"
}

$upstream = (Invoke-RepositoryGit -C $repoRoot rev-parse --abbrev-ref --symbolic-full-name '@{u}' |
    Select-Object -First 1).Trim()
$localHead = (Invoke-RepositoryGit -C $repoRoot rev-parse HEAD | Select-Object -First 1).Trim()
$upstreamHead = (Invoke-RepositoryGit -C $repoRoot rev-parse '@{u}' | Select-Object -First 1).Trim()
if ($localHead -ne $upstreamHead) {
    throw "HEAD $localHead is not the exact configured upstream $upstream ($upstreamHead). Push before compiling."
}

$remote = (Invoke-RepositoryGit -C $repoRoot config --get "branch.$branch.remote" | Select-Object -First 1).Trim()
$mergeRef = (Invoke-RepositoryGit -C $repoRoot config --get "branch.$branch.merge" | Select-Object -First 1).Trim()
if ([string]::IsNullOrWhiteSpace($remote) -or [string]::IsNullOrWhiteSpace($mergeRef) -or $remote -eq '.') {
    throw "Branch $branch must track a pushed remote branch before compiling."
}

$remoteLine = Invoke-RepositoryGit -C $repoRoot ls-remote --exit-code $remote $mergeRef | Select-Object -First 1
$remoteHead = ($remoteLine -split '\s+')[0].Trim()
if ($remoteHead -ne $localHead) {
    throw "Remote $remote $mergeRef is $remoteHead, not local HEAD $localHead. Push before compiling."
}

Write-Host "Source provenance passed: $branch at pushed commit $localHead." -ForegroundColor Green
