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
