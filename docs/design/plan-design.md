# 计划（0.0.3）—— 技术决策与详细设计

> **范围**：`sgj_item` 状态模型补上中间态 `DOING`、「计划日期」在前台露出、新增计划视图 `/plan`、首页心愿单与统计对齐、统一完成入口的日期精度。
> **结论编号接** [`external-images-design.md`](external-images-design.md)（止于 70），本组自 **71** 起，引用时统一写「设计 结论 N」。
> **本文件的三份配套**：[需求](../requirements/0.0.3-plan.md)、[架构](plan-architecture.md)、[测试用例](plan-testcases.md)、[设计图](mockups/plan-0.0.3/index.html)。
> **读前先接受三条前置事实**（方案是为它们让路的）：
> ① `sgj_item.status` 是**自由文本列**（`varchar(20)`，无数据库约束），值域的事实来源是 `sys_dict_data` 三套字典，不是 Java 枚举；
> ② `sgj_item.start_date` **早已存在**（建表注释即「开始日期（观看/阅读/计划时间）」），前台从未使用——本期是「让前台用起来」而不是「加字段」；
> ③ `PUT /app/item/{itemId}` 的 `mergeEditableFields` **已经合并** `status` / `startDate` / `finishDate`（`AppItemController:248/268/272`），写入路径已经通了，卡住的只有校验白名单。

---

## 1. 方案一句话

**给影视与书籍各加一枚中间态 `DOING`（在看 / 在读），把既有的 `start_date` 以前台「计划日期」的形式露出来（一个日期，不是一个新状态），再用一个新读接口 `/app/item/plan/list` 把「有日期的未完成项」按日期排成一页计划；首页心愿单收进 `DOING`，统计改成显式三态，四处「标记完成」收敛成同一个弹窗。**

```
                  ┌─ 想看 ──┬─ 定个日子 ──▶ 计划日期 ─┐
   条目 WANT ─────┤         └──────────────────────┘│
                  │                                  ▼
                  └─ 开始做 ──▶ DOING（在看/在读） ──▶ /plan 汇总（WANT ∪ DOING，按日期排）
                                        │
                                        └─ 完成 ──▶ DONE（时间线） ──▶ 取消 ──▶ WANT
```

**地点只有两态**：`WANT` + 计划日期即「打算去」；中间态对地点语义别扭（D-5）。

## 2. 结论（71~86）

