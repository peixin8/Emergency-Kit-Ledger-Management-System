# 共享台账易用性改进设计

用户在完整项目审查后要求“实施修改”。本轮实现审查中的两项 P2 和日常录入、查询、导入、历史及维护体验建议。沿用无需登录、局域网共享数据库和修改留痕的 V2 定位。

## 目标与边界

- 防止过期页面删除他人新内容、保存中继续输入丢失、长表单误关闭。
- Excel 保持同编号整行覆盖、空白及缺失可选列清空、全文件事务回滚；新增预览确认，不改变已有编号和历史规则。
- 已删除记录本轮仅提供只读检索和历史，不增加恢复、永久删除或新的状态机。
- 增强检索、待定位入口、统一刷新、历史变化摘要；简化单页面导航和技术信息展示，地图可以折叠。
- 新增分卷导出、原生 Excel 日期兼容；补齐数据库备份恢复说明和本地启停状态工具。
- 不新增登录、审批、库存、一机一档，不升级依赖，不改已执行迁移，不提交或部署。

## 方案取舍

采用现有单体的增量接口，前端保留原始 File，服务端计算无缓存 SHA256 指纹确认；无需 Redis 或单实例内存会话。未采用自动合并冲突：用户先比较最新记录和草稿，明确重新编辑。保留原标准 Excel 导出，另增 ZIP 分卷，避免破坏已有交换接口。数据库完整恢复使用维护步骤，不提供网页覆盖数据库按钮。

## 接口契约

沿用 `/api/v1`、JSON `{code,message,data,traceId}`、字符串 Long/版本、十进制字符串和 Asia/Shanghai 时间。所有使用者使用服务端固定共享身份。

### 版本删除

`DELETE /ledgers/{id}?version=0`，version 必填非负十进制字符串，缺失/非法 400 `VALIDATION_ERROR`；所见版本过期 409 `VERSION_CONFLICT`；不存在或已删除 404 `NOT_FOUND`；成功 200、data=null。删除获取同一共享工作区数据库行锁，版本条件删除和 DELETE 审计同一事务，失败不得新增审计。

### 导入预览与确认

- `POST /ledgers/import/preview` multipart `file`，沿用 xlsx、2 MiB、1000 行限制及行列错误信息。
- 返回 `{fileHash,previewFingerprint,importedCount,createdCount,updatedCount,missingOptionalColumns,rows:[{line,ledgerCode,action,targetId,targetVersion,changedFields,clearedFields}]}`。missingOptionalColumns 为旧模板缺失的可选列中文表头数组；clearedFields 只含非空旧值被清空的字段。
- 两个 hash 为 64 位 SHA256；action=`CREATE|UPDATE`，ID/版本为字符串或 null，字段数组使用 `LedgerInput` 的字段键。
- `POST /ledgers/import/confirm` multipart `file` 和 `previewFingerprint`，成功沿用 `{importedCount,createdCount,updatedCount}`。
- 文件、匹配目标 ID、版本、删除状态或原先不存在的目标发生变化时返回 409 `IMPORT_PREVIEW_STALE`，前端重新预览；非法指纹 400。服务端不能信任客户端预览行。
- 指纹采用保守策略：SHA256(协议版本+文件 SHA256+全局共享台账按 ID 排序的 ID/version/deleted)；任一台账变化均要求重新预览，换取无需数据库特定排序规则推断和会话缓存的可靠检查。确认在同一事务共享工作区锁内重新解析、计算指纹、校验后写入；事务、版本、唯一约束及重复目标检查仍为最终防线。相同文件可被多次明确重新导入，未引入新幂等承诺。
- 原 `POST /ledgers/import` 保留兼容现有调用和测试；网页只使用预览确认。网络结果未知时提示先刷新核对，不能自动重试。

### 查询与地图

`GET /ledgers`、`/map-points`、`/export`、`/export-batches` 接受同一 keyword/status/direction/locationStatus。locationStatus=`LOCATED|UNLOCATED` 或省略，非法值 400。网页保留简洁列表，不重新增加方向、状况筛选。

keyword 最大 100，搜索编号、原始/目标地址、联系人、序列号、接入机房、网管/业务 IP、资产归属。列表分页起始1、默认20、最大100，排序沿用 ID 降序。地图仍返回 `{records,unlocatedCount}`，按相同筛选计算，显示“条台账”。

### 已删除记录

`GET /ledgers/deleted?page=1&pageSize=20&keyword=&deletedFrom=2026-10-01&deletedTo=2026-10-28`。

返回 `{records:[{id,ledgerCode,originalAddress,destinationAddress,deletedAt}],total,page,pageSize}`。keyword 搜索编号和两处地址，最多100。日期为上海本地 ISO 日期，含当日，可单独设置，逆序400。deletedAt 来自最后 DELETE 审计，极旧无审计数据回退 updated_at；排序 deletedAt DESC,id DESC。点击记录调用已有 `/{id}/history`。不增加数据库列或迁移。

### 分卷导出和日期

`GET /ledgers/export-batches` 下载 application/zip，最多10000条；每份 xlsx 单 sheet、最多1000行且不超过2 MiB，与原导入列兼容，超过体积则继续拆分。ZIP 文件名中的工作簿按 `应急宝台账-001.xlsx` 连续编号；0条返回一个表头文件。原 `/export` 保持 xlsx；导入表头空文件仍按既有规则拒绝。Excel 原生日期单元格读取日期值（包含1904日期系统），文本日期保持明确的 ISO 规则，不猜测歧义日期，公式仍拒绝。

## 前端交互

- 保存中禁用所有可修改输入、选点和地址查询；保留失败输入。
- 有修改时关闭、Esc、遮罩和取消统一确认；浏览器离开提醒，组件卸载清理监听。明确放弃后下次新增为空。
- 区分编号重复与版本冲突。冲突时保留草稿并查看最新值及差异，只有用户选择重新编辑才用新版本重建编辑基线，不静默覆盖。
- 导入预览显示文件名、新增/覆盖数量、逐行变化/清空字段；取消不写入，确认期间禁用重复操作，过期提示重新预览。
- 当前/已删除切换及删除日期范围；历史可查完整快照及变化字段摘要，包括坐标、PTN和状况说明。
- 显示最后成功刷新时间；统一刷新列表和地图，窗口激活时刷新，编辑中不替换表单。避免重复请求和旧响应覆盖。
- 地图可折叠，待定位可点击筛选；地图和列表采用相同筛选。精简单页面导航，技术字段折叠，说明按需展开。

## 维护和验证

启动脚本使用本机 Java/Node，构建后运行 jar 和 Vite preview，PID 与启动时间存入被忽略的 `.runtime`，拒绝占用端口，不误停别的服务。状态检查接口可读，不对原有业务数据写入。H2 离线备份必须排除运行中的数据库、避免覆盖目标；完整恢复流程在独立数据库演练。MySQL 说明使用环境变量，凭据不进入命令输出或仓库。

业务测试覆盖过期删除不改数据/审计、预览不写入、过期确认全回滚、重复目标、跨组织编号、清空差异、日期/ZIP边界、已删除筛选，以及前端误关闭/保存锁定/冲突保留/导入取消和确认。前后端全套检查后，使用独立端口及隔离 H2 完成真实 HTTP 和浏览器联调。真实 MySQL、第二台局域网设备及外部地图配置分别记录环境限制。
