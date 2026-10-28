# Excel 导入覆盖 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重新导入同编号台账时完整覆盖业务字段，并保留 ID 和审计历史。

**Architecture:** Excel 服务解析并在一个事务内逐行按台账编号查询，委托既有创建/更新服务写库与审计。后端返回新增及覆盖数量；前端展示结果。

**Tech Stack:** Spring Boot、JDBC、JUnit、Vue 3、TypeScript、Vitest。

**Spec:** `docs/superpowers/specs/2026-10-28-import-overwrite-design.md`

## Global Constraints

- 前后端业务代码分别由 Backend/Frontend 子 Agent 修改。
- 每题有效测试通过后单独 Git 提交；全部完成后跑全套测试。
- 用户的 Excel 和共享开发数据库不得用作测试夹具或被提交。

## Review Focus

- 空白值清除旧值；旧模板缺少的可选列也清除旧值。
- 保留系统 ID、创建时间和历史，增加 UPDATE 留痕。
- 文件内同编号重复或已软删除编号冲突时，整份回滚并定位行号。
- 新编号创建新 ID；已有编号修改版本后可继续正常编辑。
- 前端导入反馈能区分新增与覆盖，错误时不显示成功。

---

### Task 1: 后端覆盖导入

**Files:** `backend/src/main/java/com/emergency/ledger/LedgerExcelService.java`、`LedgerService.java`、`LedgerRepository.java`、`LedgerController.java`；测试 `backend/src/test/java/com/emergency/ledger/LedgerExcelTest.java`。

**Interfaces:** `POST /api/v1/ledgers/import` 返回 `{ importedCount, createdCount, updatedCount }`。

- [ ] 先写隔离集成测试覆盖同编号全字段覆盖、空值、审计和新编号创建；运行确认因功能缺失失败。
- [ ] 写重复编号、软删除冲突、事务回滚测试；运行确认失败。
- [ ] 实现最小导入 upsert，沿用现有写库校验、锁和审计。
- [ ] 运行后端测试并确认通过。

### Task 2: 前端反馈

**Files:** `frontend/src/api/ledger.ts`、`frontend/src/views/LedgerListView.vue`；相关 Vitest 测试。

**Interfaces:** 消费 Task 1 响应，显示新增与覆盖数量。

- [ ] 先写导入结果提示测试并确认失败。
- [ ] 修改响应类型、提示和导入说明。
- [ ] 运行前端测试、类型检查、lint、构建并确认通过。

### Task 3: 审查、验证、提交

- [ ] Reviewer 审查事务、软删除、空值和 UI 文案；修复 P0/P1/P2 并复审。
- [ ] 主 Agent 运行后端 verify、前端测试和构建，检查 diff。
- [ ] 提交问题一的独立 Git commit。
