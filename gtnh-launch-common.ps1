Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Shared implementation for the local 1.7.10 / GTNH launchers.
# This file only builds, installs, clones isolated instances and invokes Prism.
# Dot-sourcing it never starts Minecraft by itself.

function Get-Rts1710Context {
    param([Parameter(Mandatory = $true)][string]$ProjectRoot)

    $resolvedProject = [System.IO.Path]::GetFullPath($ProjectRoot)
    $cacheRoot = 'E:\RTSbuilding-port-cache'
    $prismRoot = Join-Path $cacheRoot 'PrismLauncher-11.0.3'
    [pscustomobject]@{
        ProjectRoot = $resolvedProject
        SharedGradleHome = 'E:\RTSbuilding\.gradle-user-home'
        BuildJava = 'C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot'
        CacheRoot = $cacheRoot
        PrismRoot = $prismRoot
        PrismExe = Join-Path $prismRoot 'prismlauncher.exe'
        CleanInstanceId = 'GT New Horizons 2.8.4'
        FastInstanceId = 'GT New Horizons 2.8.4 - FAST'
    }
}

function Resolve-Rts1710Gradle {
    param([Parameter(Mandatory = $true)]$Context)

    $cachedRoot = Join-Path $Context.SharedGradleHome 'wrapper\dists\gradle-9.2.1-bin'
    if (Test-Path -LiteralPath $cachedRoot) {
        $cached = Get-ChildItem -LiteralPath $cachedRoot -Recurse -Filter 'gradle.bat' -File |
            Select-Object -First 1
        if ($null -ne $cached) {
            return $cached.FullName
        }
    }

    $wrapper = Join-Path $Context.ProjectRoot 'gradlew.bat'
    if (Test-Path -LiteralPath $wrapper) {
        return $wrapper
    }
    throw "Gradle 9.2.1 cache and project wrapper are both missing: $($Context.ProjectRoot)"
}

function Invoke-Rts1710Gradle {
    param(
        [Parameter(Mandatory = $true)]$Context,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    if (-not (Test-Path -LiteralPath $Context.BuildJava)) {
        throw "Build JDK is missing: $($Context.BuildJava)"
    }
    $gradle = Resolve-Rts1710Gradle -Context $Context
    $oldJava = $env:JAVA_HOME
    $oldPath = $env:PATH
    $oldGradleHome = $env:GRADLE_USER_HOME
    try {
        $env:JAVA_HOME = $Context.BuildJava
        $env:PATH = (Join-Path $Context.BuildJava 'bin') + ';' + $oldPath
        $env:GRADLE_USER_HOME = $Context.SharedGradleHome
        Push-Location -LiteralPath $Context.ProjectRoot
        try {
            # Out-Host keeps Gradle visible without leaking its output into function return values.
            & $gradle @Arguments | Out-Host
            if ($LASTEXITCODE -ne 0) {
                throw "Gradle failed with exit code $LASTEXITCODE"
            }
        } finally {
            Pop-Location
        }
    } finally {
        $env:JAVA_HOME = $oldJava
        $env:PATH = $oldPath
        $env:GRADLE_USER_HOME = $oldGradleHome
    }
}

function Get-Rts1710BuiltJar {
    param([Parameter(Mandatory = $true)]$Context)

    $libs = Join-Path $Context.ProjectRoot 'build\libs'
    $jar = Get-ChildItem -LiteralPath $libs -Filter 'rtsbuilding-forge-1.7.10-gtnh-*.jar' -File |
        Where-Object { $_.Name -notmatch '-(dev|sources)\.jar$' } |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1
    if ($null -eq $jar) {
        throw "No installable 1.7.10 GTNH jar was found in $libs"
    }
    return $jar.FullName
}

function Build-Rts1710Jar {
    param([Parameter(Mandatory = $true)]$Context)

    Invoke-Rts1710Gradle -Context $Context -Arguments @(
        'build', '--no-daemon', '--no-configuration-cache'
    )
    return Get-Rts1710BuiltJar -Context $Context
}

function Assert-RtsInstancePath {
    param(
        [Parameter(Mandatory = $true)]$Context,
        [Parameter(Mandatory = $true)][string]$Path
    )

    $instancesRoot = [System.IO.Path]::GetFullPath((Join-Path $Context.PrismRoot 'instances'))
    $resolved = [System.IO.Path]::GetFullPath($Path)
    $prefix = $instancesRoot.TrimEnd('\') + '\'
    if (-not $resolved.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to modify a path outside the Prism instances root: $resolved"
    }
}

function Install-Rts1710Jar {
    param(
        [Parameter(Mandatory = $true)]$Context,
        [Parameter(Mandatory = $true)][string]$InstanceId,
        [Parameter(Mandatory = $true)][string]$JarPath
    )

    $instance = Join-Path (Join-Path $Context.PrismRoot 'instances') $InstanceId
    Assert-RtsInstancePath -Context $Context -Path $instance
    if (-not (Test-Path -LiteralPath $instance)) {
        throw "Instance does not exist: $instance"
    }
    $mods = Join-Path $instance '.minecraft\mods'
    New-Item -ItemType Directory -Force -Path $mods | Out-Null
    Get-ChildItem -LiteralPath $mods -Filter 'rtsbuilding*.jar' -File -ErrorAction SilentlyContinue |
        ForEach-Object { Remove-Item -LiteralPath $_.FullName -Force }
    $target = Join-Path $mods ([System.IO.Path]::GetFileName($JarPath))
    Copy-Item -LiteralPath $JarPath -Destination $target -Force
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $target).Hash
    Write-Host "[RTSBuilding] Installed: $target"
    Write-Host "[RTSBuilding] SHA-256: $hash"
    return $target
}

function Copy-RtsGtnhFastInstance {
    param([Parameter(Mandatory = $true)]$Context)

    $instances = Join-Path $Context.PrismRoot 'instances'
    $source = Join-Path $instances $Context.CleanInstanceId
    $target = Join-Path $instances $Context.FastInstanceId
    Assert-RtsInstancePath -Context $Context -Path $source
    Assert-RtsInstancePath -Context $Context -Path $target
    if (-not (Test-Path -LiteralPath $source)) {
        throw "GTNH CLEAN instance does not exist: $source"
    }
    if (-not (Test-Path -LiteralPath $target)) {
        New-Item -ItemType Directory -Path $target | Out-Null
        $excluded = @(
            (Join-Path $source '.minecraft\saves'),
            (Join-Path $source '.minecraft\logs'),
            (Join-Path $source '.minecraft\crash-reports')
        )
        & robocopy.exe $source $target /E /R:2 /W:1 /NFL /NDL /NJH /NJS /NP /XD @excluded
        if ($LASTEXITCODE -gt 7) {
            throw "Failed to clone the GTNH FAST instance; robocopy exit code: $LASTEXITCODE"
        }
    }

    $cfg = Join-Path $target 'instance.cfg'
    if (Test-Path -LiteralPath $cfg) {
        $lines = Get-Content -LiteralPath $cfg -Encoding UTF8
        $hasName = $false
        $updated = foreach ($line in $lines) {
            if ($line -like 'name=*') {
                $hasName = $true
                'name=GTNH 2.8.4 FAST (Rates)'
            } else {
                $line
            }
        }
        if (-not $hasName) {
            $updated += 'name=GTNH 2.8.4 FAST (Rates)'
        }
        [System.IO.File]::WriteAllLines(
            $cfg, [string[]]$updated, [System.Text.UTF8Encoding]::new($false))
    }
    return $target
}

function Get-RtsPinnedAddon {
    param(
        [Parameter(Mandatory = $true)]$Context,
        [Parameter(Mandatory = $true)][string]$FileName,
        [Parameter(Mandatory = $true)][string]$Uri,
        [Parameter(Mandatory = $true)][string]$Sha256
    )

    $cache = Join-Path $Context.CacheRoot 'gtnh-fast-addons'
    New-Item -ItemType Directory -Force -Path $cache | Out-Null
    $target = Join-Path $cache $FileName
    $valid = Test-Path -LiteralPath $target
    if ($valid) {
        $valid = (Get-FileHash -Algorithm SHA256 -LiteralPath $target).Hash -eq $Sha256
    }
    if (-not $valid) {
        if (Test-Path -LiteralPath $target) {
            Remove-Item -LiteralPath $target -Force
        }
        Invoke-WebRequest -UseBasicParsing -Uri $Uri -OutFile $target
    }
    $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $target).Hash
    if ($actual -ne $Sha256) {
        throw "Downloaded file hash mismatch for $FileName; actual: $actual"
    }
    return $target
}

