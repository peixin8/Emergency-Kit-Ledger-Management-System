# 共享台账 V2 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 无登录共享台账、Excel 导入导出和可查看的修改历史。

**Architecture:** 当前台账仍为主表，既有审计快照作为历史。后端提供匿名共享身份及 Excel 接口；前端去掉登录并增加历史和文件操作。

**Tech Stack:** Java 17、Spring Boot、Flyway、H2/MySQL、Vue 3、TypeScript、Element Plus。

**Spec:** `docs/superpowers/specs/2026-10-27-shared-ledger-v2.md`

## Global Constraints

- 保留现有数据库和 V1 迁移，不删除记录。
- 标识符用字符串，功耗用十进制字符串。
- 文件导入全文件事务提交；错误需含行号。
- 旧审计记录与软删除历史可查询。
- 旧版多组织台账全部可见，修改时保留每条记录原组织归属。

## Review Focus

- 匿名 API 不应因缺少 `CurrentUser` 抛空指针。
- 导入重复编号和非法行时不得部分提交。
- 软删除后历史仍能找到。
- Excel 大文件需明确拒绝，不应耗尽内存。
- 代理与局域网监听应能让另一设备访问同一数据。

---

### Task 1: 后端共享访问和历史

**Files:** `backend/src/main/java/com/emergency/ledger/*`, `backend/src/test/java/com/emergency/ledger/*`

- [ ] 写未登录 CRUD 与审计历史的失败测试。
- [ ] 实现共享服务端身份和历史分页 API。
- [ ] 验证现有数据、删除后历史和异常分页。

### Task 2: 后端 Excel

**Files:** `backend/pom.xml`, `backend/src/main/java/com/emergency/ledger/*`, `backend/src/test/java/com/emergency/ledger/*`

- [ ] 写导入导出、重复/错误行回滚的失败测试。
- [ ] 实现 xlsx 导出及追加导入，约束大小和行数。
- [ ] 执行 `mvnw.cmd verify`。

### Task 3: 前端免登录、历史和文件操作

**Files:** `frontend/src/**/*`, `frontend/vite.config.ts`, `frontend/package.json`

- [ ] 写路由、历史/文件交互的失败测试。
- [ ] 移除登录 UI 与令牌依赖，增加历史时间线及 Excel 操作。
- [ ] 执行类型检查、lint、测试和构建。

### Task 4: 集成和运行

**Files:** `README.md`, `AGENTS.md`, `scripts/*`（按需）

- [ ] 更新运行说明与协作规则。
- [ ] 重启前后端，验证无登录列表、历史、Excel 和局域网访问。
- [ ] 汇总测试结果与限制。
