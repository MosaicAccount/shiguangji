# china.json 数据来源记录

| 项 | 值 |
| --- | --- |
| 数据 | 中华人民共和国省级边界 GeoJSON（含省级 FeatureCollection） |
| 来源 | 阿里云 DataV.GeoAtlas `https://geo.datav.aliyun.com/areas_v3/bound/100000_full.json` |
| 下载日期 | 2026-08-30（v1.0.0 发布准备） |
| 文件大小 | 582,522 字节 |
| SHA-256 | `99adfeded5223848bbe37a0a12f8023e11ee12161c7800521c27db42fdeac275` |
| 许可 | DataV.GeoAtlas 数据可免费用于前端地图展示（详见 <https://datav.aliyun.com/portal/school/atlas/area_selector> 版权说明） |
| 注册名 | ECharts 地图注册名保持 `china`，与 `src/views/front/travel/index.vue` 现有逻辑一致 |

## 加载策略

本地优先，CDN 兜底：`travel/index.vue` 先 `fetch('/map/china.json')`，失败时回退阿里 DataV URL。生产运行不应依赖外网 CDN。
