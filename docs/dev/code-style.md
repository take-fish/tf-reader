# tf-reader 代码风格与工程规范

> 版本：v1.0（2026-10-27）
> 适用范围：`tf-reader` 仓库全部 Java 代码、SQL 迁移、配置与文档。
> 定位：**团队协作契约**。写得"好看"不是目的，目的是让三个人的代码看起来像一个人写的，
> 让 review 只讨论设计，不讨论空格。
>
> 与它配套的是 `docs/dev/code-review-2026-10-27.md`（现状差距）。
> 本文件的每一条规则都标注了 **【强制】/【建议】**，强制项未来由 CI 自动拦截。

---

## 0. 一句话总纲

> **分层清晰、契约稳定、错误统一、字典成枚举、日志能追溯、格式交给工具。**

---

## 1. 分层与依赖方向

### 1.1 【强制】依赖只能单向

```
controller ──▶ service ──▶ repository / mapper ──▶ DB
                   │
                   └──▶ converter（MapStruct，纯函数）
```

- `controller` **禁止** import `repository` / `mapper` / `entity`；
- `service` **禁止** import `controller`、**禁止** import `jakarta.servlet.*`（拿不到 HttpServletRequest）；
- `repository` / `mapper` **禁止** import `service`；
- `entity` **禁止** import `model` / `dto`（实体不知道自己被谁用）。

> 现状违反：暂无（`ShelfService` 通过 `LibraryService` 调用是合规的）。
> 建议用 ArchUnit 固化成测试（见 11.4），否则半年后一定有人为了省事直接注 `BookRepository`。

### 1.2 【强制】entity 不出 service 层

`entity/*` 是**数据库行的镜像**，可以带 MP 注解。它**绝不能**出现在 controller 签名、
不能进 HTTP 响应、不能当 DTO 用。

理由：`ReaderChapter` 有 `content`（整章正文）、`ReaderBook` 有 `shareToken` / `storageKey`。
实体一旦直接序列化，就是把内部字段全暴露给前端。

```java
// ❌ 禁止
@GetMapping("/books/{id}")
public ReaderBook get(@PathVariable long id) { ... }

// ✅ 正确
@GetMapping("/books/{id}")
public BookDetail get(@PathVariable long id) { ... }
```

### 1.3 【强制】controller 只做四件事

1. 取身份（`UserContext.require()`）；
2. 触发校验（`@Valid`）；
3. 调 service；
4. 返回结果 / 设置状态码。

出现 `if` 分支做业务判断、出现 `Map.of(...)` 拼装、出现事务注解 —— 都是错位。

```java
// ❌ 现状（BookController:46-49）：controller 里做业务分支 + 伪造分页
if ("private".equalsIgnoreCase(scope)) {
    List<BookSummary> books = libraryService.listMine(viewer);
    return new BookPage(books, books.size(), 0, books.size());
}
```

---

## 2. 格式化：全部交给工具，不许手写争论

### 2.1 【强制】基准格式

| 项 | 取值 | 理由 |
|---|---|---|
| 缩进 | **4 空格**，禁用 Tab | 与现有代码一致 |
| 续行缩进 | **8 空格** | |
| 行宽 | **120 列**（软上限 100） | 现有代码 99% 在 120 内，几乎零重排 |
| 换行符 | 仓库内统一 LF；Windows 工作区由 `core.autocrlf=true` 还原为 CRLF | 不要手动改行尾，也不要提交 `.gitattributes` 里带 `eol=lf` 的规则（那会让全仓工作区重写一次） |
| 文件末尾 | 必须且只有一个空行 | |
| 行尾空格 | 禁止 | |
| import | 禁止 `*`；按 `java → jakarta → org → com` 分组，组间一空行 | |
| 未使用 import | 必须删除 | IDE 自动，CI 兜底 |

### 2.2 【强制】用 Spotless 固化，不靠人自觉

在 `pom.xml` 的 `<build><plugins>` 加：