function Install-RtsPinnedAddon {
    param(
        [Parameter(Mandatory = $true)]$Context,
        [Parameter(Mandatory = $true)][string]$InstanceId,
        [Parameter(Mandatory = $true)][string]$AddonPath,
        [Parameter(Mandatory = $true)][string]$OldFilePattern
    )

    $instance = Join-Path (Join-Path $Context.PrismRoot 'instances') $InstanceId
    Assert-RtsInstancePath -Context $Context -Path $instance
    $mods = Join-Path $instance '.minecraft\mods'
    New-Item -ItemType Directory -Force -Path $mods | Out-Null
    Get-ChildItem -LiteralPath $mods -Filter $OldFilePattern -File -ErrorAction SilentlyContinue |
        ForEach-Object { Remove-Item -LiteralPath $_.FullName -Force }
    Copy-Item -LiteralPath $AddonPath `
        -Destination (Join-Path $mods ([System.IO.Path]::GetFileName($AddonPath))) -Force
}

function Start-RtsPrismInstance {
    param(
        [Parameter(Mandatory = $true)]$Context,
        [Parameter(Mandatory = $true)][string]$InstanceId,
        [string]$Profile,
        [switch]$NoLaunch
    )

    if (-not (Test-Path -LiteralPath $Context.PrismExe)) {
        throw "Prism Launcher is missing: $($Context.PrismExe)"
    }
    if ($NoLaunch) {
        Write-Host '[RTSBuilding] -NoLaunch: instance prepared; Prism and Minecraft were not started.'
        return
    }
    $arguments = @('--launch', ('"' + $InstanceId.Replace('"', '') + '"'))
    if (-not [string]::IsNullOrWhiteSpace($Profile)) {
        $arguments += @('--profile', ('"' + $Profile.Replace('"', '') + '"'))
    }
    # This is an interactive user entry point, so Prism and the game stay visible.
    Start-Process -FilePath $Context.PrismExe `
        -WorkingDirectory $Context.PrismRoot -ArgumentList $arguments
}
