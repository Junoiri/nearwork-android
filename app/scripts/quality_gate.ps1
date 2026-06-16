Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-JavaHome {
    $candidates = @()

    if ($env:JAVA_HOME) {
        $candidates += $env:JAVA_HOME
    }

    $candidates += Get-ChildItem -Path "$PWD\.jdk" -Directory -Filter "jdk-17*" -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }

    $candidates += "C:\Program Files\Android\Android Studio\jbr"

    $candidates += Get-ChildItem -Path "C:\Program Files\Java" -Directory -Filter "jdk-17*" -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }
    $candidates += Get-ChildItem -Path "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-17*" -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }

    $javaFromPath = $null
    try {
        $javaFromPath = (& where.exe java 2>$null | Select-Object -First 1)
    } catch {
        $javaFromPath = $null
    }
    if ($javaFromPath) {
        $candidates += (Split-Path -Parent (Split-Path -Parent $javaFromPath))
    }

    $seen = @{}
    foreach ($candidate in $candidates) {
        if (-not $candidate) { continue }
        if ($seen.ContainsKey($candidate)) { continue }
        $seen[$candidate] = $true

        $javaExe = Join-Path $candidate "bin\java.exe"
        if (-not (Test-Path $javaExe)) { continue }

        $versionOutput = $null
        $prevPreference = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            $versionOutput = & $javaExe -version 2>&1
        } finally {
            $ErrorActionPreference = $prevPreference
        }

        if ($versionOutput -match 'version "17(\.|\")') {
            return @{ Home = $candidate; Version = $versionOutput }
        }
    }

    return $null
}

$resolved = Resolve-JavaHome
if (-not $resolved) {
    Write-Error "Unable to find a Java 17 installation. Install JDK 17 or Android Studio JBR."
}

$env:JAVA_HOME = $resolved.Home
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:GRADLE_USER_HOME = "$PWD\.gradle-local"
$env:NEARWORK_LOCAL_BUILD_DIR = "$env:TEMP\nearwork-thesis-build"

Write-Host "JAVA_HOME: $env:JAVA_HOME"
Write-Host ($resolved.Version -join [Environment]::NewLine)

function Run-Gradle {
    param(
        [Parameter(Mandatory = $true)][string[]]$Args,
        [Parameter(Mandatory = $true)][string]$Label
    )
    Write-Host ""
    Write-Host "== $Label =="
    & "$PWD\gradlew.bat" --no-daemon @Args
    if ($LASTEXITCODE -ne 0) {
        Write-Error "FAILED: $Label"
    }
    Write-Host "PASSED: $Label"
}

Run-Gradle -Args @(":app:clean", ":app:assembleDebug") -Label "assembleDebug"
Run-Gradle -Args @(":app:testDebugUnitTest") -Label "testDebugUnitTest"
Run-Gradle -Args @(":app:lintDebug") -Label "lintDebug"