```xml
<plugin>
  <groupId>com.diffplug.spotless</groupId>
  <artifactId>spotless-maven-plugin</artifactId>
  <version>2.44.0</version>
  <configuration>
    <java>
      <palantirJavaFormat>
        <version>2.50.0</version>
      </palantirJavaFormat>
      <removeUnusedImports/>
      <trimTrailingWhitespace/>
      <endWithNewline/>
      <importOrder>
        <order>java,jakarta,org,com,</order>
      </importOrder>
    </java>
    <pom>
      <sortPom><expandEmptyElements>false</expandEmptyElements></sortPom>
    </pom>
  </configuration>
  <executions>
    <execution>
      <id>spotless-check</id>
      <phase>verify</phase>
      <goals><goal>check</goal></goals>
    </execution>
  </executions>
</plugin>
```

**为什么选 palantir 而不是 google-java-format**：
google 格式是 2 空格 / 100 列，会把现有 4 空格代码全量重排；
palantir 是 4 空格 / 120 列，与本仓库现状吻合，格式化后 diff 最小。

配套动作（一次性）：
1. `mvn spotless:apply` 跑一遍全量格式化，**单独一个 commit**（`style: 统一代码格式`），
   这样 review 时能明确"这个 commit 只动空格"；
2. 在 `.git-blame-ignore-revs` 里登记该 commit 的 hash，`git blame` 才能跳过它；
3. CI 的 `verify` 阶段自动 `spotless:check`，不通过就红。

### 2.3 【强制】`.editorconfig`

放仓库根，让所有 IDE 自动对齐：

```ini
root = true

[*]
charset = utf-8
insert_final_newline = true
trim_trailing_whitespace = true
indent_style = space
indent_size = 4
max_line_length = 120

[*.{yml,yaml,json}]
indent_size = 2

[*.md]
trim_trailing_whitespace = false
```

---

## 3. 包结构：按业务域切，不按技术层切

### 3.1 【建议】目标结构

现状是"按技术层横切"（`controller/ service/ repository/ model/`），
一个"书单"功能要跳 5 个包。功能一多，每个包都变成大杂烩（`LibraryService` 342 行就是结果）。

推荐 **package-by-feature**：

```
com.fishmatrix.reader
├── ReaderApplication.java
├── common/                       # 本服务内的横切关注点
│   ├── error/                    # ApiException 子类、ErrorCodes 扩展
│   ├── policy/BookAccessPolicy   # 权限判定（唯一真相）
│   ├── support/                  # 分页、时间、文本工具
│   └── constant/ReaderPermissions, ReaderLimits
├── book/                         # 书籍域
│   ├── BookController
│   ├── BookQueryService / BookCommandService / BookImportService
│   ├── BookMapper (MP) / BookRepository (遗留，待删)
│   ├── BookEntity
│   ├── BookConverter (MapStruct)
│   └── dto/  BookSummary, BookDetail, BookPage, BookUpdateRequest
├── chapter/
├── reading/                      # 进度 + 书签
├── annotation/
├── shelf/
├── category/
└── access/                       # 访问申请
```

### 3.2 【强制】`dto/` 内部再分方向

```
dto/
├── request/    XxxRequest, XxxCreate     ← 入参，只在这里放校验注解
└── response/   XxxSummary, XxxDetail     ← 出参，只读，永不变
```

`model/` 这个包名要消失 —— 它现在同时装着请求、响应、领域对象，名字没有信息量。

### 3.3 【强制】DTO 是 `record`

- 请求/响应用 `record`（不可变、无 setter、天然线程安全）；
- **不要**给 DTO 加 `@Data`；
- 嵌套 DTO **不要**写在 controller 里（现状：`ReadingController.ProgressResponse`、
  `AccessRequestController.AccessDecision`），必须进 `dto/`。

---

## 4. 命名

### 4.1 【强制】Java 命名

