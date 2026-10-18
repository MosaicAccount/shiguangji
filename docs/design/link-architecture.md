# 0.0.5 链接 —— 架构文档

> 对应需求：`docs/requirements/0.0.5-link.md`（FR-005-1 ~ FR-005-7）
> 详细设计：`link-design.md`；测试用例：`link-testcases.md`；原型图（视觉基准）：`docs/design/mockups/link-0.0.5/index.html`

## 1. 定位与总原则

链接是**读时解析层**：互链是正文里的文本标记（`[[标题]]`），反链从正文实时派生，搜索复用既有全文索引。零表结构变更、零字典变更、零写入路径改造、零新依赖。架构上遵循四条：

1. **服务端是唯一裁决者。**「哪个 `[[...]]` 算链接、指向哪篇」全部在服务端判定（解析、可见性、重名），前端只按下发的 `links` 做字面量替换——前端发现但服务端没下发的 token 保持原文。两端不存在两套会漂移的解析器。
2. **口径复用，不另写过滤。** 链接目标与反链来源的可见性走 `AppScopeHelper.resolveCreateBy()`（匿名＝publicOwner 的公开笔记；其他登录用户＝本人；博主/管理员＝全量）；笔记侧搜索复用 `selectSgjNoteFrontList` 的可见性、摘要与全文兜底；条目侧复用 `selectSgjItemVo` 的联查口径。
3. **SQL 只做候选，精确判定在 Java。** 库的排序规则不区分大小写（`utf8mb4` CI），所以 `title in (...)` 与 `content like ...` 只缩小范围，标题的逐字相等（含大小写）在 Java 里用 `String.equals` 收口；LIKE 前通配符不走索引，个人量级可接受。
4. **不碰既有查询。** 新增 mapper 查询独立成段，不改 `listWhere` / `selectSgjItemVo` 的任何一行；笔记列表、后台、导出的响应形状不变（详情接口仅新增出参字段）。

## 2. 数据流

### 详情页（互链渲染 + 反链）

```
/note/detail?noteId=（Vue 页面）
  │  api/front/note.getFrontNote
  ▼
GET /app/note/{noteId}                     @Anonymous，可见性校验照旧
  │  SgjNoteServiceImpl.selectSgjNoteById   → note.body（剥前言/去重标题，既有逻辑）
  │  SgjNoteServiceImpl.attachLinkContext(note, createBy, anonymous)   ← 新增
  │    ├─ SgjNoteLinkUtils.extractNoteTokens(body)
  │    │     掩码（代码/行内代码/链接/图片/HTML）→ 等长 → 正则抽 token
  │    ├─ SgjNoteMapper.selectNoteRefsByTitles(titles, createBy, publicOnly)   ← 新增
  │    │     候选 → Java 按标题逐字分组：1 篇成链 / 0 篇未找到 / ≥2 篇重名
  │    ├─ 同名计数（含本篇）：>1 则反链区块不列来源
  │    └─ SgjNoteMapper.selectBacklinkCandidates(likeTitle, excludeSelf, …)   ← 新增
  │          候选 → 逐篇 buildBody + 掩码 + 精确校验（代码块里的不算）→ 片段
  ▼
响应 data = note + links[] + backlinks[] + sameTitleCount
  ▼
前端：marked 渲染 → linkifyNoteTokens(等 links 替换文本节点) → 大纲 → 关键词高亮
```

一次请求拿到全部渲染所需，**不为正文里的每条链接单独发请求**。

### 全局搜索

```
头部搜索图标 → /search?keyword=
  │  api/front/search.searchFront
  ▼
GET /app/search?keyword=                   @Anonymous
  │  AppSearchController（新）
  │  AppScopeHelper.resolveCreateBy() / isAnonymous()
  ▼
AppSearchServiceImpl（新，business）
  ├─ 影单：SgjItemMapper.selectSearchItems(['MOVIE','TV'], title, createBy)   ← 新增，混排取前 10
  ├─ 书单：selectSearchItems(['BOOK'], …)
  ├─ 足迹：selectSearchItems(['PLACE'], …)
  ├─ 笔记：selectSgjNoteFrontList(keyword=…)   ← 复用全文检索 + like 兜底 + 摘要
  │    （各组分页 limit 10，total 取自 PageHelper；结果不下发 comment/remark）
  ▼
{ groups: [ {key, total, items} ×4 ] }   空组由前端隐藏
```

## 3. 模块与文件清单

### 后端（shiguangji-server）

