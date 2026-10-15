# 计划（0.0.3）—— 架构

> 配套文档：[需求](../requirements/0.0.3-plan.md) · [详细设计](plan-design.md) · [测试用例](plan-testcases.md) · [设计图](mockups/plan-0.0.3/index.html)
> 结论编号接 `external-images-design.md`（止于 70），本组自 71 起，引用时统一写「设计 结论 N」。

## 1. 一句话

**一个既有字段（`sgj_item.start_date`）加一枚新的枚举值（`DOING`），穿过「字典 → 校验 → 聚合 → 交互」四层；不新增表、不新增列、不新增服务类。**

本期没有新的领域概念：`DOING` 只是把「正在做」这一事实从「用 `WANT` 假装」和「直接跳到 `DONE`」之间显式地放回来；「计划」不是状态，是 `WANT`/`DOING` 加一个日期。

## 2. 分层视图

```
                    ┌──────────────────────── 交互层（Vue）────────────────────────┐
   /movie /book /travel  三态胶囊·卡片流转·计划日期      /plan 计划页（新增）      首页 hero + 心愿单
        │                        │                            │                      │
        └────────┬───────────────┴────────────────────────────┴──────────┬───────────┘
                 ▼                                                        ▼
        PUT /app/item/{itemId}（既有）                    GET /app/item/plan/list（新增）
        POST /app/item/{id}/complete（既有）              GET /app/home/index（既有，口径改）
        POST /app/item/{id}/uncomplete（既有）
                 │                                                        │
                 ▼                                                        ▼
        ┌──────────────── 校验 / 写入层（Java）────────────────┐  ┌──── 聚合层（Java）────┐
        │ SgjItemServiceImpl.ITEM_STATUS  ← 唯一白名单闸门     │  │ AppHomeServiceImpl    │
        │ SgjItemMapper.updateSgjItem / updateSgjItemExt       │  │   .buildSummary 三态   │
        │ AppItemController.mergeEditableFields（已支持        │  │ SgjDashboardMapper    │
        │   status / startDate / finishDate）                  │  │   selectTodoItems     │
        └──────────────────────────────────────────────────────┘  └───────────────────────┘
                 │                                                        │
                 ▼                                                        ▼
        ┌──────────────────────────────── 字典 / 存储层 ───────────────────────────────┐
        │ sys_dict_data：sgj_movie_status / sgj_book_status 各 +1 条 DOING（DML）        │
        │ sgj_item：status varchar(20) 自由文本、start_date 既有列（无 DDL）             │
        └───────────────────────────────────────────────────────────────────────────────┘
```

## 3. 状态机

```
  影视 / 书籍                    地点（本期不加中间态，D-5）
  ┌──────┐  开始/定日期  ┌───────┐  标记完成  ┌──────┐        ┌──────┐  标记去过  ┌──────┐
  │ WANT │ ───────────▶ │ DOING │ ────────▶ │ DONE │        │ WANT │ ────────▶ │ DONE │
  └──────┘ ◀─────────── └───────┘ ◀──────── └──────┘        └──────┘ ◀──────── └──────┘
      ▲   取消 / 退回        取消               取消             ▲   取消
      └──────────────────────────────────────────────────────────┘
  唯一约束：`DOING` 与 `WANT` 是「未完成」，只有 `DONE` 是「已完成」——这条口径决定
  时间线（只 `DONE`）、心愿单（`WANT` + `DOING`）、统计（三态显式相加）三处的一致性。
```

## 4. 数据流

| 流程 | 路径 | 是否新增 |
| --- | --- | --- |
| 看计划 | 浏览器 → `GET /app/item/plan/list?pageNum&pageSize` → `AppItemController.planList` → `startPage()` + `SgjItemMapper.selectPlanItems`（复用 `selectSgjItemVo`） | **新增一个端点 + 一个查询** |
| 开始做（`WANT → DOING`） | `PUT /app/item/{itemId}` body `{status:'DOING'}` → `mergeEditableFields` → `updateSgjItem` | 复用，仅放开白名单 |
| 定计划日期 | `PUT /app/item/{itemId}` body `{startDate:'2026-10-20'}` | 复用（字段已可合并） |
| 标记完成 | `POST /app/item/{itemId}/complete` body `{finishDate,rating,comment}` → `status='DONE'`，`finishDate` 缺省当天 | 复用，仅换前端入口（首页也走它） |
| 取消 | `POST /app/item/{itemId}/uncomplete` → `status='WANT'` | 复用（`DOING` 也可退回 `WANT`） |
| 首页 | `GET /app/home/index` → `buildSummary`（三态）+ `selectTodoItems`（`WANT`∪`DOING`）+ `selectTimeline`（`DONE`） | 口径修改 |

**为什么 `planList` 挂在 `AppItemController` 而不是新开 `AppPlanController`**：既有回收站读接口就是 `/app/item/recycle/list`，同族同控制器是既有约定；新增一个只有一个只读方法的控制器，换来的只是多一个文件。

