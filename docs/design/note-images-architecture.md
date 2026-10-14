# 图片搬运 —— 架构说明（0.0.2 FR-002-9，Issue #56）

> 配套：[需求 §3.3 FR-002-9](../requirements/0.0.2-note.md)、[详细设计](note-images-design.md)、[测试用例](note-images-testcases.md)、[设计图](mockups/note-images/index.html)。
> 本文只讲**结构与边界**：谁产出引用、谁消费引用、谁回收文件，以及为什么选这套结构。具体规则与逐条决策见详细设计。

---

## 1. 三个参与者，一条闭环

图片搬运把「笔记正文」接进了文件存储体系。此前 `/profile` 下的文件只有一种引用形态：**独立列**里的一个完整值（`sgj_item.cover_url` 一列一个文件、`sgj_item_photo.url` 一行一个文件、`sys_user.avatar`）。笔记正文不一样——它是**一份长文本里嵌着若干条路径**。这一个差别派生出了本次的全部设计。

| 参与者 | 位置 | 职责 | 本次变化 |
| --- | --- | --- | --- |
| **写入侧** | 浏览器（`views/front/note/index.vue`、`views/business/note/index.vue`、`components/MarkdownEditor`） | 解析 md、匹配本地图片、上传、改写正文引用、插入图片语法 | 新增搬运弹窗与编辑器插图入口（唯一新增的用户可见路径） |
| **承接侧** | `shiguangji-admin`（`CommonController`）+ `shiguangji-file`（`FileStorageService` + `sys_file` 台账） | 落盘、命名、登记台账、按 `/profile/**` 提供读取 | 新增 `POST /common/upload/note-image`（只收图片、uuid 命名） |
| **读取侧** | 浏览器（`components/MarkdownViewer`） | 把正文里的 `/profile/...` 变成可显示的图片 | 新增 DOMPurify 钩子补 API 前缀（此前只有它不补） |
| **回收侧** | `shiguangji-quartz`（`FileCleanupTask`）+ `shiguangji-business`（`FileReferenceMapper`） | 每周判活：不在「被引用集合」里且过了保护期的文件即回收 | 引用集合新增「笔记 / 草稿正文里的 `/profile` 引用」 |

## 2. 端到端数据流

**导入搬运**

```
用户选 note.md
   │  FileReader（本地，不上传）
   ▼
parseNoteMarkdown ──> prefill{title, content, tags, noteId} + localImageRefs[]
   │                        （localImageRefs 已排除 http(s)/data:/profile）
   ├─ 往返识别 / 草稿冲突确认（既有，两个确认框都在上传之前）
   ▼
NoteImageCarryDialog ── 用户选 文件夹 / 多张图片 / 跳过
   │
   ├─ resolveImageFiles(refs, candidates, mdFileName)   ← 纯函数：相对路径 → basename 唯一
   ▼
逐张 POST /common/upload/note-image（限并发 3）
   │  返回 { path: "/profile/upload/2026/10/10/<uuid>.png" }
   ▼
rewriteNoteImageRefs(content, refs, mapping)  ← 纯函数：只换命中的，未命中保留原样
   │
   ▼
既有预填链路（结论 41/42/44 不变）→ 编辑页 → 用户点保存 → sgj_note.content
```

**渲染**

```
sgj_note.content ──> marked ──> DOMPurify.sanitize
                                    │  afterSanitizeAttributes 钩子
                                    │  img[src] → photoUrl()：站内相对路径补 VITE_APP_BASE_API
                                    ▼
                            <img src="/dev-api/profile/upload/...">
                                    │  vite dev 代理 /dev-api → 后端 → /profile/**（本地磁盘或 S3）
                                    ▼
                                 图片显示
```

**回收（每周一 03:00）**

```
stored  = 存储列举 upload/avatar/cover          ┐
recorded = sys_file 台账全量                    ├─> candidates
                                                ┘
referenced = 三列（cover_url / photo.url / avatar）
           ∪ ProfileRefExtractor.selectReferenced(笔记 + 草稿正文, candidates)
                                                │
        孤儿 = candidates − referenced，且最后可见时间已过 7 天保护期
                                                │
        比例兜底（>50% 且候选 ≥20 → 中止本轮）→ 删文件 + 删台账
```

## 3. 三条不变量

架构的全部约束可以压缩成三条。实现时任何一处违反它们，症状都不报错。

| # | 不变量 | 违反后的症状 |
| --- | --- | --- |
| **I-1** | **正文里只出现站点相对路径 `/profile/...`**，不出现绝对 URL、自定义 scheme | 绝对 URL 把 host 焊进正文：换端口 / dev↔prod / 换域名后图全裂；自定义 scheme 要求两侧都认得 |
| **I-2** | **凡是能出现在正文里的 `/profile` 引用，回收侧都必须认得** | 转存进来的图在下一轮清理中被静默删除——不报错，只是图没了（这也是为什么回收侧必须一起改，不能只做上传） |
| **I-3** | **渲染侧统一补 `VITE_APP_BASE_API` 前缀**（`photoUrl()` 的语义） | dev 下去请求 `5173/profile/...`（代理表只有 `/dev-api` 与 `/v3/api-docs`，未代理 `/profile`）→ 拿不到图（404）；生产下取决于 nginx 是否把 `/profile` 也转到后端 |

