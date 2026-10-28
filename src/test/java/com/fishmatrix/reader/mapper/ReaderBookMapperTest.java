package com.fishmatrix.reader.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fishmatrix.reader.domain.BookScope;
import com.fishmatrix.reader.domain.BookStatus;
import com.fishmatrix.reader.entity.ReaderBook;
import com.fishmatrix.reader.entity.ReaderCategory;
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

    private Long classicsId;

    @BeforeEach
    void seed() {
        ReaderCategory classics = new ReaderCategory();
        classics.setName("古文");
        classics.setSortOrder(1);
        categoryMapper.insert(classics);
        classicsId = classics.getId();

        selectInsert("古文观止", "吴楚材", "PUBLIC", "VISIBLE", classicsId);
        selectInsert("世说新语", "刘义庆", "PUBLIC", "VISIBLE", classicsId);
        selectInsert("无名笔记", "佚名", "PUBLIC", "VISIBLE", null);
        selectInsert("私有笔记", "某人", "PRIVATE", "VISIBLE", null);
    }

    private void selectInsert(String title, String author, String scope, String status, Long category) {
        ReaderBook book = new ReaderBook();
        book.setTitle(title);
        book.setAuthor(author);
        book.setDescription("");
        book.setChapterCount(1);
        book.setScope(BookScope.valueOf(scope));
        book.setStatus(BookStatus.valueOf(status));
        book.setOwnerNickname("");
        book.setCategoryId(category);
        book.setSourceFilename("");
        book.setFileSize(0L);
        bookMapper.insert(book);
    }

    @Test
    @DisplayName("公开书库只返回公共可见书，并带出分类名")
    void selectPublicPageWithCategoryName() {
        var result = bookMapper.selectPublicPage(new Page<>(1, 10), null, null);

        assertThat(result.getTotal()).isEqualTo(3);
        assertThat(result.getRecords()).hasSize(3);
        assertThat(result.getRecords())
                .extracting(ReaderBook::getTitle)
                .containsExactlyInAnyOrder("古文观止", "世说新语", "无名笔记");
        assertThat(result.getRecords())
                .filteredOn(book -> "古文观止".equals(book.getTitle()))
                .singleElement()
                .extracting(ReaderBook::getCategoryName)
                .isEqualTo("古文");
        assertThat(result.getRecords())
                .filteredOn(book -> "无名笔记".equals(book.getTitle()))
                .singleElement()
                .extracting(ReaderBook::getCategoryName)
                .isNull();
    }

    @Test
    @DisplayName("分页参数生效：第 2 页每页 2 条只剩 1 条")
    void secondPageHoldsRemainingRow() {
        var result = bookMapper.selectPublicPage(new Page<>(2, 2), null, null);

        assertThat(result.getTotal()).isEqualTo(3);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getCurrent()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(2);
    }

    @Test
    @DisplayName("categoryId 过滤只返回该分类下的书")
    void filtersByCategory() {
        var result = bookMapper.selectPublicPage(new Page<>(1, 10), classicsId, null);

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getRecords()).extracting(ReaderBook::getTitle).containsExactlyInAnyOrder("古文观止", "世说新语");
    }

    @Test
    @DisplayName("关键字可命中作者")
    void keywordMatchesAuthor() {
        var result = bookMapper.selectPublicPage(new Page<>(1, 10), null, "刘义庆");

        assertThat(result.getRecords()).extracting(ReaderBook::getTitle).containsExactly("世说新语");
    }

    @Test
    @DisplayName("关键字可命中标题，无匹配时返回空页")
    void keywordMatchesTitleAndNoMatch() {
        assertThat(bookMapper.selectPublicPage(new Page<>(1, 10), null, "观止").getRecords())
                .extracting(ReaderBook::getTitle)
                .containsExactly("古文观止");

        var none = bookMapper.selectPublicPage(new Page<>(1, 10), null, "不存在的书");
        assertThat(none.getRecords()).isEmpty();
        assertThat(none.getTotal()).isZero();
    }

    @Test
    @DisplayName("keyword 空白与前后空格的处理与旧实现一致")
    void keywordWhitespaceSemantics() {
        var blank = bookMapper.selectPublicPage(new Page<>(1, 10), null, "   ");
        assertThat(blank.getTotal()).isEqualTo(3);

        var padded = bookMapper.selectPublicPage(new Page<>(1, 10), null, " 观止 ");
        assertThat(padded.getRecords()).extracting(ReaderBook::getTitle).containsExactly("古文观止");
    }

    @Test
    @DisplayName("自动填充写入 created_at / updated_at")
    void auditFieldsAutoFilled() {
        ReaderBook book = new ReaderBook();
        book.setTitle("自动填充验证");
        book.setAuthor("");
        book.setDescription("");
        book.setChapterCount(0);
        book.setScope(BookScope.PUBLIC);
        book.setStatus(BookStatus.VISIBLE);
        book.setOwnerNickname("");
        book.setSourceFilename("");
        book.setFileSize(0L);
        bookMapper.insert(book);

        ReaderBook loaded = bookMapper.selectById(book.getId());
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }
}
