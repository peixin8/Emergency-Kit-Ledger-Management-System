#Requires -Version 7.0
[CmdletBinding()]
param([string]$RuntimeDirectory)
. (Join-Path $PSScriptRoot 'operations-common.ps1')
$runtime = Get-LedgerRuntimeDirectory $RuntimeDirectory
$state = Read-LedgerServiceState $runtime
if ($null -eq $state) { Write-Output '没有本工具管理的服务。'; return }
foreach ($name in @('backend', 'frontend')) {
    try {
        $process = Get-LedgerManagedProcess $state.$name
        $running = $null -ne $process
        $port = if ($name -eq 'backend') { $state.backendPort } else { $state.frontendPort }
        $path = if ($name -eq 'backend') { '/api/v1/ledgers?page=1&pageSize=1' } else { '/' }
        $http = '未运行'
        if ($running) {
            try { $http = [string](Invoke-WebRequest -Uri "http://127.0.0.1:$port$path" -TimeoutSec 3 -SkipHttpErrorCheck).StatusCode }
            catch { $http = '连接失败' }
        }
        [pscustomobject]@{ Service = $name; Running = $running; Port = $port; HTTP = $http }
    } catch { Write-Warning "$name 进程身份不匹配，请人工检查运行记录。" }
}
