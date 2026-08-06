Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# 1.7.10 / GTNH 启动器的共享实现。
# 这里只负责构建、准备 HMCL 隔离实例和打开 HMCL；加载本文件不会启动游戏。

function Get-Rts1710Context {
    param([Parameter(Mandatory = $true)][string]$ProjectRoot)

    $resolvedProject = [System.IO.Path]::GetFullPath($ProjectRoot)
    $cacheRoot = 'E:\RTSbuilding-port-cache'
    $hmclRoot = 'E:\HMCL-3.13.1'
    [pscustomobject]@{
        ProjectRoot = $resolvedProject
        SharedGradleHome = 'E:\RTSbuilding\.gradle-user-home'
        BuildJava = 'C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot'
        CacheRoot = $cacheRoot
        HmclRoot = $hmclRoot
        HmclExe = Join-Path $hmclRoot 'HMCL-3.13.1.exe'
        HmclConfig = Join-Path $hmclRoot '.hmcl\hmcl.json'
        HmclGameRoot = 'E:\RTSbuilding\run'
        HmclProfileName = 'rts'
        OfficialPackZip = Join-Path $cacheRoot 'gtnh-2.8.4\GT_New_Horizons_2.8.4_Java_17-25.zip'
        CleanInstanceId = 'GT New Horizons 2.8.4 - RTS CLEAN'
        FastInstanceId = 'GT New Horizons 2.8.4 - RTS SIMPLE'
        HelperRoot = Join-Path $cacheRoot 'hmcl-rtsbuilding-bridge-3.13.1'
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
            # Out-Host 让 Gradle 输出保持可见，同时不污染函数返回值。
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
    $jar = Get-ChildItem -LiteralPath $libs -Filter 'rtsbuilding-forge-1.7.10-*.jar' -File |
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

    $instancesRoot = [System.IO.Path]::GetFullPath((Join-Path $Context.HmclGameRoot 'versions'))
    $resolved = [System.IO.Path]::GetFullPath($Path)
    $prefix = $instancesRoot.TrimEnd('\') + '\'
    if (-not $resolved.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to modify a path outside the HMCL versions root: $resolved"
    }
}

function Get-RtsHmclInstancePath {
    param(
        [Parameter(Mandatory = $true)]$Context,
        [Parameter(Mandatory = $true)][string]$InstanceId
    )

    $instance = Join-Path (Join-Path $Context.HmclGameRoot 'versions') $InstanceId
    Assert-RtsInstancePath -Context $Context -Path $instance
    return $instance
}

function Test-RtsHmclInstanceReady {
    param(
        [Parameter(Mandatory = $true)]$Context,
        [Parameter(Mandatory = $true)][string]$InstanceId
    )

    $instance = Get-RtsHmclInstancePath -Context $Context -InstanceId $InstanceId
    $marker = Join-Path $instance '.rtsbuilding-hmcl-ready'
    $markerMatches = (Test-Path -LiteralPath $marker) -and
        ([System.IO.File]::ReadAllText($marker).Trim() -eq $InstanceId)
    return (Test-Path -LiteralPath (Join-Path $instance ($InstanceId + '.json'))) -and
        (Test-Path -LiteralPath (Join-Path $instance ($InstanceId + '.jar'))) -and
        (Test-Path -LiteralPath (Join-Path $instance 'hmclversion.cfg')) -and
        (Test-Path -LiteralPath (Join-Path $instance 'mods')) -and
        $markerMatches
}

function Assert-RtsHmclStopped {
    param([Parameter(Mandatory = $true)]$Context)

    $running = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
        Where-Object {
            ($_.Name -eq 'HMCL-3.13.1.exe' -and $_.ExecutablePath -eq $Context.HmclExe) -or
            ($_.Name -in @('java.exe', 'javaw.exe') -and
                $_.CommandLine -match '(?i)-jar\s+"?HMCL-3\.13\.1\.exe"?')
        }
    if ($null -ne $running) {
        throw 'HMCL is currently running. Close HMCL before preparing or selecting a GTNH instance.'
    }
    $importer = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -match 'HmclModpackImporter' }
    if ($null -ne $importer) {
        throw 'An HMCL GTNH import is already running. Wait for it to finish before retrying.'
    }
}

function Get-RtsHmclOpenJfxJars {
    param([Parameter(Mandatory = $true)]$Context)

    $openJfx = Join-Path $Context.HmclRoot '.hmcl\dependencies\windows-x86_64\openjfx'
    $jars = @(Get-ChildItem -LiteralPath $openJfx -Filter '*.jar' -File -ErrorAction SilentlyContinue |
        Sort-Object Name | ForEach-Object { $_.FullName })
    if ($jars.Count -lt 3) {
        throw "HMCL OpenJFX dependencies are missing. Open HMCL once, close it, then retry: $openJfx"
    }
    return $jars
}

function Initialize-RtsHmclBridge {
    param([Parameter(Mandatory = $true)]$Context)

    if (-not (Test-Path -LiteralPath $Context.HmclExe)) {
        throw "HMCL 3.13.1 is missing: $($Context.HmclExe)"
    }
    $javaBin = Join-Path $Context.BuildJava 'bin'
    $jarTool = Join-Path $javaBin 'jar.exe'
    $javac = Join-Path $javaBin 'javac.exe'
    if (-not (Test-Path -LiteralPath $jarTool) -or -not (Test-Path -LiteralPath $javac)) {
        throw "JDK tools are missing: $javaBin"
    }

    $extractRoot = Join-Path $Context.HelperRoot 'hmcl-classes'
    $cleanJar = Join-Path $Context.HelperRoot 'hmcl-3.13.1-classpath.jar'
    $helperClasses = Join-Path $Context.HelperRoot 'helper-classes'
    $helperSource = Join-Path $Context.ProjectRoot 'scripts\HmclModpackImporter.java'
    New-Item -ItemType Directory -Force -Path $Context.HelperRoot | Out-Null

    $repack = -not (Test-Path -LiteralPath $cleanJar)
    if (-not $repack) {
        $repack = (Get-Item -LiteralPath $cleanJar).LastWriteTimeUtc -lt
            (Get-Item -LiteralPath $Context.HmclExe).LastWriteTimeUtc
    }
    if ($repack) {
        if (Test-Path -LiteralPath $extractRoot) {
            $resolvedExtract = [System.IO.Path]::GetFullPath($extractRoot)
            $resolvedHelper = [System.IO.Path]::GetFullPath($Context.HelperRoot).TrimEnd('\') + '\'
            if (-not $resolvedExtract.StartsWith($resolvedHelper, [System.StringComparison]::OrdinalIgnoreCase)) {
                throw "Refusing to replace unexpected helper directory: $resolvedExtract"
            }
            Remove-Item -LiteralPath $extractRoot -Recurse -Force
        }
        New-Item -ItemType Directory -Path $extractRoot | Out-Null
        Push-Location -LiteralPath $extractRoot
        try {
            # HMCL 的 Windows EXE 是带启动壳的 JAR；先解包再重打包，供 javac 使用。
            $LASTEXITCODE = 0
            & $jarTool xf $Context.HmclExe
            if ($LASTEXITCODE -ne 0) {
                throw "Failed to extract HMCL; jar exit code: $LASTEXITCODE"
            }
        } finally {
            Pop-Location
        }
        $LASTEXITCODE = 0
        & $jarTool cf $cleanJar -C $extractRoot .
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to build HMCL classpath jar; jar exit code: $LASTEXITCODE"
        }
    }

    $classFile = Join-Path $helperClasses 'HmclModpackImporter.class'
    $compile = -not (Test-Path -LiteralPath $classFile)
    if (-not $compile) {
        $compile = (Get-Item -LiteralPath $classFile).LastWriteTimeUtc -lt
            (Get-Item -LiteralPath $helperSource).LastWriteTimeUtc
    }
    $openJfxJars = @(Get-RtsHmclOpenJfxJars -Context $Context)
    if ($compile) {
        New-Item -ItemType Directory -Force -Path $helperClasses | Out-Null
        $compileClasspath = (@($cleanJar) + $openJfxJars) -join ';'
        $LASTEXITCODE = 0
        & $javac -cp $compileClasspath -d $helperClasses $helperSource
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to compile the HMCL bridge; javac exit code: $LASTEXITCODE"
        }
    }

    [pscustomobject]@{
        Java = Join-Path $javaBin 'java.exe'
        Classpath = (@($helperClasses, $cleanJar) + $openJfxJars) -join ';'
    }
}

