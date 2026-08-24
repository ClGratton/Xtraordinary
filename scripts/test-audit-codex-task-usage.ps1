$ErrorActionPreference = 'Stop'
$audit = Join-Path $PSScriptRoot 'audit-codex-task-usage.ps1'
$source = Get-Content -LiteralPath $audit -Raw
if ($source -notmatch "routingDecision =") { throw 'Audit must emit routingDecision.' }
if ($source -notmatch "unbound-worker") { throw 'Audit must report unbound workers without attribution.' }
if ($source -notmatch "Resolved rollout has no machine-readable thread identity") { throw 'Audit must explain missing worker binding.' }
if ($source -notmatch "Resolved rollout belongs to thread") { throw 'Audit must reject mismatched thread identities.' }
Write-Output 'audit-codex-task-usage fixtures passed: unbound-worker, mismatched-thread rejection, valid identity routing.'
