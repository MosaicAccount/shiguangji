# 外链图片转存 —— 架构（issue #68）

> 配套文档：[需求](external-images-requirements.md) · [详细设计](external-images-design.md) · [测试用例](external-images-testcases.md)

## 1. 架构位置

转存不新建存储体系，整条链路复用 0.0.2 已有的图片搬运设施（#56 / FR-002-9），只补「服务端安全抓取」这一块：

```
前端                                     后端（shiguangji-admin）
─────────────────────────────────────────────────────────────────────────────
NoteImageCarryDialog（搬运弹窗，扩展）
  ├ 本地图片：选文件 → uploadNoteImage ──→ POST /common/upload/note-image（已有）
  └ 外链图片：勾选 → fetchExternalImage → POST /common/upload/fetch-image（新增，需登录）
                                            │
                                            ├ ExternalImageTransferService（新增）
                                            │   ├ 速率限制（Redis 分钟桶，每用户）
                                            │   ├ ExternalImageFetcher.fetch(url)（接口，新增）
                                            │   │    └ HttpExternalImageFetcher（实现，待使用者落地）
                                            │   │        SSRF 矩阵：协议 / IP 段 / 重定向逐跳 /
                                            │   │                  超时 / 体积上限
                                            │   ├ 内容校验：Content-Type + 魔数 → 扩展名
                                            │   └ FileStorageService.upload("upload", …, uuid)
                                            │        + recordSysFile（sys_file 台账）
                                            ▼
MarkdownViewer 渲染 /profile/...（已有）   孤儿对账（已有，ProfileRefExtractor 认正文引用）
```

## 2. 组件与职责

| 组件 | 模块 | 职责 | 归属 |
| --- | --- | --- | --- |
| `ExternalImageFetcher`（接口） | `web/service/image/` | 传输安全契约：协议校验、IP 段拒绝、重定向逐跳重验、超时、体积上限；返回字节 + Content-Type | 接口由 agent 定，**实现由使用者本人写**（教学例外，见设计 §4） |
| `HttpExternalImageFetcher` | 同上 | 接口的真实现 | **使用者** |
| `NoopExternalImageFetcher` | 同上 | `@ConditionalOnMissingBean` 兜底：没有真实现时启动不炸、调用即回「未启用」；真实现落地后自动退位 | agent |
| `ExternalImageTransferService` | 同上 | 速率限制 → 抓取 → 内容校验（魔数 + Content-Type）→ 落存储 → 登台账 | agent |
| `POST /common/upload/fetch-image` | `CommonController` | 登录态下的薄端点：`{url}` → `{path}` | agent |
| `fetchExternalImage(url)` | 前端 `api/common/upload.ts` | 单张调用（不批量），60s 超时 | agent |
| `collectImageRefs` 扩展 | 前端 `utils/noteImages.ts` | 外链从「只计数」升级为结构化引用列表（`external: NoteImageRef[]`），计数改为 `external.length` | agent |
| `NoteImageCarryDialog` 扩展 | 前端 `components/` | 勾选项 / 只有外链的形态 / 转存进度并入 / 失败逐条 / 结果态补做 | agent |

## 3. 数据流

### 3.1 转存一次外链

1. 前端 `collectImageRefs` 解析正文，外链引用（含 `//` 协议相对，`data:` 除外）带 `raw/index/alt` 进 `external` 列表；
2. 弹窗勾选「顺带转存外链图片」后，按 URL 去重（同图多处引用只转一次）逐张调 `fetchExternalImage`；`//cdn/x.png` 补 `https:` 前缀；
3. 服务端：登录校验（框架层）→ 速率限制 → `fetcher.fetch(url)` → 内容校验 → `upload("upload", …, NOTE_IMAGE_EXTENSION, uuid)` → `sys_file` 登记 → 回 `{path}`；
4. 前端拿 `path` 改写正文引用（`rewriteNoteImageRefs`，与本地搬运同一函数）；失败的引用原样保留并逐条展示 URL + 原因。

### 3.2 生命周期闭环

转存后的引用即 `/profile/upload/...`，与 #56 的本地搬运产物同构：渲染走 `MarkdownViewer` 的 `photoUrl()` 前缀补齐；孤儿对账走 `ProfileRefExtractor` 的正文引用抽取——被引用即保活，引用删除后由每周清理回收。**无新增清理规则**。

## 4. 安全模型（为何这样切）

- 豆瓣封面敢匿名代理（`/app/item/cover/proxy`）是因为域名白名单只放行豆瓣 CDN；转存面对任意主机，白名单失效（D-35），防护重心转为「目标可达性约束」：IP 段拒绝 + 逐跳重验 + 体积 / 超时 / 速率上限，全部收敛在 `ExternalImageFetcher` 契约内——这一块就是 issue #68 说的「主要成本」，也是交给使用者实现的教学点；
- 内容校验（魔数 + Content-Type）放在转存服务而不是抓取器：落盘前单点把关，抓取器实现质量不影响「非图片不落盘」这条红线（D-34）；
- 速率限制是 Redis 分钟桶而不是滑动窗口：转存是低频人工操作，分钟粒度足够，实现三行（D-36）。

## 5. 配置

`application.yml` 新增 `sgj.image-fetch.*`（连接 / 读取超时、重定向上限、单张体积、速率上限、测试用 `allow-private-ranges`），详见设计 §3.5。环境变量覆盖遵循仓库既有约定。
