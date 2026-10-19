# tf-reader

FishMatrix（摸鱼矩阵）阅读服务：书籍内容、阅读进度与协同评审 —— Spring Boot 3 + JDK 17。

- 端口：`8103`
- 健康检查：`GET /actuator/health`、`GET /api/v1/reader/health`
- 存储：MySQL `fm_reader`（`reader_book / reader_chapter / reader_progress / reader_bookmark / reader_annotation`）
- 内置演示数据集：`古文观止·精选`（3 章公版选段），由 `data.sql` 幂等种子
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

- 启动时执行 `schema.sql`（IF NOT EXISTS）与 `data.sql`（INSERT IGNORE）：
  `spring.sql.init.mode=${SQL_INIT_MODE:always}`
- `fm_app` 需要 `fm_reader.*` 的 DDL/DML 权限（集中机 `init-db.sh` 已授权）
- 测试通过 `spring.sql.init.mode=never` 跳过初始化，CI 不依赖 MySQL
- 已有 `fm_reader` 库不会自动执行新表 DDL 以外的改名；本版只新建表

## 环境变量

| 变量 | 默认 | 说明 |
|---|---|---|
| `MYSQL_HOST` / `MYSQL_PORT` | `10.28.25.234` / `3306` | MySQL |
| `MYSQL_APP_USER` / `MYSQL_APP_PASSWORD` | `fm_app` / 空 | 应用账号 |
| `NACOS_ADDR` / `NACOS_USERNAME` / `NACOS_PASSWORD` | — | 服务注册 |
| `SQL_INIT_MODE` | `always` | 设为 `never` 时由 DBA 手工建表 |
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