| # | 性质 | 议题 | 结论 |
| --- | --- | --- | --- |
| 71 | 已拍板 | **状态模型：只加一个 `DOING`，且只给影视 / 书籍** | 影视 `sgj_movie_status` 加「在看」、书籍 `sgj_book_status` 加「在读」；**地点不加**（D-5：中间态对地点语义别扭，「在计划」不是一个人话状态）。**修订 FR-003-1**，见 §8。四个模块共用同一条「只有 `DONE` 算完成」的口径。 |
| 72 | 约束 | **「计划」不是状态，是 `WANT`/`DOING` + 一个日期** | 不新增枚举表达「已排期」：`WANT` + `start_date` 就是计划。一个字段能表达的语义不引入第二个概念（需求 §5.1 概念控制）。 |
| 73 | 已拍板 | **状态流转复用 `PUT /app/item/{itemId}`，不新增端点** | `mergeEditableFields` 已合并 `status`（248）、`startDate`（268）、`finishDate`（272），「开始做」「定个日子」「改日期」都是同一次 `PUT`。`POST /{id}/complete` 保持「标记完成」语义（写 `finishDate`，缺省当天），`POST /{id}/uncomplete` 保持「退回 `WANT`」。新增一个 `/status` 端点等于给同一件事开第二个入口。 |
| 74 | 约束 | **写入闸门只有一处：`SgjItemServiceImpl.ITEM_STATUS`** | 它是前台 `PUT`、后台 `SgjItemController`、封面服务复用 `updateSgjItem` 三条路径的共同校验（`SgjItemServiceImpl:44`）。放开它 = 全链路可写 `DOING`；不放 = 任何入口都写不进。异常文案同步改「仅支持WANT/DOING/DONE」。 |
| 75 | 缺陷 | **`buildSummary` 的 `else` 兜底吞掉 `DOING`**（必须修） | `AppHomeServiceImpl:79` 现在是 `if DONE {done} else {wish}`：加入 `DOING` 后**进行中条目被静默计入心愿**，不报错、只是 `wishTotal` 与心愿单条数不符。改为显式三态，`summary` 增加 `doing` / `doingTotal`，并保证 `itemTotal = doneTotal + wishTotal + doingTotal`（验收：hero 数字自洽）。 |
| 76 | 已拍板 | **心愿单纳入 `DOING`，并改排序口径（D-8）** | `selectTodoItems` 由 `status='WANT'` 改为 `status in ('WANT','DOING')`，排序由 `create_time desc`（最新添加在前，无时间无次序，正是本期要解决的问题）改为 **`(start_date is null), start_date asc, create_time desc, item_id desc`** —— 有计划日期的按日期排在前，没日期的按添加时间兜底。 |
| 77 | 已拍板 | **`/plan` 数据源：新增 `GET /app/item/plan/list`** | 挂在 `AppItemController`，与既有 `/app/item/recycle/list` 同族；查询加在 `SgjItemMapper`（`selectPlanItems`），**复用 `selectSgjItemVo`** 以直接拿到封面与 `photoCover`；分页复用 `startPage()` + `((Page<?>) list).getTotal()`，不另写 `count`。不复用 `/app/item/list`：它是单状态过滤 + `update_time desc` 排序，改它会动到三个模块的既有列表。 |
| 78 | 约束 | **排序在服务端，分组在前端** | 服务端只保证 `order by (i.start_date is null), i.start_date asc, i.item_id desc`；「逾期 / 本周 / 以后 / 未排期」由前端按**浏览器本地日期**切（纯函数，见 §5.1）。理由：周起点与「今天」若在两侧各算一次，跨时区与跨零点必然对不上，而分组的正确性只对「用户眼里的今天」负责。 |
| 79 | 已拍板 | **`/plan` 匿名可读** | 与 `/app/item/list` 一致：`@Anonymous` + `appScopeHelper.resolveCreateBy()` + `maskItemsForGuest`。匿名访客看 `publicOwner` 的公开数据、笔记受 `is_public` 约束（需求 §5.3），本期不新增例外。 |
| 80 | 已拍板 | **导航：桌面 5 → 6 项，移动端 4 项不动（D-7，前提已过期）** | `navLinks` 现为 5 项（**含「书单」**），「计划」**追加在末尾**（首页 / 影单 / 书单 / 足迹 / 笔记 / 计划）——不动既有顺序，把改动收敛成纯新增；`mobileTabs` 4 项不动，移动端由首页心愿条进入 `/plan`。**需求里 D-7 的原文「移动端导航目前仅 3 项」「桌面端导航改为 5 项」与现状不符**，见 §8。 |
| 81 | 已拍板 | **`start_date` 列名与语义都不动，前台文案统一「计划日期」（D-6）** | 列名不改（避免 DDL 与全链路改名）；只改 `SgjItem.java` 与建表注释的 Javadoc、前端表单 label 与详情展示。既有数据里 `start_date` 存的确实是开始/计划时间，语义扩展不破坏存量。 |
| 82 | 缺陷 | **FR-003-7：首页「完成 →」静默写当天**（必须修） | `home/index.vue:286` 用 `new Date()` 现算 `finishDate`，与各模块「标记看过」弹窗（可选日期）精度不一致，补记场景日期必错。做法：抽出 `ItemCompleteDialog` 供**四处**复用（movie / book / travel / home），**弹窗打开时日期默认今天但可改**。首页那条「完成 →」改为打开弹窗。 |
| 83 | 现状核实 | **FR-003-6 已经实现，本期无代码改动** | `TravelMap` 已同时接收 `visited` 与 `wish` 两组点并按 `.visited` / `.want` 区分图例、图钉与聚合着色（`TravelMap:24-36, 160-164, 213-225`），`/app/travel/trajectory` 不按状态过滤（`AppTravelController:49-115`）——地图从来不依赖状态筛选切换。**D-9 纳入本期的实际交付是回归依据（设计图 `place-map.html` + 人工用例 T-87），不是功能。** 与 FR-003-6 原文的差异见 §8。 |
| 84 | 缺陷 | **前端把 `status` 写成了 `'WANT'\|'DONE'` 字面量联合** | `movie:214,315,328`、`book:188,232`、`travel:321,374`、`MediaDetailDrawer:54,154`、`ItemManager:263` 等处改为共享的 `ItemStatus`（`'WANT'\|'DOING'\|'DONE'`，见 §5.2）；状态胶囊本身是**字典驱动**的（`v-for="(s, si) in sgj_movie_status"`），字典加了 `DOING` 会自动多出一枚，所以漏改类型不会漏 UI，但会在赋值处报类型错——由 `vue-tsc` 兜住。 |
| 85 | 约束 | **字典排序：`DOING` 排在中间** | 新增 `DOING` 行给 `dict_sort=2`，并把既有 `DONE` 行的 `dict_sort` 由 `2` 改成 `3`（否则两行同序，胶囊顺序不稳定）。`init_business.sql` 全量脚本与 `sql/update/20261014_plan_status.sql` 增量脚本**都要改**，增量脚本对 `DOING` 行按 `dict_value` 先删后插（照 `update/` 既有约定，幂等且能清掉游离行）。 |
| 86 | 约束 | **地点条目的 `DOING` 不在本期值域内，但要有兜底归类** | 前台不给地点 `DOING` 入口；若后台管理页把地点改成 `DOING`，`AppTravelController:93` 的 `if DONE {visited} else {wish}` 会把它归入「想去」。**本期保留该 `else` 并补注释说明是有意归类**（不崩、不丢点）；登记为已知边界，不为其加分支。 |
| 87 | 缺陷（实测） | **地点被置成 `DOING` → 界面显示裸枚举 `DOING`** | 两个缺陷叠加：计划页对地点也渲染了「开始 →」（地点不该有中间态），而后端白名单放行了「地点的 `DOING`」。修法按根因三层：① 唯一闸门拒绝（`validateItem`：地点不支持「在看」，请用计划日期表达计划）；② 计划页不再给地点 `开始` 入口；③ 状态文案三层兜底（结论 88）保证任何情况下都不露裸枚举。历史脏数据（实测中产生的那条地点）已还原为 `WANT`。 |
| 88 | 实测 | **状态文案三层兜底，字典仍是唯一事实来源** | 取值顺序：字典 → 该类型的中文兜底 → 通用兜底（未完成 / 进行中 / 已完成）。前三者都拿不到时才回落原值。之所以要兜底：字典缓存过期（SQL 直改字典不走 `removeDict`）或增量脚本未执行时，原先的实现会直接把 `DOING` 显示给用户。 |
| 89 | 实测 | **计划页的分页必须可见** | 原 `pageSize = 20` 且「加载更多」只在有下一页时渲染：数据不足 20 条时等于没有分页，用户看不到任何分页迹象。改为 `pageSize = 10`，底部**常驻**「已展示 X / 共 N 项」，有下一页时并列「加载更多」。 |
| 90 | 实测 | **卡片操作必须是「看得出能点」的按钮** | 原三个 `link` 文字按钮挨在一起（改日期 / 开始 / 标记看过），实测反馈「看不出是三个可点的按钮」。改为三个真实按钮：状态推进为实心主操作，计划日期与退回为描边次操作，间距 8px；移动端三个按钮平分一行以放大点按目标。地点不给「开始」。 |
| 91 | 实测 | **分组要配一句人话** | 实测反馈只看「以后 / 未排期」两个词看不出分组依据（是时间？是状态？）。四组各配一句说明：逾期「计划日期已经过了」、本周「本周内（10月12日 – 10月18日）」、以后「排在下周或更远」、未排期「还没定哪天 —— 点「定个日子」补上」。 |
| 92 | 实测 | **心愿单与计划页合并为单一数据源** | 首页原先在 `/app/home/index` 里下发 `wishlist`（`selectTodoItems(20)`），与计划页是同一个集合 + 同一个排序——两份来源各带一页会在分页边界错位。改为：首页不再下发 `wishlist`，心愿单统一走 `GET /app/item/plan/list`（因此天然支持「加载更多」），并展示状态与计划日期；`selectTodoItems` 只保留给后台仪表盘的待办卡片。 |
| 93 | 实测（二轮） | **计划页分组与页头重做：组名自解释、说明写「该拿这组做什么」** | 二轮实测仍反馈「模块意义不明」——第一轮的说明只是把组名换个说法（「以后 → 排在下周或更远」是同义反复）。改为：组名直接说事（**已过期 / 这周 / 下周及以后 / 还没定日子**）；说明写成动作（「开始做，或者改个日子」「点「定个日子」把它排上」）；每组左侧身份色条 + 标题染色（过期=陶红、这周=琥珀、以后=鼠尾草、未定=中性）；页头一句话说明这页是什么（「影单 / 书单 / 足迹里还没做完的事，按你定的日子排在一起」）+ 三个计数 chip。 |
| 94 | 实测（二轮） | **心愿单状态做成胶囊，在做高亮** | 原先是 11px `--sgj-text-3` 灰字（实测反馈「根本看不清」）。改为胶囊：中性底显示 想看/想读/想去，「在做/在读」用陶红浅底 + 加粗——它是唯一需要被行动的那一档。 |
| 95 | 实测（二轮） | **心愿单分页脚复用「最近笔记」的视觉** | 与 `.note-more`（右对齐、12px、主色）同一语言：有下一页 → 「加载更多（还有 N 项）→」；加载完 → 「已全部显示 · 共 N 项」（弱色）。不再用左右分栏的「已展示 X / 共 N 项 + el-button」。 |
| 96 | 实测（三轮） | **用户可见文案统一叫「心愿」，不再「计划 / 心愿」混用** | 侧栏卡片叫心愿单、聚合页叫计划——用户指出这是同一个东西。统一为「心愿」：导航「心愿」、页头「心愿 · 想到就排上」、空态「还没有心愿」、首页脚「查看全部心愿 →」。**路由仍是 `/plan`**（与 `/movie`↔影单 的「英文路径 + 中文标签」既有模式一致，不做 URL 迁移）；文档内部继续用「计划页 / 计划日期」指代实现，不算用户可见文案。侧栏只放首屏一页（10 条），完整列表由「查看全部心愿 →」进 `/plan`——与「查看全部笔记 →」完全同一模式，卡片内不再做加载更多。 |
| 97 | 实测（三轮） | **心愿页点卡片跳模块页，必须把目标条目「落在列表里」** | 原先只带 `?itemId=` 开抽屉：影视页默认「电影 + 想看」胶囊，点一部**电视剧**（或一条「在看」的）跳过去，抽屉开了、列表里却看不到它，用户以为没跳成功。修法：跳转带上 `type` / `status` 预选胶囊（`?itemId=&type=TV&status=DOING`），三个模块页 `handleRouteQuery` 识别后先切胶囊再补一次 `loadData`（setup 里 `loadData()` 先于它执行，不补就还是旧列表）。 |
| 98 | 用户提议（#84） | **心愿页做成手绘清单：一张纸、一行一条、方框打勾** | 用户：「现实生活中清单都是这样」。曾直接实现过一版（衬线字 + 方框微转 + 胶带 + 虚线）**被否决**——「根本不是手绘、太丑」，教训：视觉方向这类主观改动必须**先出设计图、确认后再写产品代码**（该版已从分支回退）。重走流程：出设计图 `mockups/plan-0.0.3/handdrawn-checklist.html`（三套手写字体子集内嵌可切换、日纸+钢笔/夜板+粉笔、清单/空态、桌面/移动、逐屏标注）→ 用户确认「非常完美」→ 按图落地。 |
| 99 | 实测（#84） | **手写字体自托管方案：核心片 + 稀疏片（龙藏，SIL OFL 1.1）** | 页面渲染用户随手输入的标题（任意汉字），按固定文案子集化会缺字；实测否掉两条路：①均匀 unicode-range 分片（512 码位/片）在本站语料（~600 字散布全 CJK 区）命中 ~40 片 ≈ **2MB**；②稀疏片声明整段区间会被「区间与页面字符有交集」的规则误拉。**最终**：核心片 `lc-core.woff2`＝站点语料（`scripts/handdrawn-corpus.txt`，821 字含符号区）**230KB 一个文件**；稀疏片 41 片（区间内剔除核心片字符、`unicode-range` **逐字列出**与核心片零交集）共 2.8MB 磁盘、仅新字出现时按片懒加载；字体 CSS 随 `/plan` 路由块加载，其他页面零成本。真机验证：常规浏览只拉核心片。新字入库后重跑 `scripts/gen-handdrawn-font.py`（不重跑也不坏，新字回落系统字体）；`OFL.txt` 随包分发。 |
| 100 | 用户拍板（#84） | **设计图确认，按图落地心愿页；前台统一手绘为后续方向** | 字体取默认**龙藏**（钢笔感、可读性最好）；实现边界＝**只换 /plan 皮肤**，信息架构与已验收行为（分组/方框动作/分页/跳转预选）零变化。用户同时表态「之后打算把整个前台统一这种风格」→ 已记入 [pending.md](../requirements/pending.md) 作后续方向，不在本期做（涉及导航/弹窗/各列表页的全面重皮，需单独立项排期）。 |
| 101 | 实测（五轮） | **手写体只做「标题时刻」，正文回衬线** | 全页手写落地后用户反馈「字体好丑」——龙藏偏潦草，小字号（日期/按钮/说明）下显乱，且与规整的导航对比更明显。拍板方案：手写只用于页头 / 组名 / 空态三处大字，正文（条目、日期、按钮、说明）回到站内衬线 `--sgj-font-serif`；手绘感由纸 / 方框 / 抖线承担。**连带红利**：核心片语料从全站文案缩到固定标题文案（51 字），核心片 230KB → **27KB**；稀疏片保留不动（新字仍可兜底）。 |
| 102 | 实测（五轮） | **标题手写字体换马善政** | 衬线正文方案落地后用户仍觉「心愿 · 想到就排上、下周及以后」这些手写标题丑——龙藏是潦草钢笔字，大字号下笔画毛躁。拍板换**马善政**（毛笔楷书，笔画干净端正，手写感足且大气）；核心片语料不变（仍是固定标题 51 字），体积同级（28KB）。目录从 `longcang/` 换为 `mashanzheng/`，生成脚本同步切换。 |

