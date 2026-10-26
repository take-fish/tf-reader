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
