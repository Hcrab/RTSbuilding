[CmdletBinding()]
param(
    [switch]$NoBuild,
    [switch]$NoLaunch
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'gtnh-launch-common.ps1')
$context = Get-Rts1710Context -ProjectRoot $projectRoot

Assert-RtsHmclStopped -Context $context
Ensure-RtsHmclCleanInstance -Context $context
$jar = if ($NoBuild) {
    Get-Rts1710BuiltJar -Context $context
} else {
    Build-Rts1710Jar -Context $context
}
[void](Install-Rts1710Jar `
    -Context $context -InstanceId $context.CleanInstanceId -JarPath $jar)

Write-Host '[RTSBuilding] GTNH CLEAN: official 2.8.4 + current RTSBuilding; no balance addons.'
Start-RtsHmclInstance `
    -Context $context -InstanceId $context.CleanInstanceId -NoLaunch:$NoLaunch
