# tf-reader

FishMatrix（摸鱼矩阵）阅读服务：书籍内容、阅读进度与协同评审 —— Spring Boot 3 + JDK 17。

- 端口：`8103`
- 健康检查：`GET /actuator/health`、`GET /api/v1/reader/health`
- 存储：MySQL `fm_reader`（`reader_book / reader_chapter / reader_progress / reader_bookmark / reader_annotation`）
- 内置演示数据集：`古文观止·精选`（3 章公版选段），仅由测试迁移 seed `db/testdata/R__demo_books.sql` 写入
- 支持导入 UTF-8 / GBK txt（≤8 MiB，按 `第X章/回/节/卷` 拆分）

## API 一览

| 方法/路径 | 说明 |
|---|---|
| `GET /api/v1/reader/books` | 书单 |
| `GET /api/v1/reader/books/{id}` | 书籍详情 + 章节列表 |
| `GET /api/v1/reader/books/{id}/chapters/{idx}` | 章节正文 |
| `POST /api/v1/reader/books/import` | multipart 导入 txt |
| `GET/PUT /api/v1/reader/books/{id}/progress` | 阅读进度 |
| `GET/POST /api/v1/reader/books/{id}/bookmarks` | 书签 |
| `DELETE /api/v1/reader/bookmarks/{id}` | 删除书签 |
| `GET/POST .../chapters/{idx}/annotations` | 章节批注（GET 支持 `afterId` 增量） |

完整契约见 `api/openapi.yaml`。

## 数据库初始化

- 表结构由 Flyway `db/migration/V1__baseline.sql` 创建，由独立任务 `db-migrate-reader`
  使用迁移账号执行；业务容器固定 `SPRING_FLYWAY_ENABLED=false`、`SQL_INIT_MODE=never`。
- 演示书籍/章节位于 `src/main/resources/db/testdata/R__demo_books.sql`，只被测试迁移任务
  的 `FLYWAY_LOCATIONS` 加载（`classpath:db/migration,classpath:db/testdata`），生产不加载。
- 运行时账号只授 DML；迁移账号只授本库 DDL。
- H2 单元测试使用 `src/test/resources/schema-h2.sql`，不依赖 MySQL。

## 环境变量

| 变量 | 默认 | 说明 |
|---|---|---|
| `MYSQL_HOST` / `MYSQL_PORT` | `10.28.25.234` / `3306` | MySQL |
| `MYSQL_APP_USER` / `MYSQL_APP_PASSWORD` | `fm_reader_app` / 空 | 运行时账号（仅 DML） |
| `NACOS_ADDR` / `NACOS_USERNAME` / `NACOS_PASSWORD` | — | 服务注册 |
| `SQL_INIT_MODE` | `never` | 业务容器不执行 SQL 初始化 |
| `SPRING_FLYWAY_ENABLED` | `false` | 业务容器不执行迁移，由 `db-migrate-reader` 负责 |
| `READER_MAX_IMPORT_BYTES` | `8388608` | 导入文件上限 |

## 本地开发

```bash
mvn spring-boot:run    # 启动开发服务
mvn -B test            # 运行测试（不依赖 MySQL/Redis/Nacos）
```

## 构建

```bash
mvn -B -DskipTests package   # 产物 target/tf-reader.jar
java -jar target/tf-reader.jar
```

## 分支模型

- `master`：保护分支，只接受 PR（CI 必须通过，squash 合并），合并后自动发版
- `test`：联调分支，开发分支直接合入（不走 PR），可直接推送
- 开发流：`feature/*` 开发 → 直接合入 `test` 联调 → 验证 OK 后从开发分支开 PR 合 `master` → 自动发版
