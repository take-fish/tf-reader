# tf-reader 数据访问层重构 · 设计

- 日期：2026-10-26
- 状态：已实施（Phase 1 完成）
- 分支：`feature/reader-refactor`（基点待确认）
- 关联：`AGENTS.md` §8 技术债 P0/P1

---

## 1. 背景

`tf-reader` 目前用 `JdbcTemplate` + 手写 SQL 字符串访问数据库（6 个 Repository，约 700 行），存在三个结构性问题：

1. **身份二象性（P0）**：`CurrentUser.id()` 是 `long`，`CurrentUser.userId()` 是 `"u"+id`。新表（`reader_book` / `reader_shelf` / `reader_access_request`）用 `BIGINT`，老表（`reader_progress` / `reader_bookmark` / `reader_annotation`）用 `VARCHAR(64)`。同一数据库内两套用户标识，跨表无法 JOIN，编译器不拦截。
2. **裸 SQL 散落（P1）**：表名、列名硬编码在字符串里，改列名需全局搜索；`deleteBook()` 手工按序删 6 张表。
3. **对象职责过载**：`BookRecord`（18 字段）同时承担数据库行映射、领域对象、API 响应三种角色。

生产数据实测：`reader_progress` 仅 6 行，其中 1 行为无法映射的 UUID 历史数据。**当前是统一的最后低成本窗口。**

## 2. 目标与范围

### 目标

- 引入 MyBatis + MyBatis-Plus 取代 `JdbcTemplate`
- 统一用户标识为 `BIGINT`
- 建立 Entity / Mapper / Converter / Model 分层，每层单一职责

### 范围

| 阶段 | 内容 | 交付 |
|---|---|---|
| **Phase 1（本轮）** | 搭框架：依赖、配置、9 个 Entity、9 个 Mapper 接口、示例 XML、Converter 骨架、一次端到端验证 | 新旧并存，随时可编译 |
| **Phase 2（后续）** | 业务迁移：逐个 Service 切换、执行 V3 迁移、删除 `repository` 包 | 旧代码消失 |

### 明确不做

- **不改 API 契约**：`/api/v1/reader/**` 的 URL、入参、响应结构全部保持原样，`tf-web` 零改动
- **不动文件与数据库的一致性**（P1 债中"文件落盘与写库无事务"一项）：属独立问题，改动会溢出契约
- **不引入逻辑删除 / 雪花 ID / 多数据源**
- **不改 `parser` / `util` / `migration` 包**

## 3. 决策记录

| # | 决策 | 理由 |
|---|---|---|
| D1 | 一次性完成 DAO 替换与身份统一 | 生产仅 6 行数据，分两步会导致同一批代码改两遍 |
| D2 | API 契约严格冻结 | 避免改动溢出到前端，风险可控 |
| D3 | MP 为主 + XML 收尾 | 单表 CRUD 用 `BaseMapper`，多表关联/聚合才写 XML |
| D4 | Entity / DTO 分离 | 改表不波及 API，解决"一个类干三件事" |
| D5 | 引入 Lombok | Entity 共约 120 个字段，避免数百行 getter/setter。版本由 Spring Boot 父 pom 管理 |
| D6 | 删除全部不可映射历史数据 | 用户确认。生产 `reader_progress` 的 UUID 行无法映射到任何用户 |
| D7 | 主键保持 `AUTO_INCREMENT` | 不引入分布式 ID，与现有数据兼容 |

## 4. 包结构

```
com.fishmatrix.reader
├── ReaderApplication
├── config/          MybatisPlusConfig（分页插件 + 自动填充）
├── entity/          9 个表映射类（Lombok @Data + MP 注解）
├── mapper/          9 个 BaseMapper 接口
├── converter/       entity ↔ model（MapStruct）
├── model/           对外契约（现有 record，不改字段）
├── service/         业务逻辑
├── controller/      薄层
├── parser/          不动
├── util/            不动
└── migration/       不动
```

`repository/` 在 Phase 2 末尾整包删除。

## 5. Entity 设计

命名统一 `Reader` 前缀 + 表名对应，避免与 `model` 中的 `BookSummary` / `BookDetail` 撞名。

```java
@Data
@TableName("reader_book")
public class ReaderBook {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String title;
    private String author;
    private String description;
    private Integer chapterCount;
    private String scope;          // PUBLIC | PRIVATE
    private Long ownerId;          // BIGINT，统一后的用户标识
    private String ownerNickname;
    private Long categoryId;
    private String status;         // VISIBLE | HIDDEN
    private String shareToken;
    private String storageKey;
    private String sourceFilename;
    private Long fileSize;
    private String sha256;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    /** 非表字段：JOIN reader_category 得到，由 XML 填充 */
    @TableField(exist = false)
    private String categoryName;
}
```

**关键点**：`categoryName` 用 `@TableField(exist = false)` 挂载，XML 中 `SELECT b.*, c.name AS category_name` 直接映射。避免为联表结果额外造类。

