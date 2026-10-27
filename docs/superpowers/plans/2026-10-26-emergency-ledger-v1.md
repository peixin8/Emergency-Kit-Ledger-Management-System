# 应急宝设备台账 V1 Implementation Plan

**Goal:** 交付设备进出申请台账的可运行前端、后端和 MySQL 数据库。

**Architecture:** Vue 3 前端调用 Spring Boot REST 接口；后端通过服务层管理权限、校验及乐观锁，并使用 Flyway 管理 MySQL 表。H2 只用于隔离验证。

**Tech Stack:** Java 17、Spring Boot 3.4、Maven、MySQL 8、Vue 3、TypeScript、Vite、Element Plus。

**Spec:** `docs/superpowers/specs/2026-10-26-emergency-ledger-v1-design.md`

## Global Constraints

- 遵循根目录 AGENTS.md 和四个 agents/*.toml 角色规则。
- API 路径、字段、枚举、分页和响应格式按 Spec，Long ID 传字符串。
- 前后端分别只写 backend/、frontend/，公共文档由主 Agent 负责。

## Review Focus

- 地址相同或 VLAN 越界应返回明确参数错误。
- 同编号重复创建、旧版本覆盖新版本应返回冲突。
- 软删除记录不可在正常列表和详情中出现。
- 未认证用户不能读取或修改台账，跨组织记录不可见。
- 前端金额/功耗、ID、日期显示不丢精度、不偏移。

## Task 1: 后端及数据库（Backend）

**Files:** `backend/pom.xml`、`backend/src/main/**`、`backend/src/test/**`、Maven Wrapper。

**Interfaces:** 实现 Spec 中 `/api/v1/auth/login` 及全部 `/api/v1/ledgers` 接口，JSON 外壳、角色与组织隔离及 MySQL Flyway 迁移。

- [ ] 先写后端行为测试并运行，确认因功能缺失失败。
- [ ] 实现数据模型、迁移、Repository、Service、Controller、认证和错误处理。
- [ ] 运行相关测试及 Maven verify，修复失败。

## Task 2: 前端（Frontend）

**Files:** `frontend/package.json`、`frontend/src/**`、Vite 和 TypeScript 配置。

**Interfaces:** 消费 Spec 的 JSON REST API，登录及台账列表、新增、详情、编辑、删除。

- [ ] 先写关键表单/列表行为测试并确认失败。
- [ ] 实现页面、类型化接口、状态管理和布局。
- [ ] 执行类型检查、测试和生产构建。

## Task 3: 审查与集成（Reviewer、主 Agent）

- [ ] Reviewer 审查契约、权限、验证、状态和失败路径，给出文件及行号。
- [ ] 对应 Agent 修复审查项并复审。
- [ ] 主 Agent 测试后端与前端联调、更新 README 和环境说明。
- [ ] 汇总已验证范围及未解决风险。