## 3. 字典与数据

### 3.1 三套状态字典的最终值域

| 字典 | 值 | 文案 | dict_sort | listClass | 本期 |
| --- | --- | --- | --- | --- | --- |
| `sgj_movie_status` | `WANT` | 想看 | 1 | info | 不动 |
| | **`DOING`** | **在看** | **2** | warning | **新增（dict_code 112）** |
| | `DONE` | 看过 | **3**（原 2） | success | 改序 |
| `sgj_book_status` | `WANT` | 想读 | 1 | info | 不动 |
| | **`DOING`** | **在读** | **2** | warning | **新增（dict_code 122）** |
| | `DONE` | 已读 | **3**（原 2） | success | 改序 |
| `sgj_place_status` | `WANT` / `DONE` | 想去 / 去过 | 1 / 2 | info / success | **不动**（D-5） |

`dict_code` 112 / 122 已确认空闲（影视占 110/111、书籍占 120/121、地点占 130/131、地点分类自 140 起）。

### 3.2 DML

- **全量**：`sql/init_business.sql` —— 补两条 `DOING` 插入、把 `111` / `121` 的 `dict_sort` 改成 `3`、同步字典类型注释（`sgj_movie_status` 描述为「影视条目状态（想看/在看/看过）」、`sgj_book_status` 为「阅读状态（想读/在读/已读）」）与 `sgj_item.status` 列注释。
- **增量**：新增 `sql/update/20261014_plan_status.sql`，可重复执行（照 `update/` 既有约定，`DOING` 行按 `dict_value` 先删后插）：

