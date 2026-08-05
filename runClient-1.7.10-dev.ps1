[CmdletBinding()]
param(
    [switch]$NoLaunch,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArguments
)

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
. (Join-Path $projectRoot 'gtnh-launch-common.ps1')
$context = Get-Rts1710Context -ProjectRoot $projectRoot

if ($NoLaunch) {
    Write-Host '[RTSBuilding] Build-only minimal development environment; Minecraft will not start.'
    [void](Build-Rts1710Jar -Context $context)
    exit 0
}

Write-Host '[RTSBuilding] Starting the minimal Forge 1.7.10 + GTNHLib development client.'
$arguments = @('runClient', '--no-daemon', '--no-configuration-cache') + $GradleArguments
Invoke-Rts1710Gradle -Context $context -Arguments $arguments