| 元素 | 规则 | 示例 |
|---|---|---|
| 包 | 全小写，单数 | `com.fishmatrix.reader.book` |
| 类 | 大驼峰，名词 | `BookImportService` |
| 接口 | 大驼峰，**不加 `I` 前缀** | `BookMapper` |
| 方法 | 动词开头，小驼峰 | `findReadable`、`markAsHidden` |
| 常量 | 全大写下划线 | `MAX_PAGE_SIZE` |
| 枚举 | 大驼峰，常量全大写 | `BookStatus.HIDDEN` |
| 布尔 | `is/has/can/should` 开头 | `isVisible()`、`canRead()` |
| 泛型 | 单大写字母 | `T`、`R` |

### 4.2 【强制】方法名语义固定

| 前缀 | 含义 | 返回 |
|---|---|---|
| `get` | 一定成功，不存在就是 bug | 对象 |
| `find` | 可能不存在 | `Optional<T>` / `List<T>` |
| `list` / `search` | 集合查询 | `List<T>` |
| `count` / `exists` | 计数/存在 | `long` / `boolean` |
| `create` / `insert` | 新建 | 新 ID 或对象 |
| `update` / `patch` | 更新 | 对象 / void |
| `delete` / `remove` | 删除 | void / boolean |
| `require` | 断言存在，否则抛业务异常 | 对象 |
| `is` / `has` / `can` | 判定 | boolean |

> 这条能消除一类 bug：`getXxx()` 返回 `Optional` 是自相矛盾的（get 表示"一定在"）。
> 现状 `Validation.requireChapterIndex`、`LibraryService.requireReadable` 用法正确，保持。

### 4.3 【强制】类名后缀

| 后缀 | 只能用于 |
|---|---|
| `Controller` | `@RestController` |
| `Service` | `@Service`，**承载业务规则**；不要叫 `Manager` / `Helper` / `Util` |
| `Repository` | 遗留 JdbcTemplate（Phase 2 删除） |
| `Mapper` | MyBatis-Plus `BaseMapper` |
| `Converter` | MapStruct `@Mapper(componentModel="spring")` |
| `Policy` | 纯判定逻辑（无状态，可单测） |
| `Config` | `@Configuration` |
| `Properties` | `@ConfigurationProperties` |

禁止：`Utils`（除非是纯静态无依赖函数，且 `final` + 私有构造）、`Common`、`Base`、`Manager`、`Helper`。

### 4.4 【强制】数据库命名

- 表：`snake_case`，业务前缀 `reader_`，复数不加 `s`；
- 列：`snake_case`；
- 主键 `id`；外键 `<entity>_id`；
- 唯一键 `uk_<表>_<列>`；普通索引 `idx_<表>_<列>`；外键 `fk_<表>_<列>`；
- 审计列固定：`created_at` / `updated_at` / `deleted_at`（软删时）；
- 布尔列用 `is_` 前缀或形容词，**不用** `0/1` 之外的编码。

---

## 5. 注释与 Javadoc

### 5.1 【强制】注释写"为什么"，不写"是什么"

```java
// ❌ 无信息量
/** 设置标题 */
public void setTitle(String title) { ... }

// ❌ 写历史（对后来者无意义）
/** 与重构前 LibraryService.summary(...) 的计算口径保持一致 */

// ✅ 写非显然的决策
/**
 * 私有内容对无权用户统一返回 404 而不是 403：
 * 403 会暴露"这本书存在"，等于泄露书名。
 */
throw new NotFoundException("书籍不存在");
```

### 5.2 【强制】语言与格式

- 注释用**中文**，标点用中文标点（`,`→`，`）；
- 代码标识符保持英文，中英之间**不加空格**（`调用 requireReadable 方法`）；
- 类级 / 公开方法级用 `/** */`；方法内部用 `//`；
- **禁止**提交注释掉的代码（`// bookRepository.delete(...)`），要么删要么留。
- **禁止** `TODO` 无主：必须写 `// TODO(姓名, 2026-11-01): 原因`，否则不合并。