```sql
delete from sys_dict_data where dict_type = 'sgj_movie_status' and dict_value = 'DOING';
insert into sys_dict_data values(112, 2, '在看', 'DOING', 'sgj_movie_status', '', 'warning', 'N', '0', 'admin', sysdate(), '', null, '在看');
delete from sys_dict_data where dict_type = 'sgj_book_status' and dict_value = 'DOING';
insert into sys_dict_data values(122, 2, '在读', 'DOING', 'sgj_book_status', '', 'warning', 'N', '0', 'admin', sysdate(), '', null, '在读');
update sys_dict_data set dict_sort = 3 where dict_type in ('sgj_movie_status','sgj_book_status') and dict_value = 'DONE';
```

**无 DDL**：没有新表、新列、新索引。存量数据不受影响（`DOING` 是新增值，`start_date` 是既有列）。

**缓存注意**：字典走 `useDictStore` 缓存，部署后前端需清一次字典缓存才看得到新值（后台「字典管理」改动字典时会 `removeDict`；本次是 SQL 直接改，故运维需刷新缓存或等用户重登）。

## 4. 后端设计

| # | 位置 | 改动 |
| --- | --- | --- |
| 1 | `SgjItemServiceImpl:44` | `ITEM_STATUS` 加 `"DOING"`；`:174` 文案改「状态值不合法，仅支持WANT/DOING/DONE」 |
| 2 | `SgjItemServiceImpl:82` | 新增条目默认 `WANT` —— **不变**（新增即心愿，不默认开始） |
| 3 | `AppItemController` | 新增 `planList`：`@Anonymous @GetMapping("/plan/list")`，`setCreateBy(resolveCreateBy())` → `startPage()` → `selectPlanItems` → `maskItemsForGuest` → `success(list)` + `total`（照抄 `list` 的实现形状） |
| 4 | `SgjItemMapper.java` / `.xml` | 新增 `selectPlanItems(SgjItem)`：`<include refid="selectSgjItemVo"/>` + `status in ('WANT','DOING')` + `itemType`/`title`/`tags` 可选过滤 + `order by (i.start_date is null), i.start_date asc, i.item_id desc` |
| 5 | `SgjDashboardMapper.xml:96` | `selectTodoItems`：`status in ('WANT','DOING')`；排序改 `order by (start_date is null), start_date asc, create_time desc, item_id desc` |
| 6 | `AppHomeServiceImpl:60-101` | `buildSummary` 改显式三态：`DONE→done`、`DOING→doing`、其余→`wish`；新增 `doing` / `doingTotal`；`itemTotal` 三者相加 |
| 7 | `SgjItem.java:28` | 注释「状态（WANT/DOING/DONE；地点仅 WANT/DONE）」 |
| 8 | `AppTravelController:93` | 仅补注释（结论 86），行为不变 |
| 9 | `SgjItemServiceImpl.validateItem` | **地点不得置 `DOING`**：`DOING + PLACE` → 抛「地点不支持「在看」，请用计划日期表达计划」（结论 87）。放在这里是因为它是所有写入路径的唯一闸门 |
| 10 | `AppHomeServiceImpl.getHomeData` / `AppHomeController.index` | **不再下发 `wishlist`**（连同访客脱敏那行）：心愿单统一走 `/app/item/plan/list`（结论 92）。`SgjDashboardMapper.selectTodoItems` 保留但只服务后台仪表盘（`SgjDashboardServiceImpl`），README 级注释已写明 |

