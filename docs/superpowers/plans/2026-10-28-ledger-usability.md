# 共享台账易用性改进 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现用户已要求实施的项目审查改进，减少误操作并提高查询、导入和维护效率。

**Architecture:** 现有 Spring Boot/JDBC 和 Vue 3 增量实现；服务端数据库锁与无缓存指纹保护导入确认，前端组件分担预览和历史等职责。三个明确目录归属的任务，稳定契约后前后端按 AGENTS.md 并行，主 Agent 维护脚本、公共文档和联调。

**Tech Stack:** 沿用 Java 17、Spring Boot 3.4.13、Vue 3、TypeScript、Element Plus 和现有锁文件。

**Spec:** `docs/superpowers/specs/2026-10-28-ledger-usability-design.md`

## Global Constraints

- 无需登录，固定共享身份；不新增库存、审批、恢复状态机。
- 只在当前代码增量实现，保留已有未提交的导入重复目标修复。
- 不改已执行迁移，不升级依赖，不提交、推送或部署，不写原有业务库。
- Long/版本为字符串，功耗/坐标保持约定精度，时间 Asia/Shanghai。
- Frontend 仅写 frontend/；Backend 仅写 backend/；主 Agent 写根配置、scripts/ 和公共 docs/。
- 用户要求直接实施，沿用已给出的审查方向；不增加额外审批停顿。

## Review Focus

- 预览与确认间新增/编辑/删除及文件替换必须拒绝过期预览，不能检查后解锁再写。
- 选错文件、取消和断网不应触发自动重复写入；结果未知应明确核对。
- 草稿保留、最新版本加载和后台刷新不得引起静默覆盖。
- 空筛选、日期边界及已删除数据不能误计入活跃地图和导出。
- 工具不得复制正在使用的 H2、覆盖备份、误停原有进程或暴露连接凭据。

## Task 1 Backend

**Files:** LedgerController/Service/Repository/ExcelService 及职责清晰的新类，相关 backend/src/test 文件。

**Interfaces:** 严格按 Spec 新增 /import/preview、/import/confirm、/deleted、/export-batches；扩展查询 locationStatus；DELETE version 必填；保留原导入导出。

- [x] 先写失败测试：过期删除409且保留B的新值与审计；缺失version400。
- [x] 实现版本删除及共享锁；更新受影响的旧测试调用。
- [x] 先写预览无写入、差异清空、原文件替换/目标改变确认409全回滚测试，再实现指纹预览确认。
- [x] 先写已删除检索日期/分页、扩展关键词/待定位地图筛选、分卷与原生日期用例，再实现。
- [x] 用 Maven Wrapper verify 执行全套，报告具体数量和未测环境。

## Task 2 Frontend

**Files:** api/types/views/components/layouts/style 及相关 frontend 测试；可拆 ImportPreview、DeletedLedgers 等组件。

**Interfaces:** 消费 Task1 的确切 JSON 契约，前端接口类型不猜字段；旧方向/状况列表筛选保持移除。

- [x] 先写保存锁定、关闭确认、版本冲突保留草稿和删除version测试，再实现。
- [x] 先写预览取消不确认、确认使用原File/指纹、过期提示测试，再实现导入预览组件。
- [x] 实现已删除检索与历史、扩展搜索/待定位、统一刷新和时间、历史变化摘要并补必要回归。
- [x] 地图折叠、台账单位、技术信息折叠、导航与导入说明简化。
- [x] 类型检查、lint、Vitest和生产构建；报告相关数量与接口依赖。

## Task 3 维护及集成 主 Agent

**Files:** scripts/start.ps1、stop.ps1、status.ps1、backup.ps1、公共 helpers 和测试、README、docs/operations/。

- [x] 编写有实际风险断言的脚本用例，验证不存在输入、备份不覆盖、停止不误杀和端口冲突。
- [x] 实现本地可追踪启停状态、离线 H2 备份；文档明确 MySQL 和完整恢复演练方法。
- [x] 更新 smoke.ps1 DELETE version，标记旧规格中已被替代的导入条款。
- [x] 独立 Reviewer 审查每个目录任务和跨模块契约，P0/P1/P2交原Agent修复复审，最多三轮。
- [x] 启动当前构建的独立端口和临时H2，真实HTTP验证删除、导入预览确认、已删除查询及ZIP；浏览器验证核心交互和布局。
- [x] 记录最终验证与限制，结束临时服务，交付未提交改动。
