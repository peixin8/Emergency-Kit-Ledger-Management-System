param([string]$BaseUrl = 'http://127.0.0.1:8080')

$ErrorActionPreference = 'Stop'
$base = $BaseUrl.TrimEnd('/')
$list = Invoke-RestMethod -Uri "$base/api/v1/ledgers?page=1&pageSize=1"
if ($list.code -ne 'OK') { throw '无需登录查询失败。' }

$code = 'SMOKE-' + [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$record = @{
  ledgerCode = $code
  originalAddress = '测试机房 A'
  destinationAddress = '测试机房 B'
  direction = 'TRANSFER'
  applicationMethod = 'ONLINE'
  applicantContact = '联调测试'
  applicantPhone = '13800000000'
  applicationDate = Get-Date -Format 'yyyy-MM-dd'
  deviceCondition = 'GOOD'
  accessRoom = '测试接入机房'
  fiberCore = '1-2'
  networkStandard = '5G'
  bbuModel = '测试 BBU'
  rruModel = '测试 RRU'
  bbuRruPowerWatts = '120.50'
  managementIp = '192.0.2.10'
  managementVlan = 101
  businessIp = '192.0.2.11'
  businessVlan = 102
  deviceSerialNo = $code
  assetOwner = '测试部门'
  remarks = '本地联调脚本生成'
}

$created = Invoke-RestMethod -Uri "$base/api/v1/ledgers" -Method Post -ContentType 'application/json' -Body ($record | ConvertTo-Json -Depth 10)
if ($created.code -ne 'OK' -or -not $created.data.id) { throw '新建台账失败。' }
$id = [string]$created.data.id
Write-Output "新建成功：$id"

try {
  $detail = Invoke-RestMethod -Uri "$base/api/v1/ledgers/$id"
  if ($detail.data.ledgerCode -ne $code) { throw '详情与新建结果不一致。' }

  $found = Invoke-RestMethod -Uri "$base/api/v1/ledgers?page=1&pageSize=20&keyword=$code"
  if (@($found.data.records | Where-Object ledgerCode -eq $code).Count -ne 1) { throw '列表搜索未找到新记录。' }

  $record.remarks = '已通过更新验证'
  $record.version = $detail.data.version
  $updated = Invoke-RestMethod -Uri "$base/api/v1/ledgers/$id" -Method Put -ContentType 'application/json' -Body ($record | ConvertTo-Json -Depth 10)
  if ($updated.data.remarks -ne '已通过更新验证') { throw '修改结果不符合预期。' }
} finally {
  $cleanup = Invoke-RestMethod -Uri "$base/api/v1/ledgers/$id"
  $cleanupVersion = [Uri]::EscapeDataString([string]$cleanup.data.version)
  Invoke-RestMethod -Uri "$base/api/v1/ledgers/${id}?version=$cleanupVersion" -Method Delete | Out-Null
  Write-Output "已软删除测试记录：$id"
}

$history = Invoke-RestMethod -Uri "$base/api/v1/ledgers/$id/history"
if ($history.data.total -ne 3 -or $history.data.records[0].action -ne 'DELETE' -or $history.data.records[1].action -ne 'UPDATE') {
  throw '历史记录不完整或顺序错误。'
}
Write-Output '端到端冒烟测试通过。'
