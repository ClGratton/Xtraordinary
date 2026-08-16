param(
    [string]$ManifestPath,
    [ValidateSet('Release', 'UiEvidenceCandidate')]
    [string]$Mode = 'Release'
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($ManifestPath)) {
    $ManifestPath = Join-Path $repoRoot 'docs\engineering-policy.json'
}

if (-not (Test-Path -LiteralPath $ManifestPath)) {
    throw "Engineering policy manifest was not found at $ManifestPath"
}

$manifest = Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json
if ($manifest.schemaVersion -ne 1) {
    throw "Unsupported engineering policy schema version: $($manifest.schemaVersion)"
}
if (-not $manifest.rules -or $manifest.rules.Count -eq 0) {
    throw 'Engineering policy manifest must contain at least one rule.'
}

$failures = [System.Collections.Generic.List[string]]::new()
foreach ($rule in $manifest.rules) {
    $target = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $rule.path))
    if (-not $target.StartsWith([System.IO.Path]::GetFullPath($repoRoot), [System.StringComparison]::OrdinalIgnoreCase)) {
        $failures.Add("[$($rule.id)] Target escapes the repository: $($rule.path)")
        continue
    }

    switch ($rule.type) {
        'required_file' {
            if (-not (Test-Path -LiteralPath $target -PathType Leaf)) {
                $failures.Add("[$($rule.id)] $($rule.message)")
            }
        }
        'required_pattern' {
            if (-not (Test-Path -LiteralPath $target -PathType Leaf)) {
                $failures.Add("[$($rule.id)] Missing file: $($rule.path)")
                continue
            }
            $content = Get-Content -LiteralPath $target -Raw
            if (-not [regex]::IsMatch($content, $rule.pattern)) {
                $failures.Add("[$($rule.id)] $($rule.message)")
            }
        }
        'forbidden_pattern' {
            if (-not (Test-Path -LiteralPath $target -PathType Leaf)) {
                $failures.Add("[$($rule.id)] Missing file: $($rule.path)")
                continue
            }
            $content = Get-Content -LiteralPath $target -Raw
            if ([regex]::IsMatch($content, $rule.pattern)) {
                $failures.Add("[$($rule.id)] $($rule.message)")
            }
        }
        'ui_surface_evidence' {
            if (-not (Test-Path -LiteralPath $target -PathType Leaf)) {
                $failures.Add("[$($rule.id)] Missing evidence contract: $($rule.path)")
                continue
            }
            try {
                $evidence = Get-Content -LiteralPath $target -Raw | ConvertFrom-Json
            } catch {
                $failures.Add("[$($rule.id)] UI evidence contract is not valid JSON: $($_.Exception.Message)")
                continue
            }
            if ($evidence.schemaVersion -ne 1 -or -not $evidence.surfaces) {
                $failures.Add("[$($rule.id)] UI evidence contract must use schemaVersion 1 and list surfaces.")
                continue
            }
            $screenshotTest = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $evidence.screenshotTest))
            if (-not (Test-Path -LiteralPath $screenshotTest -PathType Leaf)) {
                $failures.Add("[$($rule.id)] Screenshot test file is missing: $($evidence.screenshotTest)")
                continue
            }
            $screenshotContent = Get-Content -LiteralPath $screenshotTest -Raw
            foreach ($surface in $evidence.surfaces) {
                $implementation = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $surface.implementation))
                if (-not $implementation.StartsWith([System.IO.Path]::GetFullPath($repoRoot), [System.StringComparison]::OrdinalIgnoreCase) -or
                    -not (Test-Path -LiteralPath $implementation -PathType Leaf)) {
                    $failures.Add("[$($rule.id)] Surface '$($surface.id)' has a missing or invalid implementation path.")
                    continue
                }
                foreach ($preview in @($surface.defaultPreview, $surface.adaptivePreview)) {
                    if ([string]::IsNullOrWhiteSpace($preview) -or
                        -not [regex]::IsMatch($screenshotContent, "fun\s+$([regex]::Escape($preview))\s*\(")) {
                        $failures.Add("[$($rule.id)] Surface '$($surface.id)' is missing preview '$preview'.")
                    }
                }
            }
        }
        'ui_review_attestations' {
            if (-not (Test-Path -LiteralPath $target -PathType Leaf)) {
                $failures.Add("[$($rule.id)] Missing UI review policy: $($rule.path)")
                continue
            }
            try {
                $reviewPolicy = Get-Content -LiteralPath $target -Raw | ConvertFrom-Json
            } catch {
                $failures.Add("[$($rule.id)] UI review policy is not valid JSON: $($_.Exception.Message)")
                continue
            }
            if ($reviewPolicy.schemaVersion -ne 1 -or $reviewPolicy.agentType -ne 'terra_medium' -or
                -not $reviewPolicy.roles -or -not $reviewPolicy.surfaces) {
                $failures.Add("[$($rule.id)] UI review policy must use schemaVersion 1, terra_medium, roles, and surfaces.")
                continue
            }
            $roleById = @{}
            foreach ($role in $reviewPolicy.roles) {
                if ([string]::IsNullOrWhiteSpace($role.id) -or $roleById.ContainsKey($role.id)) {
                    $failures.Add("[$($rule.id)] UI review role IDs must be non-empty and unique.")
                    continue
                }
                $roleById[$role.id] = $role
                $promptPath = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $role.prompt))
                if (-not $promptPath.StartsWith([System.IO.Path]::GetFullPath($repoRoot), [System.StringComparison]::OrdinalIgnoreCase) -or
                    -not (Test-Path -LiteralPath $promptPath -PathType Leaf)) {
                    $failures.Add("[$($rule.id)] Role '$($role.id)' has no repository-owned prompt.")
                }
                if (-not $role.requiredChecks -or $role.requiredChecks.Count -eq 0) {
                    $failures.Add("[$($rule.id)] Role '$($role.id)' must own concrete check IDs.")
                }
            }
            if ($Mode -eq 'UiEvidenceCandidate') {
                continue
            }
            foreach ($surface in $reviewPolicy.surfaces) {
                $recordPath = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $surface.record))
                if (-not $recordPath.StartsWith([System.IO.Path]::GetFullPath($repoRoot), [System.StringComparison]::OrdinalIgnoreCase) -or
                    -not (Test-Path -LiteralPath $recordPath -PathType Leaf)) {
                    $failures.Add("[$($rule.id)] Surface '$($surface.id)' has no review record.")
                    continue
                }
                try {
                    $record = Get-Content -LiteralPath $recordPath -Raw | ConvertFrom-Json
                } catch {
                    $failures.Add("[$($rule.id)] Review record for '$($surface.id)' is invalid JSON: $($_.Exception.Message)")
                    continue
                }
                if ($record.schemaVersion -ne 1 -or $record.surfaceId -ne $surface.id -or
                    $record.sourceCommit -notmatch '^[0-9a-fA-F]{40}$') {
                    $failures.Add("[$($rule.id)] Review record for '$($surface.id)' has invalid schema, surface, or source commit.")
                }
                if ($record.blockingFindings -and $record.blockingFindings.Count -gt 0) {
                    $failures.Add("[$($rule.id)] Surface '$($surface.id)' still has blocking review findings.")
                }
                foreach ($sourceFile in $surface.sourceFiles) {
                    $sourcePath = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $sourceFile))
                    if (-not $sourcePath.StartsWith([System.IO.Path]::GetFullPath($repoRoot), [System.StringComparison]::OrdinalIgnoreCase) -or
                        -not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
                        $failures.Add("[$($rule.id)] Surface '$($surface.id)' has a missing source file '$sourceFile'.")
                        continue
                    }
                    $hashProperty = $record.sourceHashes.PSObject.Properties[$sourceFile]
                    $actualHash = (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash.ToLowerInvariant()
                    if ($null -eq $hashProperty -or $hashProperty.Value.ToString().ToLowerInvariant() -ne $actualHash) {
                        $failures.Add("[$($rule.id)] Surface '$($surface.id)' review is stale for '$sourceFile'. Re-run its Terra reviewers.")
                    }
                }
                $receiptByRole = @{}
                $reviewerIds = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
                foreach ($receipt in $record.receipts) {
                    if ($receiptByRole.ContainsKey($receipt.role)) {
                        $failures.Add("[$($rule.id)] Surface '$($surface.id)' repeats role '$($receipt.role)'.")
                        continue
                    }
                    $receiptByRole[$receipt.role] = $receipt
                    if ([string]::IsNullOrWhiteSpace($receipt.reviewerId) -or -not $reviewerIds.Add($receipt.reviewerId)) {
                        $failures.Add("[$($rule.id)] Surface '$($surface.id)' must use one distinct reviewer per role.")
                    }
                    if ($receipt.agentType -ne 'terra_medium' -or $receipt.verdict -ne 'pass') {
                        $failures.Add("[$($rule.id)] Role '$($receipt.role)' for '$($surface.id)' lacks a passing Terra receipt.")
                    }
                    foreach ($evidencePath in $receipt.evidence) {
                        $resolvedEvidence = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $evidencePath))
                        if (-not $resolvedEvidence.StartsWith([System.IO.Path]::GetFullPath($repoRoot), [System.StringComparison]::OrdinalIgnoreCase) -or
                            -not (Test-Path -LiteralPath $resolvedEvidence)) {
                            $failures.Add("[$($rule.id)] Role '$($receipt.role)' cites missing evidence '$evidencePath'.")
                        }
                    }
                }
                foreach ($requiredRoleId in $surface.requiredRoles) {
                    if (-not $roleById.ContainsKey($requiredRoleId) -or -not $receiptByRole.ContainsKey($requiredRoleId)) {
                        $failures.Add("[$($rule.id)] Surface '$($surface.id)' is missing required role '$requiredRoleId'.")
                        continue
                    }
                    $requiredChecks = @($roleById[$requiredRoleId].requiredChecks)
                    $completedChecks = @($receiptByRole[$requiredRoleId].checks)
                    foreach ($requiredCheck in $requiredChecks) {
                        if ($completedChecks -notcontains $requiredCheck) {
                            $failures.Add("[$($rule.id)] Role '$requiredRoleId' for '$($surface.id)' is missing check '$requiredCheck'.")
                        }
                    }
                }
            }
        }
        default {
            $failures.Add("[$($rule.id)] Unsupported rule type: $($rule.type)")
        }
    }
}

if ($failures.Count -gt 0) {
    Write-Host "Engineering policy gate failed ($($failures.Count) violation(s)):" -ForegroundColor Red
    $failures | ForEach-Object { Write-Host " - $_" -ForegroundColor Red }
    exit 1
}

Write-Host "Engineering policy gate passed ($($manifest.rules.Count) rules)." -ForegroundColor Green
