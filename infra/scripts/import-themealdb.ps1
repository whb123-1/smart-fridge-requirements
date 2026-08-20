[CmdletBinding()]
param(
    [string]$BaseUrl,
    [string]$AdminIdentifier = 'admin',
    [string]$PasswordFile = 'secrets/smoke_admin_password',
    [string[]]$Areas = @('Chinese', 'Japanese', 'Thai', 'Vietnamese'),
    [ValidateRange(1, 100)][int]$MaxPerArea = 20,
    [ValidateRange(1, 40)][int]$BatchSize = 20,
    [switch]$SkipCertificateCheck,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$rootDir = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

if (-not $BaseUrl) {
    $envFile = Join-Path $rootDir '.env.prod'
    $appDomain = (Get-Content -LiteralPath $envFile | Where-Object { $_ -match '^APP_DOMAIN=' } | Select-Object -First 1) -replace '^APP_DOMAIN=', ''
    if (-not $appDomain) { throw 'BaseUrl is required when APP_DOMAIN is not configured' }
    $BaseUrl = "https://$($appDomain.Trim().Trim('"').Trim("'"))"
}
$BaseUrl = $BaseUrl.TrimEnd('/')

function Invoke-Json([string]$Method, [string]$Uri, [object]$Body = $null, [hashtable]$Headers = @{}) {
    $arguments = @{ Method = $Method; Uri = $Uri; Headers = $Headers; TimeoutSec = 60 }
    if ($SkipCertificateCheck) { $arguments.SkipCertificateCheck = $true }
    if ($null -ne $Body) {
        $arguments.ContentType = 'application/json; charset=utf-8'
        $arguments.Body = $Body | ConvertTo-Json -Depth 12 -Compress
    }
    Invoke-RestMethod @arguments
}

function Convert-Measure([string]$Measure) {
    $value = ([string]$Measure).Trim().Replace('½', '1/2').Replace('¼', '1/4').Replace('¾', '3/4')
    if ($value -match '^(?<whole>\d+(?:\.\d+)?)?\s*(?<fraction>\d+/\d+)?\s*(?<unit>.*)$' -and ($Matches.whole -or $Matches.fraction)) {
        $quantity = if ($Matches.whole) { [decimal]$Matches.whole } else { [decimal]0 }
        if ($Matches.fraction) {
            $parts = $Matches.fraction -split '/'
            if ([decimal]$parts[1] -ne 0) { $quantity += [decimal]$parts[0] / [decimal]$parts[1] }
        }
        $unit = $Matches.unit.Trim()
    } else {
        $quantity = [decimal]1
        $unit = $value
    }
    if (-not $unit) { $unit = 'piece' }
    if ($unit.Length -gt 24) { $unit = $unit.Substring(0, 24) }
    @{ quantity = [math]::Round($quantity, 3); unit = $unit }
}

function Convert-Meal([object]$Meal) {
    $ingredients = @()
    $seasoningPattern = 'salt|pepper|oil|sauce|vinegar|sugar|starch|spice|garlic|ginger|stock|water|flour'
    for ($index = 1; $index -le 20; $index++) {
        $name = [string]$Meal.("strIngredient$index")
        if (-not $name.Trim()) { continue }
        $measure = Convert-Measure ([string]$Meal.("strMeasure$index"))
        $role = if ($name -match $seasoningPattern) { 'SEASONING' } elseif ($ingredients.Count -eq 0) { 'PRIMARY' } else { 'SIDE' }
        $ingredients += @{
            name = $name.Trim(); role = $role; quantity = $measure.quantity; unit = $measure.unit
            scalingRule = if ($role -eq 'SEASONING') { 'BOUNDED' } else { 'LINEAR' }
        }
    }
    $steps = @([string]$Meal.strInstructions -split '(?:\r?\n){2,}|(?:STEP\s+\d+\s*-\s*)' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    if ($steps.Count -eq 0) { $steps = @('See the original recipe for preparation instructions.') }
    $sourceUrl = if ([string]$Meal.strSource -match '^https?://') { [string]$Meal.strSource } else { "https://www.themealdb.com/meal/$($Meal.idMeal)" }
    $cuisine = @{ Chinese = '中餐'; Japanese = '日料'; Thai = '泰餐'; Vietnamese = '越南菜' }[[string]$Meal.strArea]
    if (-not $cuisine) { $cuisine = [string]$Meal.strArea }
    @{
        sourceRecipeId = [string]$Meal.idMeal
        title = [string]$Meal.strMeal
        summary = "$($Meal.strArea) · $($Meal.strCategory) · imported from TheMealDB"
        cuisine = $cuisine
        taste = '原味'
        goal = '均衡'
        cookMinutes = 30
        servings = 4
        ingredients = $ingredients
        steps = $steps
        imageUrl = [string]$Meal.strMealThumb
        imageSourceUrl = $sourceUrl
        imageAttribution = 'Image and recipe metadata: TheMealDB'
    }
}

$recipes = @()
$seen = @{}
foreach ($area in $Areas) {
    $listing = Invoke-Json GET "https://www.themealdb.com/api/json/v1/1/filter.php?a=$([uri]::EscapeDataString($area))"
    foreach ($item in @($listing.meals | Select-Object -First $MaxPerArea)) {
        if ($seen.ContainsKey([string]$item.idMeal)) { continue }
        $detail = Invoke-Json GET "https://www.themealdb.com/api/json/v1/1/lookup.php?i=$($item.idMeal)"
        if ($detail.meals) {
            $recipes += Convert-Meal $detail.meals[0]
            $seen[[string]$item.idMeal] = $true
        }
    }
}

if ($DryRun) {
    Write-Output "THEMEALDB_DRY_RUN_OK recipes=$($recipes.Count) areas=$($Areas.Count)"
    $recipes | Select-Object -First 3 sourceRecipeId,title,cuisine,imageUrl | Format-Table
    exit 0
}

$passwordPath = [IO.Path]::GetFullPath((Join-Path $rootDir $PasswordFile))
$password = [IO.File]::ReadAllText($passwordPath).Trim()
$login = Invoke-Json POST "$BaseUrl/api/v1/auth/login" @{ identifier = $AdminIdentifier; password = $password } @{ Origin = $BaseUrl }
if ($login.data.user.role -ne 'ADMIN') { throw 'The configured account is not an administrator' }
$auth = @{ Authorization = "Bearer $($login.data.accessToken)" }

$sources = Invoke-Json GET "$BaseUrl/api/v1/admin/recipe-sources" $null $auth
$source = @($sources.data | Where-Object { $_.name -eq 'TheMealDB public API' -and $_.sourceVersion -eq 'api-v1' } | Select-Object -First 1)
if (-not $source) {
    $headers = $auth.Clone(); $headers['Idempotency-Key'] = [guid]::NewGuid().ToString()
    $created = Invoke-Json POST "$BaseUrl/api/v1/admin/recipe-sources" @{
        name = 'TheMealDB public API'; sourceType = 'PUBLIC_API'; licenseCode = 'UPSTREAM_TERMS'
        attributionText = 'Recipe metadata and images: TheMealDB. Original recipe links are retained per recipe.'
        allowedUse = 'Import with source attribution; review upstream terms before redistribution.'
        sourceVersion = 'api-v1'; enabled = $true
    } $headers
    $source = $created.data
}

$jobIds = @()
for ($offset = 0; $offset -lt $recipes.Count; $offset += $BatchSize) {
    $end = [math]::Min($offset + $BatchSize - 1, $recipes.Count - 1)
    $batch = @($recipes[$offset..$end])
    $headers = $auth.Clone(); $headers['Idempotency-Key'] = [guid]::NewGuid().ToString()
    $queued = Invoke-Json POST "$BaseUrl/api/v1/admin/recipe-import-jobs" @{ sourceId = $source.id; payload = @{ recipes = $batch } } $headers
    $jobIds += [string]$queued.data.id
}

$deadline = (Get-Date).AddMinutes(10)
$totals = @{ imported = 0; skipped = 0; errors = 0 }
foreach ($jobId in $jobIds) {
    do {
        if ((Get-Date) -gt $deadline) { throw "Timed out waiting for recipe import job $jobId" }
        Start-Sleep -Seconds 2
        $job = (Invoke-Json GET "$BaseUrl/api/v1/admin/recipe-import-jobs/$jobId" $null $auth).data
    } while ($job.status -in @('QUEUED', 'PROCESSING'))
    if ($job.status -notin @('COMPLETED', 'COMPLETED_WITH_ERRORS')) { throw "Recipe import job $jobId ended with status $($job.status)" }
    $totals.imported += [int]$job.importedCount
    $totals.skipped += [int]$job.skippedCount
    $totals.errors += [int]$job.errorCount
}

Write-Output "THEMEALDB_IMPORT_OK fetched=$($recipes.Count) imported=$($totals.imported) skipped=$($totals.skipped) errors=$($totals.errors)"