### 5.3 【建议】Javadoc 的最小集

- 所有 `public` 的 service 方法：一句话说明"做什么 + 什么情况下抛什么异常"；
- 所有 `record` 字段含义不显然时：类级 Javadoc 说明；
- 复杂 SQL（XML）：在 `<select>` 上方用 XML 注释说明为什么要 JOIN / 为什么要这个顺序。

---

## 6. 错误处理

### 6.1 【强制】只有一套异常体系

业务层**只允许**抛 `com.fishmatrix.common.error.ApiException` 及其子类：

```java
throw ApiException.badRequest("书名不能为空");
throw ApiException.forbidden("没有管理这本书的权限");
throw ApiException.notFound("书籍不存在");
throw ApiException.conflict("INVALID_STATE", "只有待审批的申请才能取消");
throw ApiException.tooManyRequests("REQUEST_COOLDOWN", "24 小时内不能重复申请");
```

**禁止**在业务层 `new IllegalArgumentException(...)` / `new RuntimeException(...)`。
理由：`GlobalExceptionHandler` 把 `IllegalArgumentException` 统一映射成
`400 BAD_REQUEST`，丢掉业务语义，前端只能按 message 字符串匹配。

> 现状：23 处 `IllegalArgumentException` 待迁移（见 review P1-1）。
> 迁移期允许保留，但**新代码不许再写**。

### 6.2 【强制】禁止裸 `orElseThrow()`

```java
// ❌ 抛 NoSuchElementException → 被兜底成 500
return repo.findById(id).orElseThrow();

// ✅
return repo.findById(id).orElseThrow(() -> new NotFoundException("分类不存在"));
```

> 现状 9 处（review P0-5）。

### 6.3 【强制】不吞异常

```java
// ❌
try { storage.delete(key); } catch (Exception ignored) { }

// ✅ 要么记录，要么降级，要么往上抛
try {
    storage.delete(key);
} catch (RuntimeException e) {
    log.warn("回滚已落盘文件失败", kv("event", "storage.cleanup_failed"),
             kv("storage_key", key), e);
}
```

### 6.4 【强制】错误码用常量，不用裸字符串

```java
// ❌
throw ApiException.conflict("INVALID_STATE", "只有待审批的申请才能取消");

// ✅
throw ApiException.conflict(ErrorCodes.ACCESS_INVALID_STATE, "只有待审批的申请才能取消");
```

新增错误码统一登记在 `common/error/ReaderErrorCodes.java`，格式 `SCREAMING_SNAKE_CASE`，
并在 README 的错误码表里补一行。

---

## 7. 事务

### 7.1 【强制】`@Transactional` 只加在 service 的公开方法上

- controller 加事务 → 事务边界跟着 HTTP 走，错；
- private 方法加事务 → **不生效**（Spring 代理不拦私有方法），是最隐蔽的 bug 之一；
- 同一个类的两个方法互调 → 不走代理，事务不生效；跨 service 调用才有效。

### 7.2 【强制】只读查询不加 `@Transactional`

单条查询本身原子，加事务只会白占连接。
需要"多条读看到同一快照"时才加 `@Transactional(readOnly = true)`。

### 7.3 【强制】事务里不做 I/O、不做远程调用

事务持有数据库连接，HTTP / 磁盘 / 大循环都在拖住连接池。

```
❌ @Transactional { 读文件 → 解析 → 写库(1000 条) → 调 user 服务 }
✅ 事务外: 读文件 → 解析
   事务内: 批量写库
   事务外: 清理 / 通知
```

> 现状违反：`LibraryService.importBook`（review P1-8）。

### 7.4 【建议】显式声明回滚规则

默认只回滚 `RuntimeException`。如果方法内可能抛受检异常且需要回滚，写
`@Transactional(rollbackFor = Exception.class)`；否则**不要**画蛇添足。

---

## 8. 数据访问

### 8.1 【强制】新代码只用 MyBatis-Plus

