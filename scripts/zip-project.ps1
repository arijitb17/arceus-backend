<#
.SYNOPSIS
Creates a clean backup zip of the Arceus Platform source code.

Excludes:
- Git metadata
- IDE folders
- Maven build output
- Next.js build/cache
- Node modules
- Logs
- Temporary files

Keeps:
- Spring Boot source
- Next.js source
- Shared libraries
- Scripts
- Docker files
- .env
- pom.xml
- package.json
- package-lock.json
- README
#>

param(
    [string]$SourceDir = (Get-Location).Path,
    [string]$OutputZip = "E:\projects\arceus-platform-$(Get-Date -Format 'yyyyMMdd-HHmmss').zip"
)

$ErrorActionPreference = "Stop"

# Directories to exclude (anywhere in the tree)
$excludeDirs = @(
    ".git",
    ".idea",
    ".vscode",

    "target",
    "build",
    "out",
    "bin",

    "node_modules",
    ".next",
    ".turbo",
    ".vercel",

    ".gradle",

    "logs",
    "coverage",

    ".cache",
    ".parcel-cache",

    "dist",

    "__pycache__",
    ".pytest_cache",

    ".DS_Store"
)

# File patterns to exclude
$excludeFiles = @(
    "*.class",
    "*.jar",
    "*.war",
    "*.ear",

    "*.log",
    "*.tmp",

    "*.pid",
    "*.lock",

    "*.tsbuildinfo",

    "Thumbs.db",
    ".DS_Store"
)

$staging = Join-Path $env:TEMP ("zip-staging-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $staging | Out-Null

Write-Host ""
Write-Host "==========================================="
Write-Host " Arceus Platform Backup"
Write-Host "==========================================="
Write-Host ""

Write-Host "Source      : $SourceDir"
Write-Host "Destination : $OutputZip"
Write-Host ""

Write-Host "Copying project..."

$robocopyArgs = @(
    $SourceDir,
    $staging,
    "/E",
    "/XD"
) + $excludeDirs + @(
    "/XF"
) + $excludeFiles + @(
    "/R:2",
    "/W:1",
    "/NFL",
    "/NDL",
    "/NJH",
    "/NJS",
    "/NC",
    "/NS",
    "/NP"
)

robocopy @robocopyArgs | Out-Null

if ($LASTEXITCODE -ge 8) {
    Remove-Item $staging -Recurse -Force -ErrorAction SilentlyContinue
    throw "Robocopy failed with exit code $LASTEXITCODE"
}

if (Test-Path $OutputZip) {
    Remove-Item $OutputZip -Force
}

Write-Host "Creating zip..."

Compress-Archive `
    -Path (Join-Path $staging "*") `
    -DestinationPath $OutputZip `
    -CompressionLevel Optimal

Remove-Item $staging -Recurse -Force

$zip = Get-Item $OutputZip
$size = [math]::Round($zip.Length / 1MB,2)

Write-Host ""
Write-Host "==========================================="
Write-Host " Backup Complete"
Write-Host "==========================================="
Write-Host "File : $($zip.FullName)"
Write-Host "Size : $size MB"
Write-Host ""