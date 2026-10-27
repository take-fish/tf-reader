# tf-reader

FishMatrix（摸鱼矩阵）阅读服务：公共书库、个人书库、书单、分类、访问申请、阅读进度与批注 —— Spring Boot 3 + JDK 17。

- 端口：`8103`
- 健康检查：`GET /actuator/health`、`GET /api/v1/reader/health`
- 存储：MySQL `fm_reader`（`reader_book / reader_chapter / reader_progress / reader_bookmark / reader_annotation / reader_category / reader_shelf / reader_shelf_book / reader_access_request`）
- 文件：书籍原文落本地命名卷（`fm.storage`），库表只存 `storage_key` 与 SHA-256
- 鉴权：统一使用用户中台身份（`Authorization: Bearer` 或网关签名身份头），权限码来自用户中台
- 内置演示数据集：`古文观止·精选`（3 章公版选段），仅由测试迁移 seed `db/testdata/R__demo_books.sql` 写入
- 支持导入 UTF-8 / GBK txt（≤8 MiB，按 `第X章/回/节/卷` 拆分）

## 功能分级

| 能力 | 谁可以 | 权限码 |
|---|---|---|
| 浏览公共书库 | 所有登录用户 | — |
| 上传公共书籍 | 所有登录用户 | `reader.public.upload` |
| 上传到个人书库 | 所有登录用户 | `reader.private.manage` |
| 编辑/删除自己的书 | 上传者本人 | — |
| 编辑/删除任意书籍（含公共） | 管理员或被授权角色 | `reader.content.manage.any` |
| 维护分类 | 管理员或被授权角色 | `reader.category.manage` |
| 个人书单（书架） | 书单所有者 | `reader.private.manage` |
| 访问他人私有书 | 申请并获 owner 同意 | — |

判定规则：

- `scope=PUBLIC` 的书所有登录用户可读；`scope=PRIVATE` 的书只有 `owner_id` 本人可读可管。
- 私有书对非 owner 一律返回 **404**（不暴露是否存在）；`status=HIDDEN` 的书仅本人与 `reader.content.manage.any` 可见。
- 列表项由服务端计算 `mine / canEdit / canDelete`，前端不自行推断。
- 删除只允许 owner 或具备 `reader.content.manage.any`；分类变更需要 `reader.category.manage`。

## 访问申请

分享只用于“发现并发起申请”，**不等于授权**：

- `POST /api/v1/reader/books/{id}/share` 仅私有书的 owner 可调用，生成/返回 `share_token`；
- `GET /api/v1/reader/books/shared/{shareToken}` 只返回摘要信息，不返回章节正文；
- 被分享者用 `POST /api/v1/reader/books/{id}/access-requests`（body `{"shareToken":"...","message":"..."}`）发起申请；
- owner 通过 `access-requests/received` 查看，`approve` 后可阅读；`revoke` 收回已授予的访问。

状态机：`PENDING → APPROVED / REJECTED / CANCELLED`，`APPROVED → REVOKED`。被拒绝后 24 小时内再次申请返回 429 `REQUEST_COOLDOWN`；`CANCELLED` / `REVOKED` 可以重新申请。

## API 一览

全部接口需要登录；仅 `/health`（含 `/actuator/health`）公开。

