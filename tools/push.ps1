[CmdletBinding()]
param(
    [string]$Remote = "origin",
    [string]$MainBranch = "main",
    [string]$ForgeBranch = "forge-1.20.1",
    [string]$ForgeWorktree = "sister-projects/rtsbuilding-forge-1.20.1",
    [switch]$DeleteCodexBranches
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$ForgeWork = Join-Path $Repo $ForgeWorktree

function Invoke-Git {
    param([Parameter(Mandatory = $true)][string[]]$GitArgs)
    & git -C $Repo @GitArgs
    if ($LASTEXITCODE -ne 0) {
        throw "git $($GitArgs -join ' ') failed with exit code $LASTEXITCODE"
    }
}

function Test-ForgeWorktreeMatches {
    param([string]$Branch)

    if (-not (Test-Path -LiteralPath $ForgeWork)) {
        throw "Forge worktree not found: $ForgeWork"
    }

    $branchFiles = @(Invoke-Git -GitArgs @("ls-tree", "-r", "--name-only", $Branch))
    $missing = 0
    $different = 0
    foreach ($file in $branchFiles) {
        $path = Join-Path $ForgeWork ($file -replace "/", [System.IO.Path]::DirectorySeparatorChar)
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            $missing++
            continue
        }
        $want = (Invoke-Git -GitArgs @("rev-parse", "$Branch`:$file")).Trim()
        $have = (Invoke-Git -GitArgs @("hash-object", "--", $path)).Trim()
        if ($want -ne $have) {
            $different++
        }
    }

    $tracked = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($file in $branchFiles) {
        [void]$tracked.Add($file)
    }
    $extra = 0
    foreach ($dir in @("src", "gradle", "docs", "tools")) {
        $dirPath = Join-Path $ForgeWork $dir
        if (Test-Path -LiteralPath $dirPath) {
            foreach ($item in Get-ChildItem -LiteralPath $dirPath -Recurse -File -Force) {
                $rel = $item.FullName.Substring($ForgeWork.Length + 1).Replace("\", "/")
                if (-not $tracked.Contains($rel)) {
                    $extra++
                }
            }
        }
    }

    if ($missing -ne 0 -or $different -ne 0 -or $extra -ne 0) {
        throw "Forge mirror verification failed: missing=$missing different=$different extraCodeFiles=$extra"
    }
}

$dirty = (& git -C $Repo status --porcelain --untracked-files=no)
if ($dirty) {
    throw "Tracked working tree changes are present. Commit or stash before pushing."
}

Write-Host "Fetching remote branch tips..."
Invoke-Git -GitArgs @("fetch", $Remote, $MainBranch, $ForgeBranch)

Write-Host "Verifying Forge sister worktree mirrors local $ForgeBranch..."
Test-ForgeWorktreeMatches -Branch $ForgeBranch

Write-Host "Pushing long-lived branches..."
Invoke-Git -GitArgs @("push", $Remote, "$MainBranch`:$MainBranch", "$ForgeBranch`:$ForgeBranch")

if ($DeleteCodexBranches) {
    $refs = @(Invoke-Git -GitArgs @("ls-remote", "--heads", $Remote, "codex/*"))
    $branches = @()
    foreach ($line in $refs) {
        if ($line -match "refs/heads/(codex/.+)$") {
            $branches += $Matches[1]
        }
    }
    if ($branches.Count -gt 0) {
        Write-Host "Deleting remote codex branches: $($branches -join ', ')"
        Invoke-Git -GitArgs (@("push", $Remote, "--delete") + $branches)
    }
}

Write-Host "Done."
Write-Host "main:  $(Invoke-Git -GitArgs @("rev-parse", $MainBranch))"
Write-Host "forge: $(Invoke-Git -GitArgs @("rev-parse", $ForgeBranch))"