**`selectPlanItems` 的为什么**（结论 77 的展开）：`/app/item/list` 现状是「单状态 + `update_time desc`」，而计划页要「两状态 + 计划日期升序 + 空日期置后」，用 `status` + 前端排序都做不到（分页会在客户端排序前截断）。新增查询而不是给 `selectSgjItemList` 加参数，是因为后者的调用方有三个前台列表页 + 后台管理页 + 封面服务，给它加排序开关等于让五个调用点都承担一个它们不需要的分支。

**`planList` 的返回**：`AjaxResult`（条目数组在 `data`、总数在 `total`，与 `/app/item/list` 同形状），前端 `api/front/item.ts` 加一个 `listPlanItems`。

## 5. 前端设计

### 5.1 计划页 `/plan`

> 皮肤（0.0.3 末，#84）：本节描述的信息架构不变，视觉按已确认的手绘设计图
> `mockups/plan-0.0.3/handdrawn-checklist.html` 落地（结论 98~100）——一组一张纸（胶带+抖线滤镜）、
> 行首手绘方框=状态+动作、龙藏手写字体核心片+稀疏片自托管（结论 99）、日纸+钢笔/夜板+粉笔。

**新增** `views/front/plan/index.vue`、路由 `/plan`（`FrontLayout` + `name: 'FrontPlan'`、`meta.title: '计划'`）、导航项。

- **页头**：`计划` + 副标题「{YYYY} 年 {M} 月 · 共 {N} 项 · 在想 {W} · 在做 {D}」
- **分组**（纯函数 `utils/plan.ts` → `groupPlanItems(items, today)`，先用本地日期字符串比较，`YYYY-MM-DD` 字典序即时间序）：

| 组 | 判据 | 展示 | 组名与说明（结论 93：组名自解释，说明写「该拿这组做什么」） |
| --- | --- | --- | --- |
| 已过期 | `startDate < today` | 日期文案「过期 N 天」，暖色高亮 | 说明：计划日期已经过了 —— 开始做，或者改个日子 |
| 这周 | `today ≤ startDate ≤ 本周日`（周一为一周起点） | 「今天 / 明天 / 周X」 | 说明：打算这周完成的（10月12日 – 10月18日，按当前周算） |
| 下周及以后 | `startDate > 本周日` | 「M月D日」 | 说明：日子还远，先放着 |
| 还没定日子 | 无 `startDate` | 「未排期」灰字 | 说明：点「定个日子」把它排上 |

  组内保持服务端顺序（已按 `start_date asc`）；空组不渲染；四组皆空才显示空态。**四组都带一句说明**（结论 91：实测只看「以后 / 未排期」两个词看不出分组依据）。

- **卡片操作：三个真实按钮，主次分明（结论 90）**——实测反馈原三个 `link` 文字按钮「看不出是三个可点的按钮」：
  - 状态推进（**实心主操作**）：`WANT` → `开始看/开始读 →`；`DOING` → `标记看过/标记已读`；地点只有 `标记去过`
  - 计划日期（**描边次操作**）：有日期 `📅 改日期`、没有 `📅 定个日子`
  - 退回（**描边次操作**）：`DOING` 才出现 `退回心愿`
  - **地点不给「开始」**（结论 87）；按钮间距 8px，移动端三个按钮平分一行放大点按目标
- **空态文案**：「还没有计划 —— 去影单 / 书单挑一个，填上打算哪天看。」+ 两个入口按钮
- **分页（结论 89）**：`pageSize = 10`；底部**常驻**「已展示 X / 共 N 项」，有下一页时并列「加载更多」按钮
- **匿名访客**：可读；不显示操作按钮（与各模块 `isLogin` 判断一致）

### 5.2 三态胶囊与流转

- 三个模块的状态胶囊**已经是字典驱动**，字典加了 `DOING` 即自动多一枚；要改的是 TS 类型：在 `types/api/business/item.ts` 导出 `type ItemStatus = 'WANT' | 'DOING' | 'DONE'`，各页 `activeStatus` 由 `'WANT'|'DONE'` 改为它、`switchStatus(status: ItemStatus)`。**用联合类型而不是 `string`**：三处 `switchStatus` 与详情徽标都要按值分支，放宽成 `string` 只会把类型错误推迟到运行时。地点条目实际只会取到其中两态，类型取超集不影响。
- 卡片操作按状态给：`WANT` → `开始看/开始读 →` 与 `标记看过/已读`；`DOING` → `标记看过/已读`；`DONE` → `取消看过/已读`。**足迹不加**，`WANT` 时多一个「定个日子」。
- 导航第 6 项的实现注意：`.front-nav` 在 `max-width: 768px` 以下整体隐藏、改显底部 tab（`layout/front/index.vue:308-319`），所以 6 项只影响 768px 以上的宽度；**768~820px 区间最紧**（logo + 6 个 nav-link + 主题按钮），实现后在该宽度目视核对一次，挤出时优先收 nav 间距，而不是提前切到移动端 tab。
- 状态标签文案：**字典优先，三层兜底，任何情况下都不显示裸枚举**（结论 88，实现见 `utils/itemStatus.ts`）。取值顺序：对应类型的字典 → 该类型中文兜底（想看/在看/看过…）→ 通用兜底（未完成/进行中/已完成）。原先各处直接 `dict.find(...)?.label || item.status`，字典缺值时会显示 `DOING`。
- 「计划日期」露出位置（三处，缺一处就等于没露）：**新增表单**（`movie` / `book` / `travel` 各自的 `addForm` 目前都没有 `startDate`，需加）、**编辑弹窗**（`ItemEditDialog` 同样没有，需加）、**详情抽屉**（`MediaDetailDrawer` 加一行「计划日期」）；列表卡片角标有则显示日期、无则「＋ 定个日子」。新增时留空即纯心愿（`status` 仍默认 `WANT`，见 §4 第 2 行）。

