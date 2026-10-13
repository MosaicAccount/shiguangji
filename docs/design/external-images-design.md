# 外链图片转存 —— 详细设计（0.0.6，issue #68）

> 配套文档：[需求](../requirements/0.0.6-external-images.md) · [架构](external-images-architecture.md) · [测试用例](external-images-testcases.md)
> 结论编号接 `note-images-design.md`（止于 64）。

## 1. 结论（65~70）

| # | 状态 | 议题 | 结论 |
| --- | --- | --- | --- |
| 65 | 已拍板 | 外链引用从「计数」升级为「结构化列表」 | `collectImageRefs` 的返回新增 `external: NoteImageRef[]`（`kind: 'external'`，带 `raw/index/alt`，改写复用 `rewriteNoteImageRefs`）；`externalCount` 保留为 `external.length`（#56 既有断言与提示语不动）。`data:` 与 `/profile/` 不进列表（边界 2 / 3） |
| 66 | 已拍板 | 同一 URL 多处引用只转存一次 | 按 URL（协议相对补 `https:` 后归一）去重，转存结果服务全部指向它的引用（与本地搬运 `dedupeByCandidate` 同构） |
| 67 | 已拍板 | 只有外链的笔记怎么进弹窗 | 弹窗打开条件从「本地引用 > 0」放宽为「本地或外链任一 > 0」；无本地引用时 pick 态不显示文件选择按钮与勾选框，主按钮即「转存外链图片」（FR-006-2） |
| 68 | 已拍板 | 转存与本地搬运的关系 | 勾选后并入同一条进度管线（一个进度条、一套统计），不是两个弹窗两段流程；转存**默认不勾**（D-32） |
| 69 | 已拍板 | 失败原因的口径 | 后端任何拒绝都以一句可读中文经 `ServiceException` → `msg` 返回；前端原样展示（「超时」「对方返回的不是图片」「内网地址不允许」…），不做错误码翻译层 |
| 70 | 已拍板 | 占位实现 | 真实现落地前用 `NoopExternalImageFetcher`（`@ConditionalOnMissingBean`）占位：应用能启动（既有测试不炸）、调用即回「外链图片抓取未启用」；真实现 `@Component` 落地后自动退位。**分支在此状态为 draft，不合并**（AGENTS：测试是红的 / 功能未闭环就不能说完成） |

## 2. 后端设计

### 2.1 端点（`CommonController`，与 `uploadNoteImage` 并列）

```
POST /common/upload/fetch-image        （需登录，本类不加 @Anonymous）
请求体  {"url": "https://example.com/a.png"}
成功响应 {code:200, msg:"操作成功", path:"/profile/upload/2026/10/13/<uuid>.png"}
失败响应 {code:500, msg:"<可读原因>"}   （转存失败不是系统错误，走业务码，前端逐条展示）
```

- 请求 DTO：`FetchImageRequest{ String url }`（新文件，`web/controller/common/dto/` 若无则放 `web/service/image/`）；
- 台账：成功后 `recordSysFile`（与 note-image 同一私有方法，`originalFilename` 记外链 URL）；
- 速率限制：见 §2.3。

### 2.2 接口契约（`web/service/image/ExternalImageFetcher.java`，agent 交付）

```java
public interface ExternalImageFetcher
{
    /**
     * 抓取一张外链图片。
     *
     * @param imageUrl 绝对 http(s) 地址（协议相对 // 已由调用方补全为 https:）
     * @return 字节与 Content-Type
     * @throws ServiceException 任何拒绝 / 失败都以一句可读中文抛出（结论 69）
     */
    FetchedImage fetch(String imageUrl);

    /** 抓取结果：字节 + 响应头 Content-Type（缺失时为 application/octet-stream） */
    record FetchedImage(byte[] bytes, String contentType) { }
}
```

**实现必须满足的安全矩阵**（`HttpExternalImageFetcher`，使用者实现，详见 §4 交接清单）：协议白名单、DNS 全记录 IP 段拒绝、重定向逐跳重新校验、连接 / 读取超时、流式体积上限、`sgj.image-fetch.allow-private-ranges` 测试开关。

### 2.3 转存服务（`web/service/image/ExternalImageTransferService.java`，agent 交付）

```
String transfer(String url, Long userId)
  1. 速率限制：Redis 键 fetchimg:rate:{userId}:{yyyyMMddHHmm}，
     redisTemplate.opsForValue().increment；首次（==1）时 expire 120s；
     超过 sgj.image-fetch.rate-limit-per-minute（默认 30）→ ServiceException「转存过于频繁，每分钟最多 N 次」
  2. fetcher.fetch(url)                                    ← 传输安全全部在实现内
  3. 内容校验（落盘前最后一道，D-34）：
     contentType 不以 image/ 开头 → 拒「对方返回的不是图片（Content-Type: xxx）」
     魔数嗅探（png/jpg/gif/webp/bmp）失败 → 拒「图片内容校验失败：不是受支持的图片格式」
  4. 扩展名取魔数嗅探结果；ByteArrayMultipartFile("external." + ext)
  5. fileStorageService.upload("upload", file, MimeTypeUtils.NOTE_IMAGE_EXTENSION, true)
  6. 返回 /profile 路径（台账由控制器记）
```