function Invoke-RtsHmclBridge {
    param(
        [Parameter(Mandatory = $true)]$Context,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    Assert-RtsHmclStopped -Context $Context
    $bridge = Initialize-RtsHmclBridge -Context $Context
    Push-Location -LiteralPath $Context.HmclRoot
    try {
        $LASTEXITCODE = 0
        & $bridge.Java --enable-native-access=ALL-UNNAMED `
            -cp $bridge.Classpath HmclModpackImporter @Arguments | Out-Host
        if ($LASTEXITCODE -ne 0) {
            throw "HMCL bridge failed with exit code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
}

function Move-RtsIncompleteHmclInstance {
    param(
        [Parameter(Mandatory = $true)]$Context,
        [Parameter(Mandatory = $true)][string]$InstanceId
    )

    $instance = Get-RtsHmclInstancePath -Context $Context -InstanceId $InstanceId
    if (-not (Test-Path -LiteralPath $instance)) {
        return
    }
    if (Test-RtsHmclInstanceReady -Context $Context -InstanceId $InstanceId) {
        return
    }
    $suffix = Get-Date -Format 'yyyyMMdd-HHmmss'
    $archive = $instance + '.incomplete-' + $suffix
    Assert-RtsInstancePath -Context $Context -Path $archive
    Move-Item -LiteralPath $instance -Destination $archive
    Write-Warning "Moved an incomplete HMCL import aside for recovery: $archive"
}

function Ensure-RtsHmclCleanInstance {
    param([Parameter(Mandatory = $true)]$Context)

    if (Test-RtsHmclInstanceReady -Context $Context -InstanceId $Context.CleanInstanceId) {
        return
    }
    if (-not (Test-Path -LiteralPath $Context.OfficialPackZip)) {
        throw "Official GTNH 2.8.4 client pack is missing: $($Context.OfficialPackZip)"
    }
    $instance = Get-RtsHmclInstancePath -Context $Context -InstanceId $Context.CleanInstanceId
    if ((Test-Path -LiteralPath $instance) -and
        (Test-Path -LiteralPath (Join-Path $instance 'modpack.cfg'))) {
        Write-Host '[RTSBuilding] Resuming an incomplete HMCL GTNH import from its existing cache.'
    } else {
        Move-RtsIncompleteHmclInstance -Context $Context -InstanceId $Context.CleanInstanceId
    }
    Write-Host '[RTSBuilding] First HMCL setup: importing the official GTNH 2.8.4 Java 17-25 pack.'
    Invoke-RtsHmclBridge -Context $Context -Arguments @(
        'import', $Context.HmclGameRoot, $Context.OfficialPackZip, $Context.CleanInstanceId
    )
    if (-not (Test-RtsHmclInstanceReady -Context $Context -InstanceId $Context.CleanInstanceId)) {
        throw "HMCL did not create a complete instance: $($Context.CleanInstanceId)"
    }
}

function Ensure-RtsHmclSimpleInstance {
    param([Parameter(Mandatory = $true)]$Context)

    Ensure-RtsHmclCleanInstance -Context $Context
    if (Test-RtsHmclInstanceReady -Context $Context -InstanceId $Context.FastInstanceId) {
        return
    }
    Move-RtsIncompleteHmclInstance -Context $Context -InstanceId $Context.FastInstanceId
    Write-Host '[RTSBuilding] Creating the isolated HMCL SIMPLE instance from CLEAN.'
    Invoke-RtsHmclBridge -Context $Context -Arguments @(
        'duplicate', $Context.HmclGameRoot, $Context.CleanInstanceId, $Context.FastInstanceId
    )
    if (-not (Test-RtsHmclInstanceReady -Context $Context -InstanceId $Context.FastInstanceId)) {
        throw "HMCL did not create a complete instance: $($Context.FastInstanceId)"
    }
}

function Install-Rts1710Jar {
    param(
        [Parameter(Mandatory = $true)]$Context,
        [Parameter(Mandatory = $true)][string]$InstanceId,
        [Parameter(Mandatory = $true)][string]$JarPath
    )

    $instance = Get-RtsHmclInstancePath -Context $Context -InstanceId $InstanceId
    if (-not (Test-RtsHmclInstanceReady -Context $Context -InstanceId $InstanceId)) {
        throw "HMCL instance is incomplete or missing: $instance"
    }
    $mods = Join-Path $instance 'mods'
    Get-ChildItem -LiteralPath $mods -Filter 'rtsbuilding*.jar' -File -ErrorAction SilentlyContinue |
        ForEach-Object { Remove-Item -LiteralPath $_.FullName -Force }
    $target = Join-Path $mods ([System.IO.Path]::GetFileName($JarPath))
    Copy-Item -LiteralPath $JarPath -Destination $target -Force
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $target).Hash
    Write-Host "[RTSBuilding] Installed: $target"
    Write-Host "[RTSBuilding] SHA-256: $hash"
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

    $instance = Get-RtsHmclInstancePath -Context $Context -InstanceId $InstanceId
    $mods = Join-Path $instance 'mods'
    Get-ChildItem -LiteralPath $mods -Filter $OldFilePattern -File -ErrorAction SilentlyContinue |
        ForEach-Object { Remove-Item -LiteralPath $_.FullName -Force }
    Copy-Item -LiteralPath $AddonPath `
        -Destination (Join-Path $mods ([System.IO.Path]::GetFileName($AddonPath))) -Force
}

function Select-RtsHmclInstance {
    param(
        [Parameter(Mandatory = $true)]$Context,
        [Parameter(Mandatory = $true)][string]$InstanceId
    )

    Assert-RtsHmclStopped -Context $Context
    if (-not (Test-Path -LiteralPath $Context.HmclConfig)) {
        throw "HMCL configuration is missing: $($Context.HmclConfig)"
    }
    $config = Get-Content -Raw -LiteralPath $Context.HmclConfig | ConvertFrom-Json
    $profile = $config.configurations.PSObject.Properties[$Context.HmclProfileName]
    if ($null -eq $profile) {
        throw "HMCL profile does not exist: $($Context.HmclProfileName)"
    }
    $profile.Value.selectedMinecraftVersion = $InstanceId
    $config.last = $Context.HmclProfileName
    $backup = $Context.HmclConfig + '.rtsbuilding-backup'
    Copy-Item -LiteralPath $Context.HmclConfig -Destination $backup -Force
    $json = $config | ConvertTo-Json -Depth 100
    [System.IO.File]::WriteAllText(
        $Context.HmclConfig, $json, [System.Text.UTF8Encoding]::new($false))
}

function Start-RtsHmclInstance {
    param(
        [Parameter(Mandatory = $true)]$Context,
        [Parameter(Mandatory = $true)][string]$InstanceId,
        [switch]$NoLaunch
    )

    if ($NoLaunch) {
        Write-Host '[RTSBuilding] -NoLaunch: instance prepared; HMCL and Minecraft were not started.'
        return
    }
    Select-RtsHmclInstance -Context $Context -InstanceId $InstanceId
    # 这是用户明确需要看到和操作的交互式启动器窗口。
    Start-Process -FilePath $Context.HmclExe -WorkingDirectory $Context.HmclRoot
    Write-Host "[RTSBuilding] HMCL opened with the selected instance: $InstanceId"
}
