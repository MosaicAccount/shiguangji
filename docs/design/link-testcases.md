# 0.0.5 链接 —— 测试用例

> 需求：`docs/requirements/0.0.5-link.md` §5；设计：`link-design.md`。
> 执行命令：后端 `JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl shiguangji-admin -am test`（需 MySQL 13306 / Redis 16379）；前端 `npx vitest run`、`npx vue-tsc --noEmit`、`npm run build:prod`。

## A. 后端纯函数单测（`SgjNoteLinkUtilsTest`，不依赖数据库）

| # | 用例 | 期望 |
| --- | --- | --- |
| A1 | 行内代码不解析 | `` 说明 `[[不是链接]]` 尾 `` → 掩码后该区间为空白，token 仅含正文里的标记 |
| A2 | 围栏代码不解析 | ``` 围栏内含 `[[x]]` 与 ```` ``` ```` 嵌套 ``` → 原样保留；围栏外标记正常抽出 |
| A3 | 链接/图片/引用式链接不解析 | `[看 [[x]]](u)`、`![alt [[y]]](u)`、`[文 [[z]]][ref]` 的区间被掩码 |
| A4 | HTML 标签不解析 | `<div>[[a]]</div>` 中标签本身掩码，文本节点 `[[a]]` 正常 |
| A5 | 括号内空白等价 | `[[ 标题 ]]` 与 `[[标题]]` 抽出的 `title` 相同，`raw` 保留原样 |
| A6 | 非法形态原样 | `[[]]`、`[[x`、`[[a [[b]] c]]`、标题含换行 → 不产生 token |
| A7 | distinct raw 去重 | 同一 raw 多次出现只留一条（首现下标）；不同空白变体各成一条 |
| A8 | 掩码等长 | 任意输入（含中文、emoji 前后对照长度）掩码后 `length()` 与原文一致、换行位置不变 |
| A9 | 目标解析三态 | 候选 1 篇 → noteId；0 篇 → `NOT_FOUND`；2 篇 → `AMBIGUOUS`；候选含大小写仅异的标题（`Vue` vs `vue`）不并入同一组 |
| A10 | 反链精确校验 | 候选正文代码块里有 `[[标题]]`、普通文本里没有 → 不算引用（hits=0）；普通文本命中 2 处 → hitCount=2、片段含首现前后文 |
| A11 | 片段下标 | 片段取自原文（非掩码），折叠空白后包含 token 原文；token 靠近首/尾时片段不越界 |
| A12 | LIKE 转义 | `50%`、`a_b`、`C:\x` → `%`/`_`/`\` 均被转义，拼出的模式不会误吞其他行 |
| A13 | 空输入 | 正文 null/空、无 token、候选空 → 各方法返回空集合而非 null / 不抛错 |

## B. 后端接口冒烟（dev profile，夹具标题带 `qat05` 前缀，@BeforeAll/@AfterAll 清理）

`AppNoteLinkApiSmokeTest`：

| # | 用例 | 期望 |
| --- | --- | --- |
| B1 | 登录态详情含解析 | 建两篇互链笔记 A→B；`GET /app/note/{A}` → `links[0].noteId == B`、`raw/title` 正确；`backlinks` 为引用 A 的来源列表（含 snippet/hitCount） |
| B2 | 反链排除自身与回收站 | A 正文含 `[[A自身标题]]` → 不出现在自己的 backlinks；夹具软删一篇后其引用消失 |
| B3 | 代码块假引用 | 来源笔记仅在代码块里写 `[[A]]` → 不出现在 A 的 backlinks |
| B4 | 重名降级 | 另建与 A 同标题的笔记后 → `links` 中指向该标题的条目 `reason=AMBIGUOUS`；A 的 `backlinks` 为空数组、`sameTitleCount >= 2` |
| B5 | 未找到 | `[[qat05 不存在]]` → `reason=NOT_FOUND` |
| B6 | 匿名可见性 | 匿名访问公开笔记 A：指向私密笔记的 token `NOT_FOUND`；backlinks 只含公开来源；响应不含私密来源标题 |
| B7 | 匿名访问私密笔记 | 既有行为不回归（错误响应） |
| B8 | 列表接口不受影响 | `GET /app/note/list` 响应行内无 `links`/`backlinks` 字段 |

`AppSearchApiSmokeTest`：

| # | 用例 | 期望 |
| --- | --- | --- |
| B9 | 登录态聚合 | `GET /app/search?keyword=qat05` → `groups` 顺序 movie→book→place→note；各组 `total` 与 `rows`（≤10）正确；空组不下发 |
| B10 | 匿名聚合 | 匿名只出 publicOwner 的条目与公开笔记；条目行无 `comment`/`remark` |
| B11 | 笔记组复用全文 | 命中正文（非标题）的笔记出现在 note 组且带 `excerpt` 与 `hitTotal` |
| B12 | 门槛兜底 | `keyword=x`（1 字）与空关键词 → 业务错误「关键词至少 2 个字」 |
| B13 | 无结果 | 唯一关键词无命中 → `groups` 为空数组（200，不报错） |

## C. 前端单测（vitest，jsdom）

| # | 用例 | 期望 |
| --- | --- | --- |
| C1 | linkify 成链 | 含 raw 的文本节点替换为 `a.note-link`，href 含 noteId，显示 title |
| C2 | linkify 降级 | `NOT_FOUND`/`AMBIGUOUS` → `span.note-link.is-broken`，`title` 提示分别为「笔记未找到」「标题重名，无法确定目标」，不可点 |
| C3 | 跳过代码与既有链接 | `code`/`pre`/既有 `a` 内的 raw 原样保留 |
| C4 | 服务端未下发不动 | DOM 里的 `[[x]]` 若不在 links 中 → 保持原文（无降级样式） |
| C5 | 幂等 | 连续调用两次不产生嵌套替换 |
| C6 | 多次出现全替换 | 同一 raw 在一段里出现 2 次 → 两处都替换 |
| C7 | `isLinkableTitle` | 含 `[`/`]`/换线 → false；普通标题 → true |
| C8 | 白名单 | `isPublicRoute('/search') === true`；`/plan`、`/review` 仍 false（publicRoutes spec 扩展） |

## D. 回归（手动 + 既有测试）

| # | 用例 | 期望 |
| --- | --- | --- |
| D1 | Markdown 渲染 | 标题/列表/代码块/表格/图片/链接/任务列表与引言块渲染无回归；大纲与关键词高亮正常（顺序：互链→大纲→高亮） |
| D2 | 笔记既有流程 | 列表摘要与命中计数、导入导出（token 原样往返）、草稿箱、回收站不回归 |
| D3 | 条目既有流程 | 影/书/足迹列表检索、`?itemId=` 深链、`?type=` 预选不回归 |
| D4 | 私人页 | `/plan`、`/review` 登录可见、匿名 401/弹登录不回归 |
| D5 | 后台 | 后台笔记编辑器「插入笔记链接」可用且不破坏既有插图链路；后台列表无新字段 |
| D6 | 全量检查 | 四条命令全部通过 |

## E. 人工验收清单（对照原型 `mockups/link-0.0.5/index.html`）

- [ ] 详情页：成链可点（跳转正确、不带旧关键词）；未找到/重名降级样式可区分、悬停有原因；代码块内 `[[...]]` 原样；
- [ ] 反链区块：位置在 `· 完 ·` 后、桌面/移动一致；来源标题可点、时间与片段正确；重名时改显说明；0 条不渲染；
- [ ] 编辑器：工具栏入口 → 搜索/加载更多/同名合并/特殊标题置灰；插入落在光标处且不加空行；预览保持原样文字并有提示；
- [ ] 改名提醒：改标题保存弹确认（计数正确），「返回修改」不落库，「继续保存」成功且不改任何他人正文；未改名保存不打扰；
- [ ] 搜索：头部入口（桌面+移动）→ `/search`；2 字门槛、四组顺序、空组隐藏、组头总数、每组 ≤10；条目深链按矩阵跳转；笔记结果高亮与「文中 N 处」；空态/失败态；URL 直链与回退；
- [ ] 夜间模式与移动端全流程可用。
