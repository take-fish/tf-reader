package com.fishmatrix.reader.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fishmatrix.reader.entity.ReaderBook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BookConverterSpringTest {

    @Autowired
    private BookConverter converter;

    @Test
    @DisplayName("转换器可从 Spring 容器注入并正常工作")
    void injectableAndWorks() {
        ReaderBook book = new ReaderBook();
        book.setId(1L);
        book.setTitle("测试");
        book.setChapterCount(2);

        var summary = converter.toSummary(book, false, true, false);

        assertThat(summary.title()).isEqualTo("测试");
        assertThat(summary.canEdit()).isTrue();
    }
}