### 5.3 首页心愿单与统计（FR-003-5）

- `FrontHomeData.summary` 增加 `doing` / `doingTotal`（`types/api/front/home.ts`）；**`wishlist` 从首页响应中移除**（结论 92）。
- 心愿单数据源就是计划页那一份：`GET /app/item/plan/list`（`WANT ∪ DOING`、计划日期升序、无日期置后），因此**天然支持分页**：首屏 `pageSize = 10`，卡片底部「已展示 X / 共 N 项」+「加载更多」（与计划页同一页大小，翻页边界一致）。
- 每条展示：图标 + 标题 + **状态胶囊**（`itemStatusLabel`，在想=中性底、在做=陶红浅底加粗，结论 94）+ **计划日期角标**（`PlanDateBadge`，可点改）+ `完成 →`（统一完成弹窗）。
- 分页脚与「最近笔记」的 `.note-more` 同一视觉（结论 95/96）：右下角「**查看全部心愿 →**」进 `/plan` 看完整列表（卡片内不做加载更多，侧栏只放首屏一页）。
- hero 统计四个数字**口径不变**（只统计 `DONE` 与笔记）；心愿条的**数字改为 `wishTotal + doingTotal`**（在想 + 在做），文案「心愿 · 在想 {W} / 在做 {D}」，右侧链接改指 `/plan`——**当前它写的是 `to="/note"`**，是既有缺陷，本期一并修。
- 自洽性约束：`itemTotal === doneTotal + wishTotal + doingTotal`（用例 T-73 钉住）。

### 5.4 完成弹窗统一（FR-003-7）

**新增** `components/ItemCompleteDialog/index.vue`，`v-model` + `:item`，成功 `emit('submitted')`。内部分类型给文案：

| itemType | 标题 | 日期 label | 评分 | 评论 label / placeholder |
| --- | --- | --- | --- | --- |
| MOVIE / TV | 标记看过：{title} | 看完日期 | 有 | 短评 / 写点短评吧（可选） |
| BOOK | 标记已读：{title} | 读完日期 | 有 | 读后感 / 写点读后感吧（可选） |
| PLACE | 标记去过：{title} | 去过日期 | **无** | 回忆 / 写点回忆吧（可选） |

- **日期默认今天、可改**（`value-format="YYYY-MM-DD"`）——这是 FR-003-7 的落点：补记昨天看过的电影时，不该被迫接受今天。
- `label-width` 沿用既有差异（影视/书籍 80px、地点 90px），不动视觉。
- 提交仍走 `completeFrontItem(itemId, {finishDate, rating, comment})`，服务端语义不变。
- 四个调用点（`movie` / `book` / `travel` / `home`）删除各自的 `completeForm` 与弹窗模板。**回归基线就是 §5.4 这张表**：文案、字段、可选项必须与抽取前逐字一致。

## 6. 影响文件清单

**前端（新增）**

| 文件 | 内容 |
| --- | --- |
| `views/front/plan/index.vue` | 计划页（分组、卡片、空态、加载更多） |
| `utils/plan.ts` | `groupPlanItems` / `planDateLabel`（纯函数，无网络无 DOM） |
| `components/ItemCompleteDialog/index.vue` | 统一完成弹窗（四处复用） |
| `utils/__tests__/plan.spec.ts` | 分组与日期文案用例（T-80、T-81） |

**前端（改动）**

| 文件 | 改动 |
| --- | --- |
| `router/index.ts` | 注册 `/plan` |
| `layout/front/index.vue` | `navLinks` 末尾加「计划」（桌面 6 项）；移动端不动 |
| `api/front/item.ts` | 新增 `listPlanItems` |
| `types/api/business/item.ts` | 导出 `ItemStatus`；`status` / `startDate` 注释更新（`types/api/front/item.ts` 的 `FrontItemQuery` 已含 `itemType`/`title`/`tags`/分页，计划接口直接复用，**不改**） |
| `types/api/front/home.ts` | summary 增加 `doing` / `doingTotal` |
| `views/front/movie/index.vue`、`book/index.vue`、`travel/index.vue` | 三态胶囊与流转、计划日期露出、完成弹窗改用组件 |
| `views/front/home/index.vue` | 心愿单含 `DOING`、心愿条数字与链接、完成入口改用弹窗、`handleComplete` 删除 |
| `components/MediaDetailDrawer/index.vue` | 状态徽标三态（`doing-label` + `.DOING` 样式）、详情显示计划日期 |
| `components/ItemEditDialog/index.vue` | 新增「计划日期」字段 |
| `views/business/item/index.vue`、`views/business/components/ItemManager.vue`、`views/business/recycle/index.vue` | 后台筛选与文案映射加 `DOING` 一档（`doingLabel`，默认「在看 / 在读」） |

**后端（改动，无新增类）**

