[CmdletBinding()]
param(
    [string]$Remote = "origin",
    [string]$MainBranch = "main",
    [string]$ForgeBranch = "forge-1.20.1",
    [string]$ForgeWorktree = "sister-projects/rtsbuilding-forge-1.20.1",
    [switch]$NoStash
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

function Assert-UnderRepo {
    param([string]$Path)
    $resolved = (Resolve-Path -LiteralPath $Path).Path
    if (-not $resolved.StartsWith($Repo, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to operate outside repo: $resolved"
    }
}

function Copy-TreeMirror {
    param([string]$From, [string]$To)
    if (-not (Test-Path -LiteralPath $To)) {
        New-Item -ItemType Directory -Force -Path $To | Out-Null
    }
    & robocopy $From $To /MIR /NFL /NDL /NJH /NJS /NP | Out-Null
    if ($LASTEXITCODE -gt 7) {
        throw "robocopy failed for $From -> $To with exit code $LASTEXITCODE"
    }
}

function Sync-ForgeWorktree {
    param([string]$Branch)

    New-Item -ItemType Directory -Force -Path $ForgeWork | Out-Null
    Assert-UnderRepo $ForgeWork

    $syncRoot = Join-Path ([System.IO.Path]::GetTempPath()) "rtsbuilding-forge-sync-$PID"
    $export = Join-Path $syncRoot "export"
    $archive = Join-Path $syncRoot "forge.tar"
    Remove-Item -LiteralPath $syncRoot -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $export | Out-Null

    try {
        Invoke-Git -GitArgs @("archive", "--format=tar", "-o", $archive, $Branch)
        & tar -xf $archive -C $export
        if ($LASTEXITCODE -ne 0) {
            throw "tar extraction failed with exit code $LASTEXITCODE"
        }

        foreach ($dir in @("src", "gradle", "docs", "tools")) {
            $from = Join-Path $export $dir
            $to = Join-Path $ForgeWork $dir
            if (Test-Path -LiteralPath $from) {
                Copy-TreeMirror $from $to
            } elseif (Test-Path -LiteralPath $to) {
                Assert-UnderRepo $to
                Remove-Item -LiteralPath $to -Recurse -Force
            }
        }

        foreach ($file in @(
            ".gitattributes", ".gitignore", "CREDITS.txt", "LICENSE", "LICENSE.txt",
            "README.md", "README.txt", "build.gradle", "changelog.txt",
            "gradle.properties", "gradlew", "gradlew.bat", "settings.gradle"
        )) {
            $from = Join-Path $export $file
            $to = Join-Path $ForgeWork $file
            if (Test-Path -LiteralPath $from) {
                Copy-Item -LiteralPath $from -Destination $to -Force
            } elseif (Test-Path -LiteralPath $to) {
                Remove-Item -LiteralPath $to -Force
            }
        }
    } finally {
        Remove-Item -LiteralPath $syncRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}

function Test-ForgeWorktreeMatches {
    param([string]$Branch)

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

Write-Host "Fetching $Remote/$MainBranch and $Remote/$ForgeBranch..."
Invoke-Git -GitArgs @("fetch", $Remote, $MainBranch, $ForgeBranch)

$dirty = (& git -C $Repo status --porcelain)
if ($dirty) {
    if ($NoStash) {
        throw "Working tree is dirty. Re-run without -NoStash or clean/stash manually."
    }
    $stamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Write-Host "Stashing local working tree changes before sync..."
    Invoke-Git -GitArgs @("stash", "push", "-u", "-m", "pre-sync pull.ps1 $stamp")
}

Write-Host "Syncing root worktree to $Remote/$MainBranch..."
Invoke-Git -GitArgs @("switch", $MainBranch)
Invoke-Git -GitArgs @("reset", "--hard", "$Remote/$MainBranch")
Invoke-Git -GitArgs @("branch", "-f", $ForgeBranch, "$Remote/$ForgeBranch")

Write-Host "Mirroring $ForgeBranch into $ForgeWorktree..."
Sync-ForgeWorktree -Branch $ForgeBranch
Test-ForgeWorktreeMatches -Branch $ForgeBranch

Write-Host "Done."
Write-Host "main:  $(Invoke-Git -GitArgs @("rev-parse", "HEAD"))"
Write-Host "forge: $(Invoke-Git -GitArgs @("rev-parse", $ForgeBranch))"
