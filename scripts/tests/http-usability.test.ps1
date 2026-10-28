#Requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$BaseUrl,
    [Parameter(Mandatory)][string]$WorkDirectory,
    [switch]$AllowIsolatedWrites
)
$ErrorActionPreference = 'Stop'
$endpoint = [Uri]$BaseUrl
if (-not $AllowIsolatedWrites -or $endpoint.Host -notin @('127.0.0.1', 'localhost') -or $endpoint.Port -in @(8080, 5173)) {
    throw '只允许显式授权的本机隔离端口测试写入；不得使用原有业务服务端口。'
}
New-Item -ItemType Directory -Path $WorkDirectory -Force | Out-Null
$api = $BaseUrl.TrimEnd('/') + '/api/v1/ledgers'
function Assert-Http($Value, [string]$Message) { if (-not $Value) { throw $Message } }
function Send-Json([string]$Uri, [string]$Method, $Body) {
    return Invoke-RestMethod -Uri $Uri -Method $Method -ContentType 'application/json;charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 8)))
}
$suffix = [Guid]::NewGuid().ToString('N').Substring(0, 8)
$body = @{ ledgerCode = "验收-$suffix"; originalAddress = '验收机房甲'; destinationAddress = '验收机房乙'; applicationMethod = 'ONLINE'; applicantContact = '隔离验收'; applicationDate = '2026-10-28'; direction = 'TRANSFER'; deviceCondition = 'GOOD'; deviceSerialNo = "SERIAL-$suffix"; accessRoom = "ROOM-$suffix"; remarks = '导出时的原始备注'; latitude = '24.4798000'; longitude = '118.0894000' }
$created = (Send-Json $api 'POST' $body).data
$recordUrl = $api + '/' + $created.id
$xlsx = Join-Path $WorkDirectory 'roundtrip.xlsx'
Invoke-WebRequest -Uri ($api + '/export?keyword=' + $suffix) -OutFile $xlsx
$preview = (Invoke-RestMethod -Uri ($api + '/import/preview') -Method Post -Form @{ file = Get-Item -LiteralPath $xlsx }).data
Assert-Http ($preview.updatedCount -eq 1 -and $preview.createdCount -eq 0) '预览必须匹配原有一条记录'
$history = (Invoke-RestMethod -Uri ($recordUrl + '/history')).data
Assert-Http ($history.total -eq 1) '预览不得写审计'
$body.version = $created.version; $body.remarks = '另一使用者已经更新'
$updated = (Send-Json $recordUrl 'PUT' $body).data
$staleDelete = Invoke-WebRequest -Uri ($recordUrl + '?version=' + $created.version) -Method Delete -SkipHttpErrorCheck
Assert-Http ($staleDelete.StatusCode -eq 409 -and ($staleDelete.Content | ConvertFrom-Json).code -eq 'VERSION_CONFLICT') '旧版本删除必须409'
$staleImport = Invoke-WebRequest -Uri ($api + '/import/confirm') -Method Post -Form @{ file = Get-Item -LiteralPath $xlsx; previewFingerprint = $preview.previewFingerprint } -SkipHttpErrorCheck
Assert-Http ($staleImport.StatusCode -eq 409 -and ($staleImport.Content | ConvertFrom-Json).code -eq 'IMPORT_PREVIEW_STALE') '旧预览确认必须409'
$unchanged = (Invoke-RestMethod -Uri $recordUrl).data
Assert-Http ($unchanged.remarks -eq $updated.remarks -and $unchanged.version -eq $updated.version) '冲突请求不得覆盖最新记录'
$preview = (Invoke-RestMethod -Uri ($api + '/import/preview') -Method Post -Form @{ file = Get-Item -LiteralPath $xlsx }).data
Assert-Http ($preview.rows[0].changedFields -contains 'remarks') '预览需显示备注变化'
$confirmed = (Invoke-RestMethod -Uri ($api + '/import/confirm') -Method Post -Form @{ file = Get-Item -LiteralPath $xlsx; previewFingerprint = $preview.previewFingerprint }).data
Assert-Http ($confirmed.updatedCount -eq 1) '有效确认需覆盖一条记录'
$latest = (Invoke-RestMethod -Uri $recordUrl).data
Assert-Http ($latest.remarks -eq '导出时的原始备注') '实际导入后字段必须与文件一致'
$bySerial = (Invoke-RestMethod -Uri ($api + '?keyword=SERIAL-' + $suffix)).data
Assert-Http ($bySerial.total -eq 1) '序列号搜索必须命中'
$map = (Invoke-RestMethod -Uri ($api + '/map-points?keyword=' + $suffix + '&locationStatus=LOCATED')).data
Assert-Http ($map.records.Count -eq 1 -and $map.unlocatedCount -eq 0) '地图必须应用相同筛选'
$zipPath = Join-Path $WorkDirectory 'batches.zip'
Invoke-WebRequest -Uri ($api + '/export-batches?keyword=' + $suffix) -OutFile $zipPath
$zip = [IO.Compression.ZipFile]::OpenRead($zipPath)
try { Assert-Http ($zip.Entries.Count -eq 1 -and $zip.Entries[0].Name.EndsWith('.xlsx')) 'ZIP必须包含可导入工作簿' } finally { $zip.Dispose() }
$deletedBody = $body.Clone(); $deletedBody.Remove('version'); $deletedBody.ledgerCode = "已删除-$suffix"
$deleted = (Send-Json $api 'POST' $deletedBody).data
Invoke-RestMethod -Uri ($api + '/' + $deleted.id + '?version=' + $deleted.version) -Method Delete | Out-Null
$deletedPage = (Invoke-RestMethod -Uri ($api + '/deleted?keyword=' + $suffix)).data
Assert-Http ($deletedPage.total -eq 1 -and $deletedPage.records[0].id -eq $deleted.id) '业务编号检索必须找到已删除记录'
$deletedHistory = (Invoke-RestMethod -Uri ($api + '/' + $deleted.id + '/history')).data
Assert-Http ($deletedHistory.total -eq 2 -and $deletedHistory.records[0].action -eq 'DELETE') '已删除历史必须保留'
$result = @{ activeId = $latest.id; activeCode = $latest.ledgerCode; deletedId = $deleted.id; deletedCode = $deleted.ledgerCode; activeHistoryCount = 3; deletedHistoryCount = 2; xlsx = $xlsx; zip = $zipPath }
$result | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $WorkDirectory 'http-result.json') -Encoding utf8NoBOM
Write-Output '真实 HTTP 联调通过：旧版本删除、陈旧预览拒绝、零副作用、有效导入、序列号搜索、地图筛选、ZIP、已删除检索及历史。'