- 简单 CRUD：`BaseMapper`；
- 多表 / 动态条件 / 聚合：XML（`resources/mapper/*.xml`），**不要**用 `@Select` 注解写长 SQL
  （注解里没有语法高亮、没有 `<if>`、改一个条件要重编译）；
- **禁止**在 Java 里拼 SQL 字符串（现状 `JdbcTemplate` 那套属于待删遗留代码，
  新代码不得仿写）。

### 8.2 【强制】查询列显式列出

```xml
<!-- ❌ SELECT * 会被加列、加字段影响 -->
SELECT * FROM reader_book

<!-- ✅ -->
SELECT b.id, b.title, b.author, b.updated_at
```

例外：确实要全部列且表结构稳定时允许 `b.*`，但必须配一个"实体与表一致"的守护测试。

### 8.3 【强制】分页必须双向钳制

```java
int safePage = Math.clamp(page, 0, MAX_PAGE);
int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
long offset  = (long) safePage * safeSize;   // 用 long，别用 int
```

> 现状违反：`LibraryService:66-69` 只钳了 size，没钳 page，且用 int 相乘（review P0-1）。

### 8.4 【强制】复合主键表不用 `selectById` / `updateById` / `deleteById`

`reader_progress`、`reader_shelf_book` 没有单列主键，MP 的单主键 API 对它们无意义。
写操作走 XML 自定义方法。

### 8.5 【强制】审计字段由 `MetaObjectHandler` 填，业务代码不写 `Instant.now()`

- `created_at` / `updated_at` / `added_at` 类型**必须**是 `java.time.Instant`；
- 字段必须标 `@TableField(fill = ...)`，否则 `strictInsertFill` 静默跳过（不报错，值就是 null）；
- 业务逻辑里出现 `Instant.now()` 作为"创建/更新时间"即为违规（作为业务时间，如"申请时间"可以）。

### 8.6 【强制】批量写用 `batchUpdate`，禁止循环单条 insert

导入 1000 章的书 = 1000 次往返。用 `SqlSessionFactory` 的 batch executor 或
MP 的 `saveBatch`（配 `rewriteBatchedStatements=true`）。

### 8.7 【建议】删除靠数据库约束，不靠手写级联

新表必须声明外键；已有缺外键的三列（`category_id`、`access_request.book_id`、
`shelf_book.book_id`）在 `V3` 补齐。手写 7 条 DELETE 的写法不再新增。

---

## 9. 日志

### 9.1 【强制】用 SLF4J，禁止 `System.out` / `printStackTrace`

```java
private static final Logger log = LoggerFactory.getLogger(BookImportService.class);
```

### 9.2 【强制】结构化字段用 `kv`

本项目日志已统一为 JSON（tf-common 的 logback base），字段必须走 `kv`：

```java
log.info("导入书籍完成",
        kv("event", "book.imported"),
        kv("book_id", bookId),
        kv("chapters", chapters.size()),
        kv("owner_id", viewer.id()));
```

- `event` 字段**必填**，命名 `域.动作`（`book.imported`、`access.approved`）；
- 事件名是全团队的检索锚点，**不要**临时造词。

### 9.3 【强制】级别约定

| 级别 | 用于 | 会告警吗 |
|---|---|---|
| `ERROR` | 需要人介入：数据不一致、外部依赖不可用 | 会 |
| `WARN` | 可自愈但要观察：重试成功、降级、补偿失败 | 看 |
| `INFO` | 业务事实：谁做了什么 | 否 |
| `DEBUG` | 排障细节，生产默认关 | 否 |

**禁止**：正常业务流程打 `ERROR`；`catch` 里只 log 不处理还标 ERROR（噪音）。

### 9.4 【强制】日志里不得出现

文件正文、`share_token` 全量、`Authorization` 头、`FM_IDENTITY_SECRET`、完整 SQL 参数、
用户手机号/身份证。需要排查时打**前缀或 hash**。

