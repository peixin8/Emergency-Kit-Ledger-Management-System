# 上游主分支整合与推送

用户授权完成 main 合并并推送 upstream/main（peixin8 原合作仓库）。本地基线491660b保留四项P2修复，现有MERGE_HEAD为b50eefd8b1e14fdfe0743b1c47576c3cf30961ff，包含上游六个提交。保留已有自动合并内容和双方提交，不abort/reset，不强推。

## Planner 整合契约

- 匿名共享、既有接口/Envelope、字符串ID与版本、十进制字符串功耗不变。
- 保留上游地图、地址搜索、聚焦导航、摘要列表和完整详情操作；保留PTN、故障说明、坐标、UNSPECIFIED方向、同址允许及多模板按列名导入。
- 保留四项P2：独立新增会话key；每个物理连接上海时区及LocalDate；两个VLAN局部拒绝浮点；缺文件400和超限413/UPLOAD_TOO_LARGE、准确UTF8 Content-Length。
- 文件上限2MiB、整体请求3MiB，保留Tomcat拒绝后的剩余请求处理。
- V1/V2/V3原样保留，仅隔离测试库验证，不修改历史或迁移生产数据库。

## 分工和验收

### Frontend（只修改frontend/）

- 合并LedgerListView.vue，保留上游show-location、地图及详情历史/删除/编辑按钮，加入formSession和表单key。
- LedgerListView.test.ts为add/add冲突；保留上游用例，将真实Element Plus会话回归独立放文件，隔离mock。
- 适配新增字段和详情编辑入口；校验失败用必填/功耗非法值，不恢复已取消的“同址禁止”。
- 保留取消、关闭、快速重开、保存成功、编辑切换、失败保留和提交保护回归；运行typecheck、lint、全部test和build。

### Backend（只修改backend/）

- 合并ApiErrors、LedgerExcelService、LedgerRepository、application.yml、LedgerExcelTest。
- ApiErrors唯一保留P2上传处理器，删除自动合并的重复MaxUploadSizeExceededException处理器。
- 保留上游全部多模板解析、新字段及坐标列/参数顺序；日期仍使用LocalDate。
- 保留地理编码及上海连接配置；test application.yml合并重复servlet节点为唯一2MiB/3MiB。
- LedgerMultipartLimitTest使用统一UPLOAD_TOO_LARGE；保留原P2与上游Excel/地图/地理编码回归。
- UTC与Asia/Shanghai JVM各执行Maven Wrapper verify。

### 主 Agent / Reviewer

- 主Agent备份原合并状态，协调不重叠目录；不代写业务代码。
- Reviewer只读核对三方整合、两处自动合并隐患、双方回归与真实验证证据。
- 主Agent对隔离最终服务验证CRUD/历史、Excel、VLAN、时间和代理413完整响应，不影响现有8080/5173服务。
- 仅暂存已确认文件，完成原merge commit；确认491660b和b50eefd均为祖先。
- 再fetch upstream，正常推送main:main并核对远端SHA；无写权限时准确报告，不强推或擅自更换目标。

## 环境记录

Git当前账号shenwenxiang8-cmd对合作仓库的push预检返回403，已通知用户开通写权限或切换有权限账号。此项不阻塞本地冲突解决与验证，但在权限恢复前不能宣称远程推送成功。
