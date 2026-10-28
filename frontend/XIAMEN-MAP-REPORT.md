# 福建省·厦门市限定地图交付记录

日期：2026-10-28。根据用户最新纠正，**旧的“仅改变默认视野”方案已废弃**，本次以真实行政区域裁剪为准。

## 实现

- 打包离线 WGS84 厦门市行政边界：OSM relation 3242930，Polygon 外环 2501 顶点。原始响应 SHA256 与规格一致，来源和 ODbL 记录在 `src/data/README.md` 及 JSON 元数据中；运行时无边界查询。
- `src/maps/region.ts` 对边界做结构、坐标、闭环和退化检查；Polygon/MultiPolygon、洞、边界点判断与地图裁剪共用相同 geometry。
- `src/maps/regionViewport.ts` 将包含瓦片、标记和工具提示的 Leaflet 地理 pane 按 `path(evenodd, ...)` 裁剪；区域外保留不透明底色。首帧先隐藏该 pane，move/zoom/viewreset/resize 同步更新；无效区域、投影异常或不支持裁剪时留白并提示。
- 禁用 zoom/fade/marker 动画、惯性和触摸连续缩放，使用 `animate:false`；设置 maxBounds、maxBoundsViscosity=1、按容器尺寸计算的 minZoom、瓦片 noWrap。ResizeObserver 处理抽屉和宽窄屏变化并在卸载时清理。
- 市内选中定位真实坐标 17 级；点位刷新、重新传入相同坐标、resize 不重置已操作的视野。清空选中只清理标记。市外选中不绘制、不飞出厦门，提示“超出厦门显示范围”。
- “返回厦门全市”按钮仅恢复全市视图，type=button、停止冒泡，不触发 pick/open、不清空坐标。地图点选仅接受域内/边界点并保留七位小数字符串。
- 首页标题/ARIA、分布/选点 ARIA、表单提示明确“福建省·厦门市”。侧栏保留所有已定位记录和总数，市外记录明确标注且仍可打开。手填坐标和地址搜索保留原有规则。
- 保留可配置瓦片、原有 OSM 默认底图；边界 OSM/ODbL 署名独立添加，不受自定义底图 attribution 覆盖。

## 文件

1. `src/data/xiamen-boundary.json`、`src/data/README.md`
2. `src/maps/region.ts`、`src/maps/region.test.ts`、`src/maps/regionViewport.ts`
3. `src/components/MapCanvas.vue`、`src/components/MapCanvas.test.ts`
4. `src/components/LedgerLocationMap.vue`、`src/components/LedgerForm.vue`
5. `src/views/LedgerListView.test.ts`
6. 本报告

无接口、依赖、锁文件、后端、数据库或台账数据变更；未提交、推送或重启服务。首页仍默认折叠地图。

## 实际验证

使用现有 Node 与 node_modules，工作目录为 frontend，没有安装依赖。命令结果最终均实际退出 0：

| 命令 | 结果 |
| --- | --- |
| `node node_modules/vue-tsc/bin/vue-tsc.js --noEmit` | 通过 |
| `node node_modules/eslint/bin/eslint.js .` | 通过，0 错误 |
| `node node_modules/vitest/vitest.mjs run` | 最终复验 14 文件、92 测试通过，0 失败 |
| `node node_modules/vite/bin/vite.js build` | 1475 模块构建成功，dist 已更新；存在既有 >500 kB chunk 提示 |
| `git diff --check` | 通过；仅 Git 的 LF/CRLF 提示 |

TDD 证据：几何规则先观察 3 项断言失败再通过；MapCanvas 新需求先观察 9 项失败，随后真实 Leaflet 实例测试转绿。首次几何测试曾因新模块尚未创建而导入失败，该次不作为红色断言证据；补齐最小空实现后确认真实断言失败。首次绿色运行的安全留白文案断言早于 Vue nextTick，定位后等待真实 DOM 更新；类型检查发现测试 spy 类型、LatLngBounds 重载及模板空值收窄问题，分别修正而未跳过检查。

日志位于被忽略的 `coverage/xiamen-*.log`。当前测试覆盖市内/市外/边界、人工 MultiPolygon 和洞、无效边界、首帧隐藏/同步裁剪、外市不绘制但数据保留、七位精度、字符串 ID、纯文本提示、按钮不选点、抽屉尺寸、零尺寸恢复、投影失败、Observer 与监听清理，并保留表单、台账、历史等全部回归。

## 验证边界

Vitest 使用真实 Leaflet 与 Vue 组件，但 jsdom 不渲染像素且其 CSS.supports、SVG 能力及容器尺寸由测试环境提供；不能把通过等同于真实浏览器的视觉验收。主 Agent 将在现有 5174 试用页面刷新，检查真实边界轮廓、区域外留白、拖动/缩小限制、窄屏和详情/选点场景，并安排 Reviewer 独立复审。本报告不宣称这些浏览器操作已执行。

边界仅供应用显示，不用于法律定界；不支持 CSS path 裁剪的浏览器显示安全留白与提示。后端与数据库未涉及，本轮未重复执行后端测试或生产迁移。

## 独立审查修复轮 1：XM-MAP-R1 / P2

Reviewer 的真实 Leaflet 实验发现：精确包围盒作为 maxBounds 会把最西边界点的17级定位推到容器 x=0。实现者新增西/东/南/北四个实际极值点测试，均复现某一方向容器边距为0（红）；这会被容器边缘额外截断，与行政 clip 预期裁去边界外半圆不同。

修复仅把交互视窗限制改为 `bounds.pad(0.05)`，给聚焦和拖动保留适量空白余量；行政裁剪、点内判断、全市 fit 和动态 minZoom 仍使用原始 geometry/包围盒，不扩大可显示地域。四个极值点在800×350及320×260容器中保持17级，中心距四边均至少12像素；23项定向测试转绿。此轮只修改 regionViewport、相关测试和本报告。

修复后重新执行类型检查、全工程 lint、14文件92测试、生产构建和 `git diff --check`，全部实际退出0；dist已再次更新，构建仍仅既有大chunk提示。红/绿证据分别在 `coverage/xiamen-edge-red.log`、`coverage/xiamen-edge-green.log`。主 Agent 已反馈真实浏览器行政形状、区域外不显示地图以及最小缩放限制验收通过；修复后的独立复审与浏览器补验仍由主 Agent 协调，本报告不替其预先宣称通过。