| 方法/路径 | 说明 |
|---|---|
| `GET /api/v1/reader/books?scope=public&categoryId=&q=&page=&size=` | 公共书库；`scope=private` 等价于“我的书” |
| `GET /api/v1/reader/books/mine` | 我的书（含私有） |
| `GET /api/v1/reader/books/shared` | 我有权访问的他人私有书 |
| `GET /api/v1/reader/books/shared/{shareToken}` | 分享链接预览（摘要） |
| `GET /api/v1/reader/books/{id}` | 书籍详情 + 章节列表 |
| `GET /api/v1/reader/books/{id}/chapters/{idx}` | 章节正文 |
| `POST /api/v1/reader/books/import` | multipart 导入 txt：`file`、`scope`、`title`、`author`、`categoryId` |
| `PATCH /api/v1/reader/books/{id}` | 修改标题/作者/简介/分类/可见状态 |
| `DELETE /api/v1/reader/books/{id}` | 删除书籍（owner 或 `reader.content.manage.any`） |
| `POST /api/v1/reader/books/{id}/share` | 生成私有书分享链接 |
| `POST /api/v1/reader/books/{id}/access-requests` | 发起访问申请（body 含 `shareToken`、`message`） |
| `GET /api/v1/reader/access-requests/mine` | 我发出的申请 |
| `GET /api/v1/reader/access-requests/received?status=` | 我收到的申请 |
| `POST /api/v1/reader/access-requests/{id}/{approve,reject,revoke,cancel}` | 同意/拒绝/收回/撤销 |
| `GET/POST /api/v1/reader/categories`、`PUT/DELETE /categories/{id}` | 分类；写入需 `reader.category.manage` |
| `GET/POST /api/v1/reader/shelves`、`GET/PUT/DELETE /shelves/{id}` | 个人书单 |
| `POST /DELETE /api/v1/reader/shelves/{id}/books/{bookId}` | 书单增删书籍 |
| `GET/PUT /api/v1/reader/books/{id}/progress` | 阅读进度（按用户隔离） |
| `GET/POST /api/v1/reader/books/{id}/bookmarks`、`DELETE /bookmarks/{id}` | 书签 |
| `GET/POST /api/v1/reader/books/{id}/chapters/{idx}/annotations` | 章节批注（GET 支持 `afterId` 增量） |

完整契约见 `api/openapi.yaml`。

## 配置

```yaml
fm:
  auth:
    enabled: true
    user-service-url: ${TF_USER_URL:http://127.0.0.1:8101}
    identity-secret: ${FM_IDENTITY_SECRET:}
    allow-bearer-fallback: true
  storage:
    enabled: true
    base-dir: ${FM_STORAGE_BASE_DIR:/app/data/files}
    max-file-bytes: 8388608
  messaging:
    enabled: false
  rpc:
    enabled: false
```

## 数据库初始化

- 表结构由 Flyway `db/migration/V1__baseline.sql`、`V2__library_scopes.sql` 创建，由独立任务 `db-migrate-reader`
  使用迁移账号执行；业务容器固定 `SPRING_FLYWAY_ENABLED=false`、`SQL_INIT_MODE=never`。
- 演示书籍/章节位于 `src/main/resources/db/testdata/R__demo_books.sql`，只被测试迁移任务
  的 `FLYWAY_LOCATIONS` 加载（`classpath:db/migration,classpath:db/testdata`），生产不加载。
- 运行时账号只授 DML；迁移账号只授本库 DDL。
- H2 单元测试使用 `src/test/resources/schema-h2.sql`，不依赖 MySQL。

## 环境变量

| 变量 | 默认 | 说明 |
|---|---|---|
| `MYSQL_HOST` / `MYSQL_PORT` | `127.0.0.1` / `3306` | MySQL。**默认值只服务本地开发**；生产/测试由 `reader配置/*/.env` 注入，compose 里是必填项，未注入直接启动失败 |
| `MYSQL_APP_USER` / `MYSQL_APP_PASSWORD` | `fm_reader_app` / 空 | 运行时账号（仅 DML） |
| `NACOS_ADDR` / `NACOS_USERNAME` / `NACOS_PASSWORD` | — | 服务注册 |
| `FM_IDENTITY_SECRET` | 空 | 网关身份签名密钥，须与网关一致 |
| `TF_USER_URL` | `http://127.0.0.1:8101` | 用户中台内网地址（Bearer 回退 introspection） |
| `FM_STORAGE_ENABLED` / `FM_STORAGE_BASE_DIR` | `true` / `./data/files` | 文件存储与命名卷挂载点（容器内由 compose 覆盖为 `/app/data/files`） |
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
