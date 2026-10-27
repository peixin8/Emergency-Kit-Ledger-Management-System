# 四项 P2 修复与验证记录

日期：2026-10-27（Asia/Shanghai）。基线：a37f5c9；分支：codex/fix-p2-regressions。改动保留在独立工作副本，未自行提交、推送或部署。

状态：四项修复、正式回归、独立任务审查、I01局部复审和完整工作树终审均完成；已验证范围内通过，无阻塞发现。

## 范围及分工

依据 [修复契约](../superpowers/specs/2026-10-27-p2-regressions.md) 和 [实施计划](../superpowers/plans/2026-10-27-p2-regressions.md)。

| 角色 | 交付 |
| --- | --- |
| Planner | 只读核对需求、旧数据约束、接口与回归契约 |
| Frontend | 仅 frontend/：新增表单会话修复、7 项组件回归 |
| Backend | 仅 backend/：时间、VLAN、上传边界与正式回归 |
| Reviewer | 独立只读任务审查；上传代理补丁局部复审；最终整体审查 |
| 主 Agent | 公共文档、隔离副本、最终 jar 与真实 Vite 代理联调 |

## 已修复项

| 编号 | 行为与证据 |
| --- | --- |
| F01 | 每次新增重新挂载独立表单会话，旧字段/校验不残留；取消、关闭按钮、真实关闭动画中重开、成功保存后重开、编辑后新增均有回归；保存失败保留输入，编辑切换及提交保护不变。 |
| B05 | 每个数据库物理连接固定东八区会话；MySQL 驱动连接时区同会话对齐，dev/test H2 使用兼容语法。SQL DATE 直接通过 LocalDate 读写，申请日期不受 JVM 时区影响。新建/更新/审计检查实际 Instant；旧 SQL 时间和审计 JSON 保留，不修改 V1 或做数据补偿。 |
| B03 | 两个 VLAN 字段局部拒绝所有 JSON 浮点 token，包括 1.0 和指数形式；POST/PUT 返回 400/VALIDATION_ERROR。合法整数、null/缺省保留；非法新增/更新不写台账或审计，不改变原版本和时间。 |
| B01 | 缺 file/空文件/非法文件返回 400；文件>2MiB 或请求>3MiB 返回 413/UPLOAD_TOO_LARGE；恰2MiB合法 xlsx 成功。容器拒绝后消费剩余请求，413 用明确 UTF-8 字节长度保证经默认 Vite 代理仍完整可读。失败及后续行出错无台账/审计部分写入。 |

本轮不扩展 Excel 分卷、性能、部署自动化等其他建议；没有依赖升级或数据模型变化。

## 先失败再修复的证据

- Frontend 正式新增回归在生产源码修改前：7 项中 5 项因字段/校验残留失败，2 项保护行为通过；修复后 7/7，通过完整测试 22/22。
- Backend 原实现真实 HTTP 回归：22 项中 13 断言失败、1 请求传输错误；包括 VLAN 被截断、时间偏八小时、上传 500、合法2MiB被拒绝。测试使用嵌入式 Tomcat，不以 MockMultipartFile 替代解析器。
- 固定会话后，日期回归暴露 UTC JVM 下申请日期由27日变26日；改用 LocalDate 后通过。
- 集成发现 I01：超请求直连后端能读413，Vite代理能收到完整 JSON 却读不到 chunked 结束。Python 与 Node 均稳定复现。新增3项长度/响应回归先失败，再通过明确 Content-Length 修复。

原始实现报告及 RED/GREEN 日志在工作副本的 frontend/node_modules/.cache/p2/、backend/target/，均为 Git 忽略产物；主 Agent 的代理探针及日志在 .superpowers/sdd/2026-10-27-p2-regressions/。

## 最终门禁

环境：Node.js 24.19.0、JDK17。npm 脚本使用已缓存的 npm-cli.js 等价执行；JAVA_HOME/PATH 仅在运行命令中设置，未写入项目真实凭据。

| 检查 | 结果 |
| --- | --- |
| frontend：typecheck、lint | 通过，退出0 |
| frontend：test | 5个文件、22项，通过 |
| frontend：build | 通过 |
| backend：mvnw.cmd "-DargLine=-Duser.timezone=UTC" verify | 41项、0失败/错误/跳过；BUILD SUCCESS |
| backend：mvnw.cmd "-DargLine=-Duser.timezone=Asia/Shanghai" verify | 41项、0失败/错误/跳过；BUILD SUCCESS |
| git diff --check 与变更文件 UTF-8/无 BOM | 通过，19个变更/新增文件 |

生产配置测试显式加载 src/main/resources 配置，避免 test application.yml 遮盖生产行为。MySQL 部分使用真实 Connector/J 属性解析；H2 部分运行真实物理连接和替换连接。

## 主 Agent 真实链路验证

最终 jar 以 UTC JVM、生产 dev 配置及独立 H2 内存库启动；只监听本机。前端使用原 Vite 配置，5173 → 8080，未绕过项目代理。

- 独立 Python 探针经5173：VLAN 新建/更新拒绝且无写、创建/更新/历史真实 Instant、申请日期不偏、缺/空文件400、文件/请求413、恰2MiB合法xlsx200、正常Excel导入、CRUD及删除后历史全部通过。
- scripts/smoke.ps1 通过同一代理执行：新建、查询、修改、软删除和完整三条历史通过。
- Node fetch 复核：直连/代理 × 超大单文件/超大额外part，共4个请求均完整收到413 JSON；修复前的 Invalid EOF state/WinError10053 未再出现。
- 联调只操作内存隔离库，没有连接或迁移生产数据。

## 独立审查

- Frontend 任务审查：契约符合性通过、代码质量通过，无发现；核对真实组件、RED/GREEN与22项门禁日志。
- Backend 首轮任务审查：契约符合性通过、代码质量通过，无代码发现；当时明确保留代理未闭环项，未冒充完整联调通过。
- I01/B01 补丁局部复审：I01 ADDRESSED；契约符合性与代码质量通过，无新增发现。核阅3项RED及最终双时区41项结果，原代理传输问题关闭。
- 完整工作树终审：通过，未发现新增 P0/P1/P2/P3。新 Reviewer 核对全部19个变更/新增文件及上下文、两时区各41项 Surefire XML、前端22项日志，并独立运行 Node 的4种直连/代理大请求场景，均完整收到413 JSON；没有退回项。

## 限制与运行影响

- 本机没有 MySQL/Docker 服务。真实 MySQL 的建连、会话、DATE/TIMESTAMP 与替换连接仍需隔离 MySQL 环境验证；驱动属性解析或 H2 通过不能替代实连。
- 未验证真实第二台局域网设备和真实浏览器布局；组件测试使用真实 Vue/Element Plus 与 HTTP mock，不能冒充这些验证。
- 已有大 JS chunk 和 Flyway/H2 支持版本提示仍在；本轮未升级依赖。
- 重新部署后需重启后端/重建连接池。无数据库迁移；旧来源不明的错误时间不自动补偿。回退代码会恢复原缺陷。

收尾：已保留修复工作副本与未提交改动，未推送；本轮临时 Vite/后端服务在验证后关闭，内存隔离库不作为正式台账使用。
