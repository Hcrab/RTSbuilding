param(
    [string]$Root = (Resolve-Path ".").Path
)

$ErrorActionPreference = "Stop"

$languages = @("en_us", "zh_cn", "zh_tw", "zh_hk")
$langDir = Join-Path $Root "src/main/resources/assets/rtsbuilding/lang"
$prefixes = @(
    "screen.rtsbuilding.",
    "chat.rtsbuilding.",
    "config.rtsbuilding.",
    "key.rtsbuilding.",
    "rtsbuilding.configuration.",
    "rtsbuilding.progression."
)

function Read-LanguageKeys([string]$Path) {
    if (!(Test-Path -LiteralPath $Path)) {
        throw "Missing language file: $Path"
    }
    $json = Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json
    $keys = @{}
    foreach ($prop in $json.PSObject.Properties) {
        $keys[$prop.Name] = $true
    }
    return $keys
}

function Is-TranslationKey([string]$Value) {
    foreach ($prefix in $prefixes) {
        if (($Value.Length -gt $prefix.Length) -and $Value.StartsWith($prefix, [System.StringComparison]::Ordinal)) {
            return $true
        }
    }
    return $false
}

$langKeys = @{}
foreach ($language in $languages) {
    $langKeys[$language] = Read-LanguageKeys (Join-Path $langDir "$language.json")
}

$referenced = @{}
$scanDirs = @("src/main/java", "src/test/java")
$literalPattern = [regex]'"((?:\\.|[^"\\])*)"'
foreach ($dir in $scanDirs) {
    $abs = Join-Path $Root $dir
    if (!(Test-Path -LiteralPath $abs)) {
        continue
    }
    Get-ChildItem -LiteralPath $abs -Recurse -File -Include *.java | ForEach-Object {
        $relative = Resolve-Path -LiteralPath $_.FullName -Relative
        $content = Get-Content -LiteralPath $_.FullName -Raw -Encoding UTF8
        foreach ($match in $literalPattern.Matches($content)) {
            $value = $match.Groups[1].Value
            if (Is-TranslationKey $value) {
                if (!$referenced.ContainsKey($value)) {
                    $referenced[$value] = New-Object System.Collections.Generic.List[string]
                }
                $referenced[$value].Add($relative)
            }
        }
    }
}

$hasError = $false

Write-Output "Referenced translation keys: $($referenced.Count)"
Write-Output ""
Write-Output "Missing referenced keys:"
foreach ($key in ($referenced.Keys | Sort-Object)) {
    foreach ($language in $languages) {
        if (!$langKeys[$language].ContainsKey($key)) {
            $hasError = $true
            Write-Output "  [$language] $key"
            foreach ($source in ($referenced[$key] | Sort-Object -Unique)) {
                Write-Output "    $source"
            }
        }
    }
}

$allLangKeys = @{}
foreach ($language in $languages) {
    foreach ($key in $langKeys[$language].Keys) {
        $allLangKeys[$key] = $true
    }
}

Write-Output ""
Write-Output "Unpaired language-file keys:"
foreach ($key in ($allLangKeys.Keys | Sort-Object)) {
    foreach ($language in $languages) {
        if (!$langKeys[$language].ContainsKey($key)) {
            $hasError = $true
            Write-Output "  [$language] $key"
        }
    }
}

if ($hasError) {
    exit 1
}

Write-Output "OK: language keys are paired across en_us/zh_cn/zh_tw/zh_hk."
