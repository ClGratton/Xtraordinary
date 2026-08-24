$scriptPath = Join-Path $PSScriptRoot 'maintain-x3-device.ps1'
$content = Get-Content -LiteralPath $scriptPath -Raw
if ($content -notmatch "ValidateSet\('Begin', 'Restore', 'Run'\)") { throw 'Maintenance script must expose Begin/Restore/Run lifecycle.' }
if ($content -notmatch '(?s)finally\s*\{.*Restore-Maintenance') { throw 'Maintenance Run must restore in finally.' }
if ($content -notmatch 'X3_MAINTENANCE_LEASE') { throw 'Maintenance script must use the guarded app intent.' }
if ($content -notmatch 'seconds 0') { throw 'Maintenance script must issue an explicit zero-second restore.' }
if ($content -notmatch 'manage-pixel-screen-timeout\.ps1' -or $content -notmatch 'Begin-PixelLifecycle' -or $content -notmatch 'Restore-PixelLifecycle') { throw 'Maintenance workflow must bracket X3 work with Pixel timeout lifecycle.' }
Write-Output 'X3 maintenance script contract passed.'
