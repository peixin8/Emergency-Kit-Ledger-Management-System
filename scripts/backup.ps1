#Requires -Version 7.0
[CmdletBinding()]
param(
    [string]$DatabasePath = (Join-Path $PSScriptRoot '../backend/data/emergency-ledger-dev.mv.db'),
    [string]$Destination
)
$ErrorActionPreference = 'Stop'
$sourcePath = (Resolve-Path -LiteralPath $DatabasePath -ErrorAction Stop).Path
if (-not $Destination) {
    $Destination = Join-Path $PSScriptRoot ('../backups/ledger-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '.mv.db')
}
$targetPath = [IO.Path]::GetFullPath($Destination)
if ($sourcePath -eq $targetPath -or (Test-Path -LiteralPath $targetPath)) { throw '备份目标已存在或与源文件相同，拒绝覆盖。' }
$sourceStream = $null
$targetStream = $null
$created = $false
try {
    # 独占读取失败时立即拒绝；不复制运行中的 H2 文件。
    $sourceStream = [IO.File]::Open($sourcePath, 'Open', 'Read', 'None')
    New-Item -ItemType Directory -Path (Split-Path $targetPath -Parent) -Force | Out-Null
    $targetStream = [IO.File]::Open($targetPath, 'CreateNew', 'Write', 'None')
    $created = $true
    $sourceStream.CopyTo($targetStream)
    $targetStream.Flush($true)
} catch {
    if ($targetStream) { $targetStream.Dispose(); $targetStream = $null }
    if ($created) { Remove-Item -LiteralPath $targetPath -Force }
    throw '备份失败：请确认 H2 已停止、源文件可读且目标可写；未覆盖已有备份。'
} finally {
    if ($targetStream) { $targetStream.Dispose() }
    if ($sourceStream) { $sourceStream.Dispose() }
}
[pscustomobject]@{ Backup = $targetPath; SHA256 = (Get-FileHash -LiteralPath $targetPath -Algorithm SHA256).Hash; Bytes = (Get-Item -LiteralPath $targetPath).Length }
