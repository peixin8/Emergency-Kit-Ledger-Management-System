# 前端易用性改进实施记录

范围：Task 2，按 2026-10-28-ledger-usability-design.md 增量实现。仅修改 frontend/，未升级依赖，未提交、推送、部署或写原有业务库。

## 行为与接口

- 删除携带列表/详情所见字符串 version；冲突错误保留当前记录，不盲目重试。
- 保存期间 Element Plus 表单输入、地址查询/候选选择和地图选点全部锁定；服务失败保留输入。
- 未保存修改通过抽屉关闭按钮、Esc、遮罩、取消、打开另一条记录、新增和当前/已删除视图切换共用确认；浏览器离开提醒，卸载清理事件。
- VERSION_CONFLICT 保留并锁定草稿；显式读取最新记录，展示最新/草稿字段对照及完整详情。只有确认放弃草稿、重新编辑才载入新版本，不静默换版本覆盖。异步最新响应绑定记录 ID、表单会话及详情序列。
- ImportPreview 保留原 File；预览显示文件名、新增/覆盖数量、逐行中文字段变化/清空和缺失中文表头。取消不调用确认；确认传原 File + previewFingerprint；IMPORT_PREVIEW_STALE 要求重新预览。网络结果未知提示先刷新核对，不自动重试。
- DeletedLedgers 按编号、地址、删除日期检索并分页，点击字符串 ID 查看历史。移除手填系统 ID，不增加恢复。
- keyword 扩展搜索提示，待定位入口；列表、地图、标准导出、ZIP 分卷导出沿用相同已应用筛选。
- 统一刷新列表和地图，显示两者成功后的时间；窗口激活刷新当前台账。相同条件并发读取复用，写入成功强制新查询；序列保护旧响应，地图切筛选失败时不保留旧筛选点位。后台刷新不替换正在编辑的表单。
- 地图默认折叠，选择有坐标详情自动展开聚焦；统计单位统一为条台账。导航移除单页重复标签和单子项嵌套，详情技术记录信息与长导入说明折叠。
- 历史变化摘要补 PTN、设备状况说明、纬经度；空字符串/null/undefined 归一，技术版本/时间不混入业务差异。

接口依赖：DELETE /ledgers/{id}?version=；POST /ledgers/import/preview、/confirm；GET /ledgers/deleted、/export-batches；所有当前记录查询/地图/导出支持 locationStatus。DTO、中文缺列表头、业务 key 及错误码按已确认 Spec。

## 文件

新增 components/ImportPreview.vue、DeletedLedgers.vue 及对应测试；修改 api/ledger.ts、types/ledger.ts、types/history.ts、LedgerForm/LedgerDetail/LedgerHistory/LedgerLocationMap、views/LedgerListView.vue、layouts/MainLayout.vue、style.css 及受影响测试。

## 实际验证

在 frontend/ 使用本机 node，未安装或变更依赖：

- node node_modules/vue-tsc/bin/vue-tsc.js --noEmit：通过。
- node node_modules/eslint/bin/eslint.js .：通过。
- node node_modules/vitest/vitest.mjs run：13 个文件，73 个测试全部通过。
- node node_modules/vite/bin/vite.js build：通过，1471 modules transformed。Vite 提示主 JS chunk 993.37 kB 超过 500 kB 建议阈值；本轮未扩展到依赖拆包优化。

风险用例先验证失败再修复：删除 version、提交输入锁定、dirty 取消/离开、导入预览取消/指纹确认/过期、历史新增字段、冲突锁定/明确确认、旧最新值跨记录响应、视图切换保护、编辑基线切换、重复刷新复用、写入后的旧查询隔离、地图筛选失败旧点位隔离。

真实 Element Plus Drawer/Form 用例保留关闭过渡和真实 fetch 封装；已删除组件用例验证日期、分页、字符串 ID 历史入口。其余外部请求以 Mock 隔离，不等于真实后端或地图联调通过。

## 后续集成验收

独立 Reviewer、真实 HTTP/浏览器布局与核心交互由主 Agent 继续组织；本记录不把 Mock 结果记为联调结果。真实 MySQL、第二台局域网设备、高德 Key/外网地图依赖需分别记录实际环境限制。

## 交付前最终源码复验（2026-10-28 17:50，Asia/Shanghai）

17:39:12 对 LedgerForm.vue 和 LedgerForm.geocoding.test.ts 的修改仅清理文件末尾多余空行，发生在前一轮构建之后。因此在当前工作区源码重新依次执行 typecheck、eslint、全套 Vitest、Vite build，逐步检查退出码，全部为 0；13 个测试文件、73 个测试再次通过。新 dist/index.html 时间为 17:50:03，晚于两份源码 17:39:12，构建资源 hash 与上一轮一致。Vite 保留相同主 chunk 体积提示。未切换或恢复分支，未修改其他目录；主 Agent 可重新加载 preview:5175 验收本次产物。
