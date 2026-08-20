param(
    [string]$ManifestPath,
    [ValidateSet('Release', 'UiEvidenceCandidate', 'FirmwareRelease')]
    [string]$Mode = 'Release'
)

$ErrorActionPreference = 'Stop'
$firmwareNoticeCheck = Join-Path $PSScriptRoot 'check-firmware-release-notices.ps1'
if (-not (Test-Path -LiteralPath $firmwareNoticeCheck)) {
    throw "Firmware release-notices verifier is missing: $firmwareNoticeCheck"
}
& $firmwareNoticeCheck
if ($Mode -ne 'FirmwareRelease') {
    $androidNoticeCheck = Join-Path $PSScriptRoot 'generate-android-release-notices.ps1'
    if (-not (Test-Path -LiteralPath $androidNoticeCheck)) {
        throw "Android release-notices verifier is missing: $androidNoticeCheck"
    }
    & $androidNoticeCheck -Check
    $artworkProvenanceCheck = Join-Path $PSScriptRoot 'check-artwork-provenance.ps1'
    if (-not (Test-Path -LiteralPath $artworkProvenanceCheck)) {
        throw "Artwork provenance verifier is missing: $artworkProvenanceCheck"
    }
    & $artworkProvenanceCheck
}
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

$protectedLayoutAndSelectionRuleIds = @(
    'setup-actions-use-measured-bottom-slot',
    'adaptive-first-viewport-assigns-slack',
    'layout-review-blocks-dead-space-and-action-drift',
    'settings-exclusive-choices-use-radio-semantics',
    'passes-normal-height-owns-available-viewport',
    'focus-keeps-compact-render-evidence'
)
$manifestRuleIds = @($manifest.rules | ForEach-Object { $_.id })
foreach ($protectedRuleId in $protectedLayoutAndSelectionRuleIds) {
    if ($protectedRuleId -notin $manifestRuleIds) {
        throw "Engineering policy manifest must retain protected UI rule '$protectedRuleId'."
    }
}

# This bootstrap is intentionally outside manifest dispatch.  The manifest may
# describe review details, but it may not silently remove the review gate.
$uiReviewRule = @($manifest.rules | Where-Object { $_.id -eq 'ui-changes-require-stable-terra-reviews' })
if ($uiReviewRule.Count -ne 1 -or $uiReviewRule[0].type -ne 'ui_review_attestations' -or
    $uiReviewRule[0].path -ne 'docs/ui-review-policy.json') {
    throw 'Engineering policy manifest must retain the stable Terra UI-review gate at docs/ui-review-policy.json.'
}
$androidBuildWrapper = Join-Path $repoRoot 'scripts\build-xtraordinary-app.ps1'
$androidBuildWrapperContent = Get-Content -LiteralPath $androidBuildWrapper -Raw
if ($androidBuildWrapperContent -notmatch 'UiEvidenceCandidate[\s\S]*?--rerun-tasks') {
    throw 'UI evidence candidate mode must force its exact approved tests and screenshots to rerun.'
}
$firmwareBuildWrapper = Join-Path $repoRoot 'scripts\build-x3-firmware.ps1'
$firmwareBuildWrapperContent = Get-Content -LiteralPath $firmwareBuildWrapper -Raw
if ($firmwareBuildWrapperContent -notmatch 'check-engineering-policies\.ps1[\s\S]*?-Mode FirmwareRelease') {
    throw 'The firmware wrapper must run the policy gate in FirmwareRelease mode.'
}
$usageWorkflowPath = Join-Path $repoRoot 'docs\codex-usage-workflow.md'
$usageLedgerPath = Join-Path $repoRoot 'docs\codex-usage-ledger.md'
$agentsPath = Join-Path $repoRoot 'AGENTS.md'
if (-not (Test-Path -LiteralPath $usageWorkflowPath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $usageLedgerPath -PathType Leaf)) {
    throw 'Repository-owned Codex usage checkpoints, delegation limits, and ledger enforcement must remain active.'
}
$usageWorkflowContent = Get-Content -LiteralPath $usageWorkflowPath -Raw
if ($usageWorkflowContent -notmatch 'account/rateLimits/read' -or
    $usageWorkflowContent -notmatch '30 minutes' -or
    $usageWorkflowContent -notmatch 'fork_turns' -or
    $usageWorkflowContent -notmatch 'twenty percent' -or
    (Get-Content -LiteralPath $agentsPath -Raw) -notmatch 'docs/codex-usage-workflow.md') {
    throw 'Repository-owned Codex usage checkpoints, delegation limits, and ledger enforcement must remain active.'
}

