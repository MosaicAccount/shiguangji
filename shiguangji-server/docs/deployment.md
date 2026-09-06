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

## 安全要求

- 不要将真实密码或密钥写入仓库、镜像或启动脚本。
- 首次部署后立即修改初始化管理员密码。
- 生产 profile 默认关闭 Swagger、API 文档和 Druid 控制台。
- 上传目录和数据库需要纳入独立备份策略，并定期验证恢复流程。

## 数据库初始化

仅全新数据库执行 `sql/init_system.sql`、`sql/quartz.sql`、`sql/init_business.sql`。这些脚本会重建相关表，不应用于已有生产库升级。
