[CmdletBinding()]
param(
    [switch]$NoBuild,
    [switch]$NoLaunch,
    [string]$Profile = ''
)

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
. (Join-Path $projectRoot 'gtnh-launch-common.ps1')
$context = Get-Rts1710Context -ProjectRoot $projectRoot

$jar = if ($NoBuild) {
    Get-Rts1710BuiltJar -Context $context
} else {
    Build-Rts1710Jar -Context $context
}
[void](Install-Rts1710Jar `
    -Context $context -InstanceId $context.CleanInstanceId -JarPath $jar)

Write-Host '[RTSBuilding] GTNH CLEAN: official 2.8.4 + current RTSBuilding; no balance addons.'
Start-RtsPrismInstance `
    -Context $context -InstanceId $context.CleanInstanceId -Profile $Profile -NoLaunch:$NoLaunch