function Test-PolicyGlob {
    param([string]$Path, [string]$Glob)
    $normalizedPath = $Path.Replace('\', '/')
    $escaped = [regex]::Escape($Glob.Replace('\', '/'))
    $pattern = $escaped.Replace('\*\*', '.*').Replace('\*', '[^/]*').Replace('\?', '[^/]')
    return [regex]::IsMatch($normalizedPath, "^$pattern$", [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
}

function Get-TrackedPolicyPaths {
    param([string]$RepositoryRoot)
    $safeDirectory = $RepositoryRoot.Replace('\', '/')
    return @(& git -c "safe.directory=$safeDirectory" -C $RepositoryRoot ls-files | ForEach-Object { $_.Replace('\', '/') })
}

function Get-SurfaceSourcePaths {
    param($Surface, [string[]]$TrackedPaths)
    $includes = @($Surface.sourceGlobs)
    $excludes = @($Surface.excludeGlobs)
    return @($TrackedPaths | Where-Object {
        $candidate = $_
        ($includes | Where-Object { Test-PolicyGlob -Path $candidate -Glob $_ }).Count -gt 0 -and
        ($excludes | Where-Object { Test-PolicyGlob -Path $candidate -Glob $_ }).Count -eq 0
    } | Sort-Object -Unique)
}

function Get-LatestSurfaceCommit {
    param([string]$RepositoryRoot, [string[]]$Paths)
    if ($Paths.Count -eq 0) { return $null }
    $safeDirectory = $RepositoryRoot.Replace('\', '/')
    $commit = @(& git -c "safe.directory=$safeDirectory" -C $RepositoryRoot log -1 --format=%H -- @Paths)
    if ($LASTEXITCODE -ne 0 -or $commit.Count -ne 1 -or [string]::IsNullOrWhiteSpace($commit[0])) { return $null }
    return $commit[0].Trim().ToLowerInvariant()
}

function Get-ChangedPolicyPaths {
    param([string]$RepositoryRoot, [string]$BaselineCommit)
    $safeDirectory = $RepositoryRoot.Replace('\', '/')
    return @(& git -c "safe.directory=$safeDirectory" -C $RepositoryRoot diff --name-only "$BaselineCommit..HEAD" |
        ForEach-Object { $_.Replace('\', '/') })
}

function Test-PolicyBaseline {
    param([string]$RepositoryRoot, [string]$BaselineCommit)
    if ($BaselineCommit -notmatch '^[0-9a-fA-F]{40}$') { return $false }
    $safeDirectory = $RepositoryRoot.Replace('\', '/')
    & git -c "safe.directory=$safeDirectory" -C $RepositoryRoot merge-base --is-ancestor $BaselineCommit HEAD
    return $LASTEXITCODE -eq 0
}

$protectedUiReviewBaseline = '32177b176df3cd8d1baf091439fcdf73c869b4b1'
$protectedCoverageGlobs = @(
    'app/src/main/java/com/xteink/companion/ui/**/*.kt',
    'app/src/main/java/com/xteink/companion/ui/*.kt',
    'app/src/main/res/**/*',
    'app/src/test/java/com/xteink/companion/ui/**/*.kt',
    'app/src/test/java/com/xteink/companion/ui/*.kt',
    'app/src/screenshotTest/**/*.kt',
    'firmware/src/activities/**/*.cpp',
    'firmware/src/activities/**/*.h',
    'firmware/src/activities/*.cpp',
    'firmware/src/activities/*.h',
    'firmware/lib/hal/HalDisplay.*'
)
$protectedVisualLanguageResource = 'docs/ui-visual-language-foundations.md'
$protectedRoleChecks = [ordered]@{
    'ux-flow' = @('interaction-affordance-is-self-evident', 'icon-and-spatial-metaphor-are-coherent', 'immutable-pending-operation-truth', 'no-conflicting-ticket-operations', 'operational-copy-earns-space')
    'hierarchy' = @('front-hierarchy-order', 'contained-expanding-action', 'provenance-not-front-primary')
    'layout-spacing-margins' = @(
        'carousel-peek-equals-route-rail',
        'front-back-bounds-match',
        'adaptive-bounds-do-not-overlap',
        'first-viewport-space-is-assigned',
        'sibling-actions-share-stable-lower-zone'
    )
    'typography' = @('header-reflows-before-overlap', 'operational-facts-reflow-before-truncation', 'required-large-text-fixtures')
    'color-contrast' = @('primary-reserved-for-transaction', 'quiet-remains-grayscale', 'role-pair-contrast')
    'shape-affordance' = @('selected-action-contained', 'action-shape-and-target', 'glyph-geometry-is-optically-separated', 'resting-option-remains-selectable')
    'motion-interaction' = @('page-and-turn-gesture-ownership', 'chooser-bounds-stable-through-morph', 'reduced-motion-final-state')
    'accessibility-adaptive' = @('single-selected-radio-and-owned-action', 'no-duplicate-route-or-provenance-announcement', 'all-controls-reachable-at-required-scales')
    'eink-scanner' = @('binary-hierarchy-and-safe-region', 'scanner-quiet-zone-and-scaling', 'mapped-hints-and-refresh-boundary')
}
$protectedAndroidRoles = @(
    'ux-flow', 'hierarchy', 'layout-spacing-margins', 'typography',
    'color-contrast', 'shape-affordance', 'motion-interaction', 'accessibility-adaptive'
)
$protectedX3Roles = @($protectedAndroidRoles + 'eink-scanner')

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
                $surfacePreviews = @($surface.defaultPreview, $surface.adaptivePreview)
                if (-not [string]::IsNullOrWhiteSpace($surface.compactPreview)) {
                    $surfacePreviews += $surface.compactPreview
                }
                foreach ($preview in $surfacePreviews) {
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
                -not $reviewPolicy.roles -or -not $reviewPolicy.surfaces -or -not $reviewPolicy.candidateTasks) {
                $failures.Add("[$($rule.id)] UI review policy must use schemaVersion 1, terra_medium, roles, surfaces, and candidateTasks.")
                continue
            }
            foreach ($protectedGlob in $protectedCoverageGlobs) {
                if (@($reviewPolicy.coveredSourceGlobs) -notcontains $protectedGlob) {
                    $failures.Add("[$($rule.id)] UI review policy cannot remove protected coverage '$protectedGlob'.")
                }
            }
            $expectedCandidateTasks = @(
                ':app:testCommunityDebugUnitTest', ':app:updateCommunityDebugScreenshotTest',
                ':app:testPlayDebugUnitTest', ':app:updatePlayDebugScreenshotTest'
            )
            if (@($reviewPolicy.candidateTasks).Count -ne $expectedCandidateTasks.Count -or
                @($expectedCandidateTasks | Where-Object { $_ -notin @($reviewPolicy.candidateTasks) }).Count -gt 0) {
                $failures.Add("[$($rule.id)] UI evidence candidates must run the exact Community/Play unit-test and screenshot-update task set.")
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
                } else {
                    $promptContent = Get-Content -LiteralPath $promptPath -Raw
                    if ($promptContent -notmatch [regex]::Escape(('Role ID: `' + $role.id + '`'))) {
                        $failures.Add("[$($rule.id)] Role '$($role.id)' prompt must declare its role ID.")
                    }
                    foreach ($requiredCheck in @($role.requiredChecks)) {
                        if ($promptContent -notmatch [regex]::Escape($requiredCheck)) {
                            $failures.Add("[$($rule.id)] Role '$($role.id)' prompt must require check '$requiredCheck'.")
                        }
                    }
                    if (@($role.requiredResources) -notcontains $protectedVisualLanguageResource -or
                        $promptContent -notmatch [regex]::Escape($protectedVisualLanguageResource)) {
                        $failures.Add("[$($rule.id)] Role '$($role.id)' must study the protected visual-language foundation.")
                    }
                    foreach ($requiredResource in @($role.requiredResources)) {
                        $resourcePath = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $requiredResource))
                        if (-not $resourcePath.StartsWith([System.IO.Path]::GetFullPath($repoRoot), [System.StringComparison]::OrdinalIgnoreCase) -or
                            -not (Test-Path -LiteralPath $resourcePath -PathType Leaf) -or
                            $promptContent -notmatch [regex]::Escape($requiredResource)) {
                            $failures.Add("[$($rule.id)] Role '$($role.id)' has a missing or unreferenced required resource '$requiredResource'.")
                        }
                    }
                }
                if (-not $role.requiredChecks -or $role.requiredChecks.Count -eq 0) {
                    $failures.Add("[$($rule.id)] Role '$($role.id)' must own concrete check IDs.")
                }
            }
            foreach ($protectedRoleId in $protectedRoleChecks.Keys) {
                if (-not $roleById.ContainsKey($protectedRoleId)) {
                    $failures.Add("[$($rule.id)] UI review policy cannot remove protected role '$protectedRoleId'.")
                    continue
                }
                $expectedChecks = @($protectedRoleChecks[$protectedRoleId])
                $actualChecks = @($roleById[$protectedRoleId].requiredChecks)
                if ($actualChecks.Count -ne $expectedChecks.Count -or
                    @($expectedChecks | Where-Object { $_ -notin $actualChecks }).Count -gt 0) {
                    $failures.Add("[$($rule.id)] Protected role '$protectedRoleId' must retain its exact required checks.")
                }
            }
            $trackedPaths = Get-TrackedPolicyPaths -RepositoryRoot $repoRoot
            $sourceOwners = @{}
            foreach ($surface in $reviewPolicy.surfaces) {
                if ([string]::IsNullOrWhiteSpace($surface.id) -or $surface.baselineCommit -notmatch '^[0-9a-fA-F]{40}$' -or -not $surface.sourceGlobs -or
                    -not $surface.requiredRoles -or [string]::IsNullOrWhiteSpace($surface.record)) {
                    $failures.Add("[$($rule.id)] Each UI review surface needs id, sourceGlobs, requiredRoles, and record.")
                    continue
                }
                if ($surface.baselineCommit.ToLowerInvariant() -ne $protectedUiReviewBaseline) {
                    $failures.Add("[$($rule.id)] Surface '$($surface.id)' cannot move the protected review baseline.")
                }
                $expectedSurfaceRoles = switch ($surface.kind) {
                    'android-ui' { $protectedAndroidRoles }
                    'x3-display' { $protectedX3Roles }
                    default { $null }
                }
                if ($null -eq $expectedSurfaceRoles -or @($surface.requiredRoles).Count -ne @($expectedSurfaceRoles).Count -or
                    @($expectedSurfaceRoles | Where-Object { $_ -notin @($surface.requiredRoles) }).Count -gt 0) {
                    $failures.Add("[$($rule.id)] Surface '$($surface.id)' must retain the exact protected role set for kind '$($surface.kind)'.")
                }
                if (-not (Test-PolicyBaseline -RepositoryRoot $repoRoot -BaselineCommit $surface.baselineCommit)) {
                    $failures.Add("[$($rule.id)] Surface '$($surface.id)' has an invalid or non-ancestor baseline commit.")
                    continue
                }
                foreach ($sourcePath in (Get-SurfaceSourcePaths -Surface $surface -TrackedPaths $trackedPaths)) {
                    if (($protectedCoverageGlobs | Where-Object { Test-PolicyGlob -Path $sourcePath -Glob $_ }).Count -eq 0) {
                        $failures.Add("[$($rule.id)] Surface '$($surface.id)' claims '$sourcePath' outside protected UI/display coverage.")
                    }
                    if ($sourceOwners.ContainsKey($sourcePath)) {
                        $failures.Add("[$($rule.id)] UI/display source '$sourcePath' is claimed by both '$($sourceOwners[$sourcePath])' and '$($surface.id)'.")
                    } else {
                        $sourceOwners[$sourcePath] = $surface.id
                    }
                }
            }
            $allChangedCoveredPaths = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
            foreach ($surface in $reviewPolicy.surfaces) {
                $changedForSurface = Get-ChangedPolicyPaths -RepositoryRoot $repoRoot -BaselineCommit $surface.baselineCommit
                foreach ($changedPath in $changedForSurface) {
                    if ((@($reviewPolicy.coveredSourceGlobs) | Where-Object { Test-PolicyGlob -Path $changedPath -Glob $_ }).Count -gt 0) {
                        [void]$allChangedCoveredPaths.Add($changedPath)
                        if (-not $sourceOwners.ContainsKey($changedPath)) {
                            $failures.Add("[$($rule.id)] Changed UI/display source '$changedPath' is not mapped to one review surface.")
                        }
                    }
                }
            }
            if ($Mode -eq 'UiEvidenceCandidate') {
                continue
            }
            foreach ($surface in $reviewPolicy.surfaces) {
                if (($Mode -eq 'FirmwareRelease' -and $surface.kind -eq 'android-ui') -or
                    ($Mode -eq 'Release' -and $surface.kind -eq 'x3-display')) {
                    continue
                }
                $changedSinceBaseline = Get-ChangedPolicyPaths -RepositoryRoot $repoRoot -BaselineCommit $surface.baselineCommit
                $surfaceSourcePaths = Get-SurfaceSourcePaths -Surface $surface -TrackedPaths $trackedPaths
                if (@($changedSinceBaseline | Where-Object { $_ -in $surfaceSourcePaths }).Count -eq 0) {
                    continue
                }
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
                    $record.sourceCommit -notmatch '^[0-9a-fA-F]{40}$' -or
                    [string]::IsNullOrWhiteSpace($record.implementationOwnerId)) {
                    $failures.Add("[$($rule.id)] Review record for '$($surface.id)' has invalid schema, surface, or source commit.")
                }
                if ($null -eq $record.findings -or @($record.findings).Count -ne 0) {
                    $failures.Add("[$($rule.id)] Surface '$($surface.id)' must record an explicit empty findings array before release.")
                }
                $sourceFiles = $surfaceSourcePaths
                if ($sourceFiles.Count -eq 0) {
                    $failures.Add("[$($rule.id)] Surface '$($surface.id)' classifies no tracked source files.")
                    continue
                }
                $latestSurfaceCommit = Get-LatestSurfaceCommit -RepositoryRoot $repoRoot -Paths $sourceFiles
                if ($null -eq $latestSurfaceCommit -or [string]::IsNullOrWhiteSpace($record.sourceCommit) -or
                    $record.sourceCommit.ToLowerInvariant() -ne $latestSurfaceCommit) {
                    $failures.Add("[$($rule.id)] Surface '$($surface.id)' receipt must bind to its latest source-changing commit.")
                }
                $reviewedFiles = $record.reviewedFiles
                if ($null -eq $reviewedFiles) {
                    $failures.Add("[$($rule.id)] Surface '$($surface.id)' must hash every classified reviewed file.")
                    $reviewedFiles = [pscustomobject]@{}
                }
                foreach ($sourceFile in $sourceFiles) {
                    $sourcePath = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $sourceFile))
                    if (-not $sourcePath.StartsWith([System.IO.Path]::GetFullPath($repoRoot), [System.StringComparison]::OrdinalIgnoreCase) -or
                        -not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
                        $failures.Add("[$($rule.id)] Surface '$($surface.id)' has a missing source file '$sourceFile'.")
                        continue
                    }
                    $hashProperty = $reviewedFiles.PSObject.Properties[$sourceFile]
                    $actualHash = (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash.ToLowerInvariant()
                    if ($null -eq $hashProperty -or $hashProperty.Value.ToString().ToLowerInvariant() -ne $actualHash) {
                        $failures.Add("[$($rule.id)] Surface '$($surface.id)' review is stale for '$sourceFile'. Re-run its Terra reviewers.")
                    }
                }
                if ($null -ne $reviewedFiles) {
                    foreach ($reviewedPath in $reviewedFiles.PSObject.Properties.Name) {
                        if ($reviewedPath -notin $sourceFiles) {
                            $failures.Add("[$($rule.id)] Surface '$($surface.id)' review includes unclassified or obsolete source '$reviewedPath'.")
                        }
                    }
                }
                $receiptByRole = @{}
                $reviewerIds = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
                $citedEvidencePaths = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
                foreach ($receipt in $record.receipts) {
                    if (-not $roleById.ContainsKey($receipt.role)) {
                        $failures.Add("[$($rule.id)] Surface '$($surface.id)' has an unknown reviewer role '$($receipt.role)'.")
                        continue
                    }
                    if ($receiptByRole.ContainsKey($receipt.role)) {
                        $failures.Add("[$($rule.id)] Surface '$($surface.id)' repeats role '$($receipt.role)'.")
                        continue
                    }
                    $receiptByRole[$receipt.role] = $receipt
                    if ([string]::IsNullOrWhiteSpace($receipt.reviewerId) -or -not $reviewerIds.Add($receipt.reviewerId)) {
                        $failures.Add("[$($rule.id)] Surface '$($surface.id)' must use one distinct reviewer per role.")
                    }
                    if ($receipt.reviewerId -ieq $record.implementationOwnerId -or
                        [string]::IsNullOrWhiteSpace($receipt.reviewerTaskId) -or
                        -not $reviewerIds.Add("task:$($receipt.reviewerTaskId)")) {
                        $failures.Add("[$($rule.id)] Surface '$($surface.id)' must use distinct non-owner reviewer and task identities.")
                    }
                    if ($receipt.agentType -ne 'terra_medium' -or $receipt.verdict -ne 'pass') {
                        $failures.Add("[$($rule.id)] Role '$($receipt.role)' for '$($surface.id)' lacks a passing Terra receipt.")
                    }
                    if ($null -eq $receipt.findings -or @($receipt.findings).Count -ne 0) {
                        $failures.Add("[$($rule.id)] Role '$($receipt.role)' for '$($surface.id)' must record an explicit empty findings array before release.")
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
                        $evidenceMap = $receiptByRole[$requiredRoleId].checkEvidence
                        $checkEvidence = if ($null -eq $evidenceMap) { @() } else { @($evidenceMap.PSObject.Properties[$requiredCheck].Value) }
                        if ($checkEvidence.Count -eq 0) {
                            $failures.Add("[$($rule.id)] Role '$requiredRoleId' for '$($surface.id)' needs leaf evidence for '$requiredCheck'.")
                            continue
                        }
                        if ($requiredCheck -eq 'interaction-affordance-is-self-evident' -and
                            @($checkEvidence | Where-Object { $_.path -match '\.png$' }).Count -eq 0) {
                            $failures.Add("[$($rule.id)] Role '$requiredRoleId' for '$($surface.id)' must cite rendered PNG evidence for '$requiredCheck'.")
                        }
                        foreach ($evidence in $checkEvidence) {
                            if ([string]::IsNullOrWhiteSpace($evidence.path) -or $evidence.sha256 -notmatch '^[0-9a-fA-F]{64}$') {
                                $failures.Add("[$($rule.id)] Role '$requiredRoleId' for '$($surface.id)' has malformed evidence for '$requiredCheck'.")
                                continue
                            }
                            $resolvedEvidence = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $evidence.path))
                            if (-not $resolvedEvidence.StartsWith([System.IO.Path]::GetFullPath($repoRoot), [System.StringComparison]::OrdinalIgnoreCase) -or
                                -not (Test-Path -LiteralPath $resolvedEvidence -PathType Leaf)) {
                                $failures.Add("[$($rule.id)] Role '$requiredRoleId' for '$($surface.id)' cites missing leaf evidence '$($evidence.path)'.")
                                continue
                            }
                            $normalizedEvidencePath = $evidence.path.Replace('\', '/')
                            if ((@($reviewPolicy.evidenceGlobs) | Where-Object { Test-PolicyGlob -Path $normalizedEvidencePath -Glob $_ }).Count -eq 0) {
                                $failures.Add("[$($rule.id)] Role '$requiredRoleId' for '$($surface.id)' cites evidence outside approved evidence kinds '$($evidence.path)'.")
                                continue
                            }
                            [void]$citedEvidencePaths.Add($normalizedEvidencePath)
                            $actualEvidenceHash = (Get-FileHash -LiteralPath $resolvedEvidence -Algorithm SHA256).Hash.ToLowerInvariant()
                            if ($actualEvidenceHash -ne $evidence.sha256.ToString().ToLowerInvariant()) {
                                $failures.Add("[$($rule.id)] Role '$requiredRoleId' for '$($surface.id)' has stale evidence '$($evidence.path)'.")
                            }
                        }
                    }
                }
                if (-not $surface.requiredEvidenceFiles -or @($surface.requiredEvidenceFiles).Count -eq 0) {
                    $failures.Add("[$($rule.id)] Changed surface '$($surface.id)' must declare deterministic required evidence files.")
                } else {
                    foreach ($requiredEvidencePath in @($surface.requiredEvidenceFiles)) {
                        $normalizedRequiredEvidence = $requiredEvidencePath.Replace('\', '/')
                        if (-not $citedEvidencePaths.Contains($normalizedRequiredEvidence)) {
                            $failures.Add("[$($rule.id)] Surface '$($surface.id)' receipts do not cover required evidence '$requiredEvidencePath'.")
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
