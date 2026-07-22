$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$ToolsRoot = Join-Path $ProjectRoot ".tools"

$env:JAVA_HOME = Join-Path $ToolsRoot "jdk-17"
$env:ANDROID_HOME = Join-Path $ToolsRoot "android-sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:PLATFORMIO_CORE_DIR = Join-Path $ToolsRoot "platformio-core"
$env:PYTHONUTF8 = "1"
$env:PYTHONIOENCODING = "utf-8"
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$env:GIT_CONFIG_COUNT = "2"
$env:GIT_CONFIG_KEY_0 = "safe.directory"
$env:GIT_CONFIG_VALUE_0 = $ProjectRoot.Replace("\", "/")
$env:GIT_CONFIG_KEY_1 = "safe.directory"
$env:GIT_CONFIG_VALUE_1 = (Join-Path $ProjectRoot "firmware").Replace("\", "/")

$ToolPaths = @(
    (Join-Path $env:JAVA_HOME "bin"),
    (Join-Path $env:ANDROID_HOME "platform-tools"),
    (Join-Path $env:ANDROID_HOME "cmdline-tools\latest\bin"),
    (Join-Path $ToolsRoot "gradle-9.5.0\bin"),
    (Join-Path $ToolsRoot "platformio-venv\Scripts")
)

$env:Path = (($ToolPaths + @($env:Path)) -join ";")
Write-Host "Xteink toolchains are active for this PowerShell session."