**为什么走 `SgjItemMapper` 而不是 `SgjDashboardMapper`**：计划页要封面、首张照片兜底与分页 `total`，`selectSgjItemVo`（含 `photo_cover` / `photo_count` 子查询）已经全给了；`SgjDashboardMapper.selectTodoItems` 是首页侧栏的窄投影（无 `photoCover`），复用它会逼着再补一次字段。`SgjDashboardMapper` 继续只管首页那几处聚合。

## 5. 边界与约束

1. **可见范围只有一处实现**：`planList` 与既有 `/app/item/list` 一样，入口第一行 `sgjItem.setCreateBy(appScopeHelper.resolveCreateBy())`，然后 `maskItemsForGuest` 脱敏；不另写过滤逻辑（需求 §5.3）。匿名访客可读 `/plan`，读到的 `WANT`/`DOING` 与列表页口径一致。
2. **排序在服务端、分组在前端**：服务端只保证 `order by (start_date is null), start_date asc, item_id desc`；「逾期 / 本周 / 以后」由前端按浏览器本地日期切。理由见 设计 结论 78——周起点与「今天」若在两侧各算一次，跨时区与跨零点必然对不上。
3. **写入闸门只有一个**：`SgjItemServiceImpl.ITEM_STATUS` 是所有写入路径的必经校验（前台 `PUT`、后台 `SgjItemController`、封面服务复用 `updateSgjItem`），放开它即全链路放开；反向说，不放开它则任何入口都写不进 `DOING`。
4. **无 DDL**：`status` 是自由文本列、`start_date` 既有列，本期只有字典 DML（`dict_sort` 调整 + 两条 `DOING`）。因此 0.0.3 的「无表结构变更」结论维持（D-10、D-21 已拍板不做）。
5. **唯一新增的抽象是完成弹窗组件**：`movie` / `book` / `travel` 各有一份近乎逐字重复的完成弹窗（三份），首页再补一个入口就是第四份，故抽出来复用。抽取是本期唯一的组件级重构，回归面见 §6。

## 6. 回归热点（写死二值假设的位置，全量清单）

**后端**

| 位置 | 现状 | `DOING` 加入后的后果 |
| --- | --- | --- |
| `SgjItemServiceImpl:44` | `ITEM_STATUS = [WANT, DONE]` | **写入被拒**（`DOING` 存不进去），必须改 |
| `SgjItemServiceImpl:174` | 异常文案「仅支持WANT/DONE」 | 文案与白名单不符，改文案 |
| `AppHomeServiceImpl:79` | `if DONE {done} else {wish}` | **`DOING` 被静默计入心愿**：不报错，只是 `wishTotal` 与心愿单条数不符，必须改显式三态 |
| `AppTravelController:93` | `if DONE {visited} else {wish}` | 地点本期不产生 `DOING`，行为不变；作为「若将来放开地点中间态」的既有假设登记 |
| `SgjDashboardMapper.xml:96` | `selectTodoItems: status = 'WANT'` | `DOING` 不进心愿单（本期按 D-8 要进，改 `in`） |
| `SgjDashboardMapper.xml:121/133` | `selectTimeline` / `countTimeline: status='DONE'` | 不变（时间线只含 `DONE`），但**必须回归**：列表与 `timelineTotal` 仍一致 |

**前端**

| 位置 | 现状 |
| --- | --- |
| `views/front/movie/index.vue:214,315,328,506` | `activeStatus` 类型 `'WANT'\|'DONE'`、`switchStatus` 形参、`handleTypeChange` 复位、取消完成 |
| `views/front/book/index.vue:188,232,432` | 同上 |
| `views/front/travel/index.vue:321,374,693,1189` | 同上（地点不加 `DOING`，只放宽类型与日期露出） |
| `components/TravelMap/index.vue:35-36,164` | `MapPoint.status: 'DONE'\|'WANT'`，两态图钉样式与聚合着色 |
| `components/MediaDetailDrawer/index.vue:54,154` | 状态徽标 `WANT`/`DONE` 样式与文案 |
| `views/business/item/index.vue:20`、`views/business/components/ItemManager.vue:263,458`、`views/business/recycle/index.vue:169` | 后台条目与回收站的状态筛选 / 文案映射 |
| `types/api/business/item.ts:10` | 类型注释「状态（WANT/DONE）」 |

**共享代码回归要求**（需求 §5.6）：本期触及状态模型、`SgjDashboardMapper`、`AppHomeServiceImpl` 三处共享位置，PR 描述须列出回归过的存量流程：四模块「想看/看过」筛选、标记与取消标记、首页时间线分页、心愿单「完成 →」、后台条目列表与回收站。

## 7. 明确不在架构层引入

- 不引入 Java 枚举类或状态机引擎：`status` 是自由文本列，字典才是值域的事实来源，加枚举类会立刻产生「枚举 vs 字典 vs 数据库」三处口径；
- 不新增进度字段（第几集 / 第几页，D-10 已拍板不做）；
- 不做提醒 / 通知（本期无调度、无新定时任务）；
- 不做 `sgj_item.is_public`（D-21 已拍板不提前）；
- 不做行程清单（`sgj_item_todo` 等新表留在 [pending.md](../requirements/pending.md) §3.1）。