| 文件 | 职责 | 新增/修改 |
| --- | --- | --- |
| `shiguangji-business/.../utils/SgjNoteLinkUtils.java` | 掩码、token 抽取、目标解析、反链精确校验、LIKE 转义（纯函数） | 新增 |
| `shiguangji-business/.../domain/NoteLink.java` / `NoteBacklink.java` | 详情出参 DTO（非表字段） | 新增 |
| `shiguangji-business/.../domain/SgjNote.java` | 增加 `links` / `backlinks` / `sameTitleCount` 非表字段 | 修改 |
| `shiguangji-business/.../mapper/SgjNoteMapper.java` + XML | `selectNoteRefsByTitles`、`selectBacklinkCandidates` | 修改 |
| `shiguangji-business/.../service/impl/SgjNoteServiceImpl.java` | `attachLinkContext`：解析编排（判定逻辑收敛在 SgjNoteLinkUtils 静态纯函数，可脱离 DB 单测） | 修改 |
| `shiguangji-business/.../service/IAppSearchService.java` + `impl/AppSearchServiceImpl.java` | 聚合四组检索 | 新增 |
| `shiguangji-business/.../mapper/SgjItemMapper.java` + XML | `selectSearchItems`（`item_type in` + 标题 LIKE + 范围） | 修改 |
| `shiguangji-admin/.../controller/business/AppNoteController.java` | 详情接口挂 `attachLinkContext` | 修改 |
| `shiguangji-admin/.../controller/business/AppSearchController.java` | `GET /app/search`，`@Anonymous`，关键词校验 | 新增 |
| `shiguangji-business/src/test/.../SgjNoteLinkUtilsTest.java` | 解析/掩码/校验纯函数单测 | 新增 |
| `shiguangji-admin/src/test/.../AppNoteLinkApiSmokeTest.java` | 详情 links/backlinks 冒烟（夹具注入 `[[...]]` 正文） | 新增 |
| `shiguangji-admin/src/test/.../AppSearchApiSmokeTest.java` | 搜索接口冒烟 | 新增 |

### 前端（shiguangji-web）

| 文件 | 职责 | 新增/修改 |
| --- | --- | --- |
| `src/utils/noteLinks.ts` | `linkifyNoteTokens`（DOM 文本节点替换，跳过 code/pre/a）、`isLinkableTitle` | 新增 |
| `src/types/api/business/note.ts` | `NoteLink` / `NoteBacklink` 类型；`SgjNote` 出参字段；查询参数补 `title` | 修改 |
| `src/api/front/search.ts` + `src/types/api/front/search.ts` | `searchFront(keyword)` | 新增 |
| `src/views/front/note/detail.vue` | 渲染顺序（互链→大纲→高亮）、反链区块、`a.note-link` 事件委托、同路由换 noteId 重载 | 修改 |
| `src/components/NoteBacklinks/index.vue` | 反链面板（来源/时间/片段；重名说明态） | 新增 |
| `src/components/NoteLinkPicker/index.vue` | 插入互链选择器（按标题搜、同名合并、特殊标题置灰） | 新增 |
| `src/components/MarkdownEditor/index.vue` | 工具栏「插入笔记链接」+ 行内（不加空行）光标插入 | 修改 |
| `src/views/front/note/edit.vue` | 标题变更且存在引用时保存前确认 | 修改 |
| `src/views/front/search/index.vue` | `/search` 聚合页 | 新增 |
| `src/layout/front/index.vue` | 头部搜索入口（桌面 + 移动，导航项数量不动） | 修改 |
| `src/router/index.ts` + `src/utils/publicRoutes.ts` | `/search` 路由与免登录白名单（连同 spec） | 修改 |
| `src/utils/__tests__/noteLinks.spec.ts`、`publicRoutes.spec.ts` | 前端单测 | 新增/修改 |

## 4. 升级路径（已接受的天花板）

- **反链是详情页一次 LIKE 扫描 + 精确校验**：`content` 为 longtext、前通配不走索引，千篇量级可接受；笔记量或公开访问量上来后新增 `sgj_note_link` 关系表 + 一次性回填，`attachLinkContext` 的出参形状不变（`ponytail:` 注释标注）。
- **搜索每组固定前 10 条**：不做分页与组内展开；`PageHelper` 的 `total` 如实下发，前端组头展示，关键词更具体即可收窄。
- **`[[...]]` 非标准 Markdown**：导出/外部渲染器中原样保留，属已知可移植性代价（需求 §6）。
