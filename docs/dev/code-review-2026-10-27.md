# tf-reader 代码审查报告（2026-10-27）

> 审查范围：`feature/reader-refactor` @ `d5b7813`（= `origin/test` @ `ab9472e` 的代码内容），
> 65 个主代码 Java 文件（2456 行）、11 个测试类（739 行）、9 张业务表。
> 审查基准：Spring Boot 3 + JDK 17 微服务的企业级工程规范（分层、契约、校验、可观测、数据一致性、自动化约束）。
>
> 结论先说：**功能是对的，工程化是缺的。**
> 现在这套代码"能跑、能测、能上线"，但缺少让**三个人同时改还不出事**的那些约束：
> 统一异常、参数校验、字典枚举、并发控制、静态检查、业务日志。
> 下面按「会不会出事故 → 会不会拖慢开发 → 会不会让人踩坑」三档排列。

---

## 0. 摘要

| 级别 | 数量 | 含义 |
|---|---|---|
| 🔴 P0 | 5 | 会直接造成越权、数据错乱、线上 500 或安全事故，**功能开发前应修** |
| 🟠 P1 | 13 | 架构与契约层面的欠账，**随功能迭代必须收敛**，否则越写越贵 |
| 🟡 P2 | 18 | 可维护性、可发现性、文档一致性问题，**顺手修** |
| ⚪ 缺口 | 14 | 企业标配能力本项目完全没有（校验、枚举、日志、静态检查等） |

---

## 1. 🔴 P0：会出事故的问题

### P0-1 分页 offset 整数溢出（可被外部触发）

**位置**：`service/LibraryService.java:66-69`

```java
int safePage = Math.max(0, page);
int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
...
bookRepository.findPublic(categoryId, keyword, safePage * safeSize, safeSize)
```

`page` 直接来自 `@RequestParam`，没有任何上限。`page=21474836, size=100` 时
`safePage * safeSize` 溢出成**负数**，SQL 变成 `LIMIT 100 OFFSET -2147483600`，
轻则 `SQLException` → 500，重则数据库做全表扫描。

`safeSize` 有钳制，`safePage` 没有 —— 只钳了一半。

**修**：给 `page` 也设上限（例如 10_000），或 offset 用 `long` 计算并显式校验。
最稳的是 `long offset = (long) safePage * safeSize;` 再判断是否超过 `Integer.MAX_VALUE`。

---

### P0-2 容器以 root 运行

**位置**：`Dockerfile:1-5`

```dockerfile
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY target/tf-reader.jar /app/app.jar
EXPOSE 8103
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

没有 `USER`。Java 进程以 root 运行，两重风险：

1. 一旦有反序列化 / 文件读取漏洞，攻击者直接拿到容器 root；
2. 挂载的 `fishmatrix_reader-files` 命名卷里文件属主全是 root，运维想清理得先 `docker exec`。

**修**：

```dockerfile
RUN useradd -r -u 10001 -m app
COPY --chown=10001:10001 target/tf-reader.jar /app/app.jar
USER 10001
```

顺带建议：`HEALTHCHECK`、`-XX:MaxRAMPercentage=75`、`.dockerignore`（现在 `target/` 会进构建上下文）。

---

### P0-3 健康检查对外暴露内部细节

**位置**：`src/main/resources/application.yml:78`

```yaml
management:
  endpoint:
    health:
      show-details: always