枚举（`scope` / `status`）在 Phase 1 保持 `String`，与数据库一致，不引入 `IEnum`，减少迁移面。

## 6. Mapper 设计

| 场景 | 实现方式 |
|---|---|
| 单表增删改查 | `BaseMapper<ReaderBook>` |
| 条件查询 | `LambdaQueryWrapper`（无魔法字符串） |
| 分页 | `Page<T>` + `PaginationInnerInterceptor` |
| 公开书库（JOIN 分类 + 关键字 + 分页） | XML |
| 我获批访问的私有书（JOIN 申请记录） | XML |
| 批量插入章节 | XML `<foreach>` |

XML 仅承载多表关联与聚合，单表逻辑一律不写。

配置（`application.yml`）：

```yaml
mybatis-plus:
  mapper-locations: classpath:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      id-type: auto
```

## 7. 身份统一迁移 `V3__unify_user_identity.sql`

```sql
-- 1. "u123" -> 123
UPDATE reader_progress   SET user_id = CAST(SUBSTRING(user_id, 2) AS UNSIGNED) WHERE user_id REGEXP '^u[0-9]+$';
UPDATE reader_bookmark   SET user_id = CAST(SUBSTRING(user_id, 2) AS UNSIGNED) WHERE user_id REGEXP '^u[0-9]+$';
UPDATE reader_annotation SET user_id = CAST(SUBSTRING(user_id, 2) AS UNSIGNED) WHERE user_id REGEXP '^u[0-9]+$';

-- 2. 清空无法映射的历史数据（用户确认全删）
DELETE FROM reader_progress   WHERE user_id NOT REGEXP '^[0-9]+$';
DELETE FROM reader_bookmark   WHERE user_id NOT REGEXP '^[0-9]+$';
DELETE FROM reader_annotation WHERE user_id NOT REGEXP '^[0-9]+$';

-- 3. 改列类型
ALTER TABLE reader_progress   MODIFY user_id BIGINT NOT NULL;
ALTER TABLE reader_bookmark   MODIFY user_id BIGINT NOT NULL;
ALTER TABLE reader_annotation MODIFY user_id BIGINT NOT NULL;
```

执行时机：**Phase 2**，在 Service 切换前完成。

代码侧：`ReadingService` / `AnnotationService` 中 `viewer.userId()` → `viewer.id()`。
`tf-common` 的 `CurrentUser.userId()` **保留并标注 `@Deprecated`**，因其他服务可能仍在引用，不在本次删除。

## 8. 横切关注点

| 项 | 做法 |
|---|---|
| 分页 | `PaginationInnerInterceptor(DbType.MYSQL)`；`BookPage` 由 `Page<T>` 组装，响应结构不变；对外 page 为 0-based、MP Page 为 1-based，Phase 2 接线时必须换算 |
| 时间字段 | `MetaObjectHandler` 自动填充 `created_at` / `updated_at` |
| 事务 | 保持在 Service 层 `@Transactional`，边界不变 |
| 级联删除 | `deleteBook` 的多表删除集中到 `ReaderBookMapper` 的一个 XML 方法 |
| 文件一致性 | 不动 |

## 9. 测试策略

- 沿用 H2 `MODE=MySQL`，MyBatis-Plus 兼容。
- `schema-h2.sql` 需同步 V3 改动。**这是已知的重复源**，本次不重构，但确认一条硬规则并写入 `AGENTS.md`：*修改 Flyway 迁移必须同步修改 `schema-h2.sql`*。
- **契约不变的硬验证**：重构前后各抓一次真实响应（`/books`、`/books/{id}`、`/books/{id}/chapters/{i}`、`/shelves`、`/access-requests/received`），逐字节 diff 必须一致。

## 10. Phase 1 验收标准

1. `mvn -B test` 全绿（现有 23 个测试不退化）
2. 9 个 Entity + 9 个 Mapper 接口 + 至少 1 个 XML 可用
3. 一次端到端验证通过：用 MP 查出公开书库列表（含 `categoryName`），分页与自动填充生效
4. **现有业务代码一行未改**，应用可正常启动、接口行为不变
5. 新旧依赖共存期无冲突（`JdbcTemplate` 与 MP 同时存在）

## 11. 风险

| 风险 | 应对 |
|---|---|
| Lombok + MapStruct 需 `lombok-mapstruct-binding`，编译顺序敏感 | 在 `maven-compiler-plugin` 显式配置 `annotationProcessorPaths`，Phase 1 即验证 |
| MP 3.5.18 与 Spring Boot 3.5 兼容性 | 已确认 `mybatis-plus-spring-boot3-starter` 最新 3.5.18（2026-10-25），Phase 1 首个提交即验证启动 |
| 新旧 DAO 并存期数据源配置冲突 | MP 复用现有 `DataSource`，不新增连接池 |
| `schema-h2.sql` 与迁移漂移 | 写入 `AGENTS.md` 硬规则；Phase 2 结束时复核一次 |
