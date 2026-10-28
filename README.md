# 应急宝设备共享台账 V2

Vue 3 前端和 Spring Boot 后端共用一份数据库台账。打开网页即可查询和编辑，无需登录；每次创建、编辑、软删除都保留历史快照。支持 `.xlsx` 导入和导出，供离线交换文件。

## 运行环境

- JDK 17、Node.js 20.19+、PowerShell。
- 后端自带 Maven Wrapper，前端用 npm 锁文件。
- 正式多人使用建议 MySQL 8；`dev` 配置使用本机文件型 H2，适合体验与联调。局域网浏览器访问的是运行机器上的同一个后端和数据库文件，不要在多台机器上同时直接打开该 H2 文件。

## 本机与局域网启动

已有 Java 17、Node.js 和前端依赖时，可在 **PowerShell 7** 使用维护脚本构建并后台运行：

```powershell
./scripts/start.ps1 -Build
./scripts/status.ps1
./scripts/stop.ps1
```

首次仍需在 `frontend/` 执行 `npm ci`。脚本运行后端 jar 与前端构建预览，避免持续占用两个终端；它是本地维护工具，正式部署仍建议配置进程守护和反向代理。运行记录及日志在被 Git 忽略的 `.runtime/`；只停止本工具记录且身份匹配的进程，已有手动服务不会被接管。原端口被占用时拒绝启动。

支持 `-JavaHome`、`-NodePath`、`-BackendPort`、`-FrontendPort`、`-LocalOnly`；使用 `-Profile mysql` 前须配置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`。不带 `-Build` 使用现有构建，代码变化后需重新构建并重启。启动会按既有 Flyway 配置执行迁移，首次试用新版本应先备份，并在隔离数据库验收。

[完整备份与恢复演练](docs/operations/backup-and-recovery.md)包含 H2 离线备份和 MySQL 步骤。Excel 不能替代数据库完整备份。

在两个终端分别执行：

```powershell
cd backend
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev'
```

```powershell
cd frontend
npm ci
npm run dev
```

本机打开 `http://127.0.0.1:5173/`。同一局域网的其他设备打开 `http://<运行机器的内网 IP>:5173/`，即可操作同一份台账。运行机器必须保持开机且前后端进程持续运行；如无法从其他设备连接，检查本机防火墙是否允许局域网访问 5173 和 8080 端口。匿名共享模式允许能访问该地址的人修改台账，因此仅在受信任的内网使用，不向公网开放。

开发数据库位于 `backend/data/emergency-ledger-dev.mv.db`，已被 Git 忽略。切换至 MySQL 前，另行备份及迁移现有 H2 数据，不能只修改连接地址就认为历史数据已转移。

## MySQL 配置

`compose.yaml` 可启动本地 MySQL；先将 `.env.example` 复制为 `.env` 并设置 `DB_PASSWORD`。后端以默认配置启动时，设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 指向自己的 MySQL 数据库。Flyway 在空库上执行 V1 建表；现有迁移文件不应改写。前端仍运行 `npm run dev`，通过 Vite 将 `/api` 转发给本机后端。

## 使用方式