| 文件 | 改动 |
| --- | --- |
| `business/.../service/impl/SgjItemServiceImpl.java` | `ITEM_STATUS` + 异常文案 |
| `business/.../mapper/SgjItemMapper.java` + `resources/mapper/business/SgjItemMapper.xml` | `selectPlanItems` |
| `admin/.../controller/business/AppItemController.java` | `planList` 端点 |
| `business/.../resources/mapper/business/SgjDashboardMapper.xml` | `selectTodoItems` 纳入 `DOING` + 排序 |
| `business/.../service/impl/AppHomeServiceImpl.java` | `buildSummary` 三态 + `doing/doingTotal` |
| `business/.../domain/SgjItem.java`、`admin/.../AppTravelController.java` | 注释 |

**SQL**

| 文件 | 改动 |
| --- | --- |
| `sql/init_business.sql` | 两条 `DOING` + `dict_sort` 改序 + 注释 |
| `sql/update/20261014_plan_status.sql`（新增） | 同上，幂等 |

## 7. 测试与验收

自动化用例见 [plan-testcases.md](plan-testcases.md)（T-72 起），覆盖：状态白名单放行 `DOING`、计划接口的过滤 / 排序 / 分页 / 可见范围 / 匿名脱敏、`buildSummary` 三态自洽、字典值域与排序、前端分组纯函数与日期文案、完成弹窗的分类型文案。

需求 §5 的六条验收逐条落点：

| 需求验收 | 落点 |
| --- | --- |
| 三模块（影视 / 书籍；地点两态）流转一致 | T-72、T-85 |
| 时间线仅 `DONE`、心愿单仅 `WANT ∪ DOING` | T-79、T-74 |
| `/plan` 汇总与排序、逾期与本周区分 | T-75、T-80 |
| hero 统计自洽 | T-73 |
| 完成入口日期语义一致（FR-003-7） | T-82、T-83 |
| 回归：三模块筛选 / 标记 / 取消、时间线分页、心愿单快捷操作 | T-79 与测试用例 §3 的 R-1~R-8 |

## 8. 与需求文档的偏差（需回改 `0.0.3-plan.md`）

| 位置 | 需求原文 | 本设计的口径 | 原因 |
| --- | --- | --- | --- |
| §3.1 FR-003-1 | 三套字典各新增 `DOING` | **影视 / 书籍两套**各加，地点不加 | D-5：地点的「在看」不成立 |
| §3.2 FR-003-2 | 双向流转 | 明确复用 `PUT /app/item/{itemId}` | `mergeEditableFields` 已支持，结论 73 |
| §3.2 FR-003-6 | 地图两态图钉区分（🔵→✅） | **已实现**，本期只交回归依据 | 结论 83：`TravelMap` 已同屏两态、`trajectory` 不按状态过滤 |
| §8 D-7 | 「移动端导航目前仅 3 项」「桌面端导航改为 5 项」 | 移动端 **4** 项（不动）；桌面 **5 → 6** 项 | 现状 `navLinks` 已含「书单」共 5 项、`mobileTabs` 4 项 |

## 9. 非目标

- 不做集数 / 页数进度（「看到第几集」「读到第几页」，D-10 已拍板不做）；
- 不做提醒与通知（计划日期临近不推任何消息，本期无调度）；
- 不做行程清单（`sgj_item_todo` 子项）与行程容器（多条目归组）；
- 不做 `sgj_item.is_public`（D-21 已拍板不提前）；
- 不做日历视图 / 甘特图：`/plan` 只有一种线性分组视图；
- 不做「计划」的批量操作（批量定日期 / 批量完成）。

## 10. 实施顺序（小步提交）

| # | 提交 | 内容 | 检查 |
| --- | --- | --- | --- |
| 1 | `docs:` | 本文件 + 架构 + 测试用例 + 设计图 + 需求文档偏差回改 | — |
| 2 | `feat(sql):` | 两套字典加 `DOING` + `dict_sort` 改序（init + update 增量） | 手工执行增量脚本两遍幂等；`mvn -pl shiguangji-admin -am test` |
| 3 | `fix(business):` | `ITEM_STATUS` 放开 `DOING` + 文案 + 注释 | 同上（T-72） |
| 4 | `feat(business):` | `selectPlanItems` + `GET /app/item/plan/list` + smoke | 同上（T-75） |
| 5 | `fix(business):` | `buildSummary` 三态 + `selectTodoItems` 纳入 `DOING` 与排序 | 同上（T-73、T-74） |
| 6 | `feat(web):` | `utils/plan.ts` + `/plan` 页 + 路由 + 导航 + spec | `npx vitest run` + `npx vue-tsc --noEmit` + `npm run build:prod`（T-80、T-81） |
| 7 | `feat(web):` | `ItemCompleteDialog` + 四处接线（含首页日期修复） | 同上（T-82、T-83） |
| 8 | `feat(web):` | 三态胶囊与流转、计划日期露出、首页心愿单与统计对齐 | 同上（T-84、T-85） |
| 9 | `feat(web):` | 后台筛选与文案映射加 `DOING` | 同上 |
| 10 | `fix(web):` | `/plan` 免登录白名单 + 公开路由清单抽成可测模块 | 同上（T-88 部分） |
| 11 | `fix(business):` | **实测修订**：地点不得置 `DOING`（闸门）+ 心愿单单一数据源（首页去 `wishlist`） | `mvn -pl shiguangji-admin -am test`（T-91、T-74 改造） |
| 12 | `fix(web):` | **实测修订**：状态文案三层兜底 + 计划页按钮重做 / 分组说明 / 分页可见 + 心愿单分页与计划日期 | `npx vitest run` + `npx vue-tsc --noEmit` + `npm run build:prod`（T-91、T-92、T-84 修订） |

提交 3 与 5 是两处**必须同批落地**的修复（3 不落地则 `DOING` 写不进、5 不落地则统计静默错），但分属两个可独立回滚的改动，故分开提交；提交 2 是纯数据变更，可单独回滚。提交 11 的两处后端改动共用同一个冒烟测试类的改造（T-91 新增 + T-74 改造），拆开会让中间提交的测试处于「半更新」状态，故合并为一次提交。

