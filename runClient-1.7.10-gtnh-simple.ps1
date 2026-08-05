[CmdletBinding()]
param(
    [switch]$NoBuild,
    [switch]$NoLaunch,
    [switch]$IncludeEzgt,
    [string]$Profile = ''
)

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
. (Join-Path $projectRoot 'gtnh-launch-common.ps1')
$context = Get-Rts1710Context -ProjectRoot $projectRoot

[void](Copy-RtsGtnhFastInstance -Context $context)
$jar = if ($NoBuild) {
    Get-Rts1710BuiltJar -Context $context
} else {
    Build-Rts1710Jar -Context $context
}
[void](Install-Rts1710Jar `
    -Context $context -InstanceId $context.FastInstanceId -JarPath $jar)

$rates = Get-RtsPinnedAddon -Context $context `
    -FileName 'gtnhrates-1.11.0-2.8.4.jar' `
    -Uri 'https://github.com/Sladki/GTNHRates/releases/download/1.11.0-2.8.4/gtnhrates-1.11.0-2.8.4.jar' `
    -Sha256 '6175F3AC08727CF3962507D7C425E178353839B15FB7B6AD42FE60FB7C1DA69C'
Install-RtsPinnedAddon -Context $context -InstanceId $context.FastInstanceId `
    -AddonPath $rates -OldFilePattern 'gtnhrates*.jar'

if ($IncludeEzgt) {
    Write-Warning 'EZGT only claims post-2.8.4 daily compatibility; this combination is experimental.'
    $ezgt = Get-RtsPinnedAddon -Context $context `
        -FileName 'ezgt-2026-05-22.jar' `
        -Uri 'https://github.com/KingofKeet/EZGT/releases/download/master-packages/ezgt.jar' `
        -Sha256 '862C912C153AFC45852BEECE787088FBCD0FA12A56A60DA0A2B13E703CEB0DD8'
    Install-RtsPinnedAddon -Context $context -InstanceId $context.FastInstanceId `
        -AddonPath $ezgt -OldFilePattern 'ezgt*.jar'
}

Write-Host '[RTSBuilding] GTNH Simple: isolated 2.8.4 FAST instance + GTNH Rates 1.11.0.'
Write-Host '[RTSBuilding] The CLEAN instance and its saves are untouched.'
Start-RtsPrismInstance `
    -Context $context -InstanceId $context.FastInstanceId -Profile $Profile -NoLaunch:$NoLaunch
