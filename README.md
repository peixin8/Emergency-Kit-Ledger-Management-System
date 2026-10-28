# 应急宝设备共享台账 V2

Vue 3 前端和 Spring Boot 后端共用一份数据库台账。打开网页即可查询和编辑，无需登录；每次创建、编辑、软删除都保留历史快照。支持 `.xlsx` 导入和导出，供离线交换文件。

## 运行环境

- JDK 17、Node.js 20.19+、PowerShell。
- 后端自带 Maven Wrapper，前端用 npm 锁文件。
- 正式多人使用建议 MySQL 8；`dev` 配置使用本机文件型 H2，适合体验与联调。局域网浏览器访问的是运行机器上的同一个后端和数据库文件，不要在多台机器上同时直接打开该 H2 文件。

## 本机与局域网启动

在两个终端分别执行：

```powershell
cd backend
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev'
```

```powershell
cd frontend
npm ci
npm run dev
```

本机打开 `http://127.0.0.1:5173/`。同一局域网的其他设备打开 `http://<运行机器的内网 IP>:5173/`，即可操作同一份台账。运行机器必须保持开机且前后端进程持续运行；如无法从其他设备连接，检查本机防火墙是否允许局域网访问 5173 和 8080 端口。匿名共享模式允许能访问该地址的人修改台账，因此仅在受信任的内网使用，不向公网开放。

开发数据库位于 `backend/data/emergency-ledger-dev.mv.db`，已被 Git 忽略。切换至 MySQL 前，另行备份及迁移现有 H2 数据，不能只修改连接地址就认为历史数据已转移。

## MySQL 配置

`compose.yaml` 可启动本地 MySQL；先将 `.env.example` 复制为 `.env` 并设置 `DB_PASSWORD`。后端以默认配置启动时，设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 指向自己的 MySQL 数据库。Flyway 在空库上执行 V1 建表；现有迁移文件不应改写。前端仍运行 `npm run dev`，通过 Vite 将 `/api` 转发给本机后端。

## 使用方式

- 首页显示当前台账。查询、增加、编辑和软删除会写入服务端数据库；并发编辑使用版本号检测冲突。
- 台账目标地址可在地图上手动选点或填写 WGS84 经纬度。地址搜索使用高德地图 Web 服务：先按[高德官方指引](https://lbs.amap.com/api/webservice/create-project-and-key)创建应用，添加服务平台为“Web 服务”的 Key，再在启动后端的终端配置环境变量 `AMAP_WEB_SERVICE_KEY`（不要写入 Git 或前端配置），重新启动后端。仅点击“搜索地址”时才会发送地址；搜索候选为估计位置，选择后应在地图核对和微调，保存台账才会写入数据库。未配置 Key 时仍可手动选点。
- 每行的“历史”展示创建、编辑、删除前后的完整快照。软删除后可在“已删除台账的 ID”处输入 ID 查历史。共享模式无法识别具体个人，历史统一标示“共享用户”。
- “导出 Excel”导出当前筛选出的未删除台账；可导入系统导出文件、旧版 22/24 列模板，以及包含“PTN端口（网元+槽口）”的现有台账。导入按中文表头识别列，仅新增未使用的台账编号；空白进出方向显示为“未填写”，故障原文保存在“设备状况说明”，带 `W` 的功耗按瓦数读取。导入失败会提示行列且整份回滚，不覆盖已有记录。文件限制为 `.xlsx`、2 MB、1000 条数据；导出上限为 10000 条。
- 首版字段包括 ID、原始地址、目标地址、申请方式、联系人、设备状况、机房、纤芯、制式、BBU/RRU 型号和功耗、网管及业务 IP/VLAN，并补充申请日期、方向、序列号、资产归属、备注等。

## 验证

```powershell
cd backend
.\mvnw.cmd verify
```

```powershell
cd frontend
npm run typecheck
npm run lint
npm run test
npm run build
```

服务运行后可执行 `./scripts/smoke.ps1` 验证真实接口。脚本会创建一条 `SMOKE-` 测试台账，验证查询、修改和历史后软删除该记录；审计留痕仍保留。

设计与接口见 [V2 规格](docs/superpowers/specs/2026-10-27-shared-ledger-v2.md)。MySQL 实例和真实第二台设备需要在对应环境单独联调；本机 H2 测试不能代替这两项验证。
