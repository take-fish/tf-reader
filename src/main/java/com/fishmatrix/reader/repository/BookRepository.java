package com.fishmatrix.reader.repository;

import com.fishmatrix.reader.model.BookSummary;
import com.fishmatrix.reader.model.ChapterContent;
import com.fishmatrix.reader.model.ChapterSummary;
import com.fishmatrix.reader.util.Texts;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class BookRepository {

    private final JdbcTemplate jdbc;
    private final SimpleJdbcInsert bookInsert;
    private final SimpleJdbcInsert chapterInsert;

    public BookRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.bookInsert = new SimpleJdbcInsert(jdbc)
                .withTableName("reader_book")
                .usingGeneratedKeyColumns("id");
        this.chapterInsert = new SimpleJdbcInsert(jdbc)
                .withTableName("reader_chapter")
                .usingGeneratedKeyColumns("id");
    }

    public List<BookSummary> findAll() {
        return jdbc.query(
                "SELECT id, title, author, description, chapter_count, updated_at "
                        + "FROM reader_book ORDER BY updated_at DESC, id DESC",
                (rs, rowNum) -> new BookSummary(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getString("author"),
                        rs.getString("description"),
                        rs.getInt("chapter_count"),
                        rs.getTimestamp("updated_at").toInstant()));
    }

    public Optional<BookSummary> findById(long bookId) {
        List<BookSummary> rows = jdbc.query(
                "SELECT id, title, author, description, chapter_count, updated_at "
                        + "FROM reader_book WHERE id = ?",
                (rs, rowNum) -> new BookSummary(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getString("author"),
                        rs.getString("description"),
                        rs.getInt("chapter_count"),
                        rs.getTimestamp("updated_at").toInstant()),
                bookId);
        return rows.stream().findFirst();
    }

    public List<ChapterSummary> findChapters(long bookId) {
        return jdbc.query(
                "SELECT chapter_index, title, word_count FROM reader_chapter "
                        + "WHERE book_id = ? ORDER BY chapter_index",
                (rs, rowNum) -> new ChapterSummary(
                        rs.getInt("chapter_index"),
                        rs.getString("title"),
                        rs.getInt("word_count")),
                bookId);
    }

    public Optional<ChapterContent> findChapter(long bookId, int chapterIndex) {
        List<ChapterContent> rows = jdbc.query(
                "SELECT chapter_index, title, content FROM reader_chapter "
                        + "WHERE book_id = ? AND chapter_index = ?",
                (rs, rowNum) -> {
                    String content = rs.getString("content");
                    return new ChapterContent(
                            rs.getInt("chapter_index"),
                            rs.getString("title"),
                            content,
                            Texts.paragraphCount(content));
                },
                bookId,
                chapterIndex);
        return rows.stream().findFirst();
    }

    public long insertBook(
            String title,
            String author,
            String description,
            int chapterCount,
            Instant now) {
        Map<String, Object> params = new HashMap<>();
        params.put("title", title);
        params.put("author", author);
        params.put("description", description);
        params.put("chapter_count", chapterCount);
        params.put("created_at", Timestamp.from(now));
        params.put("updated_at", Timestamp.from(now));
        Number key = bookInsert.executeAndReturnKey(params);
        return key.longValue();
    }

    public void insertChapter(
            long bookId,
            int chapterIndex,
            String title,
            String content,
            int wordCount) {
        Map<String, Object> params = new HashMap<>();
        params.put("book_id", bookId);
        params.put("chapter_index", chapterIndex);
        params.put("title", title);
        params.put("content", content);
        params.put("word_count", wordCount);
        chapterInsert.execute(params);
    }
}
