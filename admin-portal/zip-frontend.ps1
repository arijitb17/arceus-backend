# zip-frontend.ps1

$ProjectPath = "E:\projects\arceus-admin-portal"
$ZipFile = "E:\projects\arceus-admin-portal.zip"

# Remove old zip if it exists
if (Test-Path $ZipFile) {
    Remove-Item $ZipFile -Force
}

# Temporary directory
$TempDir = Join-Path $env:TEMP "arceus-admin-portal-temp"

if (Test-Path $TempDir) {
    Remove-Item $TempDir -Recurse -Force
}

# Copy project excluding unwanted folders/files
$ExcludeDirs = @(
    ".next",
    "node_modules",
    ".git",
    ".turbo",
    ".vercel",
    "dist",
    "build",
    "coverage",
    ".cache"
)

$ExcludeFiles = @(
    "*.log"
)

New-Item -ItemType Directory -Path $TempDir | Out-Null

Get-ChildItem -Path $ProjectPath -Recurse | ForEach-Object {

    $Relative = $_.FullName.Substring($ProjectPath.Length).TrimStart('\')

    # Skip excluded directories
    foreach ($dir in $ExcludeDirs) {
        if ($Relative -like "$dir*" -or $Relative -like "*\$dir\*") {
            return
        }
    }

    # Skip excluded files
    foreach ($file in $ExcludeFiles) {
        if ($_.Name -like $file) {
            return
        }
    }

    $Destination = Join-Path $TempDir $Relative

    if ($_.PSIsContainer) {
        New-Item -ItemType Directory -Force -Path $Destination | Out-Null
    }
    else {
        New-Item -ItemType Directory -Force -Path (Split-Path $Destination) | Out-Null
        Copy-Item $_.FullName $Destination -Force
    }
}

Compress-Archive -Path "$TempDir\*" -DestinationPath $ZipFile -Force

Remove-Item $TempDir -Recurse -Force

Write-Host ""
Write-Host "==================================" -ForegroundColor Green
Write-Host "Frontend zipped successfully!" -ForegroundColor Green
Write-Host "Output: $ZipFile" -ForegroundColor Cyan
Write-Host "=================================="# zip-frontend.ps1

$ProjectPath = "E:\projects\arceus-admin-portal"
$ZipFile = "E:\projects\arceus-admin-portal.zip"

# Remove old zip if it exists
if (Test-Path $ZipFile) {
    Remove-Item $ZipFile -Force
}

# Temporary directory
$TempDir = Join-Path $env:TEMP "arceus-admin-portal-temp"

if (Test-Path $TempDir) {
    Remove-Item $TempDir -Recurse -Force
}

# Copy project excluding unwanted folders/files
$ExcludeDirs = @(
    ".next",
    "node_modules",
    ".git",
    ".turbo",
    ".vercel",
    "dist",
    "build",
    "coverage",
    ".cache"
)

$ExcludeFiles = @(
    "*.log"
)

New-Item -ItemType Directory -Path $TempDir | Out-Null

Get-ChildItem -Path $ProjectPath -Recurse | ForEach-Object {

    $Relative = $_.FullName.Substring($ProjectPath.Length).TrimStart('\')

    # Skip excluded directories
    foreach ($dir in $ExcludeDirs) {
        if ($Relative -like "$dir*" -or $Relative -like "*\$dir\*") {
            return
        }
    }

    # Skip excluded files
    foreach ($file in $ExcludeFiles) {
        if ($_.Name -like $file) {
            return
        }
    }

    $Destination = Join-Path $TempDir $Relative

    if ($_.PSIsContainer) {
        New-Item -ItemType Directory -Force -Path $Destination | Out-Null
    }
    else {
        New-Item -ItemType Directory -Force -Path (Split-Path $Destination) | Out-Null
        Copy-Item $_.FullName $Destination -Force
    }
}

Compress-Archive -Path "$TempDir\*" -DestinationPath $ZipFile -Force

Remove-Item $TempDir -Recurse -Force

Write-Host ""
Write-Host "==================================" -ForegroundColor Green
Write-Host "Frontend zipped successfully!" -ForegroundColor Green
Write-Host "Output: $ZipFile" -ForegroundColor Cyan
Write-Host "=================================="