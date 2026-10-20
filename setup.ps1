# Windows entry point: powershell -ExecutionPolicy Bypass -File .\setup.ps1 <step> [options]
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$env:PYTHONIOENCODING = 'utf-8'
$script = Join-Path $PSScriptRoot 'scripts\aurora_setup.py'
foreach ($candidate in @(@('py', '-3'), @('python'), @('python3'))) {
    $exe = $candidate[0]
    if (Get-Command $exe -ErrorAction SilentlyContinue) {
        $pre = @($candidate | Select-Object -Skip 1)
        & $exe @pre -c "import sys; sys.exit(sys.version_info < (3, 8))" 2>$null
        if ($LASTEXITCODE -eq 0) {
            & $exe @pre $script @args
            exit $LASTEXITCODE
        }
    }
}
Write-Error "Python 3.8+ not found. Install it with: winget install Python.Python.3.12"
exit 1