## 4. 生命周期时间线（一个搬运进来的文件）

| 时刻 | 事件 | 判活状态 |
| --- | --- | --- |
| T0 | 用户确认搬运，文件落 `upload/...`、登记台账、正文改写为 `/profile/...` | 被引用（正文） |
| T0+15s | 编辑页草稿自动保存把同一份正文推给 `sgj_note_draft` | 被引用（草稿也行） |
| T0+分钟 | 用户点保存 → `sgj_note.content` 落库、草稿行删除 | 被引用（笔记正文） |
| —— 若用户全程放弃 —— | | |
| T0 | 文件与台账已存在，但正文从未落库 | **未被引用**，等待保护期 |
| T0+7d | 孤儿清理发现它不在 referenced 里 | 删除文件 + 台账（**结论 60 的收益**） |
| —— 若笔记后来被彻底删除 —— | | |
| Tn | `purge` 物理删除笔记行 | 不再被引用，7 天后回收 |
| Tn | 笔记**软删**（进回收站） | **仍被引用**（回收站可恢复，与既有三列口径一致，不过滤 `del_flag`） |

## 5. 模块与文件

| 模块 | 文件 | 是否新增 |
| --- | --- | --- |
| 前端 · 纯逻辑 | `src/utils/noteImages.ts` | 新增 |
| 前端 · UI | `src/components/NoteImageCarryDialog/index.vue` | 新增 |
| 前端 · UI | `src/components/MarkdownEditor/index.vue`（插图 + 粘贴） | 改动 |
| 前端 · 渲染 | `src/components/MarkdownViewer/index.vue`（前缀钩子） | 改动 |
| 前端 · API | `src/api/common/upload.ts` | 新增 |
| 前端 · 入口 | `views/front/note/index.vue`、`views/business/note/index.vue` | 改动 |
| 后端 · 接口 | `admin/.../controller/common/CommonController.java` | 改动 |
| 后端 · 常量 | `common/.../utils/file/MimeTypeUtils.java` | 改动 |
| 后端 · 存储 | `shiguangji-file`（`FileStorageService` / `sys_file`） | **复用，不改** |
| 后端 · 回收 | `shiguangji-file/.../cleanup/ProfileRefExtractor.java` | 新增 |
| 后端 · 回收 | `quartz/.../task/FileCleanupTask.java`、`business/.../FileReferenceMapper.{java,xml}` | 改动 |
| 数据 | —— | **无表结构变更** |

## 6. 依赖与边界

- **新增前端依赖：无**。目录用 `webkitdirectory`（Chrome / Edge / Firefox / Safari 均支持），多选用 `multiple`；不引 JSZip。理由：不打包压缩包也能覆盖 Obsidian 的 vault 布局（`note.md` + `attachments/*.png`），而 zip 要引入解包依赖并自己处理大文件与重复项。
- **新增后端依赖：无**。端点复用 `FileStorageService` 与 `sys_file` 台账。
- **依赖既有能力**：`el-input` 的内层 `textarea`（光标插入）、`DOMPurify` 的钩子机制、`photoUrl()` 的前缀语义、`FileUploadUtils.uuidFilename` 的命名、`OrphanFileReconciler` 的对账与保护期。
- **鉴权边界**：上传端点需登录（`/common/**` 未 `permitAll`）；`GET /profile/**` 匿名可读（既有，与封面/相册/头像一致，本次不收紧，见设计 结论 61）。
- **dev 环境边界**：`/profile/**` **不在** vite 代理表里，所以渲染侧必须补 `VITE_APP_BASE_API`（`/dev-api/profile/...` 才会被代理到后端）。这条是 I-3 的来源。
- **数据边界**：前台读接口一律经 `AppScopeHelper`；本次不改任何读接口，也不新增读接口。

## 7. 被否掉的四种架构（连同否掉的理由）

| 备选 | 为什么不做 |
| --- | --- |
| **服务端接收 zip 解包转存** | 要额外解决四件事：解包临时目录的生命周期、zip 炸弹与重复项、上传体积上限、解包产物谁来删；收益只是「用户少选一次文件夹」。而且整包上传会把没被引用的图也搬进来，反而给回收侧添负担 |
| **「上传即入库」：解析完直接建笔记，图片随后补** | 与结论 34/37 已否的旧方案同源：解析失败或产物有问题时会在库里留下垃圾笔记；且丢掉了「保存前可改标题/标签/公开性」这一步。搬运因此必须发生在**预填之前、保存之前**，而不是服务端 |
| **正文存绝对 URL `http://host/profile/...`** | 直接把 host 焊进正文（违反 I-1）：dev 上搬的图到 prod 全裂，换端口全裂。全仓库既有约定是相对路径 + 渲染侧补前缀 |
| **图片存专用目录 `note/` 且不纳入清理** | 零误删风险（`download`/`import` 两个目录有先例），但等于给清理留一个永久的洞：删除的笔记其图永不回收。而且台账查询是全局的——只要登记了就会被当候选，最后仍会被删，所以连记账都不能做，一致性地烂掉。**采用方案 A（正文引用并入 referenced）后，图片照常被回收，且不依赖新目录** |
