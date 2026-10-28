# 台账数据库备份与恢复

Excel 用于业务字段交换，不是完整备份。完整数据库包含活跃与已删除台账、审计前后快照、固定共享身份、组织和 Flyway 迁移记录。维护人员应至少每日备份一次，升级、批量导入及切换数据库前另存备份；按单位要求确定保留期，至少保留最近七份并定期复制到运行机器以外的受控位置。

备份目录默认 `backups/`，日志与进程记录默认 `.runtime/`，两者均被 Git 忽略。数据库、备份和日志可能包含联系人、网络参数和地址，应限制操作系统访问权限。环境变量、地图密钥及数据库凭据单独安全保管，不写入仓库或备份说明。

## H2 离线备份

使用 PowerShell 7。先通知使用者并停止后端。如果服务由本项目工具启动，执行：

```powershell
./scripts/stop.ps1
./scripts/backup.ps1
```

原先在终端启动的后端需在原终端停止；`stop.ps1` 只管理自己记录的进程。默认数据库是 `backend/data/emergency-ledger-dev.mv.db`，使用其他文件时显式传入：

```powershell
./scripts/backup.ps1 -DatabasePath 'D:/LedgerData/ledger.mv.db' -Destination 'D:/LedgerBackups/ledger-20261028.mv.db'
```

脚本独占读取数据库，文件正在使用时拒绝，目标已存在时不覆盖。保存脚本输出的 SHA256、字节数、备份时间、应用版本（可用 `git rev-parse HEAD` 并附本次未提交变更标识）及迁移版本。数据库文件不能在多台后端间直接共享。

## H2 隔离恢复演练

1. 核对备份 SHA256。新建一个空的演练目录，将备份复制为 `ledger.mv.db`，保留原始备份不动，不覆盖原业务库。
2. 使用其他端口和运行目录启动副本。`DevDataPath` 填文件前缀，不能带 `.mv.db` 后缀。应用版本须与备份兼容；新版本可能自动执行后续迁移，所以只能先操作副本。

```powershell
./scripts/start.ps1 -LocalOnly -BackendPort 8081 -FrontendPort 5174 `
  -DevDataPath 'D:/LedgerRecovery/ledger' -RuntimeDirectory 'D:/LedgerRecovery/runtime'
./scripts/status.ps1 -RuntimeDirectory 'D:/LedgerRecovery/runtime'
```

3. 打开 `http://127.0.0.1:5174/`，核对活跃记录总数、已删除记录、抽样业务字段、修改历史及导出；在演练副本创建并修改测试记录，确认审计正常。
4. 记录恢复耗时、核对结果及失败原因。停止演练实例：`./scripts/stop.ps1 -RuntimeDirectory 'D:/LedgerRecovery/runtime'`。
5. 实际生产恢复需明确停机窗口和所选备份，先保留故障库副本，再由维护人员执行。不要把演练路径直接改成原业务路径运行脚本。

## MySQL 备份

先确认连接的是目标台账数据库，使用有备份权限的维护账号。当前 `compose.yaml` 的 MySQL 数据库全为 InnoDB，可用一致性逻辑备份；备份期间不要执行 DDL/迁移。以下命令使用容器已有环境变量，不把明文密码写进命令或仓库，PowerShell 7.4 及以上保留原生命令输出字节：

```powershell
New-Item -ItemType Directory -Path backups -Force | Out-Null
$backupFile = Join-Path (Resolve-Path backups) ('mysql-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.sql')
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump --user="$MYSQL_USER" --single-transaction --routines --triggers --events --no-tablespaces --default-character-set=utf8mb4 "$MYSQL_DATABASE"' > $backupFile
if ($LASTEXITCODE -ne 0) { throw 'MySQL 备份失败；该文件不能作为有效备份。' }
Get-FileHash -LiteralPath $backupFile -Algorithm SHA256
```

不同部署由数据库管理员使用其既有备份工具，不要把生产凭据粘贴到聊天或脚本。仅导出 `emergency_ledger` 业务数据库，不需要备份 MySQL 系统账号；目标环境的应用账号由维护人员配置。

## MySQL 隔离恢复演练

在独立 MySQL 实例建立空的演练数据库，使用 MySQL 官方客户端或数据库管理工具导入完整 SQL 文件。只授予演练库权限，不对生产库执行清空、覆盖或迁移。导入成功后，将新启动后端的 DB_URL/DB_USERNAME/DB_PASSWORD 指向演练库，再按 H2 演练同样核对台账、删除状态、历史、迁移和导出。H2 恢复演练不能替代 MySQL 恢复演练。

H2 到 MySQL 迁移需要单独的数据迁移和审计保留方案。直接改连接地址或只导入业务 Excel 不能迁移完整历史。
