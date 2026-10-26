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
