# 0.0.4 回顾 —— 架构文档

> 对应需求：`docs/requirements/0.0.4-review.md`（FR-004-1 ~ FR-004-5，D-11 ~ D-13、D-47 ~ D-49）
> 详细设计：`review-design.md`；测试用例：`review-testcases.md`；原型图：`docs/design/mockups/review-0.0.4/`

## 1. 定位与总原则

回顾是**纯只读聚合层**：零表结构变更、零字典变更、零写入路径，全部数据来自既有表（`sgj_item`、`sgj_note`、经 `sgj_item_place` 补全的地点字段）按自然年聚合。架构上遵循三条：

1. **不碰既有查询**——新增独立的 `SgjReviewMapper`，不改 `SgjDashboardMapper` / `AppTravelController` 的任何一行，回顾页出问题不影响首页 / 时间线 / 足迹页；
2. **口径逐字对齐**——新 mapper 的 `where` 前缀（`del_flag='0'` + `create_by` + `status='DONE'`）与 `selectTimeline` / `selectItemTypeStats` 完全一致，防止「回顾数字与首页对不上」；
3. **可见性复用**——`AppScopeHelper.resolveCreateBy()` 是唯一的范围解析入口，回顾接口不标 `@Anonymous`（匿名 401），登录态聚合范围与其他前台接口同源。

## 2. 数据流

```
/review?year=2024（Vue 页面，手绘皮肤）
  │  api/front/review.ts → getReviewYear(year)
  ▼
GET /app/review/year?year=2024        （登录态，无 @Anonymous）
  │  AppReviewController（shiguangji-admin）
  │  appScopeHelper.resolveCreateBy() 解析范围
  ▼
IAppReviewService / AppReviewServiceImpl（shiguangji-business）
  ▼
SgjReviewMapper（新）+ SgjReviewMapper.xml
  ├── selectReviewYears(createBy)            年份列表（条目 finish_date ∪ 笔记 create_time）
  ├── selectYearSummary(year, createBy)      各类型 DONE 数 + 笔记数
  ├── selectYearPlaces(year, createBy)       地点全量字段（含 province/country/坐标/评分/短评/标签/封面）
  ├── selectYearTopRated(year, type, n, createBy)   评分榜（MOVIE / BOOK 各一次）
  └── selectYearTagStats(year, createBy)     标签原样拉回，Java 侧拆分聚合
  ▼
一次请求返回整年数据（个人数据量级千级条目，无需分页）
```

**为什么一个接口而不是五个**：回顾页是「一屏看全」的聚合面，五个区块同源同年，拆成五个接口只会带来五次往返与口径竞态；数据量在个人量级（千级条目、地点数十级）一次下发无压力。

**为什么标签在 Java 拆而不在 SQL 拆**：`tags` 是 `varchar(500)` 英文逗号分隔文本，MySQL 侧拆分要么用 `SUBSTRING_INDEX` 递归（脏数据易错），要么改表（本期零变更）。原样拉回后在 Java 用既有的拆分口径处理，可单测钉住空串 / 空格清洗。

## 3. 模块与文件清单

### 后端（shiguangji-server）

| 文件 | 职责 |
| --- | --- |
| `shiguangji-admin/.../controller/business/AppReviewController.java` | `GET /app/review/year`，无 `@Anonymous`，`year` 参数校验（非法年份 400） |
| `shiguangji-business/.../service/IAppReviewService.java` | 服务接口 |
| `shiguangji-business/.../service/impl/AppReviewServiceImpl.java` | 聚合编排 + 标签拆分 + ` provinces`/`country` 分组键 |
| `shiguangji-business/.../mapper/SgjReviewMapper.java` | 数据层接口 |
| `shiguangji-business/src/main/resources/mapper/business/SgjReviewMapper.xml` | 五条查询 |
| `shiguangji-admin/src/test/java/.../AppReviewApiSmokeTest.java` | 冒烟测试（登录态 200 / 匿名 401 / 年份过滤） |

