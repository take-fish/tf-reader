package com.fishmatrix.reader.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
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

    private static final Set<String> AUDIT_FIELDS = Set.of("createdAt", "updatedAt", "addedAt");

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
                assertThat(hasAnnotatedId(type))
                        .as("%s 是复合主键，不应有 @TableId", type.getSimpleName())
                        .isFalse();
            } else {
                assertThat(hasAnnotatedId(type))
                        .as("%s 缺少 @TableId", type.getSimpleName())
                        .isTrue();
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

    @Test
    @DisplayName("@TableId 落在 id 字段且为自增")
    void tableIdOnIdFieldWithAutoType() throws NoSuchFieldException {
        for (Class<?> type : EXPECTED.keySet()) {
            if (type == ReaderProgress.class || type == ReaderShelfBook.class) {
                continue;
            }
            Field idField = type.getDeclaredField("id");
            TableId tableId = idField.getAnnotation(TableId.class);
            assertThat(tableId)
                    .as("%s 的 @TableId 应落在 id 字段", type.getSimpleName())
                    .isNotNull();
            assertThat(tableId.type()).as("%s 的主键应为 AUTO", type.getSimpleName()).isEqualTo(IdType.AUTO);
        }
    }

    @Test
    @DisplayName("审计字段类型精确为 Instant 且标注了 fill")
    void auditFieldsUseInstantWithFill() {
        int total = 0;
        for (Class<?> type : EXPECTED.keySet()) {
            for (Field field : type.getDeclaredFields()) {
                if (!AUDIT_FIELDS.contains(field.getName())) {
                    continue;
                }
                total++;
                assertThat(field.getType())
                        .as("%s.%s 必须是 Instant", type.getSimpleName(), field.getName())
                        .isEqualTo(Instant.class);
                TableField tableField = field.getAnnotation(TableField.class);
                assertThat(tableField)
                        .as("%s.%s 缺少 @TableField", type.getSimpleName(), field.getName())
                        .isNotNull();
                assertThat(tableField.fill())
                        .as("%s.%s 的 fill 不能是 DEFAULT", type.getSimpleName(), field.getName())
                        .isNotEqualTo(FieldFill.DEFAULT);
            }
        }
        assertThat(total).as("审计字段总数应为 11").isEqualTo(11);
    }

    @Test
    @DisplayName("entity 包内 @TableName 类恰好 9 个")
    void exactlyNineTableEntities() {
        assertThat(EXPECTED).hasSize(9);
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
