# 后端易用性改进交付记录

日期：2026-10-28。依据仓库 AGENTS.md、agents/backend.toml、共享台账易用性设计及计划 Task 1。仅修改 backend/；保留任务开始时已有的导入数据库目标去重修改。未提交、推送、升级依赖、修改迁移或启动原有业务数据库。

## 行为与接口

- `DELETE /api/v1/ledgers/{id}?version=0` 必填非负十进制字符串；缺失/非法 400 VALIDATION_ERROR，过期 409 VERSION_CONFLICT，已删除/不存在 404。共享工作区锁、版本条件删除及审计处于同一事务；过期请求不改新内容与审计。
- 新增 multipart `POST /import/preview` 和 `POST /import/confirm`。预览执行完整 Excel/Jakarta/IP/坐标/功耗校验，返回文件 SHA256、全局版本指纹、逐行 CREATE/UPDATE、目标字符串 ID/version、字段变化及实际清空字段；missingOptionalColumns 为缺失的可选中文表头。
- 确认不信任客户端预览行，在共享数据库锁内重新解析文件、读取全局 ID/version/deleted、核对指纹并整批写入。文件/任一台账状态变化返回 409 IMPORT_PREVIEW_STALE；缺失或非法指纹 400。保留旧 `/import` 兼容路径及整文件事务回滚。
- 新增 `/deleted`，编号/两地址检索、上海本地日期含首尾日期、分页、删除时间倒序；最后 DELETE 时间优先，无审计回退 updated_at。不提供恢复操作。
- 列表/地图/标准导出/分卷导出共用关键词、status、direction、locationStatus 校验及筛选。关键词扩展到序列号、机房、网管/业务 IP、资产归属；地图未定位计数也应用相同筛选。
- 新增 `/export-batches` ZIP；总数最多10000，每个工作簿单sheet、至多1000数据行且至多2MiB，按实际压缩后大小进一步拆分；零记录保留一个表头文件。文件名 `应急宝台账-001.xlsx` 起连续编号。
- 兼容 Excel 1900/1904 日期系统中的原生日期单元格；文本日期保持 ISO，所有公式仍拒绝。

## 文件和职责

生产文件：`ApiErrors.java`、`LedgerController.java`、`LedgerService.java`、`LedgerRepository.java`、`LedgerExcelService.java`；新增 `LedgerImportService.java` 负责无缓存预览指纹/差异/确认事务，`LedgerBatchExportService.java` 负责分卷，不将职责继续集中到 Excel 解析类。

测试：新增 `LedgerUsabilityTest.java`（11例）；增补原有未提交 `LedgerExcelDatabaseIdentityTest.java` 的数据库相等语义预览/确认回滚测试；调整 `LedgerApiTest.java`、`LedgerExcelTest.java`、`LedgerLocationTest.java`、`P2HttpRegressionTest.java`、`SharedLedgerApiTest.java` 的版本删除请求，保留其有价值断言。

以上 Java 路径位于 `src/main/java/com/emergency/ledger/` 或 `src/test/java/com/emergency/ledger/`。未新增数据库列、索引、迁移或第三方依赖。

## 实际验证

进程级设置 `JAVA_HOME=C:\Users\ASUS\.cache\emergency-ledger-review\jdk17\jdk-17.0.20.1+1`，将其 bin 加到进程 PATH；使用 Maven Wrapper，实际 Maven 工作目录 backend/。

1. `mvnw.cmd -q -Dtest=LedgerUsabilityTest test`：先观察新增7例全部按缺失行为失败（旧删除缺version仍200，预览路由缺失，原生日期400，关键词未匹配等）；日志 `target/ux-red.log`。
2. 同一命令实现后：7例全部通过；日志 `target/ux-green.log`。
3. `mvnw.cmd -q verify`：边界测试增补后首次全套84例通过；日志 `target/ux-verify.log`。
4. `mvnw.cmd -q verify`：增加锁覆盖全事务的实际并发阻塞检查，以及10001条导出拒绝/删除参数边界后，最终 **15类86例，失败0、错误0、跳过0，退出码0**；日志 `target/ux-verify-final.log`，统计取自 Surefire XML。jar 已生成于 `target/ledger-1.0.0.jar`。