```

任何能访问 8103 的人 GET `/actuator/health` 就能看到数据库连接串、Redis、磁盘明细。
分机防火墙确实只放通 `10.28.13.214`，但这是纵深防御里最不该省的一层。

**修**：`show-details: when-authorized`（配合 actuator security），或直接 `never`，
排障时用 `?showDetails=true` + 管理员凭据。

---

### P0-4 默认数据源指向生产库

**位置**：`src/main/resources/application.yml:28-30`

```yaml
url: jdbc:mysql://${MYSQL_HOST:10.28.13.214}:${MYSQL_PORT:3306}/${MYSQL_DB:fm_reader}...
username: ${MYSQL_APP_USER:fm_reader_app}
```

本地 `mvn spring-boot:run` 忘了带环境变量 → **直连生产库**。
而且生产库和测试库账号密码完全相同（AGENTS.md 坑 8），连错端口连报错都没有。

**修**：默认值改成 `127.0.0.1:13307`（测试库）或 `localhost:3306`，
让"不配置"等于"连不上"，而不是"连生产"。生产由 compose 注入，本来就注入。

---

### P0-5 裸 `orElseThrow()` 9 处 → 用户看到 500

**位置**：

| 文件 | 行 |
|---|---|
| `service/ShelfService.java` | 53, 65 |
| `service/AccessRequestService.java` | 70, 85, 93, 107, 121 |
| `service/CategoryService.java` | 31, 43 |

`Optional.orElseThrow()` 无参版本抛 `NoSuchElementException`，
它不是 `ApiException`，被 `GlobalExceptionHandler.handleUnexpected` 接住 → **HTTP 500**
+ 日志刷 `ERROR` 告警 + 前端提示"服务开小差了"。

而这些位置全是"记录刚好被别人删了"这种**正常业务分支**，应该是 404。

**修**：统一 `orElseThrow(() -> new NotFoundException("..."))`。
建议加一条 ArchUnit 规则或 Checkstyle `IllegalThrows` 直接禁掉无参调用。

---

## 2. 🟠 P1：架构与契约欠账

### P1-1 三套异常机制并存

| 机制 | 出现次数 | 出口 |
|---|---|---|
| `new IllegalArgumentException(...)` | 23 | 400 `BAD_REQUEST`（无业务码） |
| `new NotFoundException(...)` | 13 | 404 `NOT_FOUND` |
| `ApiException.badRequest/forbidden/conflict/tooManyRequests` | ~10 | 对应 code |

同一个"参数不对"，`Validation.text()` 抛 `IllegalArgumentException`，
`AccessRequestService` 抛 `ApiException.badRequest` —— 前端拿到的 `code` 不一样，
没法按 code 做国际化或埋点。

**修**：业务层只用 `ApiException`（及其子类），`ErrorCodes` 里补 `BOOK_NOT_FOUND`
`ACCESS_REQUEST_CONFLICT` 等具体码；`IllegalArgumentException` 留给框架边界
（参数绑定、`@Valid`、`Optional`）。

---

### P1-2 状态与权限是裸字符串，且多份定义

**状态字面量**：

| 值 | 出现位置 |
|---|---|
| `"PUBLIC"` / `"PRIVATE"` | `LibraryService.SCOPE_*`、`BookRepository` SQL 里 `'PUBLIC'`、`BookRecord.isPublic()` 里 `"PUBLIC"` |
| `"VISIBLE"` / `"HIDDEN"` | `LibraryService.STATUS_*`、`BookRepository` SQL、`BookRecord.isVisible()` |
| `"PENDING"/"APPROVED"/"REJECTED"/"CANCELLED"/"REVOKED"` | `AccessRequestService` 的 `switch`、`AccessRequestRepository` 的 SQL 与 `upsertPending`、`decide` |

同一组值有 3 处独立定义。加一个 `"ARCHIVED"` 状态，要改 3 个 Java 文件 + 2 段 SQL，
漏一个就是"状态查不到"。

**权限字符串**：`"reader.content.manage.any"` 重复 6 次、
`"reader.category.manage"` 5 次、`"reader.private.manage"` 2 次、
`"reader.public.upload"` 1 次，散在 4 个文件里。拼错一个字母 = 静默无权限。

**修**：
- `enum BookScope { PUBLIC, PRIVATE }`、`enum BookStatus`、`enum AccessStatus`，
  用 MP 的 `@EnumValue` 存 VARCHAR，`@JsonValue` 保持对外字符串不变（**不破坏前端契约**）；
- `final class ReaderPermissions { public static final String CONTENT_MANAGE_ANY = ...; }`。

---

### P1-3 权限判定逻辑有两份实现

**位置**：`service/LibraryService.java:258-271` 与 `service/ShelfService.java:95-101`

```java
// LibraryService
if (book.isPrivate()) {
    if (book.isOwnedBy(viewer.id()) || bookRepository.hasApprovedAccess(bookId, viewer.id())) return book;
    throw new NotFoundException("书籍不存在");
}
if (book.isVisible() || viewer.hasPermission("reader.content.manage.any")) return book;

