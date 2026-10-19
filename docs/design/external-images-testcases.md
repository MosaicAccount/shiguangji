# 外链图片转存 —— 测试用例（issue #68）

> 配套文档：[需求](external-images-requirements.md) · [架构](external-images-architecture.md) · [详细设计](external-images-design.md)
> 编号接 `note-images-testcases.md`（止于 T-48）。

## 1. 自动化用例（agent 维护）

### 1.1 后端

| 编号 | 用例 | 断言要点 | 落点 |
| --- | --- | --- | --- |
| T-49 | 匿名调用 fetch-image 被拒 | HTTP 200 + 业务码 401（本系统契约：业务码不是 HTTP 状态码） | `ExternalImageApiSmokeTest` |
| T-50 | 登录转存成功（stub 抓取器返回 png） | code 200；`path` 匹配 `/profile/upload/\d{4}/\d{2}/\d{2}/[uuid].png`；GET path 回读同字节；`sys_file` 多一行、原始名记外链 URL | 同上 |
| T-51 | 抓取失败不落盘不登记 | stub 抛「内网地址不允许抓取」→ code 500 且 msg 含原因；`sys_file` 行数不变 | 同上 |
| T-52 | 非图片内容拒 | stub 返回 `text/html` → 拒；返回 png 头 + 伪字节（魔数不符）→ 拒；两者都不落盘 | 同上 + `ExternalImageTransferServiceTest` |
| T-53 | 速率限制 | 测试属性把上限调到 2，第 3 次调用被拒且 msg 含「每分钟最多 2 次」 | `ExternalImageApiSmokeTest`（properties 覆盖） |
| T-54 | 内容校验矩阵 | 五种魔数（png/jpg/gif/webp/bmp）各自命中扩展名；Content-Type 非 `image/` 拒；空字节拒；魔数与扩展名不一致以魔数为准 | `ExternalImageTransferServiceTest`（纯单测，stub fetcher + mock 存储） |
| T-55 | webp / gif 外链端到端 | stub 返回 webp 魔数 → path 以 `.webp` 结尾（衔接 #67 的口径） | `ExternalImageApiSmokeTest` |

### 1.2 抓取器 SSRF 矩阵（`ExternalImageFetcherTest`，**随真实现落地时补写**，见设计 §4）

| 编号 | 用例 | 断言要点 |
| --- | --- | --- |
| T-56 | 非 http(s) 协议拒 | `file://`、`ftp://`、`data:`、无 scheme 一律拒，msg 含「仅支持 http(s)」 |
| T-57 | 内网 / 保留地址拒 | `127.0.0.1`、`localhost`、`10.0.0.1`、`172.16.0.1`、`192.168.1.1`、`169.254.1.1`、`100.64.0.1`、`[::1]`、IPv4 映射的 `::ffff:127.0.0.1` 全拒，且**不发起连接**（本地桩服务器无请求到达） |
| T-58 | 成功路径（allow-private-ranges=true + 本地桩） | png 字节 + Content-Type 原样返回 |
| T-59 | 重定向逐跳重验 | 桩 302 → 另一路径成功；302 → `file://` 拒；302 超过 max-redirects 拒；每跳 Location 为相对地址也能解析 |
| T-60 | 体积上限 | 桩返回超过 max-bytes 的流 → 拒「超过大小上限」，连接被断开（桩侧可见提前 EOF） |
| T-61 | 非 200 响应 | 桩返回 404 / 403 → 拒且 msg 含状态码 |

### 1.3 前端

| 编号 | 用例 | 断言要点 | 落点 |
| --- | --- | --- | --- |
| T-62 | 外链结构化收集 | `http(s):` / `//` 引用进 `external`（带 raw/index/alt）；`externalCount === external.length`（#56 既有断言不动）；`data:` 与 `/profile/` 不进任何列表 | `noteImages.spec.ts` |
| T-63 | 协议相对归一 | `toAbsoluteExternalUrl('//cdn/x.png')` → `https://cdn/x.png`；`https://…` 原样；`http://…` 原样（转不改协议） | 同上 |
| T-64 | 外链改写复用既有函数 | external 引用与本地引用混合时按位置从后往前替换互不干扰；同一 URL 多处引用一处不落（转存失败）则全部保留 | 同上 |
| T-65 | 代码块 / 行内代码不收集 | 沿用 #56 的掩码口径，外链在代码里同样不收集 | 同上 |

## 2. 人工用例（只有人能跑）

| 编号 | 用例 | 步骤 | 预期 |
| --- | --- | --- | --- |
| T-66 | 真实外链转存 | 导入一篇含 3 张公网图（含 1 张 `//` 协议相对、1 张 `http://`）的 md → 弹窗勾选「顺带转存外链图片」→ 选本地图片目录 → 完成 | 进度合并计数；详情页 3 张外链全部从本站 `/profile` 出图；正文引用已改写 |
| T-67 | 防盗链外链失败可见 | 含一张 403 防盗链图的笔记转存 | 结果态逐条列出该 URL 与「对方返回 HTTP 403」；正文该引用原样保留，详情页仍指向对方（裂图有「图片已失效」标记，结论 64） |
| T-68 | 只有外链的笔记 | 导入一篇只有外链、无本地引用的 md | 弹窗照开（结论 67）：只有「转存外链图片」主按钮，无文件选择 |
| T-69 | 结果态补做 | 转存时不勾选 → 结果态点「补转存外链」 | 补跑成功后引用改写、统计更新 |
| T-70 | 孤儿回收闭环 | 转存成功后删掉正文里的引用（或删除笔记）→ 等每周清理（或手动触发） | 转存图被回收，`sys_file` 台账一致 |
| T-71 | 速率限制体感 | 1 分钟内连转 30+ 张 | 超限后弹「转存过于频繁」，稍后恢复 |

## 3. 回归清单（触及共享代码，必须全绿）

- `noteImages.spec.ts` 既有 20 条（本地搬运收集 / 匹配 / 改写不回归）；
- `NoteImageUploadSmokeTest`（笔记插图上传链路不动）；
- `ItemCoverApiSmokeTest` / `AppApiAuthIsolationSmokeTest`（封面与检索链路不动）；
- `npx vue-tsc --noEmit`、`npm run build:prod`。
