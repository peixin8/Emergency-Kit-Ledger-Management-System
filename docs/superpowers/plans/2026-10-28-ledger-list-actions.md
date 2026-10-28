# 首页台账列表行操作 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 精简首页列表并提供每行编辑、删除按钮。

**Architecture:** 只改 Vue 列表页，复用已有详情/编辑抽屉及删除确认逻辑。

**Tech Stack:** Vue 3、TypeScript、Element Plus、Vitest。

**Spec:** `docs/superpowers/specs/2026-10-28-ledger-list-actions-design.md`

## Global Constraints

- 业务代码仅 Frontend 子 Agent 修改。
- 问题二测试通过后单独提交；问题三随后处理。

## Review Focus

- 两个字段只从首页摘要与筛选移除，详情和编辑仍可见。
- 编辑与删除准确对应当前行 ID。
- 取消不删除；成功刷新列表和地图；失败提示并保留记录。
- 查询和导出不再发送已删除的筛选参数。
- 表格操作按钮有可读文字，能够键盘操作。

---

### Task 1: 首页列表行操作

**Files:** `frontend/src/views/LedgerListView.vue`、`frontend/src/views/LedgerListView.test.ts`。

**Interfaces:** 复用 `openRecord(id,'edit')` 和 `remove(record)`。

- [ ] 先写会失败的测试，覆盖列和筛选移除、编辑、删除取消/成功/失败、请求参数。
- [ ] 实现最小页面调整。
- [ ] 运行前端测试、类型检查、lint、构建。

### Task 2: 审查和提交

- [ ] Reviewer 独立审查，修复 P0/P1/P2 后复审。
- [ ] 主 Agent 检查 diff 和运行测试，创建问题二独立提交。
