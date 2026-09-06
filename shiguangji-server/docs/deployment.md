# 部署说明

生产环境使用 `prod` profile：

```bash
SPRING_PROFILES_ACTIVE=prod java -jar shiguangji-admin/target/shiguangji-admin.jar
```

## 必需配置

生产环境至少需要注入：

- `MYSQL_URL`
- `MYSQL_USERNAME`
- `MYSQL_PASSWORD`
- `REDIS_HOST`
- `REDIS_PASSWORD`
- `JWT_SECRET`，使用不少于 32 字节的随机值

可以通过 `SERVER_PORT`、`REDIS_PORT`、`REDIS_DATABASE`、`SGJ_UPLOAD_PATH` 和 `TOKEN_EXPIRE_TIME` 调整其他运行参数。

## 文件存储

默认 `local`：文件写入 `SGJ_UPLOAD_PATH` 本地目录，通过 `/profile/**` 静态映射对外提供。

可切换为 S3 兼容存储（MinIO、阿里云 OSS 等），相关环境变量：

| 变量 | 说明 |
| --- | --- |
| `SGJ_STORAGE_TYPE` | `local`（默认）/ `s3` |
| `SGJ_S3_ENDPOINT` | 服务地址：MinIO 如 `http://127.0.0.1:9000`；OSS 如 `https://oss-cn-hangzhou.aliyuncs.com` |
| `SGJ_S3_REGION` | 区域，OSS 如 `oss-cn-hangzhou` |
| `SGJ_S3_BUCKET` | 存储桶名，需预先创建 |
| `SGJ_S3_ACCESS_KEY` / `SGJ_S3_SECRET_KEY` | 访问密钥 |
| `SGJ_S3_PATH_STYLE` | 寻址风格：MinIO 用 `true`；**阿里云 OSS 仅支持虚拟主机风格，必须为 `false`** |

切换存储后端无需迁移业务数据：业务表（如 `sgj_item.cover_url`）保存的是 `/profile` 前缀的相对路径，两种模式下含义一致。历史本地文件迁移到 S3 时保持对象 key 与 `/profile` 后的路径相同即可，迁移清单可查 `sys_file` 台账表。

## 安全要求

- 不要将真实密码或密钥写入仓库、镜像或启动脚本。
- 首次部署后立即修改初始化管理员密码。
- 生产 profile 默认关闭 Swagger、API 文档和 Druid 控制台。
- 上传目录和数据库需要纳入独立备份策略，并定期验证恢复流程。

## 数据库初始化

仅全新数据库执行 `sql/init_system.sql`、`sql/quartz.sql`、`sql/init_business.sql`。这些脚本会重建相关表，不应用于已有生产库升级。
