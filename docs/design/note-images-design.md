# 图片搬运（0.0.2 FR-002-9，Issue #56）—— 技术决策与详细设计

> **范围**：带图笔记导入时把图片一起搬进来（正文引用改写为站内 `/profile` 路径），并给笔记编辑器补上插图入口。
> **结论编号接续** [`note-0.0.2-design.md`](note-0.0.2-design.md) 的 47 续编（48~61），引用时统一写「设计 结论 N」。
> **本文件的三份配套**：[需求 §3.3 FR-002-9](../requirements/0.0.2-note.md)、[架构](note-images-architecture.md)、[测试用例](note-images-testcases.md)、[设计图](../design/mockups/note-images/index.html)。
> **前置事实**（读本文件前先接受这三条，方案是为它们让路的）：
> ① 导入**全程在浏览器本地**完成（结论 34），服务端从未见过那个 md 文件；
> ② `vite.config.ts` 的 dev 代理只有 `/dev-api` 与 `/v3/api-docs`，**`/profile/**` 不在其中**，因此正文里的 `/profile/...` 必须由渲染侧补前缀才显示得出来；
> ③ 每周的孤儿文件清理按「业务列 ∪ 台账」判活，正文**不在**引用来源里——转存进来的图不解决这一点，7 天后会被静默删掉（不报错，只是图没了）。

---

## 1. 方案一句话

**导入仍是浏览器本地解析；解析完若正文里有本地图片引用，就多一步「指出这些图在哪」（选文件夹或选图片），前端按引用匹配后逐张上传到 `/profile`，把正文里的引用改写成站内路径，再走既有的预填 → 编辑页 → 保存链路。** 编辑器另加「插图」按钮与粘贴上传，让搬运未命中的图与应用内写带图笔记都有正当手段。

```
选 md ──解析──> 有本地图片引用？ ──否──> 既有预填链路（不变）
                    │
                    是
                    ▼
        「搬运笔记里的图片」：选文件夹 / 选图片 / 跳过
                    │
        匹配（相对路径 → basename 唯一）→ 逐张上传 → 改写正文
                    │
                    ▼
        既有预填链路（结论 41 / 42 / 44 不变）
```

## 2. 技术决策