// ShelfService.canRead —— 同一套规则，换了个写法
if (book.isPublic()) {
    return book.isVisible() || viewer.hasPermission("reader.content.manage.any");
}
return book.isOwnedBy(viewer.id()) || bookRepository.hasApprovedAccess(book.id(), viewer.id());
```

**这是安全相关的规则**。两份实现意味着：哪天产品说"被拒绝过的用户不能再读"，
改了一处就漏一处，漏的那处就是越权入口。

**修**：抽 `BookAccessPolicy`（`@Component`），提供
`boolean canRead(BookRecord, CurrentUser)` / `void requireReadable(...)`，
`LibraryService` 与 `ShelfService` 都只调它。这也让这条规则**可单测**。

---

### P1-4 新旧两套数据访问层并存，且新的那套零接线

- `repository/*`（6 个类，JdbcTemplate）—— 所有 `service` 都在用；
- `mapper/*`（9 个接口）+ `entity/*`（9 个类）+ `converter/BookConverter` —— **完全没有被调用**。

实测：`BookConverter` 全仓只有 1 次命中（声明处本身），`mapper` 除测试外无引用。

问题不在"暂时没用"，而在**新人打开项目会看到两套写法，然后照抄错的或抄新的**，
而新的那套还没被业务验证过（只有 2/9 张表跑过真实 SQL）。

**修**（按性价比）：
1. 立刻：给 `repository/*` 加 `@Deprecated(forRemoval = true)` + 包级 `package-info.java`
   写清"Phase 2 将删除，新代码请用 `mapper/`"；
2. 短期：按业务域逐个切换（先 `category`/`shelf` 这种简单的），每切一个删一个旧仓储；
3. 别拖：两套并存的窗口越长，切换成本越高。

---

### P1-5 零参数校验，靠手写工具类

`jakarta.validation` / `@Valid` 出现次数：**0**。
`Validation.*` 被手工调用 **28 处**（多为 `text` / `truncate`），错误信息是硬编码中文串，
且分散在 service 层（本该在入参边界）。

```java
String title = request.title() == null
        ? book.title()
        : Validation.text(request.title(), 255, "书名", true);
```

**修**：

```java
public record BookUpdateRequest(
        @Size(max = 255) String title,
        @Size(max = 128) String author,
        @Size(max = 1024) String description,
        Long categoryId,
        @Pattern(regexp = "VISIBLE|HIDDEN") String status) {}
```

controller 加 `@Valid`，错误统一由 `GlobalExceptionHandler` 的
`handleValidation` 输出 `VALIDATION_FAILED` + `details.fields`。
`Validation` 只保留跨字段规则（如"起止时间""章节索引上界"）。

> 注意：改成注解校验会**改变错误响应体**（多出 `details.fields`）。因为要冻结契约，
> 建议和前端确认一次再动手，或先只加注解、保留现有 `code`。

---

### P1-6 响应结构不统一

| 写法 | 位置 |
|---|---|
| `Map.of("books", list)` 包一层 | `BookController:56,61`、`ReadingController:57`、`AnnotationController:37`、`CategoryController:33`、`ShelfController:36`、`AccessRequestController:39,46` |
| 直接返回 record | `BookPage`、`BookDetail`、`ShelfDetail`、`Shelf`、`Category`、`Annotation`… |
| controller 内嵌套 record 再包一层 | `ReadingController:74 ProgressResponse`、`AccessRequestController:71 AccessDecision` |

前端要针对每个接口写不同的解包逻辑，这是 bug 与返工的高发区。

**修**：定一种。建议列表接口统一 `{"items": [...], "total": n}`（可带 `page/size`），
单对象接口直接返回对象。`BookPage` 已经是这个形状，改名为 `PageResponse<T>` 复用。

---

### P1-7 DTO 散落在 controller

`ReadingController.ProgressResponse`、`AccessRequestController.AccessDecision` 定义在
controller 内部，而 `model/` 里又放着 `ProgressRequest`、`AccessRequestCreate`。

**修**：全部挪到 `dto/request` 与 `dto/response`，controller 只做"收/转/发"。

---

### P1-8 文件 I/O 在数据库事务里

**位置**：`service/LibraryService.java:100-180`

```java
@Transactional
public BookSummary importBook(...) {
    ...
    try (InputStream input = file.getInputStream()) {
        stored = storage.store(input, filename, ...);   // 8MB 落盘
    }
    ...
    bookId = bookRepository.insertBook(...);            // 才开始写库
    for (...) bookRepository.insertChapter(...);        // 可能上千章
}
```

事务持有一个数据库连接的同时做磁盘写 + 章节循环插入。8MB 文件、1000 章的书，
一个事务可能持续数秒到数十秒，连接池会被拖住；而且事务回滚**不会**删已落盘的文件
（现在靠 `catch (RuntimeException) { storage.delete(...) }` 补偿，补偿自己失败就被吞了）。

**修**：拆成三段——
1. 事务外：读字节、解码、解析章节、落盘（拿到 `storageKey`）；
2. 事务内：`insertBook` + 批量 `insertChapter`（用 `batchUpdate`，别逐条）；
3. 事务外：失败删文件（用 `try/finally`，别只 catch `RuntimeException`）。

---

### P1-9 N+1 查询

**位置**：`service/ShelfService.java:36-45`

```java
for (Long bookId : shelfRepository.findBookIds(shelfId)) {
    bookRepository.findById(bookId)                     // N 次
            .filter(book -> canRead(book, viewer))      // 私有的再 +1 次 hasApprovedAccess
            .ifPresent(book -> books.add(libraryService.summary(book, viewer)));
}
```

一个 50 本书的书单 = 51~101 次查询。

**修**：`findByIdIn(Collection<Long>)` 一次查完 + 一次批量查已批准访问关系。

---

### P1-10 删除靠手写级联，数据库没有外键

| 代码 | 手写删除的表数 |
|---|---|
| `BookRepository.deleteBook:191-199` | 7 张（shelf_book, access_request, annotation, bookmark, progress, chapter, book） |
| `CategoryRepository.delete:51-54` | 先 `UPDATE reader_book SET category_id = NULL`，再 DELETE |
| `ShelfRepository.delete:95-98` | 2 张 |

而 `V2__library_scopes.sql` 里只给 `reader_shelf_book.shelf_id` 建了外键，
`reader_access_request.book_id`、`reader_book.category_id`、`reader_shelf_book.book_id`
**都没有**。哪天有人在别处删了一本书，就会留下孤儿批注/进度/申请。

**修**：给这三列补 FK（`ON DELETE CASCADE` 或 `ON DELETE SET NULL`），
`deleteBook` 收敛成一条 `DELETE FROM reader_book WHERE id = ?`。
这一步需要 `V3` 迁移 + 清历史脏数据，属于"越晚做越贵"的典型。

---

### P1-11 没有并发控制

没有 `version` 列、没有 `@Version`、没有 `WHERE version = ?`。
两个人同时 PATCH 同一本书，后写的**静默覆盖**前写的，双方都不知道。

**修**：`ALTER TABLE ... ADD COLUMN version INT NOT NULL DEFAULT 0`，
实体加 `@Version`，`MybatisPlusConfig` 加 `OptimisticLockerInnerInterceptor`，
前端拿到 `409` 提示"内容已被修改，请刷新"。

---

### P1-12 `@Data` 用在持久化实体上

9 个实体全部 `@Data`，自动生成 `equals/hashCode`（**包含所有字段**）与 `toString`。

- `ReaderChapter.content` 已经用 `@ToString.Exclude` 处理了日志场景（做得好），
  但 `equals/hashCode` 里**仍然包含整章正文** —— 实体一旦进 `Set`/作为 `Map` key，
  哈希要扫几十 KB 字符串；
- `@Data` 生成 setter，实体成为可变对象，跨层传递时谁都能改（service 里 `book.setTitle(...)` 悄悄改状态）。

**修**：`@Getter @Setter`（或 `@Getter` + 构造器）+
`@EqualsAndHashCode(onlyExplicitlyIncluded = true)` 且只包含主键。

---

### P1-13 业务写操作零日志

全仓 `Logger` / `log.` 出现次数：**0**。

`GlobalExceptionHandler` 只记录异常出口，所以：

- 导入一本 8MB 的书 —— 没有日志；
- 管理员批准了别人的私有书访问 —— 没有日志；
- 删掉一本书 —— 没有日志。

出事之后（"我书怎么没了""谁批的"）**无法追溯**。这是运维事故里最常见的一类。

**修**：关键写操作打结构化日志，复用 tf-common 的 `kv`：

```java
log.info("导入书籍完成", kv("event", "book.imported"), kv("book_id", bookId),
        kv("chapters", chapters.size()), kv("bytes", stored.size()), kv("owner_id", viewer.id()));
```

红线：日志里**永远不要**打文件内容、`share_token` 全量、身份签名。

---

## 3. 🟡 P2：可维护性问题

| # | 问题 | 位置 | 建议 |
|---|---|---|---|
| P2-1 | `LibraryService` 342 行，扛了权限、导入、存储、分享、映射、归一化 6 件事 | `service/LibraryService.java` | 拆 `BookQueryService` / `BookCommandService` / `BookImportService` / `BookAccessPolicy` / `BookAssembler` |
| P2-2 | `HealthController` 与 `/actuator/health` 重复，且 `@Value` 字段注入 | `controller/HealthController.java:13` | 删掉；或改构造注入 `@Value` |
| P2-3 | 死代码 | `util/Validation.java:12 requireUserId`、`converter/BookConverter` | 删，或`BookConverter`立刻接线（见 P1-4） |
| P2-4 | 同义反复测试 | `DependencyPresenceTest`（测依赖存在）、`EntityMappingTest:98 exactlyNineTableEntities`（测常量=9） | 删。它们不会因为代码变坏而失败 |
| P2-5 | 同一段 SELECT 写两遍 | `repository/ShelfRepository.java:26-37, 39-50` | 提 `private static final String SELECT_SHELF` |
| P2-6 | `delete` 不校验存在性；`update` 用 400 表达 404 | `service/CategoryService.java:37-49` | 删不存在的分类返回 404；`update` 改 `NotFoundException` |
| P2-7 | 分享链接永久有效，无过期、无吊销 | `service/LibraryService.java:239-249` | 加 `share_token_expires_at`，或提供 `DELETE /share` |
| P2-8 | 分页/批量上限散落 | `LibraryService:43 MAX_PAGE_SIZE`、`AnnotationService:19 MAX_BATCH`、controller 默认值 `20`/`200` | 提 `ReaderLimits` 常量类，controller 引用同一常量 |
| P2-9 | `Long != long` 隐式拆箱比较 | `AccessRequestService:100,114,127`、`BookRecord.isOwnedBy:255` | `Objects.equals(...)` 或 `.longValue()`。当前列 NOT NULL 不炸，但风格危险 |
| P2-10 | 注释写"重构前怎样"，而不是"为什么" | `converter/BookConverter.java:13` | 删历史叙述；注释只解释**非显然的决策** |
| P2-11 | Javadoc 覆盖不一致 | `model/BookSummary` 有，`model/Shelf`/`Category` 没有 | 公开类型至少一行说明；内部实现靠命名自述 |
| P2-12 | README 与代码漂移 | `README.md:105` 写 `10.28.25.234`（代码是 `10.28.13.214`）；`:110` 写 `/app/data/files`（代码是 `./data/files`） | 修，并约定"配置默认值只写在 `application.yml`，README 不重复抄" |
| P2-13 | 无 `.editorconfig`、无格式化强制、无静态检查 | 仓库根 | 见 `docs/dev/code-style.md` 第 2 节 |
| P2-14 | 设计文档放在 `docs/superpowers/{specs,plans}`（agent 工具目录），不是团队结构 | `docs/` | 迁到 `docs/adr/NNNN-*.md`（决策）+ `docs/design/*.md`（设计） |
| P2-15 | 仓库内没有 `AGENTS.md` / `CONTRIBUTING.md`，构建坑只存在于开发机 | 仓库根 | 把 AGENTS.md 里"环境/踩坑"段落纳入仓库，否则新人必踩 `JAVA_HOME` 和 `tf-common` 镜像劫持 |
| P2-16 | `pom.xml` 无 enforcer / 无覆盖率 / 无依赖收敛 | `pom.xml` | 见 code-style 第 2 节 |
| P2-17 | 集群地址硬编码 3 次 | `application.yml:10,28,33` | 提 `${FM_CLUSTER_HOST:10.28.13.214}` |
| P2-18 | MP 配置与默认值重复（噪声） | `application.yml:54-56` | `banner:false`、`id-type:auto` 是默认值，删掉更干净 |

补充两条不在上表、但值得单独说的：

**P2-19 `BookController.books?scope=private` 是假分页**

```java
List<BookSummary> books = libraryService.listMine(viewer);
return new BookPage(books, books.size(), 0, books.size());
```

返回 `total = size = page 数`，前端拿到 `size=0`（空书单时）。且 `/books?scope=private`
与 `/books/mine` 完全重复。这是上一轮已知债务，Phase 2 一并处理。

**P2-20 `api/openapi.yaml` 与代码无自动校验**

契约文件存在（19KB），但 CI 里没有"代码实现 vs 契约"的比对，
也没有用 springdoc 从代码生成。两边会持续漂移，最后谁都不信契约。
建议二选一：springdoc 生成（代码即真相）或在 CI 加 `openapi-diff`（契约即真相）。

---

## 4. ⚪ 企业标配能力缺口清单

这些不是"代码写错"，而是"框架里根本没有"，所以每个新功能都要手工补一遍：

| # | 能力 | 现状 | 缺失后果 |
|---|---|---|---|
| 1 | 参数校验（`jakarta.validation`） | 0 处 | 每个接口手写 if，漏一个就是脏数据 |
| 2 | 错误码枚举 | 字符串散落 | 前端无法按 code 分支；改文案要动代码 |
| 3 | 字典枚举（scope/status） | 裸字符串 ×3 份 | 新增状态必漏改 |
| 4 | 乐观锁 | 无 | 并发编辑静默覆盖 |
| 5 | 软删除 / 删除审计 | 物理删除 | 误删不可恢复，无"谁删的" |
| 6 | 业务日志 | 0 | 事故无法追溯 |
| 7 | 业务指标（Micrometer 自定义） | 无 | 不知道导入成功率、审批耗时 |
| 8 | 外键约束 | 缺 3 个 | 孤儿数据 |
| 9 | 批量写入 | 逐条 insert | 1000 章的书导入慢 |
| 10 | 代码格式化 / 静态检查 | 无 | 风格漂移，review 耗在空格式上 |
| 11 | 覆盖率门槛 | 无（40 个测试，无 JaCoCo） | 不知道哪些分支没测 |
| 12 | 分层守护测试（ArchUnit） | 无 | controller 直接调 repository 没人拦 |
| 13 | 依赖漏洞扫描（Dependabot / OWASP） | 无 | CVE 靠运气 |
| 14 | ADR / 团队文档 | 无 | 决策只活在聊天记录里 |

**优先级建议**：#1 #2 #3 #6 是"每写一个功能都会被 prod 一次"的，随 Phase 2 一起做；
#10 #12 是"一次投入长期收益"，见下一份文档；其余排到功能稳定后。

---

## 5. 建议的推进顺序

```
第 0 步（本周，1~2 天）
  └─ P0-1 分页钳制、P0-5 裸 orElseThrow、P0-3 健康检查、P0-4 默认数据源
     —— 都是小改，风险低，收益立刻

第 1 步（与 Phase 2 同时）
  ├─ P1-2 枚举化字典 + P1-3 BookAccessPolicy
  ├─ P1-1 统一 ApiException + P1-5 参数校验
  └─ 每切换一个业务域，就顺手把 P1-13 日志补上

第 2 步（Phase 2 收尾）
  ├─ P1-10 外键 + P1-11 乐观锁（同一支 V3 迁移）
  └─ P1-8 拆导入事务 + P1-9 批量化（导入是唯一的重路径）

第 3 步（功能开发中期）
  ├─ P1-4 删旧仓储
  ├─ P2-13 格式化/静态检查落地（code-style.md）
  └─ P2-14/15 文档归位

持续（随手）
  └─ P2 其余项
```

---

## 6. 审查者保留意见

- 本报告只针对当前快照，未覆盖运行时行为（未压测、未做故障注入）。
- P0-4「默认指向生产」可能是团队**有意**的部署约定，若是，请在 README 里写明理由并加启动横幅警告；
  本报告按"最小惊讶原则"仍判为 P0。
- 未审计 `tf-common` 的安全实现（`IdentitySigner`、`LocalFileStorage` 路径处理），建议单独一轮。