覆盖：缺失/非法/过期/有效/重复删除；预览台账表与审计表完全无变化；旧模板缺列清空、变化字段和指纹；替换文件/新增/编辑/删除目标/全局其他台账变化均使确认失效且不写入；确认共享锁从解析至提交持续持有；数据库相等语义重复目标整批回滚；已删除日期日末、旧数据回退、日期/分页参数边界；地图/查询/导出一致过滤；1001行拆分、900行高熵数据触发2MiB拆分、空导出、超过10000拒绝；1900/1904日期及公式/非法字段拒绝。

测试使用隔离内存 H2，包括区分大小写及 VARCHAR_IGNORECASE 数据库；Flyway实际启动并执行/验证3个现有迁移，既有随机真实HTTP端口回归30例运行通过。所有改动 Java 文件检查 UTF-8 无 BOM。

## 限制与联调事项

- 无目标的新编号可能在实际数据库排序规则下互为别名。预览按原始trim编号以及数据库已存在目标ID尽量拒绝，不猜测排序规则、不写临时台账；此类全新别名可能均显示 CREATE，但确认以数据库实际目标ID重复检查返回409 DUPLICATE_LEDGER_CODE并全文件/审计回滚。H2大小写不敏感测试已验证具体第3行错误。预览不是成功保证，前端须正常展示确认失败；该限制已通知主Agent并得到认可。
- 未获得隔离真实 MySQL，故未执行真实 MySQL 排序规则/锁/连接集成。生产SQL采用参数绑定、MySQL/H2共同支持的 FOR UPDATE、LIMIT/OFFSET、COALESCE/MAX/派生表；日期由Java生成LocalDateTime边界，未引入H2专用生产语法。仍需真实 MySQL 集成验证。
- 沿用依赖存在 Flyway 对 H2 2.3.232 的支持版本提示，测试迁移成功，未擅自升级。
- 新接口真实已打包文件H2启动、前后端浏览器联调、备份恢复演练及第二台局域网设备验证由主Agent集成；本报告不把MockMvc通过等同于这些项目已完成。
- 后端实现与验证已完成，等待主Agent安排独立Reviewer审查；无自行审查结论。

## 独立审查修复轮 1：BE-UX-01（P2）

根因：导出共用 exportRecords 使用的 repository.list 分别执行 COUNT 与 SELECT。COUNT 返回10000后新增提交可使实际返回10001条，原实现仅检查total，因而标准导出和分卷导出都可能超限。

修复仅在 `LedgerExcelService.java` 增加实际 `records.size()>10000` 检查，与原total检查共同执行。不改变契约、不为文件生成增加数据库锁、不修改其他功能。

新增 `LedgerExportRaceTest.java`（2个参数用例，标准/分卷）。测试使用隔离真实H2和真实JdbcTemplate，在真实COUNT返回后提交第10001条，再执行真实SELECT；仅在测试中添加确定性的SQL调度点，没有伪造Page、total或records。断言COUNT确为10000、数据库实际10001、导出抛400 VALIDATION_ERROR。

- 修改生产代码前：`mvnw.cmd -q -Dtest=LedgerExportRaceTest test`，2例均因“预期ApiException但未抛出”失败，退出码1；日志 `target/ux-review-r1-red.log`。
- 最小修复后：相同命令2例全部通过，退出码0；日志 `target/ux-review-r1-green.log`。
- 最终 `mvnw.cmd -q verify`：**16类88例，失败0、错误0、跳过0，退出码0**；日志 `target/ux-review-r1-verify.log`，已重新生成jar。此结果取代前文86例的最终验证状态。

已向主Agent和Reviewer发送修复及红绿证据，等待独立复审。真实MySQL等前述环境限制不变。
