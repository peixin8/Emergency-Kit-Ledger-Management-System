#Requires -Version 7.0
[CmdletBinding()]
param(
    [ValidateSet('dev', 'mysql')][string]$Profile = 'dev',
    [ValidateRange(1024, 65535)][int]$BackendPort = 8080,
    [ValidateRange(1024, 65535)][int]$FrontendPort = 5173,
    [string]$JavaHome,
    [string]$NodePath,
    [string]$DevDataPath,
    [string]$RuntimeDirectory,
    [switch]$Build,
    [switch]$LocalOnly
)
. (Join-Path $PSScriptRoot 'operations-common.ps1')
$runtime = Get-LedgerRuntimeDirectory $RuntimeDirectory
if ($BackendPort -eq $FrontendPort) { throw '前后端端口不能相同。' }
$previous = Read-LedgerServiceState $runtime
if ($previous) {
    foreach ($entry in @($previous.backend, $previous.frontend)) {
        if (Get-LedgerManagedProcess $entry) { throw '已有本工具启动的服务，请先运行 status.ps1 或 stop.ps1。' }
    }
}
Assert-LedgerPortAvailable $BackendPort
Assert-LedgerPortAvailable $FrontendPort
$java = Resolve-LedgerJava $JavaHome
$node = Resolve-LedgerNode $NodePath
$backendRoot = Join-Path $script:LedgerProjectRoot 'backend'
$frontendRoot = Join-Path $script:LedgerProjectRoot 'frontend'
if ($Profile -eq 'mysql' -and (-not $env:DB_URL -or -not $env:DB_USERNAME -or -not $env:DB_PASSWORD)) {
    throw 'MySQL 模式需先配置 DB_URL、DB_USERNAME、DB_PASSWORD；本工具不保存凭据。'
}
if ($Build) {
    $savedPath = $env:PATH
    try {
        $env:PATH = (Split-Path $java -Parent) + [IO.Path]::PathSeparator + $savedPath
        Push-Location $backendRoot
        try { & './mvnw.cmd' '-DskipTests' 'package'; if ($LASTEXITCODE -ne 0) { throw '后端构建失败。' } } finally { Pop-Location }
        Push-Location $frontendRoot
        try {
            if (-not (Test-Path -LiteralPath 'node_modules/vite/bin/vite.js')) { throw '缺少前端依赖，请先在 frontend 执行 npm ci。' }
            & $node 'node_modules/vue-tsc/bin/vue-tsc.js' '--noEmit'
            if ($LASTEXITCODE -ne 0) { throw '前端类型检查失败。' }
            & $node 'node_modules/vite/bin/vite.js' 'build'
            if ($LASTEXITCODE -ne 0) { throw '前端构建失败。' }
        } finally { Pop-Location }
    } finally { $env:PATH = $savedPath }
}
$jar = Join-Path $backendRoot 'target/ledger-1.0.0.jar'
if (-not (Test-Path -LiteralPath $jar) -or -not (Test-Path -LiteralPath (Join-Path $frontendRoot 'dist/index.html'))) {
    throw '缺少构建产物，请先使用 -Build，或执行前后端构建。'
}
New-Item -ItemType Directory -Path $runtime -Force | Out-Null
$address = if ($LocalOnly) { '127.0.0.1' } else { '0.0.0.0' }
$javaArguments = @('-jar', ('"' + $jar + '"'), "--server.port=$BackendPort", "--server.address=$address")
if ($Profile -eq 'dev') {
    $javaArguments += '--spring.profiles.active=dev'
    if ($DevDataPath) {
        $databaseFile = [IO.Path]::GetFullPath($DevDataPath).Replace('\', '/')
        if ($databaseFile.Contains(';') -or $databaseFile.Contains('"')) { throw 'DevDataPath 不允许分号或引号。' }
        $javaArguments += ('"--spring.datasource.url=jdbc:h2:file:' + $databaseFile + ';MODE=MySQL;DATABASE_TO_LOWER=TRUE"')
    }
}
# 不改项目代理配置：启动时为构建预览指定本次后端端口。
$previewCode = "import {preview} from 'vite'; await preview({preview:{host:'$address',port:$FrontendPort,strictPort:true,proxy:{'/api':{target:'http://127.0.0.1:$BackendPort',changeOrigin:true}}}});"
$backend = $null
$frontend = $null
try {
    $backend = Start-Process -FilePath $java -ArgumentList $javaArguments -WorkingDirectory $backendRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtime 'backend.log') -RedirectStandardError (Join-Path $runtime 'backend-error.log')
    Wait-LedgerHttp "http://127.0.0.1:$BackendPort/api/v1/ledgers?page=1&pageSize=1" $backend
    $frontend = Start-Process -FilePath $node -ArgumentList @('--input-type=module', '-e', ('"' + $previewCode + '"')) -WorkingDirectory $frontendRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtime 'frontend.log') -RedirectStandardError (Join-Path $runtime 'frontend-error.log')
    Wait-LedgerHttp "http://127.0.0.1:$FrontendPort/" $frontend 20
    $state = @{ projectRoot = $script:LedgerProjectRoot; backendPort = $BackendPort; frontendPort = $FrontendPort; backend = New-LedgerProcessEntry $backend; frontend = New-LedgerProcessEntry $frontend }
    $state | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $runtime 'services.json') -Encoding utf8NoBOM
    Write-Output "已启动：http://127.0.0.1:$FrontendPort/。局域网访问请使用本机内网 IP；运行日志和进程记录位于 $runtime。"
} catch {
    foreach ($process in @($frontend, $backend)) {
        if ($process) { $process.Refresh(); if (-not $process.HasExited) { Stop-Process -InputObject $process } }
    }
    throw
}
