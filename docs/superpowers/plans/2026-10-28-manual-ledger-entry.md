# 手动新增与不同 ID 导入 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在导入工具栏提供明显的手动新增入口，并说明 Excel 新编号生成不同系统 ID。

**Architecture:** 复用已有 Vue 表单抽屉、创建接口及刷新流程，不改后端。

**Tech Stack:** Vue 3、TypeScript、Element Plus、Vitest。

**Spec:** `docs/superpowers/specs/2026-10-28-manual-ledger-entry-design.md`

## Global Constraints

- 前端业务代码由 Frontend 子 Agent 修改。
- 本题测试通过后单独提交；随后全套测试供用户验收。

## Review Focus

- 工具栏新增按钮可见、可键盘操作，与右上角入口使用同一空白表单。
- 创建提交调用既有 POST；成功刷新列表和地图，失败保留输入。
- Excel 不允许指定系统主键；界面明确新编号/同编号与系统 ID 关系。
- 不影响问题一的覆盖导入与问题二的行操作。

---

### Task 1: 新增入口和说明

**Files:** `frontend/src/views/LedgerListView.vue`、`frontend/src/views/LedgerListView.test.ts`，如需 `frontend/src/style.css`。

**Interfaces:** 复用 `openCreate()`、`save()`、`createLedger()`。

- [ ] 先写会失败的测试，覆盖入口可见、空白表单、成功创建/失败保留、导入 ID 说明。
- [ ] 实现工具栏按钮和必要布局/文案。
- [ ] 运行前端测试、类型检查、lint、构建。

### Task 2: 审查和提交

- [ ] Reviewer 独立审查，修复 P0/P1/P2 并复审。
- [ ] 主 Agent 复核与测试后创建问题三独立提交。