- 首页显示当前台账。新增、编辑和软删除写入服务端数据库；编辑和删除均使用所见版本检测冲突。保存中锁定表单，有未保存修改时关闭会提醒；编辑冲突可保留草稿并查看最新内容。
- 搜索支持编号、地址、联系人、设备序列号、接入机房、网管/业务 IP 和资产归属；“待定位”可筛出需要补坐标的记录。统一刷新列表和地图，显示最后成功刷新时间；地图默认收起，点击有坐标记录可展开聚焦，统计单位为台账条数。
- 台账目标地址可在地图上手动选点或填写 WGS84 经纬度。地址搜索使用高德地图 Web 服务：先按[高德官方指引](https://lbs.amap.com/api/webservice/create-project-and-key)创建应用，添加服务平台为“Web 服务”的 Key，再在启动后端的终端配置环境变量 `AMAP_WEB_SERVICE_KEY`（不要写入 Git 或前端配置），重新启动后端。仅点击“搜索地址”时才会发送地址；搜索候选为估计位置，选择后应在地图核对和微调，保存台账才会写入数据库。未配置 Key 时仍可手动选点。
- 详情中的“历史记录”展示创建、编辑、删除前后的完整快照及变化摘要。已删除记录可按编号、地址和删除日期检索后查看历史，无需记住系统 ID。本轮不提供恢复或永久删除。共享模式无法识别具体个人，历史统一标示“共享用户”。
- “导出 Excel”导出当前筛选出的未删除台账；可导入系统导出文件、旧版 22/24 列模板，以及包含“PTN端口（网元+槽口）”的现有台账。导入按中文表头识别列，以台账编号匹配：已有编号覆盖整行业务信息并保留系统 ID 和修改历史，新编号新增并生成不同 ID；文件未包含的台账不变。空白单元格以及旧模板缺失的可选列会清空对应旧值，建议先导出新版文件再编辑导入。空白进出方向显示为“未填写”，故障原文保存在“设备状况说明”，带 `W` 的功耗按瓦数读取。导入失败会提示行列且整份回滚。文件限制为 `.xlsx`、2 MB、1000 条数据；导出上限为 10000 条。
- 首版字段包括 ID、原始地址、目标地址、申请方式、联系人、设备状况、机房、纤芯、制式、BBU/RRU 型号和功耗、网管及业务 IP/VLAN，并补充申请日期、方向、序列号、资产归属、备注等。

网页导入选择文件后先预览，显示新增/覆盖数量、变化及清空字段和旧模板缺列，确认才写入。预览后任何共享台账发生变化都必须重新预览；新编号在数据库排序规则下互为别名时，可能在确认时才拒绝，但仍整份回滚。不要把“预览成功”当作已导入。确认网络结果未知时先刷新核对，不能直接重复导入。

“ZIP 分卷导出”将筛选结果拆成每卷最多 1000 行且不超过 2 MiB 的工作簿，解决标准单文件导出超过导入上限时需要手工拆分的问题；解压后逐卷预览导入。兼容 Excel 原生日期（1900/1904 日期系统）及 ISO 文本日期，公式仍拒绝。

## 输入与时间边界

- 每次打开新增台账都恢复默认字段并清除旧校验提示；保存失败时保留本次输入。
- 网管与业务 VLAN 可留空；填写时必须为 1–4094 的整数。JSON 小数值（包括 `1.0`）返回 HTTP 400，不再自动截断。
- Excel 文件上限为 2,097,152 字节（2 MiB），完整 multipart 请求上限为 3,145,728 字节（3 MiB），为边界及请求头预留空间。缺文件、空文件或内容非法返回 HTTP 400／`VALIDATION_ERROR`；文件或请求超限返回 HTTP 413／`UPLOAD_TOO_LARGE`。
- 应用的每个数据库连接固定使用东八区会话，使数据库生成的创建、更新和历史时间与接口的 `Asia/Shanghai` 解释一致；修改配置后需重启后端，使连接池重新建立连接。申请日期仍是无时区日期。
- 不自动调整已有 SQL 时间或审计快照。若旧实例曾在错误时区写入数据，需先核对来源并备份，再另行制定修正方案，不能直接对全部历史加减八小时。

## 验证

```powershell
cd backend
.\mvnw.cmd verify
```

时间回归还需分别在 UTC 与上海 JVM 时区运行测试：

```powershell
cd backend
.\mvnw.cmd "-DargLine=-Duser.timezone=UTC" verify
.\mvnw.cmd "-DargLine=-Duser.timezone=Asia/Shanghai" verify
```

```powershell
cd frontend
npm run typecheck
npm run lint
npm run test
npm run build
```

服务运行后可执行 `./scripts/smoke.ps1` 验证真实接口。脚本会创建一条 `SMOKE-` 测试台账，验证查询、修改和历史后软删除该记录；审计留痕仍保留。

维护脚本测试：`./scripts/tests/operations.test.ps1`。真实新增功能 HTTP 验收脚本仅对自己新建的隔离库使用：`./scripts/tests/http-usability.test.ps1 -BaseUrl http://127.0.0.1:8081 -WorkDirectory <临时输出目录> -AllowIsolatedWrites`；脚本拒绝原有 8080/5173 端口，会保留验收样例及历史，不在业务库运行。

设计与接口见 [V2 规格](docs/superpowers/specs/2026-10-27-shared-ledger-v2.md)。MySQL 实例和真实第二台设备需要在对应环境单独联调；本机 H2 测试不能代替这两项验证。
