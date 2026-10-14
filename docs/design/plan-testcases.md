# 计划（0.0.3）—— 测试用例

> 配套文档：[需求](../requirements/0.0.3-plan.md) · [架构](plan-architecture.md) · [详细设计](plan-design.md) · [设计图](mockups/plan-0.0.3/index.html)
> 编号接 `external-images-testcases.md`（止于 T-71）。
> 后端用例跑在既有 smoke 体系里：`dev` profile、MySQL `localhost:13306`、Redis `localhost:16379`，命令 `mvn -pl shiguangji-admin -am test`；前端 `npx vitest run`。

## 1. 自动化用例（agent 维护）

### 1.1 后端

| 编号 | 用例 | 断言要点 | 落点 |
| --- | --- | --- | --- |
| T-72 | 状态白名单放行 `DOING` | `PUT /app/item/{id}` body `{status:'DOING'}` → code 200；回读该条目 `status == 'DOING'`；`status:'PAUSED'` 仍被拒且 msg 含「仅支持WANT/DOING/DONE」；新增条目缺省仍是 `WANT` | `AppPlanApiSmokeTest` |
| T-73 | `buildSummary` 三态自洽 | 造 1 `WANT` + 1 `DOING` + 1 `DONE` 后：`wishTotal==1`、`doingTotal==1`、`doneTotal==1`、`itemTotal==3`，且 **`itemTotal == doneTotal + wishTotal + doingTotal`**；`doing`/`wish`/`done` 三个按类型的 map 之和各自等于对应 total（`DOING` 不得再出现在 `wish` 里） | `AppPlanApiSmokeTest` |
| T-74 | 心愿单单一数据源与分页边界 | `/app/home/index` **不再**下发 `wishlist`；心愿单走 plan/list：`page1 ∪ page2` 覆盖全部夹具且两页之间无重复（结论 92）；排序与状态同计划页 | 同上 |
| T-75 | 计划接口集合 / 排序 / 分页 | `GET /app/item/plan/list`：只含 `WANT` ∪ `DOING`（无 `DONE`）；无日期的排在最后；`start_date` 升序；`total` 等于符合条件的总数而非当前页长度；`pageSize=2` 翻页无重复无遗漏 | 同上 |
| T-76 | 计划接口的可见范围 | 匿名 → 只见 `publicOwner` 的数据；登录用户 → 只见自己的；`?pageNum=0` 等异常入参不 500（照既有分页接口的口径） | 同上 |
| T-77 | 计划接口访客脱敏 | 匿名请求返回的条目 `comment` 为空（与 `/app/item/list` 的 `maskItemsForGuest` 口径一致），`title` / `status` / `startDate` 保留 | 同上 |
| T-78 | 字典值域与排序 | `sgj_movie_status` / `sgj_book_status` 各 3 条且 `dict_sort` 为 `1/2/3` 对应 `WANT/DOING/DONE`；`sgj_place_status` **仍只有 2 条**（`DOING` 不得出现在地点）；三套字典无重复 `dict_value` | `SgjStatusDictTest`（纯 SQL 断言，或并入 smoke） |
| T-79 | 时间线仍只含 `DONE`（回归） | 存在 `DOING` 条目时：`/app/home/index` 的 `timeline` 与 `/app/home/timeline` 的 `rows` 都不含它；`total` 与按同一条件独立统计的行数相等 | `AppPlanApiSmokeTest` |

### 1.2 前端

| 编号 | 用例 | 断言要点 | 落点 |
| --- | --- | --- | --- |
| T-80 | `groupPlanItems` 分组边界 | 以固定 `today = '2026-10-14'`（周三）为例：`10-13` → 逾期；`10-14` → 本周（今天）；`10-18`（本周日）→ 本周；`10-19`（下周一）→ 以后；无日期 → 未排期；**组内顺序等于入参顺序**（不重排）；空入参返回四个空组 | `utils/__tests__/plan.spec.ts` |
| T-81 | `planDateLabel` 日期文案 | `逾期 1 天`（昨天）、`今天`、`明天`、本周内的 `周六`、更远的 `10月20日`；跨月跨年不出负数天数（`逾期 0 天` 视为 `今天`） | 同上 |
| T-82 | `ItemCompleteDialog` 分类型形态 | MOVIE → 标题「标记看过：」+「看完日期」+ 评分 +「短评」；BOOK →「标记已读：」+「读完日期」+ 评分 +「读后感」；PLACE →「标记去过：」+「去过日期」+ **无评分** +「回忆」；打开时 `finishDate` 默认**本地今天** | `components/ItemCompleteDialog/__tests__/index.spec.ts` |
| T-83 | 完成弹窗提交用用户选的日期 | 把日期改成 `2026-10-01` 后提交 → 以 `{finishDate:'2026-10-01'}` 调用 `completeFrontItem`（断言出参而不是只断言被调用）；成功后 `emit('submitted')` | 同上 |
| T-84 | 首页心愿条、状态文案与心愿单分页 | 心愿条数字 = `wishTotal + doingTotal`；`DOING` 条目状态文案是「在看 / 在读」；心愿条链接指向 `/plan`（回归：改前误指 `/note`）；心愿单展示计划日期，底部「已展示 X / 共 N 项」，点「加载更多」以 `pageNum=2` 追加（结论 92） | `views/front/home/__tests__/wishlist.spec.ts` |

