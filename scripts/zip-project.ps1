<#
.SYNOPSIS
    Zips the current project folder into a clean archive, excluding build
    output, IDE folders, logs, and other caches - only real source/config
    files go in.

.USAGE
    From the project root (E:\projects\arceus-platform), in PowerShell:

        .\zip-project.ps1

    Optional custom output name:

        .\zip-project.ps1 -OutputZip arceus-backup.zip

    Optional custom source folder (defaults to current directory):

        .\zip-project.ps1 -SourceDir E:\projects\arceus-platform

.WHAT GETS EXCLUDED
    Directories (anywhere in the tree):
        target, .git, .idea, .vscode, node_modules, logs, out, bin

    Files (anywhere in the tree):
        *.class, *.log, *.tmp, .DS_Store, Thumbs.db

    Everything else - source code, poms, application.yml, scripts, the
    .env file, etc. - is included as-is.

.NOTE ON .env
    The .env file IS included by default, since a "backup zip" that can't
    actually restore a working setup isn't very useful. That file has real
    credentials in it (see the rotation checklist inside it) - if you're
    sending this zip anywhere other than your own backup drive, strip
    .env out first or rotate those credentials.
#>

param(
    [string]$SourceDir = (Get-Location).Path,
    [string]$OutputZip = "arceus-platform-$(Get-Date -Format 'yyyyMMdd-HHmmss').zip"
)

$ErrorActionPreference = "Stop"

$excludeDirs  = @('target', '.git', '.idea', '.vscode', 'node_modules', 'logs', 'out', 'bin')
$excludeFiles = @('*.class', '*.log', '*.tmp', '.DS_Store', 'Thumbs.db')

$staging = Join-Path $env:TEMP ("zip-staging-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $staging | Out-Null

Write-Host "==> Copying clean source tree to staging folder..."
Write-Host "    (excluding: $($excludeDirs -join ', '))"

# robocopy mirrors the tree while skipping the excluded dirs/files - far more
# reliable for this than trying to filter file-by-file with Compress-Archive.
$robocopyArgs = @(
    $SourceDir, $staging, '/E',
    '/XD'
) + $excludeDirs + @('/XF') + $excludeFiles + @(
    '/NFL', '/NDL', '/NJH', '/NJS', '/NC', '/NS', '/NP'
)

robocopy @robocopyArgs | Out-Null
# robocopy exit codes 0-7 are all "success" (8+ mean a real error)
if ($LASTEXITCODE -ge 8) {
    Remove-Item -Recurse -Force $staging -ErrorAction SilentlyContinue
    throw "robocopy failed with exit code $LASTEXITCODE"
}

Write-Host "==> Creating archive: $OutputZip"
if (Test-Path $OutputZip) { Remove-Item $OutputZip }
Compress-Archive -Path (Join-Path $staging '*') -DestinationPath $OutputZip -CompressionLevel Optimal

Write-Host "==> Cleaning up staging folder..."
Remove-Item -Recurse -Force $staging

$sizeMB = [math]::Round((Get-Item $OutputZip).Length / 1MB, 2)
Write-Host ""
Write-Host "Done: $OutputZip ($sizeMB MB)"