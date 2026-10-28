# Excel 导入真实变化识别 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 同内容重导显示无变化，只有业务字段不同才实际覆盖并写历史。

**Architecture:** 后端在共享锁与导入事务内比较规范化业务字段；前端显示新增、覆盖、无变化数量。

**Tech Stack:** Spring Boot、JDBC、JUnit、Vue 3、TypeScript、Vitest。

**Spec:** `docs/superpowers/specs/2026-10-28-import-real-change-design.md`

## Global Constraints

- 后端/前端业务修改分别由对应子 Agent 完成。
- 本题测试通过后单独提交；再处理自动定位，最终全套测试。
- 不在运行中的共享数据库重导用户文件作为测试。

## Review Focus

- 全字段同值不更新版本、时间或历史；有差异完整覆盖。
- 空白清除旧值算变化；功耗和经纬度等价格式不算变化。
- 混合新增、覆盖、无变化计数准确，`importedCount` 保持有效行总数。
- 文件内重复、软删冲突、后续行失败仍整批回滚。
- 前端不再把“无变化”称作“覆盖”。

---

### Task 1: 后端变化识别

**Files:** `backend/src/main/java/com/emergency/ledger/LedgerService.java`、`LedgerRepository.java`、`LedgerExcelService.java`；`backend/src/test/java/com/emergency/ledger/LedgerExcelTest.java`。

**Interfaces:** `ImportCounts(importedCount,createdCount,updatedCount,unchangedCount)`。

- [ ] 先写会失败的隔离集成测试，覆盖同内容重导、版本/历史不变及混合计数。
- [ ] 写差异字段、空值清除、数值等价边界测试并确认旧实现失败。
- [ ] 实现规范化字段比较及三类结果计数。
- [ ] 运行后端测试。

### Task 2: 前端真实结果提示

**Files:** `frontend/src/api/ledger.ts`、`frontend/src/views/LedgerListView.vue` 及相关测试。

**Interfaces:** 消费 `unchangedCount`，显示三类数量。

- [ ] 先写会失败的成功提示测试。
- [ ] 修改响应类型及反馈文案。
- [ ] 运行前端测试、类型检查、lint、构建。

### Task 3: 审查验证与提交

- [ ] Reviewer 审查边界与测试，修复 P0/P1/P2 后复审。
- [ ] 主 Agent 验证、提交本题的独立 Git commit。
