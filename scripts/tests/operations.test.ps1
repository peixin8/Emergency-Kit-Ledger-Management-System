$ErrorActionPreference = 'Stop'
$scriptsRoot = Split-Path $PSScriptRoot -Parent
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('ledger-ops-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot | Out-Null
function Assert-True($Value, [string]$Message) { if (-not $Value) { throw $Message } }
function Assert-Rejected([scriptblock]$Action, [string]$Message) {
    $rejected = $false
    try { & $Action | Out-Null } catch { $rejected = $true }
    Assert-True $rejected $Message
}
try {
    $source = Join-Path $testRoot 'ledger.mv.db'
    $backup = Join-Path $testRoot 'backup.mv.db'
    [IO.File]::WriteAllBytes($source, [byte[]](1, 2, 3, 4, 5))
    & (Join-Path $scriptsRoot 'backup.ps1') -DatabasePath $source -Destination $backup | Out-Null
    Assert-True ((Get-FileHash $source).Hash -eq (Get-FileHash $backup).Hash) '离线备份必须逐字节一致'
    Assert-Rejected { & (Join-Path $scriptsRoot 'backup.ps1') -DatabasePath $source -Destination $backup } '不能覆盖已有备份'
    Assert-Rejected { & (Join-Path $scriptsRoot 'backup.ps1') -DatabasePath (Join-Path $testRoot 'missing.mv.db') -Destination (Join-Path $testRoot 'missing-backup.mv.db') } '缺少数据库必须拒绝'
    $locked = [IO.File]::Open($source, 'Open', 'ReadWrite', 'None')
    try {
        Assert-Rejected { & (Join-Path $scriptsRoot 'backup.ps1') -DatabasePath $source -Destination (Join-Path $testRoot 'locked-backup.mv.db') } '使用中的数据库不能备份'
        Assert-True (-not (Test-Path (Join-Path $testRoot 'locked-backup.mv.db'))) '失败不能留下伪备份'
    } finally { $locked.Dispose() }
    $runtime = Join-Path $testRoot 'runtime'
    New-Item -ItemType Directory -Path $runtime | Out-Null
    $self = Get-Process -Id $PID
    . (Join-Path $scriptsRoot 'operations-common.ps1')
    $validEntry = New-LedgerProcessEntry $self
    $roundTripEntry = $validEntry | ConvertTo-Json | ConvertFrom-Json
    Assert-True ((Get-LedgerManagedProcess $roundTripEntry).Id -eq $PID) 'JSON往返后合法进程身份必须仍可识别'
    $state = @{ projectRoot = Split-Path $scriptsRoot -Parent; backend = @{ id = $PID; startedAt = '2000-01-01T00:00:00.0000000Z'; executable = $self.Path }; frontend = $null }
    $state | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $runtime 'services.json') -Encoding UTF8
    Assert-Rejected { & (Join-Path $scriptsRoot 'stop.ps1') -RuntimeDirectory $runtime } 'PID启动时间不匹配必须拒绝停止'
    Assert-True ($null -ne (Get-Process -Id $PID)) '不得停止无关进程'
    Write-Output '维护脚本测试通过：备份内容、不覆盖、缺失输入、占用保护、无伪备份、PID复用保护。'
} finally {
    # 测试目录是当前测试生成的绝对临时路径，验证边界后清理。
    $resolvedTestRoot = [IO.Path]::GetFullPath($testRoot)
    $resolvedTempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    if ($resolvedTestRoot.StartsWith($resolvedTempRoot, [StringComparison]::OrdinalIgnoreCase) -and (Split-Path $resolvedTestRoot -Leaf).StartsWith('ledger-ops-')) {
        Remove-Item -LiteralPath $resolvedTestRoot -Recurse -Force
    }
}
