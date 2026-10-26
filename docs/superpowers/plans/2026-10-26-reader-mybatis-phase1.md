# tf-reader 数据访问层框架（Phase 1）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `tf-reader` 中搭好 MyBatis-Plus + Lombok + MapStruct 的数据访问框架骨架，与现有 `JdbcTemplate` 代码并存，应用行为零变化。

**Architecture:** 新增 `entity` / `mapper` / `converter` 三层，与现有 `repository` 层并存；本阶段只创建新类和一个完整垂直切片（`ReaderBook` 的公开书库查询），不改任何现有 Service、Controller、Model。

**Tech Stack:** JDK 17、Spring Boot 3.5.16、MyBatis-Plus 3.5.18、Lombok（Boot 父 pom 管理版本）、MapStruct 1.6.3、H2（测试）。

**Spec:** `docs/superpowers/specs/2026-10-26-reader-mybatis-refactor-design.md`

## Global Constraints

- JDK 17；`JAVA_HOME` 必须在构建前设为 `D:\develop\jdk17`（全局默认指向 jdk11）
- Spring Boot 3.5.16（父 pom 已是）
- MyBatis-Plus starter：`com.baomidou:mybatis-plus-spring-boot3-starter:3.5.18`
- `mybatis-plus-jsqlparser:3.5.18` 是分页必需配套件（MyBatis-Plus 3.5.9+ 将 `PaginationInnerInterceptor` 从 `mybatis-plus-extension` 拆出）
- MapStruct：`1.6.3`；`lombok-mapstruct-binding`：`0.2.0`
- **不得修改** `com.fishmatrix.reader.model` 包下任何文件的字段
- **不得修改** 任何 `controller` / `service` / `repository` / `parser` / `util` / `migration` 下已有文件
- 本阶段**不新增 Flyway 迁移**，**不执行 V3**
- 实体命名统一 `Reader` 前缀（`ReaderBook`、`ReaderChapter` …），与表名一一对应
- 所有实体使用 Lombok `@Data`，主键 `@TableId(type = IdType.AUTO)`
- 表 `reader_progress`、`reader_shelf_book` 是**复合主键**，不设 `@TableId`，写操作走 XML

---

### Task 1: 引入依赖与注解处理器

**Files:**
- Modify: `pom.xml`
- Test: `src/test/java/com/fishmatrix/reader/DependencyPresenceTest.java`

