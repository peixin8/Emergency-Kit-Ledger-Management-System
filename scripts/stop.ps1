#Requires -Version 7.0
[CmdletBinding()]
param([string]$RuntimeDirectory)
. (Join-Path $PSScriptRoot 'operations-common.ps1')
$runtime = Get-LedgerRuntimeDirectory $RuntimeDirectory
$state = Read-LedgerServiceState $runtime
if ($null -eq $state) { Write-Output '没有本工具管理的服务。'; return }
# 先核对全部进程，任何身份不匹配时一个也不停止。
$managed = @()
foreach ($entry in @($state.frontend, $state.backend)) {
    $process = Get-LedgerManagedProcess $entry
    if ($process) { $managed += $process }
}
foreach ($process in $managed) {
    Stop-Process -InputObject $process -ErrorAction Stop
    $process.WaitForExit(10000) | Out-Null
}
Remove-Item -LiteralPath (Join-Path $runtime 'services.json')
Write-Output '本工具启动的服务已停止；其他原有服务未操作。'