地点字段（`province` / `country` / `latitude` / `longitude`）由 `SgjItem` 域对象既有虚拟字段承载，`SgjItemMapper` 既有的 place 联查口径在 `SgjReviewMapper.xml` 里照抄（`left join sgj_item_place`），不新写关联逻辑。

### 前端（shiguangji-web）

| 文件 | 职责 |
| --- | --- |
| `src/api/front/review.ts` | `getReviewYear(year)` |
| `src/types/api/front/review.ts` | `ReviewYearData` 等契约类型 |
| `src/views/front/review/index.vue` | 回顾页：报头 / 统计卡 / 足迹 / 评分榜 / 标签 / 空态 / 骨架 |
| `src/views/front/review/mapLayout.ts` | **足迹绘制规则的全部纯函数**（分档判定、等比投影、自动缩放、按屏幕距离并簇、聚合阶梯、标签侧、连线收边）——独立成模块以便单测 |
| `src/views/front/review/ReviewMap.vue` | 足迹块（手绘示意底 + 图钉 + 连线），消费 `mapLayout` 的输出 |
| `src/views/front/review/ReviewDetailModal.vue` | 弹层：点详情（手绘卡片）与组地图（高德裱框 + 懒加载）两态 |
| `src/views/front/review/__tests__/mapLayout.spec.ts` | 绘制规则单测 |
| `src/router/index.ts` | `/review` 路由（不进 `PUBLIC_ROUTES` 白名单 → 守卫天然拦截） |
| `src/views/front/home/index.vue` | hero 按钮排加回顾入口卡（登录态渲染） |

### 复用与不新建

| 复用点 | 说明 |
| --- | --- |
| `AppScopeHelper` | 可见范围唯一入口，不另写过滤 |
| `utils/map.ts#loadAMap()` | 高德 SDK 进程内单例懒加载，组地图首次打开才调用 |
| `views/front/plan/handdrawn.css` | 0.0.7 手绘皮肤单源（D-47），回顾页 `<style>` 不重复定义令牌 |
| `utils/publicRoutes.ts` | `/review` **不加**进白名单（私人内容，同 `/plan`） |
| 首页 `hero-map-entry` 形制 | 回顾入口卡直接复用该结构与样式类 |

## 4. 关键架构决策

| 决策 | 理由 |
| --- | --- |
| 一个 `GET /app/review/year` 返回整年 | 五区块同源同年；个人量级一次下发；避免多次往返的口径竞态 |
| 新建 `SgjReviewMapper` 而非扩 `SgjDashboardMapper` | 后台仪表盘与前台回顾是两个消费方，混在一个 mapper 里改动面互相牵连；新 mapper 可整文件回滚 |
| 足迹绘制规则放 `mapLayout.ts` 纯函数 | 分档 / 并簇 / 投影 / 标签侧 / 连线收边是可枚举的规则集，混在 `.vue` 里没法单测；原型图（mockup）中的 JS 逻辑原样迁移于此 |
| 前端不做服务端聚合（省/大区分组在前端算） | 地点全量已随接口下发（数十级），分组是展示逻辑不是数据口径；后端只保证 `province`/`country` 原样返回 |
| 高德只在组地图弹层出现 | D-49：首屏零地图依赖；`loadAMap()` 单例保证 SDK 只加载一次；失败时弹层内以清单兜底 |
| `/review` 不进白名单 | D-11 仅登录可见：守卫弹登录 + 接口 401 + 访客不渲染入口，三处对齐 `/plan` 先例 |

## 5. 风险与边界

- **`province` 为 null**（境外 / 未选省）：分组键在 Java / 前端两侧都显式处理（`country` 非空且非「中国」按国家；皆空归「未分类」），不得 NPE；
- **`finish_date` 为空的 `DONE` 条目**：所有年度查询显式 `finish_date is not null`，保证「总数 = 各年之和」；
- **SDK 加载失败**（弱网 / key 失效）：组地图弹层捕获 `loadAMap` 异常，展示「地图加载失败，以下是该组地点清单」的降级态，不白屏；
- **年份参数非法**（非数字 / 超界）：后端 400，前端守卫默认取年份列表第一项。