## 2. 人工用例（只有人能跑）

| 编号 | 用例 | 步骤与期望 |
| --- | --- | --- |
| T-85 | 三模块状态流转与筛选 | 影单：`想看 → 在看（开始看）→ 看过（标记，选日期）→ 取消看过 → 想看`，每一步列表筛选、卡片按钮、详情状态三处一致；书籍同理；足迹只有 `想去 / 去过` 两枚胶囊，`想去` 卡片有「定个日子」。另核对「计划日期」三处露出：新增表单留空仍为纯心愿、编辑弹窗可改、详情抽屉可见 |
| T-86 | 首页补记日期（FR-003-7） | 首页心愿单点「完成 →」→ 弹窗日期默认今天 → 改成昨天 → 确认后进时间线，`finishDate` 是昨天而不是今天 |
| T-87 | 地图两态同屏（FR-003-6 现状核实，D-9 交付依据） | 足迹页切地图模式：`去过` 与 `想去` 图钉**同时**出现且样式不同、图例两者都在；点击弹卡的标签与图钉一致；缩放到聚合层时聚合牌着色不把两态混为一谈 |
| T-88 | 导航与移动端 | 桌面顶部导航 6 项（首页 / 影单 / 书单 / 足迹 / 笔记 / 计划），`计划` 可进；移动端底部仍 4 项（首页 / 影单 / 足迹 / 笔记）；移动端从首页心愿条能进 `/plan` 且页面单列可读；**计划页底部常驻「已展示 X / 共 N 项」**（结论 89，数据不足一页时也要看得见） |
| T-91 | 地点不得被置成 `DOING` + 状态文案三层兜底 | 后端：`PUT {status:'DOING'}` 到地点条目 → code 500 且 msg 含「地点不支持」，状态不变；地点仍可写计划日期（结论 87）。前端：`itemStatusLabel` 字典优先 → 类型中文兜底 → 通用兜底，**任何分支都不返回裸枚举**（地点 `DOING` → 「进行中」而非 `DOING`）（结论 88） | `AppPlanApiSmokeTest` + `utils/__tests__/itemStatus.spec.ts` |
| T-92 | 计划页分页可见与按钮可点性 | 计划页 `pageSize = 10`：15 条数据时出现「加载更多」，点击后追加且「已展示 X / 共 N」更新；三个操作按钮为真实按钮（实心主操作 + 描边次操作），地点卡片不出现「开始」（结论 89/90） | 人工 T-88 扩展（自动化只覆盖纯函数与文案） |
| T-89 | 后台条目与回收站 | 后台「全部条目」筛选下拉出现「在看 / 在读」且能筛出 `DOING`；回收站条目的状态文案不出现空白或 `undefined` |
| T-90 | 字典缓存刷新 | 执行增量 SQL 后，前台首次进入仍可能看不到「在看」（`useDictStore` 缓存）；清缓存或重登后胶囊出现第三枚，顺序为 想看 / 在看 / 看过 |

## 3. 回归清单（本期触及共享代码，必须逐条过）

需求 §5.6 要求：触及状态模型、`SgjDashboardMapper`、`AppHomeServiceImpl` 时回归既有流程。

| # | 回归项 | 通过标准 |
| --- | --- | --- |
| R-1 | 三个模块「想看 / 想看筛选列表」 | 存量 `WANT` 条目仍在对应胶囊下，条数与改前一致 |
| R-2 | 「标记看过 / 读过 / 去过」与「取消」 | 弹窗字段、文案、日期、评分、短评与改前逐字一致；取消后回 `WANT` |
| R-3 | 首页时间线 | 分页「展开更多」条数与 `timelineTotal` 一致；分组年月标签不变 |
| R-4 | 首页心愿单 | 存量 `WANT` 条目仍在；「完成 →」不再静默写当天 |
| R-5 | 首页 hero 统计 | 四个数字与改前一致（只有已完成计入）；`itemTotal` 自洽 |
| R-6 | 后台「全部条目」与回收站 | 列表、筛选、文案无新增报错；`DOING` 条目不落到未定义分支 |
| R-7 | 足迹地图与轨迹 | 两态图钉、照片聚合、点击弹卡与改前一致 |
| R-8 | 笔记模块 | 不受影响（本期不动笔记代码），抽一条笔记详情 + 一条带图笔记确认渲染正常 |

## 4. 命令与证据

| 检查 | 命令 |
| --- | --- |
| 后端 | `mvn -pl shiguangji-admin -am test`（JDK 17；先起 MySQL 13306 / Redis 16379） |
| 前端单测 | `cd shiguangji-web && npx vitest run` |
| 类型 | `cd shiguangji-web && npx vue-tsc --noEmit` |
| 构建 | `cd shiguangji-web && npm run build:prod` |
| 增量 SQL 幂等 | `mysql -h127.0.0.1 -P13306 -uroot -p < shiguangji-server/sql/update/20261014_plan_status.sql` 连跑两遍，第二遍不报错且 `sys_dict_data` 行数不变 |