魔数嗅探表：`89 50 4E 47`→png；`FF D8 FF`→jpg；`47 49 46 38`→gif；`42 4D`→bmp；`52 49 46 46 ?? ?? ?? ?? 57 45 42 50`（RIFF…WEBP）→webp。

### 2.4 配置（`application.yml` 新增，均可用环境变量覆盖）

```yaml
sgj:
  image-fetch:
    connect-timeout-ms: 5000      # 抓取器：每跳连接超时
    read-timeout-ms: 10000        # 抓取器：读取总超时
    max-redirects: 3              # 抓取器：重定向上限，每跳重新校验
    max-bytes: 10485760           # 单张体积上限（10MB，与 NOTE_IMAGE_MAX_SIZE 对齐）
    rate-limit-per-minute: 30     # 转存服务：每用户每分钟调用上限
    allow-private-ranges: false   # 仅测试：放行内网/回环段（D-39），生产必须 false
```

## 3. 前端设计

### 3.1 `utils/noteImages.ts`

- `NoteImageRef['kind']` 扩为 `'markdown' | 'wikilink' | 'external'`；`ImageRefCollection` 新增 `external: NoteImageRef[]`，`externalCount` 改为派生量（结论 65）；
- 新增 `toAbsoluteExternalUrl(target: string): string`：`//cdn/x.png` → `https://cdn/x.png`，其余原样（结论 66 的归一步）；
- 改写复用 `rewriteNoteImageRefs`（external 引用同样带 index，从后往前替换不受影响）。

### 3.2 `api/common/upload.ts`

`fetchExternalImage(url: string): Promise<FetchImageResult>` → `POST /common/upload/fetch-image`，`{path}`；`repeatSubmit: false`、`timeout: 60000`（与 `uploadNoteImage` 同理：单张大图慢网络）。

### 3.3 `NoteImageCarryDialog`

| 状态 | 变化 |
| --- | --- |
| pick（有本地引用） | 新增勾选框「顺带转存外链图片（N 处）」**默认不勾**；勾选后外链说明文案改为「将把 N 处外链下载到本站备份，引用改指本站」 |
| pick（无本地引用，结论 67） | 不显示文件选择按钮与勾选框，主按钮「转存外链图片」 |
| uploading | 队列 = 本地上传 +（勾选时）外链转存；同一 URL 去重；限并发 3 不变；进度与统计共用 |
| result | 本地统计不变；新增外链统计：成功 N / 失败 M，失败逐条列出 URL + 原因（前 5 条 + 「等 M 处」）；仍有未转存外链时提供「补转存外链」按钮（FR-006-4），基于当前改写后的正文重跑外链管线 |

- 弹窗打开条件放宽（结论 67），两个调用方（后台 `views/business/note/index.vue`、前台 `views/front/note/index.vue`）同步改；
- `open()` 签名：`external: number` → `externalRefs: NoteImageRef[]`；
- 关闭 / 跳过语义不变：返回 null 正文不动，已转存的图交孤儿清理（#56 结论 60 同款）。

## 4. 交接清单 —— `HttpExternalImageFetcher` 由使用者本人实现

**为什么交出去**（AGENTS 教学例外的判据是「练手价值」）：这一个类是整个 issue 的「主要成本」所在——SSRF 防护矩阵（DNS 全记录校验、rebinding 对策、重定向逐跳重验）是值得亲手搭一遍的安全架构点；其余（端点接线、速率限制、前端弹窗、文档）是搬运不是教学，留在 agent 手里。

**要实现的东西**：新类 `shiguangji-server/shiguangji-admin/src/main/java/com/shiguangji/web/service/image/HttpExternalImageFetcher.java`，实现 `ExternalImageFetcher`（§2.2 契约），`@Component`（落地后 `NoopExternalImageFetcher` 自动退位）。不改任何其他文件——接口、配置键、端点、前端都已就位。

**行为规格**（验收口径，逐条对应需求 §5）：

