# 应急宝设备台账 V1 设计

## 目标与范围

交付可本地启动的 Vue 3 前端、Spring Boot 后端和 MySQL 数据库迁移。V1 管理每次应急宝设备进出或迁移申请的一条台账：列表搜索、查看、新建、修改、软删除。用户列出的全部字段必须可录入、显示及保存。

“原始地址／目标地址”解释为本次设备移动的起点和终点；“进出申请方式”解释为线上、纸质、电话、其他；“应急宝设备状况”使用良好、维修中、故障、退役。方向另设入、出、调拨。V1 不引入审批流、库存扣减或外部网管连接。

API 枚举值：申请方式 `ONLINE/PAPER/PHONE/OTHER`，设备状况 `GOOD/REPAIR/FAULT/RETIRED`，方向 `IN/OUT/TRANSFER`。前端显示中文；服务端存储代码值。

## 数据字段

`id` 为自增 Long，API 传十进制字符串。`ledgerCode` 唯一台账编号；`originalAddress`、`destinationAddress`、`applicationMethod`、`applicantContact`、`deviceCondition`、`direction`、`applicationDate` 必填。其余字段为 `applicantPhone`、`accessRoom`、`fiberCore`、`networkStandard`、`bbuModel`、`rruModel`、`bbuRruPowerWatts`、`managementIp`、`managementVlan`、`businessIp`、`businessVlan`、`deviceSerialNo`、`assetOwner`、`remarks`、`createdAt`、`updatedAt`、`version`。功耗是瓦数十进制字符串，两位小数；VLAN 为 1–4094；IP 支持 IPv4/IPv6。地址不可相同，功耗不得为负。

`applicationDate` 采用 `YYYY-MM-DD`；`createdAt`、`updatedAt` 返回带 `+08:00` 偏移的 ISO 8601 字符串，按 Asia/Shanghai 时区解释与展示。数据库连接按同一时区配置。

## 契约与安全

`/api/v1/auth/login`：POST 用户名/密码登录，返回访问令牌和当前用户 `{id,username,role,organizationId}`。`/api/v1/ledgers`：GET 列表（page 从 1，pageSize 默认 20、最大 100；keyword、status、direction 可选），POST 新建；`/api/v1/ledgers/{id}`：GET 详情、PUT 修改、DELETE 软删除。更新提交 `version`，冲突返回 409。列表返回 `{code:"OK",message:"成功",data:{records,total,page,pageSize},traceId}`，详情及修改同一响应外壳。400 参数错误、401 未认证、403 无权限、404 不存在、409 冲突、500 服务错误。`status` 筛选对应 `deviceCondition`。

建 `organization`、`app_user`、`emergency_device_ledger`、`ledger_audit` 四张表。用户角色为 `ADMIN`、`EDITOR`、`VIEWER`：全部可读本组织台账，前两者可新建与修改，仅 ADMIN 可软删。服务端从令牌确定用户和组织，不信任请求自填组织；跨组织记录返回 404。管理员由环境变量在启动时引导创建，密码只存散列；角色和组织扩展可通过数据库管理，V1 不提供用户管理界面。新增、修改、删除与审计在同一事务中。

## 架构和运行

后端 Java 17、Spring Boot 3.4 系列、Maven Wrapper；MySQL 8 为交付数据库，Flyway 管理表结构。H2 仅用于隔离的本地验证。前端 Vue 3、TypeScript、Vite、Vue Router、Pinia、Element Plus。API 地址由环境配置和开发代理提供。敏感配置不进入仓库。

## 验收

前后端可独立启动；MySQL 空库通过迁移生成表；管理员可登录并完成一条台账的新建、查询、修改、搜索与软删除；字段验证、重复编号、版本冲突、未授权与跨组织访问有明确响应。后端测试、前端类型检查和生产构建通过，README 记录真实验证及环境依赖。

## 待后续确认

申请方式的实际选项、审批节点、联系方式格式、设备数量/库存口径、多组织权限、网管对接及历史数据导入。本版采用上述默认值，后续可迁移调整。