## 11. 设计图

落在 `docs/design/mockups/plan-0.0.3/`：`index.html`（索引与出图口径）、`plan-page.html`（计划页四组 + 空态 + 桌面 / 移动）、`status-flow.html`（三态胶囊与流转：影单 / 书单 / 足迹两态 / 详情抽屉）、`wishlist-home.html`（首页 hero + 心愿单含 `DOING` + 统一完成弹窗三形态）、`place-map.html`（FR-003-6 现状核实：两态图钉同屏）、`handdrawn-checklist.html`（**#84 手绘清单，已确认并按图实现**——四轮修订后的视觉以此图为准）。出图约定沿用 [note-0.0.2 §3.2](note-0.0.2-design.md)：每屏一页、页内切状态与主题、橙色标注气泡与页脚一一对应、设备框可滚动、静态保真（不连真实接口）。

**组图与实现的差异（修订后）**：`plan-page.html` / `wishlist-home.html` 画的是修订前的形态（三个文字按钮、组说明只有未排期、心愿单无分页）。§5.1 与 §5.3 的文字是修订后的落地口径，以文字为准；组图待下次成组更新时一并同步。

## 12. 实测修订记录（结论 87~92）

用户实际使用后报的 5 个问题与落点，逐条可追溯：

| # | 实测反馈 | 根因 | 落点 |
| --- | --- | --- | --- |
| R1 | 计划页点「开始」后状态显示 `DOING` | 计划页对**地点**也给了「开始」，后端又放行了「地点的 `DOING`」，而地点字典没有这个值 → 前端回退成裸枚举 | 结论 87/88：闸门拒绝 + 不给入口 + 三层兜底；历史脏数据还原 `WANT` |
| R2 | 三个按钮挨太近，看不出是按钮 | 三个 `link` 文字按钮无边框、间距 4px | 结论 90：主操作实心 / 次操作描边，间距 8px，移动端平分一行 |
| R3 | 看不懂「以后 / 未排期」 | 组头只有词、没有说明 | 结论 91：四组各配一句人话，本周写明日期区间 |
| R4 | 计划页要分页 | `pageSize = 20` 且「加载更多」只在有下一页时出现，少量数据等于没有分页 | 结论 89：`pageSize = 10` + 常驻「已展示 X / 共 N 项」 |
| R5 | 心愿单没分页；希望也显示状态与计划日期 | 首页那份 `wishlist` 是独立的一次 `limit 20` 查询，与计划页同集合同排序却各带一页 | 结论 92：心愿单改用 `plan/list` 单一数据源，天然带分页，并展示状态与计划日期 |

二轮实测反馈（结论 93~95）：

| # | 实测反馈 | 根因 | 落点 |
| --- | --- | --- | --- |
| R6 | 计划页太丑、「区分的这几个模块是干什么的」不知道 | 一轮的组说明只是组名的同义反复（「以后 → 排在下周或更远」），且视觉上每组没有身份、页头没说这页是什么 | 结论 93：组名自解释（已过期 / 这周 / 下周及以后 / 还没定日子）、说明写成动作、每组身份色条与染色标题、页头一句话 + 计数 chip |
| R7 | 首页心愿单的状态根本看不清 | 11px `--sgj-text-3` 灰字 | 结论 94：状态胶囊，「在做」陶红浅底加粗 |
| R8 | 心愿单分页样式与「最近笔记」不一致、太丑 | 我自造的左右分栏「已展示 X / 共 N + el-button」 | 结论 95：复用 `.note-more` 视觉，「加载更多（还有 N 项）→」/「已全部显示 · 共 N 项」 |

三轮实测反馈（结论 96~97）：

| # | 实测反馈 | 根因 | 落点 |
| --- | --- | --- | --- |
| R9 | 心愿单「加载更多」还是丑；最近笔记是「查看全部笔记 →」，心愿单照做、统一右下角 | 侧栏窄卡片里做分页本身就是别扭的交互 | 结论 96：卡片内不再分页，右下角「查看全部心愿 →」进 `/plan`（与「查看全部笔记 →」同一模式） |
| R10 | 「计划」和「心愿」是一个东西，文案应统一（统一用心愿） | 同一概念两个名字：侧栏叫心愿单、页面叫计划 | 结论 96：用户可见文案全部统一「心愿」；路由 `/plan` 不动（英文路径 + 中文标签是既有模式） |
| R11 | 心愿页点「黑镜」跳到影单，列表里根本没有这一项 | 跳转只带 `?itemId=` 开抽屉；影视页默认「电影 + 想看」胶囊——电视剧（或「在看」）落过去不在当前列表里 | 结论 97：跳转带 `type`/`status` 预选胶囊，模块页先切胶囊再补 `loadData`，目标条目可见且抽屉自动开 |

四轮（手绘清单，#84）：

| # | 反馈 / 提议 | 落点 |
| --- | --- | --- |
| R12 | 「做一个手绘的清单样式？现实生活中清单都是这样」——第一版直接实现被否（不是手绘、太丑），改走设计图先行 | 结论 98：出设计图（三字体可切/日夜/清单空态/逐屏标注），用户确认后按图落地 |
| R13 | 设计图「非常完美」；先落心愿页，前台统一是后续打算 | 结论 99/100：字体核心片+稀疏片自托管（常规浏览 230KB 一个文件）、只换 /plan 皮肤、行为零变化；前台统一记入 pending |
| R14 | 落地后「这页面字体好丑啊」 | 手写体铺满全页：龙藏在小字号下潦草、与规整导航对比刺眼 | 结论 101：手写只做页头/组名/空态三处大字，正文回衬线；核心片语料随之 230KB→27KB |