1. **协议白名单**：URL 解析失败、scheme 非 `http` / `https` → 拒「仅支持 http(s) 图片地址」；
2. **IP 段拒绝（防 SSRF）**：主机名先 `InetAddress.getAllByName` 解析（失败 → 拒「域名无法解析」），**全部** A / AAAA 记录逐一校验，任一命中即拒「内网 / 保留地址不允许抓取」：
   - `InetAddress` 自带判定：`isAnyLocalAddress`、`isLoopbackAddress`、`isLinkLocalAddress`、`isSiteLocalAddress`、`isMulticastAddress`；
   - 自带判定盖不住的段需显式比对（前缀 / 掩码）：`0.0.0.0/8`、`100.64.0.0/10`（CGNAT）、`192.0.0.0/24`、`192.0.2.0/24`、`198.18.0.0/15`、`198.51.100.0/24`、`203.0.113.0/24`、`240.0.0.0/4`、`255.255.255.255`；IPv6 侧 `::ffff:0:0/96`（IPv4 映射地址要先拆出内层 IPv4 再按上表校验）；
3. **DNS rebinding 对策**：解析校验通过后，连接必须绑到**已校验的那个 IP**（推荐 `Socket` / `SSLSocket` 显式 `connect(InetAddress, port)`，TLS 的 SNI 与 Host 头用原主机名；Java 17 的 `HttpClient` 没有公开的 DNS resolver 钩子，直接按主机名请求会留下「解析两次答案不同」的 TOCTOU 窗口）。若评估后选择接受残余风险（每跳重验 + 短超时缓解），必须在类注释里写明残余窗口——两条路都算合格，推荐前者；
4. **重定向**：3xx 取 `Location`（相对地址按当前 URL 解析为绝对），跳数 +1 后**回到第 1 步重新走完整校验（含重新解析 IP）**；超过 `max-redirects`（默认 3）→ 拒「重定向次数超限」；`Location` 缺失 → 拒；
5. **超时与体积**：连接超时 `connect-timeout-ms`（5s）、读取总超时 `read-timeout-ms`（10s）；响应体**流式**读取，累计超过 `max-bytes`（10MB）立即断开 → 拒「图片超过大小上限」；
6. **成功**：HTTP 200 → 返回 `FetchedImage(bytes, contentType)`；非 200 / 3xx → 拒「对方返回 HTTP xxx」；
7. **测试开关**：`allow-private-ranges=true` 时跳过第 2 步的段拒绝（协议校验保留）——仅用于本地桩服务器验证成功路径与重定向链，默认 false，生产必须 false（配置注释已写明）；
8. **请求头**：普通浏览器 UA（豆瓣客户端那套可用），`Accept: image/*`。

**字段与口径**：无表结构变更、无 SQL；只读配置键（§2.4）；不写库、不落盘（落盘在转存服务）。

**验收标准**（合入前全部要绿）：
- `ExternalImageFetcherTest`（agent 在你的实现落地时按下面矩阵补写并跑绿）覆盖：`file://` / `ftp://` 拒；`127.0.0.1`、`localhost`、`10.0.0.1`、`192.168.1.1`、`[::1]` 拒；`allow-private-ranges=true` + 本地桩服务器：png 成功、3xx 逐跳成功、`file://` 跳转拒、超 `max-bytes` 拒、非 200 拒；
- 既有 `ExternalImageApiSmokeTest` 从 stub 换成真实现再跑一遍全绿（不删 stub 用例）；
- `mvn -pl shiguangji-admin -am test` 全量绿。

**测试要点**（实现自测时的下手处）：`com.sun.net.httpserver.HttpServer` 起本地桩（JDK 自带，无新依赖）；拒绝向量全是本机 / 内网地址，天然可测；成功路径记得开 `allow-private-ranges`（`@SpringBootTest(properties = …)` 或直接 new 出来注入配置值单测，不必起容器）。

## 5. 提交切分（每个提交自身检查过）

1. `docs:` 需求 0.0.6 + 架构 / 设计 / 用例四件套 + `requirements/README.md` 版本表；
2. `feat(file):` `ExternalImageFetcher` 接口 + `NoopExternalImageFetcher` 占位 + 配置键；
3. `feat(file):` 转存服务 + `/common/upload/fetch-image` 端点 + 冒烟（stub 抓取器：匿名拒 / 成功落盘登记台账 / 失败不落盘 / 非图片拒 / 速率限制）；
4. `test(file):` 转存服务单测（内容校验矩阵 + 速率限制边界）；
5. `feat(web):` `noteImages.ts` 外链结构化 + `toAbsoluteExternalUrl` + API 客户端 + 单测；
6. `feat(web):` 搬运弹窗外链转存（勾选 / 只有外链形态 / 失败逐条 / 结果态补做）+ 两个调用方。

真实现（§4）落地时：`feat(file): HttpExternalImageFetcher（#68 教学交接件）` + `test(file): ExternalImageFetcherTest SSRF 矩阵`，由 agent 审查并亲自跑测试后再取消 PR 的 draft 状态。
