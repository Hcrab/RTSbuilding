[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'gtnh-launch-common.ps1')

$context = Get-Rts1710Context -ProjectRoot $projectRoot
$jarPath = Build-Rts1710Jar -Context $context
$hash = (Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash

Write-Host "Build complete: $jarPath"
Write-Host "SHA256: $hash"
