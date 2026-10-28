#Requires -Version 7.0
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:LedgerProjectRoot = Split-Path $PSScriptRoot -Parent

function Get-LedgerRuntimeDirectory([string]$Directory) {
    if ([string]::IsNullOrWhiteSpace($Directory)) { return Join-Path $script:LedgerProjectRoot '.runtime' }
    return [IO.Path]::GetFullPath($Directory)
}

function Read-LedgerServiceState([string]$Directory) {
    $path = Join-Path $Directory 'services.json'
    if (-not (Test-Path -LiteralPath $path)) { return $null }
    $state = Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($state.projectRoot -ne $script:LedgerProjectRoot) { throw '运行记录属于其他工程，拒绝操作。' }
    return $state
}

function Get-LedgerManagedProcess($Entry) {
    if ($null -eq $Entry) { return $null }
    $process = Get-Process -Id $Entry.id -ErrorAction SilentlyContinue
    if ($null -eq $process) { return $null }
    # PowerShell 新版 JSON 会自动把 ISO 时间转成 DateTime；统一比较 UTC ticks。
    $recordedStart = [DateTimeOffset]$Entry.startedAt
    if ($process.StartTime.ToUniversalTime().Ticks -ne $recordedStart.UtcDateTime.Ticks -or $process.Path -ne $Entry.executable) {
        throw '进程身份与运行记录不匹配，可能发生 PID 复用；拒绝停止无关进程。'
    }
    return $process
}

function New-LedgerProcessEntry($Process) {
    $Process.Refresh()
    return @{ id = $Process.Id; startedAt = $Process.StartTime.ToUniversalTime().ToString('O'); executable = $Process.Path }
}

function Resolve-LedgerJava([string]$JavaHome) {
    $candidateHome = if ($JavaHome) { $JavaHome } else { $env:JAVA_HOME }
    if ($candidateHome) {
        $candidate = Join-Path $candidateHome 'bin/java.exe'
        if (Test-Path -LiteralPath $candidate) { return (Resolve-Path -LiteralPath $candidate).Path }
        throw '指定的 JavaHome 未找到 bin/java.exe。'
    }
    $command = Get-Command java -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    throw '未找到 Java。请配置 JAVA_HOME 或传入 -JavaHome，需 JDK 17。'
}

function Resolve-LedgerNode([string]$NodePath) {
    if ($NodePath) {
        if (Test-Path -LiteralPath $NodePath) { return (Resolve-Path -LiteralPath $NodePath).Path }
        throw '指定的 NodePath 不存在。'
    }
    $command = Get-Command node -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    throw '未找到 Node.js。请配置 PATH 或传入 -NodePath。'
}

function Assert-LedgerPortAvailable([int]$Port) {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Any, $Port)
    $listener.Server.ExclusiveAddressUse = $true
    try { $listener.Start() } catch { throw "端口 $Port 已被占用；请先核对原服务或指定其他端口。" }
    finally { $listener.Stop() }
}

function Wait-LedgerHttp([string]$Url, $Process, [int]$TimeoutSeconds = 60) {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        $Process.Refresh()
        if ($Process.HasExited) { throw '服务提前退出，请查看 .runtime 中的日志。' }
        try {
            $response = Invoke-WebRequest -Uri $Url -TimeoutSec 2 -SkipHttpErrorCheck
            if ($response.StatusCode -eq 200) { return }
        } catch { }
        Start-Sleep -Milliseconds 500
    }
    throw '等待服务启动超时，请查看运行日志。'
}
