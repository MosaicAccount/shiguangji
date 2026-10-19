# 0.0.4 回顾 —— 测试用例

> 需求：`docs/requirements/0.0.4-review.md` §5；设计：`review-design.md`。
> 执行命令：后端 `mvn -pl shiguangji-admin -am test`（需 MySQL 13306 / Redis 16379）；前端 `npx vitest run`、`npx vue-tsc --noEmit`、`npm run build:prod`。

## A. 后端（`AppReviewApiSmokeTest`，dev profile 冒烟 + `AppReviewServiceImpl` 标签拆分单测）

| # | 用例 | 期望 |
| --- | --- | --- |
| A1 | 登录态 `GET /app/review/year?year=2024` | 200；`years` 降序且含 2024；`summary` 各值与库内 `finish_date` 归年口径一致（手工 SQL 核对） |
| A2 | 匿名访问同接口 | 401（接口未标 `@Anonymous`） |
| A3 | `year=abc` / `year=1799` | 400，`msg` 含「year」 |
| A4 | 汇总口径 | `summary.total == movie+book+place(+其他类型若有)`；与首页 `doneTotal` 按年切片一致；`finish_date` 为空的 DONE 条目不计入任何年 |
| A5 | 跨年归因 | `create_time=2023`、`finish_date=2024` 的条目只出现在 2024 |
| A6 | 笔记归年 | 笔记按 `create_time` 归年，与条目年份并集构成 `years` |
| A7 | 地点字段 | `places` 按 `finishDate` 升序；含 `province/country/latitude/longitude/rating/comment/tags/coverUrl/photoCount`；境外点 `country` 为国名、`province` 为 null 不 NPE |
| A8 | 无坐标地点 | `latitude`/`longitude` 为 null 的地点仍在 `places` 里（下发不丢），字段为 null |
| A9 | 评分榜 | 只含 `rating` 非空者；`rating desc, finish_date desc`；MOVIE/BOOK 各 ≤ 5 条；`ratedCount` 与 `count(*) … rating is not null` 一致 |
| A10 | 标签拆分（Service 单测，不依赖库） | `"人文, 美食,,人文"` → `人文:2, 美食:1`；空串 / 空格 / null 行为确定；同 count 按名字字典序 |
| A11 | 空年份 | `year=1999`（无数据）→ 200，`summary` 全 0、数组全空（不 404） |
| A12 | 范围隔离 | 其他登录用户（非博主非管理员）只看到自己的年度数据（`resolveCreateBy` 口径） |

## B. 前端纯函数（`mapLayout.spec.ts`，vitest）

| # | 用例 | 期望 |
| --- | --- | --- |
| B1 | 分档 | 10 个地点（桌面容器）逐点；11 个聚合；容器 < 768px 时 5 个逐点、6 个聚合 |
| B2 | 等比投影 + 自动缩放 | 南北半球混合（悉尼 -33.86）不越界；所有点落在 `[14,86]` 区间；单点居中 (50,50)；纵横比例不被拉伸（对两点验证 x/y 缩放系数一致） |
| B3 | 并簇 | 距离 5% 内的两点并为一簇、位置取均值、簇内保持时间序；距离 6% 的不并 |
| B4 | 标签侧 | 邻居均值在下方 → `up=true`；端点只看唯一邻居；贴边 `x>74` → `edge='r'`、`x<26` → `edge='l'` |
| B5 | 连线收边 | 每段两端距圆心 `≥27px`（按向量验算）；段长 `≤54px` 的段被丢弃；空簇 / 单簇时无路径输出 |
| B6 | 聚合阶梯 | 15 地点跨 13 省 + 2 国 → 按「省」15 泡 > 12 → 自动「大区」8 泡；集中型（6 省）保持「省」；境外在省档按国家成泡、大区档并入「境外」；`province/country` 皆空归「未分类」不 NPE |

## C. 前端页面与守卫（vitest 组件 / 路由测试 + 手动）

| # | 用例 | 期望 |
| --- | --- | --- |
| C1 | 白名单 | `isPublicRoute('/review') === false`（`publicRoutes` spec 补断言） |
| C2 | 状态机 | loading 渲染骨架且无报头；空数据渲染空态面板、四数归零；有数据渲染五区块 |
| C3 | 年份切换 | `‹ ›` 步进含无数据年（步进到即空态）；胶囊只列 `years`；`›` 在最大年禁用 |
| C4 | 足迹空坐标 | 含 null 坐标地点时图内出现「没填坐标，没落在图上」交代，无控制台错误 |
| C5 | 弹层 | 点图钉开点详情（字段齐）；点聚合泡开组地图；组地图内点图钉下钻到点详情；`×` 关闭 |
| C6 | 高德懒加载 | 首屏（未开弹层）网络请求无 AMap SDK；首次打开组地图后 `loadAMap` 被调用且仅一次 |
| C7 | SDK 失败降级 | mock `loadAMap` reject → 弹层显示失败态 + 清单可用，不白屏 |
| C8 | 访客入口 | 未登录首页不渲染回顾入口卡；登录后渲染 |
| C9 | 移动端 | 视口 < 768px：报头单列、统计卡两列、逐点阈值 5、弹层为底部抽屉形态 |

## D. 回归（手动 + 既有测试）

| # | 用例 | 期望 |
| --- | --- | --- |
| D1 | `publicRoutes` 既有 spec | 全绿（白名单未动） |
| D2 | 首页 | 时间线分页、访客脱敏与心愿清零不变；hero 排新增回顾卡后窄屏换行正常 |
| D3 | `/plan`、`/travel` | 页面与接口行为不变（本期未改其代码路径） |
| D4 | 全量检查 | `npx vue-tsc --noEmit`、`npx vitest run`、`npm run build:prod`、`mvn -pl shiguangji-admin -am test` 全部通过 |

## E. 人工验收清单（对照原型 `review-page.html`）

- [ ] 报头：年份与说明句基线对齐；统计卡四张等高；口径行文案逐字一致；
- [ ] 足迹：点阵纸底 + 指北针 + 抖线框；「手绘示意 · 非比例地图」在左下、「点任一点 → 高德看真实位置」在左上；连线不穿圈、不压标签；
- [ ] 聚合档标注所用粒度；「散 15」型数据自动降「按大区 8 个」；
- [ ] 弹层弹性展开动画（过冲）与原型一致；
- [ ] 空态文案「{year} 年还没有记录」；加载骨架为手绘形态（非灰块）。
