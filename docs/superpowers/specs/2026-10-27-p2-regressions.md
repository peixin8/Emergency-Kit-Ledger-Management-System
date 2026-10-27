# 四项 P2 修复契约

依据：用户确认修复四项已复现 P2，并要求按 AGENTS.md 分工、补充回归测试、独立审查及集成验证。Planner 已读取 agents/planner.toml 与当前实现并输出本契约，主 Agent 保存。

## 范围与约束

- F01：新增表单残留；B05：数据库会话时间与接口解释不一致；B03：VLAN 小数截断；B01：上传边界返回 500。
- 保持匿名共享 V2、接口路径、字符串 ID/版本号、十进制字符串功耗、现有依赖版本及包管理器。
- 不扩展其他审查建议，不修改 V1，不重写已有台账或审计数据，不新增表结构迁移。
- Frontend 仅修改 frontend/，Backend 仅修改 backend/；主 Agent 维护公共文档；Reviewer 独立只读。不得自行提交、推送或发布。

## F01：新增表单会话

- 每次点击新增，字段恢复 emptyLedger() 默认值，旧校验提示消失。
- 覆盖取消、抽屉关闭、保存成功后再次新增，以及关闭动画未结束立即重开。
- 编辑记录 A/B 显示对应内容，编辑后新增不沿用旧字段或校验。
- 保存失败保留本次输入；提交中保留重复提交及关闭保护。
- 可采用独立表单会话标识/重新挂载或显式重置，不能仅依赖 null→null 的 modelValue watcher。
- 使用真实 Element Plus Form/Input/Drawer 的组件回归，API 可 mock，不能 stub 掉本次缺陷依赖的组件生命周期。

## B05：数据库会话时区

- 每个物理连接使用前固定为上海会话。MySQL 使用 SET time_zone = '+08:00'；H2 使用 SET TIME ZONE '+08:00'，按数据源配置选择兼容语法。
- MySQL 驱动连接时间解释与会话对齐；仅设置 URL serverTimezone 不足以保证会话生效。
- 新建/替换连接同样生效，CURRENT_TIMESTAMP 的创建、更新、审计写入都受保护。
- 保留 SQL TIMESTAMP→LocalDateTime→Asia/Shanghai 的既有读取解释，保留已有审计 JSON；不对旧值统一加减八小时。
- 无法确定来源的历史错误时间不自动补偿；已有记录和快照保持原值。
- createdAt、updatedAt、occurredAt 继续带时间偏移并表示正确 Instant；applicationDate 不偏移。
- UTC 与 Asia/Shanghai 两种 JVM 环境均验证请求前后的实际 Instant 窗口，不能仅检查 +08:00 后缀。
- 测试旧 TIMESTAMP/审计 JSON 不被改写或重新解释，测试连接池后续物理连接也使用正确时区。

## B03：VLAN 输入

- POST/PUT 的 managementVlan、businessVlan 接受整数 1–4094，null/缺省维持可空。
- JSON 浮点 token 全部拒绝，包括 1.9、4094.9、1.0、指数形式；不得截断后校验。
- 返回 HTTP 400、code=VALIDATION_ERROR，保持错误 Envelope。
- 非法新增不写台账或审计；非法更新不改变字段、version、updatedAt 或审计数量。
- 使用作用范围明确的 Jackson 配置/反序列化方案，不改变其他既有字段语义。

## B01：上传边界

路径保持 POST /api/v1/ledgers/import，multipart 字段 file，保持匿名可用。

| 输入 | HTTP | code |
| --- | --- | --- |
| 缺 file、空文件、非 xlsx、损坏文件、业务数据非法 | 400 | VALIDATION_ERROR |
| 文件超过 2,097,152 字节 | 413 | UPLOAD_TOO_LARGE |
| multipart 整体请求超过 3,145,728 字节 | 413 | UPLOAD_TOO_LARGE |
| 恰 2,097,152 字节且可解析、业务合法的 xlsx | 200 | OK |

- max-file-size 保持 2MB，max-request-size 改为 3MB，给请求边界和头部预留空间。
- 业务层大小兜底与容器限制返回一致 413/code。
- 错误为 {code,message,data:null,traceId}，中文可理解提示，traceId 非空；成功保持 importedCount 响应。
- 联调补充：大请求拒绝响应经默认 Vite 代理也必须完整可读。413 的 UTF-8 JSON 响应应提供准确 Content-Length，避免上游 Connection: close 与分块转发组合导致客户端得到完整 JSON 却无法读到响应结束；不能在前端吞掉传输错误冒充成功。
- 必须通过真实嵌入式 Servlet HTTP 验证解析器大小限制，不能仅用 MockMultipartFile。
- 恰 2MiB 用真实有效 xlsx，不能用损坏的任意字节替代。
- 每项失败均无台账/审计写入；后续行非法/重复也使整文件台账与审计回滚。

## 验证与回滚

- 后端 Maven Wrapper verify，分别传 -DargLine=-Duser.timezone=UTC 和 -DargLine=-Duser.timezone=Asia/Shanghai，确认测试实际运行。
- 前端 typecheck、lint、test、build；主 Agent 做隔离数据库真实 HTTP 联调。
- MySQL 与真实第二台设备若环境不可用，明确记录未验证范围及复现方式，不将 H2/Mock 成功当作替代。
- 部署需重启后端/重建连接池；无数据库迁移或数据回滚。代码回退会恢复原缺陷。
