# 四项 P2 修复实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 修复四项已复现 P2，并通过回归、独立审查和集成验证。

**Architecture:** Frontend 修复新增会话生命周期；Backend 修复会话时区、JSON VLAN 绑定和 Servlet 上传异常。保留 V2 共享模式和旧数据解释。

**Tech Stack:** Java 17、Spring Boot、JDBC/Flyway、H2/MySQL、Vue 3、TypeScript、Element Plus、Vitest。

**Spec:** docs/superpowers/specs/2026-10-27-p2-regressions.md

## Global Constraints

- 保持匿名共享 V2、接口路径、字符串 ID/版本号、十进制字符串功耗、现有依赖版本及包管理器。
- 不扩展其他审查建议，不修改 V1，不重写已有台账或审计数据，不新增表结构迁移。
- Frontend 仅修改 frontend/，Backend 仅修改 backend/；主 Agent 维护公共文档；Reviewer 独立只读。不得自行提交、推送或发布。
- 先写并观察失败回归，再做最小修复；输出实际命令及结果。

## Review Focus

- 连续新增尤其关闭动画未结束时，字段与校验均重置；失败保存保留输入。
- UTC JVM 与新连接不能产生错标偏移的时间；旧审计快照不被重新解释。
- VLAN 的 JSON 浮点 token 包括 1.0 不得绕过整数校验。
- 上传必须经过真正 Servlet 解析；2MiB 合法文件成功，超限错误可读且无写入。
- 后续导入行失败时，前面已处理的台账和审计全部回滚。

---

### Task 1: Frontend 修复与回归

**Files:**
- Modify: frontend/src/views/LedgerListView.vue
- Modify if needed: frontend/src/components/LedgerForm.vue
- Create: frontend/src/views/LedgerListView.test.ts
- Modify test configuration only if needed: frontend/vite.config.ts

**Interfaces:** 消费既有 LedgerInput、emptyLedger()、CRUD API；不改变后端接口。

- [x] 读取 AGENTS.md、agents/frontend.toml 的 developer_instructions 与 Spec 的 F01。
- [x] 用真实 Element Plus 组件写回归：取消后重开（含校验）、保存后重开、快速重开、编辑/新增切换、编辑 A/B、失败保存输入保留。
- [x] 在未改源码时运行回归，确认原缺陷导致断言失败，记录 RED 证据。
- [x] 实现独立新增会话/可靠重置，保持提交保护及日期默认值。
- [x] 运行 npm run typecheck、npm run lint、npm test、npm run build 并自查 diff。
- [x] 输出文件、RED/GREEN 命令及结果、未测范围；由主 Agent 交只读 Reviewer 审查后处理反馈。

### Task 2: Backend 三项修复与回归

**Files:**
- Modify: backend/src/main/java/com/emergency/ledger/ApiErrors.java
- Modify: backend/src/main/java/com/emergency/ledger/LedgerExcelService.java
- Modify as needed: backend/src/main/java/com/emergency/ledger/LedgerRequest.java 或明确的 Jackson 配置
- Modify: backend/src/main/resources/application.yml、application-dev.yml、backend/src/test/resources/application.yml
- Modify: backend/src/test/java/com/emergency/ledger/LedgerExcelTest.java
- Create regression tests under: backend/src/test/java/com/emergency/ledger/

**Interfaces:** 保留 CRUD/Excel/历史接口；新增错误契约按 Spec 的 B01/B03；时间解释按 B05。

- [x] 读取 AGENTS.md、agents/backend.toml 的 developer_instructions 与 Spec 的 B05/B03/B01。
- [x] 写真实 HTTP 测试覆盖两种 JVM 时区的创建、更新、历史 Instant；旧数据不改；后续物理连接时区。
- [x] 写 POST/PUT 两 VLAN 浮点、范围与合法整数/null 回归，失败不改台账、版本、时间及审计。
- [x] 写 Servlet 真 HTTP 上传回归：缺 file、空 file、2MiB+1、请求>3MiB、恰2MiB合法 xlsx、正常导入及后续行失败回滚台账/审计。
- [x] 未改业务代码时运行新增用例，确认 RED 为原缺陷而非测试环境错误。
- [x] 按 Spec 做最小修复；更新原有超限 400 断言为 413/UPLOAD_TOO_LARGE。
- [x] 分别执行 .\mvnw.cmd "-DargLine=-Duser.timezone=UTC" verify 与 .\mvnw.cmd "-DargLine=-Duser.timezone=Asia/Shanghai" verify。
- [x] 自查文件、数据库兼容与旧数据影响，输出证据及 MySQL 未测限制，交独立 Reviewer 审查。

### Task 3: 主 Agent 文档、集成与独立终审

**Files:** README.md、docs/ 公共说明；不代写 frontend/backend 业务代码。

- [x] 更新 README 的上传错误、时间策略、回归执行说明与已知历史数据限制。
- [x] 每任务独立审查契约符合性和代码质量，问题退回原角色修复并复审，最多连续三轮。
- [x] 对最终版本执行前后端门禁、隔离数据库真实 HTTP CRUD/历史/Excel、四项失败路径回归。
- [x] 将完整 diff、契约、实现报告与验证结果交新 Reviewer 只读终审。
- [x] 记录审查结论、实际命令和未测项；保持未提交改动可供用户检查，不自行推送。