| # | 性质 | 议题 | 结论 |
| --- | --- | --- | --- |
| 48 | 已拍板 | **方向：搬运在前端，服务端不解包** | 前端拿到 md 与候选图片文件后，只上传**被正文引用到的**那几个文件（20 张附件的 vault 里正文只引用 9 张，就只传 9 张），再把引用改写成 `/profile/...`。**不做服务端接收 zip**：那要额外解决临时目录生命周期、zip 炸弹、上传体积上限、解包产物清理四件事，收益只是「少选一次文件夹」。**零新依赖**：目录用 `<input type="file" webkitdirectory>`，多选用 `multiple`，不引 JSZip。 |
| 49 | 已拍板 | **一次一篇：搬运是导入的第二步** | 第一步仍是「选一个 md」（不变），第二步只在正文有本地图片引用时出现，且出现的时机在两个既有确认框（往返识别、草稿冲突）**之后**——用户若选「先去看看」，一张图都不会上传。不推翻 0.0.2 非目标「不做笔记目录整体导入」：选整个 vault 目录只用来**给这一篇笔记找图**，不批量导入多篇。 |
| 50 | 已拍板 | **匹配规则：四级优先，歧义不猜** | 见 §3.2。核心是「相对路径能对上就用相对路径，对不上退到按文件名唯一匹配；同名多个文件视为未命中，不猜」。 |
| 51 | 已拍板 | **未命中一律保留原引用** | 不丢用户内容：找不到的文件，正文里的原引用**一字不动**，并在结果里逐条列出来。丢掉引用或写占位符都是不可逆的信息损失。 |
| 52 | 已拍板 | **新增端点 `POST /common/upload/note-image`** | 参数 `file`；**需登录**（`/common/**` 未 `permitAll`，与既有上传一致）；只收图片 `NOTE_IMAGE_EXTENSION = {bmp, gif, jpg, jpeg, png, webp}`（在既有 `IMAGE_EXTENSION` 上补 webp）；`useCustomNaming = true`（`yyyy/MM/dd/<32位hex>.<ext>`）；分类沿用 `upload`；登记 `sys_file` 台账；返回 `{ path: "/profile/upload/..." }`（**只回站内相对路径**，不回绝对 URL，避免被存进正文）。**不复用 `/common/upload`**：它的白名单是 23 项（含 doc/docx/zip/html），正文插图只该收图片；而且它的命名保留原文件名（可能含空格与中文），会把引用推出安全字符集。**不复用 `/common/uploads`（批量）**：它把 `originalFilenames` 用逗号拼回传，文件名含逗号即错位；逐张上传才能报出「哪一张失败」。 |
| 53 | 约束 | **正文只存站点相对路径 `/profile/...`** | 与全仓库既有约定一致（封面 `cover_url`、相册 `sgj_item_photo.url`、头像 `avatar`）。存 `http://host/profile/...` 会把 host 焊进正文——换端口、dev↔prod、将来换域名全部失效；存 `file://`、`attachment://` 之类自定义 scheme 则要两侧都认得，得不偿失。 |
| 54 | 缺陷 | **`MarkdownViewer` 是唯一不补前缀的渲染面** | 全仓库的图片都走 `utils/sgj.ts:photoUrl()`（补 `VITE_APP_BASE_API`）或 `VITE_APP_BASE_API + res.fileName`；只有 `MarkdownViewer` 把 `marked` 产出的 `src` 原样交给 DOM。dev 下去请求 `http://127.0.0.1:5173/profile/...` → 404（`vite.config.ts` 的代理表只有 `/dev-api` 与 `^/v3/api-docs/(.*)`，**没有 `/profile`**；`/dev-api/profile/...` 才会被代理到后端）。修法：给 `DOMPurify` 挂 `afterSanitizeAttributes` 钩子，把 `img[src]` 过一遍 `photoUrl()`（外链 / `data:` / `blob:` 原样）。钩子**必须只注册一次**（仿文件里既有的 marked 单例守卫）：dev 的 HMR 会重复执行模块，重复挂钩会让第二次前缀变成 `/dev-api/dev-api/...`。 |
| 55 | 已拍板 | **孤儿对账：把正文引用并入 referenced（方案 A）** | `FileReferenceMapper` 新增 `selectContentsWithProfileRefs()`：取 `sgj_note.content` 与 `sgj_note_draft.content` 里含 `/profile/` 的行（`union all`，不去重）。**不过滤 `del_flag`**：回收站里的笔记可恢复，其图必须留（与既有三列的口径一致）。**草稿必须一起抽**：`sgj_note_draft` 没有自动过期机制（结论 14），一篇编辑了 8 天还没保存的笔记，其图只能靠草稿正文保住。抽取查询失败时**抛异常终止本轮**（照抄 `listStored()` 的做法），绝不比照空集——空集会把整个图库判成孤儿。 |
| 56 | 已拍板 | **抽取以「候选集为字典」做前缀匹配，不用正则猜路径边界** | 见 §4.2。取舍的依据是两种错的代价不对称：**误抽**只会让某个文件少删一次（安全），**漏抽**会让被引用的文件被删（丢图）。正则猜边界一定会在某些形状上漏抽（引用后面紧跟中文、`（` 全角括号、`%20` 编码），而「拿 `/profile/` 之后的宽 token 去候选集里试前缀」不会漏，且与生成侧将来的命名变化解耦。 |
| 57 | 约束 | **候选集必须先算出来** | 引用抽取需要候选集做字典，所以 `FileCleanupTask` 内的顺序调整为：`listStored()` + `listRecorded()` → `candidates` → `referenced = 业务列 ∪ 抽取(candidates)`。保护期（7 天）、比例兜底（50% / 最少 20 个候选）、删除与台账清理逻辑**一律不变**。 |
| 58 | 已拍板 | **编辑器插图入口同批做：工具栏「插图」+ 粘贴上传** | 两条理由：搬运未命中的图在应用内没有正当手段补回（issue 原文即此意）；粘贴截图是最高频的插图入口。插入点在 `el-input` 内层 `textarea` 上做（`selectionStart/End` + `setRangeText`，插入后 `nextTick` 恢复光标——Vue 的 v-model 回流会重置光标位置）。粘贴**只在剪贴板里有图片时**拦截，纯文本粘贴不受影响。两项都走 52 的同一个端点。 |
| 59 | 缺陷 | **改写正文后必须重新校验 100000 上限** | 解析阶段的上限校验发生在改写**之前**，而每处引用改写要加约 60 字符。漏这一步会出现「导入通过、到编辑页保存被拒」。改写后超长**照常预填**并在编辑页提示，不静默回退——用户明确要搬图，回退等于把图丢了；编辑页既有 `contentTooLong` 拦截仍然生效，用户可以删减后保存。 |
| 60 | 收益 | **上传了但没保存的图会被回收** | 搬运是一张张上传的，用户完全可能中途放弃或最后不保存。这些图落在 `upload` 分类、登记了台账、又不在 referenced 里，于是 7 天后由既有的孤儿清理回收——这正是采用方案 A（55）而不是「专用目录永不清理」的原因。同一次导入内同一文件只上传一次（按候选文件去重）；**跨笔记、跨次导入不去重**（按内容哈希去重属非目标）。 |
| 61 | 约束 | **图片匿名可读，本次不收紧** | `SecurityConfig` 对 `GET /profile/**` 是 `permitAll`，与封面 / 相册 / 头像一致。笔记图片路径是 `日期目录 + 32 位 uuid`，不可枚举；不引入每文件鉴权（那要给静态资源加一层代理与签名，与本版主题无关）。已知边界：**私密笔记的图片，知道 URL 的人能打开**。 |
| 62 | 已拍板 | **外链图片不转存，但不静默** | `http(s):` / `//` 开头的引用**不搬运、不改写**——它是绝对地址，导入后浏览器直接去对方服务器取图，本来就能显示，没有可搬的东西（这也是 0.0.2 既有 `LOCAL_IMAGE_PATTERN` 把外链排除在外的本意）。但**必须计数并明说**：解析出 `externalCount`，搬运弹窗写一句「另有 {N} 处是外链图片，不参与搬运；若显示不出来，可用编辑器的『插图』手动替换」——否则一篇 10 张图（7 本地 + 3 外链）的笔记只报「已搬运 7 张」，用户会以为另 3 张失败了。**不做转存**（下载一份到自己服务器、把引用改成 `/profile/...`）：它买到的只是「不依赖对方」（对方删图 / 关站 / 防盗链 / 限速 / `http` 混合内容），属「归档」主题；而代价是服务器要替用户访问任意网址（SSRF、被当图床、替人刷流量）——仓库里豆瓣封面之所以敢抓，是因为白名单**只**放行豆瓣 CDN，笔记外链来自全世界。转存单开 issue 排后续版本（[#68 转存外链图片](https://github.com/MosaicAccount/shiguangji/issues/68)）。`data:` 内嵌图既不算外链也不提示：它随正文一起显示，不会失效。 |
| 63 | 缺陷 | **匹配规则①的 md 目录必须从「未过滤的完整文件树」里取** | 实测（选整个 vault 搬运）踩到：弹窗为「只搬图片」先把候选表滤成图片，而 **md 自己不是图片**，于是「按文件名定位 md、取其所在目录」返回空、**规则①整个静默失效**，引用只能退到「文件名唯一匹配」——`attachments/a.png` 因与 `dup/a.png` 同名被误判为未命中，图不搬、正文里留着未改写的原引用（**不报错**，只是少搬一张；这类静默降级最难查）。修法：`mdDirOf(完整文件列表, md 文件名)` 在**过滤之前**算出目录，`resolveImageFiles(refs, candidates, mdDir)` 不再自己从候选表里找 md（顺带把「md 不在所选树里」显式表达为 `mdDir === ''`，退回规则②③）。回归用例 T-57 同时钉住「mdDirOf 从完整树取目录」与「拿到目录后歧义由精确路径解开」。**第二半（同批加固）**：光修 mdDir 还不够——「平铺多选（`webkitRelativePath` 为空）」与「md 不在所选树里」两种真实场景本就拿不到目录，带目录的引用仍会退到文件名匹配而撞同名。故新增**规则②.5「路径后缀唯一匹配」**：候选路径以引用路径结尾（按路径段对齐）且唯一即命中，`attachments/a.png` 不再受 `dup/a.png` 干扰；同名分处两个父目录时照样判歧义、不猜（T-59）。另外弹窗在选择后打一行 `console.debug('[note-image-carry] 选择结果', {mdFileName, mdDir, 选中文件, 图片候选, 命中, 未命中})`：这类静默降级不看现场日志很难查，mdDir 与候选数是第一现场。 |

## 3. 前端设计

### 3.1 流程与状态

搬运弹窗（新组件 `NoteImageCarryDialog`）的状态机：

| 状态 | 触发 | 展示 | 出口 |
| --- | --- | --- | --- |
| 默认 | 解析出 `localImageRefs.length > 0` | 「正文里有 N 处本地图片引用（去重后 M 个文件）」+ 两个选择按钮 + 「跳过，只导入文字」 | 选文件 / 选文件夹 / 跳过 / 关闭 |
| 上传中 | 选定文件、解析出映射后 | 进度条 + 「正在上传 3 / 9 …」+ 已成功 / 失败计数 | 等完成 |
| 结果 | 上传结束 | 「已搬运 X 张图片，正文引用已改写」+ 未命中的逐条列出（可滚动） | 「完成，去编辑」 |
| 空结果 | 一个引用都没匹配上 | 「没有匹配到任何图片（已保留原引用）」 | 再选一次 / 跳过 |
| 超限 | 单张 > 10MB 或文件数 > 50 | 忽略项提示行 | 继续 |

**关闭弹窗（X / ESC）= 跳过**：不改写正文，已上传的图交给孤儿清理（结论 60）。不存在「半改写」的中间态——改写只在「完成，去编辑」时一次性应用。

### 3.2 匹配算法（纯函数，`utils/noteImages.ts`）

**收集**（`collectImageRefs(content): { local: NoteImageRef[]; externalCount: number }`）：

| 输入形状 | 处理 |
| --- | --- |
| `![](./a.png)`、`![](attachments/a.png)`、`![alt](a.png "标题")`、`![](<a b.png>)` | 取括号内目标；去掉 `"标题"`、外层 `<>`；`%xx` 先解码 |
| `![[a.png]]`、`![[attachments/a.png\|200]]`、`![[a.png#^block]]` | 取 `\|` 与 `#` 之前的部分 |
| 围栏代码块（``` / ~~~）与行内代码里的引用 | **不收集**（那是示例代码，改写它会毁掉示例） |
| `http(s):`、`data:`、`//` 开头 | **不收集**（外链不需要搬）。其中 `http(s):` 与 `//` 要**计数**（结论 62 的提示语），`data:` 不计数 |
| **`/profile/...`** | **不收集**（已搬运。这一条同时是「导出再导入」能无损往返的前提，见 §5） |

实现取「扫行 + 状态机」（围栏开关、行内代码区间），而不是一条更大的正则——一条正则无法同时表达「跳过代码块」与「取出目标」。

**匹配**（`resolveImageFiles(refs, candidates, mdFileName)`，对每个引用依次尝试，命中即止）：

| 优先级 | 规则 | 覆盖的场景 |
| --- | --- | --- |
| 1 | **md 同目录的精确相对路径**：md 的目录由 `mdDirOf(完整文件列表, md 文件名)` 在**过滤成图片之前**算出（`md` 自己不是图片，先滤掉它规则①就静默失效，见结论 63），与引用路径拼接后精确比对 | Obsidian 的 `![](attachments/a.png)`，以及用户同时选了 md 与所在文件夹 |
| 2 | **相对所选根目录的路径** | 用户直接选了 `attachments` 这一层、或 md 不在所选树里 |
| 2.5 | **路径后缀唯一匹配**：候选路径以引用路径结尾（按路径段对齐）且唯一 | 平铺多选（`webkitRelativePath` 为空）、md 不在所选树里时，`attachments/a.png` 仍能对上；同名分处两个父目录 → 唯一性不成立，不猜（结论 63 第二半） |
| 3 | **文件名唯一匹配**（basename，大小写不敏感） | `![[a.png]]`（Obsidian 的短引用，路径由 vault 配置决定）、平铺多选图片 |
| 4 | 以上任一档命中多个 → **未命中** | 同名不同目录的文件，不猜 |

候选文件表由 `{ file, name, relPath }` 组成：`name` = `File.name`，`relPath` = `File.webkitRelativePath`（多选文件时为空串）。

**改写**（`rewriteNoteImageRefs(content, refs, mapping)`）：按收集时的位置索引替换，只替换命中的引用，未命中的原样保留；同一文件被多处引用只上传一次、共用同一个结果。

**上限**：单次最多 50 张、单张 ≤ 10MB（`spring.servlet.multipart.max-file-size`），超出部分忽略并提示；上传限并发 3；上传失败不重试（避免放大失败），该引用计入未命中。

### 3.3 渲染侧

`MarkdownViewer` 的 `DOMPurify` 钩子（结论 54）是「正文存相对路径」能成立的一半。另一半是 `utils/sgj.ts:photoUrl()` 本身的语义：只给站内相对路径补前缀，`http(s):` / `data:` / `blob:` 原样返回。

**导出不受影响**：`buildExportMarkdown` 只写 front-matter 三个键，正文原样输出（结论 39 / 47）。所以导出的 md 里是 `/profile/...`；因为 §3.2 把它排除出「本地图片引用」，**同一实例上导出再导入是无损的**（不会再弹搬运框，也不会误报「本版不搬图片」）。站外可用性（导出带图整包）属对称能力，见 §7 非目标。

### 3.4 界面文案（与设计图逐字对齐）

搬运弹窗：

- 标题：`搬运笔记里的图片`
- 正文：`正文里有 {N} 处本地图片引用（去重后 {M} 处不同路径）。这些文件不在 md 里，需要你指出它们在哪。`
  （M 是**去重后的路径数**，不是文件数：`![[a.png]]` 与 `![x](attachments/a.png)` 是两处不同路径，可能指向同一个文件，也可能像歧义用例那样指向两个；真正上传了几个文件要等选完文件才知道，所以默认态不承诺「文件数」，结果态才报「已搬运 N 张」）
- 说明：`选笔记所在的文件夹最准（相对路径能对上）；也可以直接多选图片文件，按文件名匹配。找不到的会保留原引用。`
- 按钮：`选择图片文件夹`（主）/ `选择图片文件`（次）/ `跳过，只导入文字`（文字按钮）
- 上传中：`正在上传 {i} / {total} …`
- 结果：`已搬运 {X} 张图片，正文引用已改写。`
- 未命中：`{K} 处没找到对应文件，保留了原引用：`（列表最多展示 5 条 + `等 {K} 处`）
- 失败：`{F} 张上传失败，保留了原引用。`
- 忽略：`已忽略 {Z} 个超过 10MB 的文件。` / `一次最多搬运 50 张，超出的已忽略。`
- 空结果：`没有匹配到任何图片（已保留原引用）。`
- 跳过后的列表页提示：`正文里有 {N} 处图片引用未搬运，导入后那些图会显示不出来。`
- 外链说明（`externalCount > 0` 时，出现在弹窗默认态）：`另有 {E} 处是外链图片（https://…），不参与搬运；若显示不出来，可用编辑器的「插图」手动替换。`（结论 62）

编辑器（`MarkdownEditor`）：

- 工具栏按钮：`插图`（图片图标），旁注 `上传后插入到光标处；也可以直接粘贴截图`
- 上传中：按钮 loading，不可重复点
- 失败：`图片上传失败：{msg}`，正文与预览都不变
- 插入格式：`![](/profile/upload/yyyy/MM/dd/<uuid>.<ext>)`，按需在前后补换行；多选文件按顺序每行一张、之间空一行
- 粘贴：仅当剪贴板含 `image/*` 时 `preventDefault` 并上传

## 4. 后端设计

### 4.1 上传端点

| 项 | 值 |
| --- | --- |
| 路由 | `POST /common/upload/note-image`（`CommonController` 内新增方法） |
| 入参 | `MultipartFile file` |
| 鉴权 | 无 `@Anonymous` → `anyRequest().authenticated()`，匿名调用拿到业务码 401（与 `/common/upload` 一致） |
| 校验 | `NOTE_IMAGE_EXTENSION`（新增常量：`bmp/gif/jpg/jpeg/png/webp`）；`FileUploadUtils.assertAllowed` 复用（含 50MB 代码级上限，实际先撞 multipart 的 10MB） |
| 落盘 | `fileStorageService.upload("upload", file, NOTE_IMAGE_EXTENSION, true)` → `/profile/upload/2026/10/10/<32hex>.png` |
| 台账 | 复用既有私有方法 `recordSysFile(file, fileName)`（登记失败只记日志，不影响上传——既有语义） |
| 响应 | `AjaxResult.success().put("path", "/profile/upload/...")` |
| 前端 | `src/api/common/upload.ts`：`uploadNoteImage(file): Promise<NoteImageUploadResult>`，`FormData` 单文件，走既有 `request`（自动带 Bearer） |

**一个实现细节**：`NOTE_IMAGE_EXTENSION` 是独立数组，而 `FileUploadUtils.assertAllowed` 用**引用相等**（`allowedExtension == MimeTypeUtils.IMAGE_EXTENSION`）决定是否抛「图片格式」专用异常，所以新常量会走通用的 `InvalidExtensionException`（提示里仍会列出允许的后缀）。若要保留图片专用文案，就得直接扩展 `MimeTypeUtils.IMAGE_EXTENSION`——那会同时放宽封面 / 头像的上传，属既有 webp 缺口，另开 issue，不在本次范围内。

### 4.2 引用抽取（`ProfileRefExtractor`）

新增纯逻辑类 `shiguangji-file/.../cleanup/ProfileRefExtractor`（与 `OrphanFileReconciler` 同层：不碰 DB、不碰存储，可纯单测）。

```java
/** 从正文里挑出「确实是候选文件」的引用（候选集当字典，见设计 结论 56） */
public static Set<String> selectReferenced(Collection<String> contents, Set<String> candidates)
```

算法：对每段正文用 `indexOf("/profile/")` 逐处定位；从该位置起取**宽 token**（到空白、`"` `'` `<` `>` `|` `(` `)` `[` `]` 或中文标点为止，上限 200 字符）；从 token 的长前缀往短试 `candidates.contains(prefix)`，首个命中即收；再对 URL 解码后的 token 试一次。

为什么不用「正则抽全部引用再与候选集取交集」：正则必须猜路径在哪里结束，猜窄了会漏（漏 = 误删），猜宽了会把后面的中文说明吃进路径（吃进去就不再等于真实 key，也是漏）。以候选集为字典则两边都不猜。代价是抽取要拿到候选集，故结论 57 调整了任务内的计算顺序。

### 4.3 清理任务接线

```java
Map<String, Instant> stored = listStored();
Map<String, Instant> recorded = listRecorded();
Set<String> candidates = new HashSet<>(stored.keySet());
candidates.addAll(recorded.keySet());

Set<String> referenced = new HashSet<>(fileReferenceMapper.selectReferencedStorageKeys());
try {
    referenced.addAll(ProfileRefExtractor.selectReferenced(
            fileReferenceMapper.selectContentsWithProfileRefs(), candidates));
} catch (Exception e) {
    // 抽取失败若按空集处理，正文引用的图会被全部误删，必须中止本轮
    throw new IllegalStateException("读取笔记正文引用失败，已中止本次清理", e);
}
// 之后的比例兜底、删除、台账清理逻辑全部不变
```

`FileReferenceMapper` 新增：

```xml
<!-- 正文里的 /profile 引用（笔记 + 草稿）。
     不过滤 del_flag：回收站里的笔记可恢复，其图必须留。
     草稿没有过期机制，不抽就会误删在编辑中的笔记的图。 -->
<select id="selectContentsWithProfileRefs" resultType="java.lang.String">
    select content from sgj_note where content like '%/profile/%'
    union all
    select content from sgj_note_draft where content like '%/profile/%'
</select>
```

**已知上限**（代码里留 `ponytail:` 注释）：这是每周一次的两表 `LIKE` 全表扫描，且命中行的正文会全部读进内存。个人规模（万级笔记、命中行数只有带图的那几十上百篇）完全够用；量级上来后改为在写入侧维护一张「正文引用行表」，对账改成查表。

## 5. 往返契约（与既有结论的关系）

| 既有结论 | 本次变化 |
| --- | --- |
| 结论 34（导入只做 md、全程不上传） | **修订**：无图片引用时仍然零上传；有引用时，仅上传被用户确认搬运的那几张图。解析仍在浏览器本地完成 |
| 结论 35（图片引用无法转存，「本版不搬图片」） | **作废后半段**：现在搬得了，界面文案随之改写（§3.4）。`data:` base64 内嵌图仍不处理 |
| 结论 37（原件的 union 行一律不做） | **部分重启**：原件仍不留存、`source_file` 列仍不做；但「让孤儿清理认得正文引用」这一条以 55/56 的方式实现（不是给既有三列加 union 行，而是新增一条内容抽取） |
| 结论 39 / 47（导出形状对齐导入侧） | **不变**：导出仍原样输出正文、只写三个 front-matter 键。因为 `/profile/` 不再算「本地图片引用」，往返识别与解析行为都不变 |
| 结论 41 / 42 / 44（预填与草稿对齐、空白草稿冲突、守卫只有一份实现） | **不变**：搬运发生在预填**之前**，改写的正文作为新的 `prefill.content` 进入同一条链路 |

## 6. 影响文件清单

**前端（新增）**

| 文件 | 内容 |
| --- | --- |
| `shiguangji-web/src/utils/noteImages.ts` | 收集 / 匹配 / 改写三个纯函数（无网络、无 DOM），上限常量 |
| `shiguangji-web/src/components/NoteImageCarryDialog/index.vue` | 搬运弹窗：选择、进度、结果、逐条未命中（两个入口共用） |
| `shiguangji-web/src/api/common/upload.ts` | `uploadNoteImage()` |
| `shiguangji-web/src/utils/__tests__/noteImages.spec.ts` | 纯逻辑用例（见 testcases T-01~T-12） |

**前端（改动）**

| 文件 | 改动 |
| --- | --- |
| `utils/noteImport.ts` | `parseNoteMarkdown` 出参增加 `localImageRefs` 与 `externalImageCount`（外链处数，供结论 62 的提示）；`/profile/` 排除；`localImageCount` 改为 `localImageRefs.length`（既有断言不变） |
| `components/MarkdownViewer/index.vue` | DOMPurify 钩子补图片前缀（一次注册） |
| `components/MarkdownEditor/index.vue` | 工具栏「插图」按钮 + 粘贴上传 + 光标插入 |
| `views/front/note/index.vue` | 解析后接搬运弹窗；提示文案改「未搬走的引用」 |
| `views/business/note/index.vue` | 同上（后台弹窗形态） |
| `components/MarkdownViewer/__tests__/index.spec.ts` | 补图片前缀与钩子幂等断言 |

**后端（新增）**

| 文件 | 内容 |
| --- | --- |
| `shiguangji-file/.../cleanup/ProfileRefExtractor.java` | 候选集字典式引用抽取（纯逻辑） |
| `shiguangji-file/src/test/.../ProfileRefExtractorTest.java` | 纯单测 |
| `shiguangji-admin/src/test/.../NoteImageUploadSmokeTest.java` | 端点 smoke（匿名 401 / 命名形状 / 台账 / 拒收非图片） |

**后端（改动）**

| 文件 | 改动 |
| --- | --- |
| `admin/.../controller/common/CommonController.java` | 新增 `/upload/note-image` |
| `common/.../utils/file/MimeTypeUtils.java` | 新增 `NOTE_IMAGE_EXTENSION` |
| `business/.../mapper/FileReferenceMapper.java` + `resources/mapper/business/FileReferenceMapper.xml` | 新增 `selectContentsWithProfileRefs` |
| `quartz/.../task/FileCleanupTask.java` | 计算顺序调整 + 并入正文引用 + 失败中止 |
| `quartz/src/test/.../FileCleanupTaskTest.java` | 补用例 |

**SQL**：无表结构变更（`content` 已是 `longtext`，无需新列新表新索引）。

## 7. 非目标

- 不做服务端 zip 解包、不做整目录批量导入多篇笔记（一次一篇，见 结论 49）；
- 不做 Word（docx）内嵌图片转存（沿 0.0.2 非目标）；
- 不做 `data:` base64 内嵌图片的转存与改写（结论 35 的后半段仍然成立）；
- 不做「导出带图整包」（导出的 md 在站外仍然指不到图；对称能力另立 issue）；
- 不做图片压缩 / 缩略图 / EXIF 清理 / 按内容哈希的跨笔记去重；
- 不做每文件鉴权的图片访问（结论 61）；
- 不迁移历史笔记里已经失效的 `./a.png` 引用（源文件早已不在，无从迁移，只能手工补图——编辑器插图入口就是为它准备的）。

## 8. 实施顺序（小步提交）

| # | 提交 | 内容 | 检查 |
| --- | --- | --- | --- |
| 1 | `docs:` | 本文件 + 架构 + 测试用例 + 需求与既有设计文档的对齐修订 + 设计图 | — |
| 2 | `feat(file):` | `/common/upload/note-image` 端点 + `NOTE_IMAGE_EXTENSION` + smoke | `mvn -pl shiguangji-admin -am test` |
| 3 | `fix(file):` | `ProfileRefExtractor` + mapper 查询 + 任务接线 + 单测 | 同上 |
| 4 | `fix(web):` | `MarkdownViewer` 图片前缀 + spec | `npx vitest run` |
| 5 | `feat(web):` | `utils/noteImages.ts` + 搬运弹窗 + 两个入口接线 + specs | `vitest` + `vue-tsc` + `build:prod` |
| 6 | `feat(web):` | 编辑器「插图」+ 粘贴上传 | 同上 |

每个提交都必须让相关检查在**该提交上**通过（提交 3 是后端独立的修复，可单独回滚；提交 4 是渲染侧独立的修复，也可单独回滚）。

## 9. 设计图

落在 `docs/design/mockups/note-images/`：`index.html`（索引与出图口径）、`carry-images.html`（搬运弹窗五态 + 落地提示 + 移动端）、`editor-image.html`（工具栏 / 上传中 / 插入成功 / 粘贴 / 失败 / 移动端）。出图约定沿用 [note-0.0.2 §3.2](note-0.0.2-design.md)：每屏一页、页内切状态与主题、橙色标注气泡与页脚一一对应、设备框可滚动、静态保真（不连真实接口）。