### 9.5 【强制】异常日志用 `log.error(msg, kv..., e)` 结尾传异常

不要 `log.error(e.getMessage())`（丢栈）；不要再拼 `"异常: " + e`（重复）。

---

## 10. 测试

### 10.1 【强制】命名

```java
@Test
@DisplayName("私有书对非 owner 返回 404，且不暴露书名")
void privateBook_isInvisibleToStranger() { ... }
```

- 方法名 `场景_预期` 的蛇形写法（可直接读成句子）；
- 必须有 `@DisplayName`，用中文写**业务规则**；
- 一个测试只断言一件事（一个 `assertThat` 链可以，但不要测两个不相关行为）。

### 10.2 【强制】测试金字塔

| 层 | 用什么 | 占比目标 |
|---|---|---|
| 单元（service / policy / util） | 纯 JUnit + Mockito，不起 Spring | 60% |
| 集成（repository / mapper / SQL） | `@SpringBootTest` + H2(MODE=MySQL) | 30% |
| 契约（controller） | `@WebMvcTest` + MockMvc | 10% |

**禁止**为了"覆盖率好看"写同义反复测试：

```java
// ❌ 测常量等于常量；代码怎么改它都不会失败
assertThat(EXPECTED).hasSize(9);

// ❌ 测依赖在 classpath 上
assertThat(loadable("lombok.Data")).isTrue();
```

> 现状：`DependencyPresenceTest`、`EntityMappingTest.exactlyNineTableEntities` 属于此类，应删。

### 10.3 【强制】集成测试必须 `@Transactional`

否则数据污染后续测试，且顺序敏感。现状 `ReaderLibraryIntegrationTest` 已正确使用。

### 10.4 【强制】测试数据用构造器，不用共享可变状态

`static` 可变字段、`@BeforeAll` 里改全局状态都会造成测试间耦合。

### 10.5 【建议】分层守护测试（ArchUnit）

```java
@AnalyzeClasses(packages = "com.fishmatrix.reader")
class ArchitectureTest {

    @ArchTest
    static final ArchRule controller_must_not_touch_repository =
            noClasses().that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat().resideInAnyPackage("..repository..", "..mapper..");

    @ArchTest
    static final ArchRule entity_must_not_leak_to_controller =
            noClasses().that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat().resideInAPackage("..entity..");
}
```

一次投入，永久拦住"图省事直接注仓储"。

---

## 11. 配置与密钥

### 11.1 【强制】默认值必须"安全失败"

```yaml
# ❌ 不配就连生产
url: jdbc:mysql://${MYSQL_HOST:10.28.13.214}:3306/fm_reader

# ✅ 不配就连本地，连不上就报错
url: jdbc:mysql://${MYSQL_HOST:127.0.0.1}:${MYSQL_PORT:13307}/fm_reader
```

### 11.2 【强制】密钥只走环境变量

`FM_IDENTITY_SECRET`、数据库密码、Nacos 密码**永远**不能有非空默认值、
不能进 Git、不能进日志、不能进聊天工具。

### 11.3 【强制】一个概念一个配置项

集群地址 `10.28.13.214` 现在在 `application.yml` 出现 3 次（nacos/mysql/redis）。
提成 `${FM_CLUSTER_HOST}`，改一次全改。

### 11.4 【建议】配置项必须能追溯到使用点

新增 `app.reader.*` 时，同步在 README 的「环境变量」表里加一行；
orphan 配置（配了没人读）在 review 时删掉。

---

## 12. Git 与提交流程

### 12.1 【强制】commit message 格式

```
<type>(<scope>): <中文短句，不超过 50 字>

<可选正文：为什么这么做，而不是做了什么>
```

`type` 只允许：`feat` `fix` `refactor` `perf` `test` `docs` `style` `chore` `build` `ci` `revert`。

`scope` 用业务域：`book` `chapter` `reading` `shelf` `category` `access` `auth` `db` `ci`。