**Interfaces:**
- Consumes: 无
- Produces: 后续所有 Task 可用的 `com.baomidou.mybatisplus.*`、`lombok.*`、`org.mapstruct.*` 类

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/com/fishmatrix/reader/DependencyPresenceTest.java`：

```java
package com.fishmatrix.reader;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DependencyPresenceTest {

    @Test
    @DisplayName("MyBatis-Plus / Lombok / MapStruct 依赖已就位")
    void requiredLibrariesOnClasspath() {
        assertThat(loadable("com.baomidou.mybatisplus.core.mapper.BaseMapper")).isTrue();
        assertThat(loadable("com.baomidou.mybatisplus.annotation.TableName")).isTrue();
        assertThat(loadable("com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor")).isTrue();
        assertThat(loadable("lombok.Data")).isTrue();
        assertThat(loadable("org.mapstruct.Mapper")).isTrue();
    }

    private static boolean loadable(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```powershell
$env:JAVA_HOME='D:\develop\jdk17'; $env:PATH="D:\develop\jdk17\bin;"+$env:PATH
cd D:\project\mygithub\take_fish\tf-reader
mvn -B -Dtest=DependencyPresenceTest test
```

预期：编译失败或断言失败（依赖尚不存在）。

- [ ] **Step 3: 修改 pom.xml**

在 `<properties>` 中追加：

```xml
    <mybatis-plus.version>3.5.18</mybatis-plus.version>
    <mapstruct.version>1.6.3</mapstruct.version>
    <lombok-mapstruct-binding.version>0.2.0</lombok-mapstruct-binding.version>
```

在 `<dependencies>` 中（`spring-boot-starter-test` 之前）追加：

```xml
    <dependency>
      <groupId>com.baomidou</groupId>
      <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
      <version>${mybatis-plus.version}</version>
    </dependency>
    <dependency>
      <groupId>com.baomidou</groupId>
      <artifactId>mybatis-plus-jsqlparser</artifactId>
      <version>${mybatis-plus.version}</version>
    </dependency>
    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <optional>true</optional>
    </dependency>
    <dependency>
      <groupId>org.mapstruct</groupId>
      <artifactId>mapstruct</artifactId>
      <version>${mapstruct.version}</version>
    </dependency>
```

在 `<build><plugins>` 中，`spring-boot-maven-plugin` **之前**追加：

```xml
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration>
          <annotationProcessorPaths>
            <path>
              <groupId>org.projectlombok</groupId>
              <artifactId>lombok</artifactId>
              <version>${lombok.version}</version>
            </path>
            <path>
              <groupId>org.mapstruct</groupId>
              <artifactId>mapstruct-processor</artifactId>
              <version>${mapstruct.version}</version>
            </path>
            <path>
              <groupId>org.projectlombok</groupId>
              <artifactId>lombok-mapstruct-binding</artifactId>
              <version>${lombok-mapstruct-binding.version}</version>
            </path>
          </annotationProcessorPaths>
        </configuration>
      </plugin>
```

- [ ] **Step 4: 运行测试，确认通过**

```powershell
mvn -B -Dtest=DependencyPresenceTest test
```

预期：`Tests run: 1, Failures: 0`。

- [ ] **Step 5: 跑全量测试确认无回归**

```powershell
mvn -B test
```

预期：`Tests run: 24`（原 23 + 新增 1），全绿。

- [ ] **Step 6: 提交**

```powershell
git add pom.xml src/test/java/com/fishmatrix/reader/DependencyPresenceTest.java
git commit -m "build: 引入 MyBatis-Plus、Lombok 与 MapStruct 依赖"
```

---

### Task 2: MyBatis-Plus 配置（分页 + 审计字段自动填充）

**Files:**
- Create: `src/main/java/com/fishmatrix/reader/config/MybatisPlusConfig.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/fishmatrix/reader/config/MybatisPlusConfigTest.java`

**Interfaces:**
- Consumes: Task 1 的 MP 依赖
- Produces:
  - Bean `MybatisPlusInterceptor`（分页拦截器）
  - Bean `MetaObjectHandler`（自动填充 `createdAt` / `updatedAt`）

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/com/fishmatrix/reader/config/MybatisPlusConfigTest.java`：

```java
package com.fishmatrix.reader.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MybatisPlusConfigTest {

    @Autowired
    private MybatisPlusInterceptor interceptor;

    @Autowired
    private MetaObjectHandler metaObjectHandler;

    @Test
    @DisplayName("分页拦截器与自动填充处理器已注册")
    void beansRegistered() {
        assertThat(interceptor).isNotNull();
        assertThat(interceptor.getInterceptors()).isNotEmpty();
        assertThat(metaObjectHandler).isNotNull();
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```powershell
mvn -B -Dtest=MybatisPlusConfigTest test
```

预期：`NoSuchBeanDefinitionException`（`MybatisPlusInterceptor` 不存在）。

- [ ] **Step 3: 创建配置类**

创建 `src/main/java/com/fishmatrix/reader/config/MybatisPlusConfig.java`：

```java
package com.fishmatrix.reader.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import java.time.Instant;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /** 统一维护 created_at / updated_at，业务代码不再手写 Instant.now()。 */
    @Bean
    public MetaObjectHandler auditMetaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                Instant now = Instant.now();
                strictInsertFill(metaObject, "createdAt", Instant.class, now);
                strictInsertFill(metaObject, "updatedAt", Instant.class, now);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                strictUpdateFill(metaObject, "updatedAt", Instant.class, Instant.now());
            }
        };
    }
}
```

- [ ] **Step 4: 修改 application.yml**

在 `app:` 节点**之前**追加顶层节点：

```yaml
mybatis-plus:
  mapper-locations: classpath:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    banner: false
    db-config:
      id-type: auto
```

- [ ] **Step 5: 运行测试，确认通过**

```powershell
mvn -B -Dtest=MybatisPlusConfigTest test
```

预期：`Tests run: 1, Failures: 0`。

- [ ] **Step 6: 提交**

```powershell
git add src/main/java/com/fishmatrix/reader/config/MybatisPlusConfig.java src/main/resources/application.yml src/test/java/com/fishmatrix/reader/config/MybatisPlusConfigTest.java
git commit -m "feat: 配置 MyBatis-Plus 分页插件与审计字段自动填充"
```

---

### Task 3: 九个实体类

**Files:**
- Create: `src/main/java/com/fishmatrix/reader/entity/ReaderBook.java`
- Create: `src/main/java/com/fishmatrix/reader/entity/ReaderChapter.java`
- Create: `src/main/java/com/fishmatrix/reader/entity/ReaderProgress.java`
- Create: `src/main/java/com/fishmatrix/reader/entity/ReaderBookmark.java`
- Create: `src/main/java/com/fishmatrix/reader/entity/ReaderAnnotation.java`
- Create: `src/main/java/com/fishmatrix/reader/entity/ReaderCategory.java`
- Create: `src/main/java/com/fishmatrix/reader/entity/ReaderShelf.java`
- Create: `src/main/java/com/fishmatrix/reader/entity/ReaderShelfBook.java`
- Create: `src/main/java/com/fishmatrix/reader/entity/ReaderAccessRequest.java`
- Test: `src/test/java/com/fishmatrix/reader/entity/EntityMappingTest.java`

**Interfaces:**
- Consumes: Task 2 的 MP 注解支持
- Produces: 九个类，包名 `com.fishmatrix.reader.entity`

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/com/fishmatrix/reader/entity/EntityMappingTest.java`：

```java
package com.fishmatrix.reader.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.lang.reflect.Field;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EntityMappingTest {

    private static final Map<Class<?>, String> EXPECTED = Map.of(
            ReaderBook.class, "reader_book",
            ReaderChapter.class, "reader_chapter",
            ReaderProgress.class, "reader_progress",
            ReaderBookmark.class, "reader_bookmark",
            ReaderAnnotation.class, "reader_annotation",
            ReaderCategory.class, "reader_category",
            ReaderShelf.class, "reader_shelf",
            ReaderShelfBook.class, "reader_shelf_book",
            ReaderAccessRequest.class, "reader_access_request");

    @Test
    @DisplayName("实体与表名一一对应，且除复合主键外都有自增主键")
    void tablesAndKeys() {
        EXPECTED.forEach((type, expectedTable) -> {
            TableName tableName = type.getAnnotation(TableName.class);
            assertThat(tableName).as("%s 缺少 @TableName", type.getSimpleName()).isNotNull();
            assertThat(tableName.value()).isEqualTo(expectedTable);
        });

        for (Class<?> type : EXPECTED.keySet()) {
            if (type == ReaderProgress.class || type == ReaderShelfBook.class) {
                assertThat(hasAnnotatedId(type)).as("%s 是复合主键，不应有 @TableId", type.getSimpleName()).isFalse();
            } else {
                assertThat(hasAnnotatedId(type)).as("%s 缺少 @TableId", type.getSimpleName()).isTrue();
            }
        }
    }

    @Test
    @DisplayName("ReaderBook.categoryName 是非表字段")
    void categoryNameIsNotAColumn() throws NoSuchFieldException {
        Field field = ReaderBook.class.getDeclaredField("categoryName");
        TableField annotation = field.getAnnotation(TableField.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.exist()).isFalse();
    }

    private static boolean hasAnnotatedId(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            if (field.isAnnotationPresent(TableId.class)) {
                return true;
            }
        }
        return false;
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```powershell
mvn -B -Dtest=EntityMappingTest test
```

预期：编译失败（实体类不存在）。

- [ ] **Step 3: 创建九个实体**

`src/main/java/com/fishmatrix/reader/entity/ReaderBook.java`：

```java
package com.fishmatrix.reader.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

@Data
@TableName("reader_book")
public class ReaderBook {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String title;
    private String author;
    private String description;
    private Integer chapterCount;
    private String scope;
    private Long ownerId;
    private String ownerNickname;
    private Long categoryId;
    private String status;
    private String shareToken;
    private String storageKey;
    private String sourceFilename;
    private Long fileSize;
    private String sha256;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    /** 非表字段：JOIN reader_category 得到，由 XML 填充。 */
    @TableField(exist = false)
    private String categoryName;
}
```

`src/main/java/com/fishmatrix/reader/entity/ReaderChapter.java`：

```java
package com.fishmatrix.reader.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("reader_chapter")
public class ReaderChapter {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long bookId;
    private Integer chapterIndex;
    private String title;
    private String content;
    private Integer wordCount;
}
```

`src/main/java/com/fishmatrix/reader/entity/ReaderProgress.java`：

```java
package com.fishmatrix.reader.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/** 复合主键 (user_id, book_id)：不设 @TableId，写操作走 XML。 */
@Data
@TableName("reader_progress")
public class ReaderProgress {

    private Long userId;
    private Long bookId;
    private Integer chapterIndex;
    private Double scrollRatio;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
```

`src/main/java/com/fishmatrix/reader/entity/ReaderBookmark.java`：

```java
package com.fishmatrix.reader.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

@Data
@TableName("reader_bookmark")
public class ReaderBookmark {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long bookId;
    private Integer chapterIndex;
    private Integer paragraphIndex;
    private String excerpt;
    private String note;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;
}
```

`src/main/java/com/fishmatrix/reader/entity/ReaderAnnotation.java`：

```java
package com.fishmatrix.reader.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

@Data
@TableName("reader_annotation")
public class ReaderAnnotation {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long bookId;
    private Integer chapterIndex;
    private Integer paragraphIndex;
    private Long userId;
    private String nickname;
    private String content;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;
}
```

`src/main/java/com/fishmatrix/reader/entity/ReaderCategory.java`：

```java
package com.fishmatrix.reader.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

@Data
@TableName("reader_category")
public class ReaderCategory {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;
    private Integer sortOrder;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;
}
```

`src/main/java/com/fishmatrix/reader/entity/ReaderShelf.java`：

```java
package com.fishmatrix.reader.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

@Data
@TableName("reader_shelf")
public class ReaderShelf {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long ownerId;
    private String name;
    private String description;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
```

`src/main/java/com/fishmatrix/reader/entity/ReaderShelfBook.java`：

```java
package com.fishmatrix.reader.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/** 复合主键 (shelf_id, book_id)：不设 @TableId，写操作走 XML。 */
@Data
@TableName("reader_shelf_book")
public class ReaderShelfBook {

    private Long shelfId;
    private Long bookId;

    @TableField(value = "added_at", fill = FieldFill.INSERT)
    private Instant addedAt;
}
```

`src/main/java/com/fishmatrix/reader/entity/ReaderAccessRequest.java`：

```java
package com.fishmatrix.reader.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

@Data
@TableName("reader_access_request")
public class ReaderAccessRequest {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long bookId;
    private Long ownerId;
    private Long requesterId;
    private String requesterNickname;
    private String status;
    private String message;
    private String decisionMessage;
    private Instant decidedAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
```

- [ ] **Step 4: 运行测试，确认通过**

```powershell
mvn -B -Dtest=EntityMappingTest test
```

预期：`Tests run: 2, Failures: 0`。

- [ ] **Step 5: 提交**

```powershell
git add src/main/java/com/fishmatrix/reader/entity src/test/java/com/fishmatrix/reader/entity/EntityMappingTest.java
git commit -m "feat: 新增九个 MyBatis-Plus 实体"
```

---

### Task 4: Mapper 接口与公开书库查询 XML

**Files:**
- Create: `src/main/java/com/fishmatrix/reader/mapper/ReaderBookMapper.java` … 共 9 个
- Create: `src/main/resources/mapper/ReaderBookMapper.xml`
- Test: `src/test/java/com/fishmatrix/reader/mapper/ReaderBookMapperTest.java`

**Interfaces:**
- Consumes: Task 3 的实体
- Produces:
  - 九个 `BaseMapper<T>` 接口
  - `ReaderBookMapper.selectPublicPage(Page<ReaderBook> page, Long categoryId, String keyword)` 返回 `IPage<ReaderBook>`，其中 `categoryName` 已填充

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/com/fishmatrix/reader/mapper/ReaderBookMapperTest.java`：

```java
package com.fishmatrix.reader.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fishmatrix.reader.entity.ReaderBook;
import com.fishmatrix.reader.entity.ReaderCategory;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ReaderBookMapperTest {

    @Autowired
    private ReaderBookMapper bookMapper;

    @Autowired
    private ReaderCategoryMapper categoryMapper;

    private Long categoryId;

    @BeforeEach
    void seed() {
        ReaderCategory category = new ReaderCategory();
        category.setName("古文");
        category.setSortOrder(1);
        categoryMapper.insert(category);
        categoryId = category.getId();

        insertBook("古文观止", "吴楚材", "PUBLIC", "VISIBLE", categoryId);
        insertBook("私有笔记", "某人", "PRIVATE", "VISIBLE", null);
    }

    private void insertBook(String title, String author, String scope, String status, Long category) {
        ReaderBook book = new ReaderBook();
        book.setTitle(title);
        book.setAuthor(author);
        book.setDescription("");
        book.setChapterCount(1);
        book.setScope(scope);
        book.setStatus(status);
        book.setOwnerNickname("");
        book.setCategoryId(category);
        book.setSourceFilename("");
        book.setFileSize(0L);
        bookMapper.insert(book);
    }

    @Test
    @DisplayName("公开书库分页查询带出分类名，且不返回私有书")
    void selectPublicPageWithCategoryName() {
        Page<ReaderBook> page = new Page<>(1, 10);
        var result = bookMapper.selectPublicPage(page, null, null);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getTotal()).isEqualTo(1);
        ReaderBook row = result.getRecords().get(0);
        assertThat(row.getTitle()).isEqualTo("古文观止");
        assertThat(row.getCategoryName()).isEqualTo("古文");
    }

    @Test
    @DisplayName("关键字按标题过滤")
    void keywordFiltersByTitle() {
        var result = bookMapper.selectPublicPage(new Page<>(1, 10), null, "观止");
        assertThat(result.getRecords()).extracting(ReaderBook::getTitle).containsExactly("古文观止");

        var none = bookMapper.selectPublicPage(new Page<>(1, 10), null, "不存在的书");
        assertThat(none.getRecords()).isEmpty();
    }

    @Test
    @DisplayName("自动填充写入 created_at / updated_at")
    void auditFieldsAutoFilled() {
        ReaderBook book = new ReaderBook();
        book.setTitle("自动填充验证");
        book.setAuthor("");
        book.setDescription("");
        book.setChapterCount(0);
        book.setScope("PUBLIC");
        book.setStatus("VISIBLE");
        book.setOwnerNickname("");
        book.setSourceFilename("");
        book.setFileSize(0L);
        bookMapper.insert(book);

        ReaderBook loaded = bookMapper.selectById(book.getId());
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```powershell
mvn -B -Dtest=ReaderBookMapperTest test
```

预期：编译失败（Mapper 不存在）。

- [ ] **Step 3: 创建九个 Mapper 接口**

九个文件除类名与实体外完全相同，模板如下（以 `ReaderBookMapper` 为例）：

`src/main/java/com/fishmatrix/reader/mapper/ReaderBookMapper.java`：

```java
package com.fishmatrix.reader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fishmatrix.reader.entity.ReaderBook;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReaderBookMapper extends BaseMapper<ReaderBook> {

    /** 公开书库：JOIN 分类名 + 可选关键字 + 分页。 */
    IPage<ReaderBook> selectPublicPage(
            Page<ReaderBook> page,
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword);
}
```

其余八个（内容仅为 `@Mapper` + `extends BaseMapper<实体>`）：

```java
// src/main/java/com/fishmatrix/reader/mapper/ReaderChapterMapper.java
package com.fishmatrix.reader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fishmatrix.reader.entity.ReaderChapter;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReaderChapterMapper extends BaseMapper<ReaderChapter> {
}
```

```java
// src/main/java/com/fishmatrix/reader/mapper/ReaderProgressMapper.java
package com.fishmatrix.reader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fishmatrix.reader.entity.ReaderProgress;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReaderProgressMapper extends BaseMapper<ReaderProgress> {
}
```

```java
// src/main/java/com/fishmatrix/reader/mapper/ReaderBookmarkMapper.java
package com.fishmatrix.reader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fishmatrix.reader.entity.ReaderBookmark;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReaderBookmarkMapper extends BaseMapper<ReaderBookmark> {
}
```

```java
// src/main/java/com/fishmatrix/reader/mapper/ReaderAnnotationMapper.java
package com.fishmatrix.reader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fishmatrix.reader.entity.ReaderAnnotation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReaderAnnotationMapper extends BaseMapper<ReaderAnnotation> {
}
```

```java
// src/main/java/com/fishmatrix/reader/mapper/ReaderCategoryMapper.java
package com.fishmatrix.reader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fishmatrix.reader.entity.ReaderCategory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReaderCategoryMapper extends BaseMapper<ReaderCategory> {
}
```

```java
// src/main/java/com/fishmatrix/reader/mapper/ReaderShelfMapper.java
package com.fishmatrix.reader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fishmatrix.reader.entity.ReaderShelf;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReaderShelfMapper extends BaseMapper<ReaderShelf> {
}
```

```java
// src/main/java/com/fishmatrix/reader/mapper/ReaderShelfBookMapper.java
package com.fishmatrix.reader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fishmatrix.reader.entity.ReaderShelfBook;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReaderShelfBookMapper extends BaseMapper<ReaderShelfBook> {
}
```

```java
// src/main/java/com/fishmatrix/reader/mapper/ReaderAccessRequestMapper.java
package com.fishmatrix.reader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fishmatrix.reader.entity.ReaderAccessRequest;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReaderAccessRequestMapper extends BaseMapper<ReaderAccessRequest> {
}
```

> 九个 Mapper 统一 `@Mapper` + `extends BaseMapper<T>`，不写多余 import。

- [ ] **Step 4: 创建 XML**

创建 `src/main/resources/mapper/ReaderBookMapper.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.fishmatrix.reader.mapper.ReaderBookMapper">

  <select id="selectPublicPage" resultType="com.fishmatrix.reader.entity.ReaderBook">
    SELECT b.*, c.name AS category_name
    FROM reader_book b
    LEFT JOIN reader_category c ON c.id = b.category_id
    WHERE b.scope = 'PUBLIC'
      AND b.status = 'VISIBLE'
    <if test="categoryId != null">
      AND b.category_id = #{categoryId}
    </if>
    <if test="keyword != null and keyword != ''">
      AND (LOWER(b.title) LIKE CONCAT('%', LOWER(#{keyword}), '%')
        OR LOWER(b.author) LIKE CONCAT('%', LOWER(#{keyword}), '%'))
    </if>
    ORDER BY b.updated_at DESC, b.id DESC
  </select>

</mapper>
```

- [ ] **Step 5: 运行测试，确认通过**

```powershell
mvn -B -Dtest=ReaderBookMapperTest test
```

预期：`Tests run: 3, Failures: 0`。

若分页 SQL 在 H2 下报错，检查 `PaginationInnerInterceptor` 的 `DbType` 是否为 `MYSQL`（H2 以 `MODE=MySQL` 运行，兼容 `LIMIT ?,?`）。

- [ ] **Step 6: 提交**

```powershell
git add src/main/java/com/fishmatrix/reader/mapper src/main/resources/mapper src/test/java/com/fishmatrix/reader/mapper/ReaderBookMapperTest.java
git commit -m "feat: 新增九个 Mapper 与公开书库查询 XML"
```

---

### Task 5: MapStruct 转换器

**Files:**
- Create: `src/main/java/com/fishmatrix/reader/converter/BookConverter.java`
- Test: `src/test/java/com/fishmatrix/reader/converter/BookConverterTest.java`

**Interfaces:**
- Consumes: `ReaderBook`（Task 3）、`BookSummary`（既有 model）
- Produces: `BookSummary toSummary(ReaderBook book, boolean mine, boolean canEdit, boolean canDelete)`

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/com/fishmatrix/reader/converter/BookConverterTest.java`：

```java
package com.fishmatrix.reader.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fishmatrix.reader.entity.ReaderBook;
import com.fishmatrix.reader.model.BookSummary;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class BookConverterTest {

    private final BookConverter converter = Mappers.getMapper(BookConverter.class);

    @Test
    @DisplayName("实体转 BookSummary，字段与标志位正确")
    void mapsAllFields() {
        ReaderBook book = new ReaderBook();
        book.setId(7L);
        book.setTitle("古文观止");
        book.setAuthor("吴楚材");
        book.setDescription("desc");
        book.setChapterCount(3);
        book.setScope("PRIVATE");
        book.setOwnerId(42L);
        book.setOwnerNickname("小鱼");
        book.setCategoryId(5L);
        book.setCategoryName("古文");
        book.setStatus("VISIBLE");
        book.setCreatedAt(Instant.parse("2026-10-01T00:00:00Z"));
        book.setUpdatedAt(Instant.parse("2026-10-02T00:00:00Z"));

        BookSummary summary = converter.toSummary(book, true, true, false);

        assertThat(summary.id()).isEqualTo(7L);
        assertThat(summary.title()).isEqualTo("古文观止");
        assertThat(summary.author()).isEqualTo("吴楚材");
        assertThat(summary.chapterCount()).isEqualTo(3);
        assertThat(summary.scope()).isEqualTo("PRIVATE");
        assertThat(summary.ownerId()).isEqualTo(42L);
        assertThat(summary.ownerNickname()).isEqualTo("小鱼");
        assertThat(summary.categoryName()).isEqualTo("古文");
        assertThat(summary.status()).isEqualTo("VISIBLE");
        assertThat(summary.mine()).isTrue();
        assertThat(summary.canEdit()).isTrue();
        assertThat(summary.canDelete()).isFalse();
        assertThat(summary.createdAt()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
        assertThat(summary.updatedAt()).isEqualTo(Instant.parse("2026-10-02T00:00:00Z"));
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```powershell
mvn -B -Dtest=BookConverterTest test
```

预期：编译失败（`BookConverter` 不存在）。

- [ ] **Step 3: 创建转换器**

`src/main/java/com/fishmatrix/reader/converter/BookConverter.java`：

```java
package com.fishmatrix.reader.converter;

import com.fishmatrix.reader.entity.ReaderBook;
import com.fishmatrix.reader.model.BookSummary;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BookConverter {

    /**
     * mine / canEdit / canDelete 由服务层按当前用户判定后传入，
     * 与重构前 LibraryService.summary(...) 的计算口径保持一致。
     */
    @Mapping(target = "mine", source = "mine")
    @Mapping(target = "canEdit", source = "canEdit")
    @Mapping(target = "canDelete", source = "canDelete")
    BookSummary toSummary(ReaderBook book, boolean mine, boolean canEdit, boolean canDelete);
}
```

- [ ] **Step 4: 运行测试，确认通过**

```powershell
mvn -B -Dtest=BookConverterTest test
```

预期：`Tests run: 1, Failures: 0`。

- [ ] **Step 5: 提交**

```powershell
git add src/main/java/com/fishmatrix/reader/converter src/test/java/com/fishmatrix/reader/converter/BookConverterTest.java
git commit -m "feat: 新增基于 MapStruct 的书籍转换器"
```

---

### Task 6: 回归验证与收尾

**Files:**
- Modify: `AGENTS.md`（追加两条硬规则）

**Interfaces:**
- Consumes: Task 1–5 全部产出
- Produces: 可交付的 Phase 1 状态

- [ ] **Step 1: 全量测试**

```powershell
$env:JAVA_HOME='D:\develop\jdk17'; $env:PATH="D:\develop\jdk17\bin;"+$env:PATH
cd D:\project\mygithub\take_fish\tf-reader
mvn -B test
```

预期：`Tests run: 31`（原 23 + 新增 8），全绿，无跳过。

- [ ] **Step 2: 打包验证**

```powershell
mvn -B -DskipTests package
```

预期：`BUILD SUCCESS`，产出 `target/tf-reader.jar`。

- [ ] **Step 3: 确认现有业务代码零改动**

```powershell
git diff --stat origin/master -- src/main/java/com/fishmatrix/reader/controller src/main/java/com/fishmatrix/reader/service src/main/java/com/fishmatrix/reader/repository src/main/java/com/fishmatrix/reader/model
```

预期：**无输出**（这四层一行未改）。

- [ ] **Step 4: 确认现有接口行为不变**

```powershell
mvn -B -Dtest=BookControllerTest,ReaderLibraryIntegrationTest test
```

预期：全绿。这两个测试覆盖了书库列表、权限 404、分享与访问申请链路，是"契约未变"的守卫。

> 设计文档 §9 的「逐字节响应 diff」留到 Phase 2 执行。本阶段按构造不改变任何对外行为（controller/service/model 零改动），故以现有测试作为守卫即可。

- [ ] **Step 5: 更新 AGENTS.md**

在 `AGENTS.md` 的 §8 技术债表格**之前**新增一节：

```markdown
### 数据访问层（Phase 1 完成后）

- `entity/` `mapper/` `converter/` 为 MyBatis-Plus 新体系；`repository/` 为遗留 JdbcTemplate，Phase 2 删除。
- 硬规则：**修改 Flyway 迁移必须同步修改 `src/test/resources/schema-h2.sql`**（两份 schema 并存期）。
- 硬规则：`reader_progress`、`reader_shelf_book` 是复合主键，写操作走 XML，不要调用 `updateById`。
```

- [ ] **Step 6: 提交**

```powershell
git add AGENTS.md
git commit -m "docs: 记录数据访问层新体系约定"
```

---

## 完成定义（Definition of Done）

- [ ] `mvn -B test` 全绿，31 个测试
- [ ] `mvn -B -DskipTests package` 成功
- [ ] `controller` / `service` / `repository` / `model` 四层零改动
- [ ] 存在可用的垂直切片：`ReaderBookMapper.selectPublicPage` 带出 `categoryName`，分页与自动填充生效
- [ ] 九个实体、九个 Mapper 接口、一个 XML、一个 Converter 就位
- [ ] `AGENTS.md` 已记录新体系约定

## 后续（Phase 2，不在本计划内）

逐个 Service 切换到 Mapper → 执行 `V3__unify_user_identity.sql` → 删除 `repository` 包 → 契约 diff 验证。
