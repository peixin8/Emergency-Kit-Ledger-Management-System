# 福建省厦门市限定地图

用户明确纠正：“不是地图默认视野，而是只显示福建厦门的地图。”本规格取代本轮先前仅调整默认视野的方案；以真正限制地图呈现区域为验收标准。

## 显示与边界

- 首页、详情、表单共用MapCanvas，地图仅呈现厦门市区域内地理内容；区域外是不透明底色，不显示相邻城市、台湾等区域地图。不能仅以标题、fitBounds、minZoom或maxBounds代替裁剪。
- 使用离线WGS84厦门边界，绘制和点内判断共用相同数据，支持Polygon/MultiPolygon及洞，边界线上算区域内。
- 数据为OpenStreetMap relation 3242930，Nominatim返回名称“厦门市, 福建省, 中国”，2026-10-28通过官方API取得未简化polygon_geojson（polygon_threshold=0）；原始文件SHA256 `21DFB8BBEE003F03AF70972DA3454F4429F43CF1BF79D408B251F592EB3613DB`，2501个顶点。离线资源需记录来源及ODbL署名，不用于法律定界。
- 来源 `https://nominatim.openstreetmap.org/search?city=Xiamen&state=Fujian&country=China&format=geojson&polygon_geojson=1&polygon_threshold=0&limit=1`；接口文档 `https://nominatim.org/release-docs/latest/api/Search/`。根Agent已下载原始响应到被忽略的 `.superpowers/xiamen-map/xiamen-boundary-source.geojson`，Frontend从该文件提取受校验的geometry及必要元数据，不新增运行时边界请求。

## 交互

- 初始显示全市边界，按包围盒设置maxBounds、maxBoundsViscosity=1、动态minZoom及瓦片noWrap；保留缩放/署名。禁止缩小到省外或全球，拖动不能飞离厦门；不以矩形硬裁剪代替行政区域形状。
- 缩放、移动、resize和首帧均须确保区域外不露底图；可禁用zoom/fade/marker动画和惯性以保证遮罩一致，程序定位animate=false。禁止只在moveend后更新遮罩。
- 返回厦门全市只改变视图，不修改输入、不触发pick/open。ResizeObserver处理可见尺寸、窄屏、抽屉变化，清理监听和observer；区域无效/无法裁剪时安全留白并提示。
- 地图只绘制范围内点位；范围内选中仍定位真实坐标17级，市域边缘点不能被错误截断。范围外选中不飞出厦门、不绘制市外标记，显示“超出厦门显示范围”。
- 台账数据、后台查询和数量不按地域删除或筛除；旁侧记录仍可查看，外市记录明确标注。原有坐标不改写、不清空，不迁移数据库。
- 仅地图点击选点增加边界检查，区域外/洞内点击不产生pick；手工坐标和地址搜索不新增后端地域限制，保留原校验与存量记录。表单的范围提示应与此一致。
- 标题/ARIA明确“福建省·厦门市”；保留OpenStreetMap底图及环境配置、WGS84存储、七位小数和导航转换，无新Key/依赖或API变化。

## 分工与验证

Planner只读规划已完成；Frontend仅写frontend/的边界资源、地图呈现、提示和必要测试；主Agent维护此规格及浏览器集成；Reviewer独立只读审查。无需后端更改或重新运行无关后端测试。

先写失败测试：厦门内/外/边界、人工多面与洞、外市不飞出/不绘制但数据保留、外区点击不选点、精度、裁剪首帧/移动缩放/resize、监听清理。保留现有台账、表单、历史等回归。执行前端typecheck/lint/Vitest全套/build，最后刷新5174真实页面检查全市形状、区域外留白、拖动/缩小限制及窄屏；更新可供用户查看的截图，独立复审P0/P1/P2清零后交付。
