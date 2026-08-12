param(
    [string]$ManifestPath
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