```
feat(access): 支持被拒后 24 小时内不可重复申请
fix(book): 修复分页 page 溢出导致 OFFSET 为负
refactor(book): 抽出 BookAccessPolicy 统一权限判定
style: 全量格式化（spotless:apply，无逻辑变更）
```

### 12.2 【强制】一个 commit 一件事

"格式化"与"改逻辑"**绝不能**在同一个 commit —— 否则 review 时看不出哪行是真改动。

### 12.3 【强制】分支模型（沿用现状）

- `master`：保护，只收 PR，squash 合并，CI 必过，合并即发版；
- `test`：联调分支，**直接推**，不写 PR；
- `feature/*`：从 `origin/master` 拉，**不从 `test` 拉**；禁止 `test → master` 合并。

### 12.4 【强制】PR 自检

- [ ] `mvn -B -ntp verify` 本地通过
- [ ] `mvn spotless:check` 通过
- [ ] 新增/修改的行为有测试，且测试能在"改错代码"时变红
- [ ] 没有新增 `IllegalArgumentException` / 裸 `orElseThrow()` / 裸状态字符串
- [ ] 数据库变更同时改了 `schema-h2.sql`（两份 schema 并存期）
- [ ] 契约变更（路径/字段/错误码）已在 PR 描述里单列，并通知前端

---

## 13. 安全红线

| # | 红线 |
|---|---|
| 1 | 不硬编码密钥、不把 `.env` 提交进 Git |
| 2 | 日志不打 token / 签名 / 文件正文 / 完整手机号 |
| 3 | 私有资源对无权用户返回 **404**，不返回 403（403 泄露存在性） |
| 4 | 上传文件按扩展名 + 大小 + 内容双重校验，存储路径不得由用户输入拼接 |
| 5 | 分页 / 批量接口必须有上限，防止被当成 DOS 放大器 |
| 6 | actuator 端点默认不暴露细节 |
| 7 | 跨服务调用必须带 `traceparent`，且不信任用户可伪造的身份头 |

---

## 14. 落地清单（按顺序执行）

| 步骤 | 动作 | 产出 | 影响面 |
|---|---|---|---|
| 1 | 加 `.editorconfig` + `.gitattributes` | 2 个文件 | 无 |
| 2 | `pom.xml` 加 spotless + enforcer | 构建变严 | CI 会先红 |
| 3 | `mvn spotless:apply` 全量格式化，**单独 commit** | 1 个 style commit | 全仓 diff（仅空白） |
| 4 | `.git-blame-ignore-revs` 登记该 commit | 1 个文件 | `git blame` 可读 |
| 5 | CI 加 `spotless:check`（`verify` 阶段已含） | CI 守护 | 后续 PR 必须格式化 |
| 6 | 加 ArchUnit 分层测试 | 1 个测试类 | 拦住架构腐化 |
| 7 | 新代码按本规范写；旧代码随手改 | — | 渐进收敛 |

> 第 3 步是唯一会影响所有人的动作，**建议单独排期**，并在群里提前打招呼：
> "明天会有一个只改空白的 commit，`git blame` 请用 `--ignore-revs-file`"。

---

## 15. 附：本规范的"不要"

| 不要 | 因为 |
|---|---|
| 不要用 `Map<String, Object>` 当响应体 | 无类型、无文档、改字段无人知 |
| 不要在 controller 里定义 DTO | 同一个对象会散落多处 |
| 不要用 `@Data` 标实体 | 可变 + `equals` 含全部字段 |
| 不要用裸字符串表示状态/权限 | 拼错即静默失效 |
| 不要在事务里做 I/O | 拖垮连接池 |
| 不要循环单条 insert | N 次往返 |
| 不要裸 `orElseThrow()` | 500 冒充 404 |
| 不要 `printStackTrace` / `System.out` | 日志体系失序 |
| 不要写"重构前如何"的注释 | 历史在 git 里，不在代码里 |
| 不要为了覆盖率写同义反复测试 | 假安全感比没测试更糟 |
