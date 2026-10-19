package com.fishmatrix.reader.repository;

import com.fishmatrix.reader.model.Bookmark;
import com.fishmatrix.reader.model.ReadingProgress;
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
public class ReadingRepository {

    private final JdbcTemplate jdbc;
    private final SimpleJdbcInsert bookmarkInsert;

    public ReadingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.bookmarkInsert = new SimpleJdbcInsert(jdbc)
                .withTableName("reader_bookmark")
                .usingGeneratedKeyColumns("id");
    }

    public Optional<ReadingProgress> findProgress(String userId, long bookId) {
        List<ReadingProgress> rows = jdbc.query(
                "SELECT chapter_index, scroll_ratio, updated_at FROM reader_progress "
                        + "WHERE user_id = ? AND book_id = ?",
                (rs, rowNum) -> new ReadingProgress(
                        rs.getInt("chapter_index"),
                        rs.getDouble("scroll_ratio"),
                        rs.getTimestamp("updated_at").toInstant()),
                userId,
                bookId);
        return rows.stream().findFirst();
    }

    public void upsertProgress(
            String userId,
            long bookId,
            int chapterIndex,
            double scrollRatio,
            Instant now) {
        Timestamp timestamp = Timestamp.from(now);
        jdbc.update(
                "INSERT INTO reader_progress "
                        + "(user_id, book_id, chapter_index, scroll_ratio, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE chapter_index = ?, scroll_ratio = ?, updated_at = ?",
                userId,
                bookId,
                chapterIndex,
                scrollRatio,
                timestamp,
                chapterIndex,
                scrollRatio,
                timestamp);
    }

    public List<Bookmark> findBookmarks(String userId, long bookId) {
        return jdbc.query(
                "SELECT id, chapter_index, paragraph_index, excerpt, note, created_at "
                        + "FROM reader_bookmark WHERE user_id = ? AND book_id = ? "
                        + "ORDER BY chapter_index, paragraph_index, id",
                (rs, rowNum) -> new Bookmark(
                        rs.getLong("id"),
                        rs.getInt("chapter_index"),
                        rs.getInt("paragraph_index"),
                        rs.getString("excerpt"),
                        rs.getString("note"),
                        rs.getTimestamp("created_at").toInstant()),
                userId,
                bookId);
    }

    public Optional<Bookmark> findBookmark(
            String userId,
            long bookId,
            int chapterIndex,
            int paragraphIndex) {
        List<Bookmark> rows = jdbc.query(
                "SELECT id, chapter_index, paragraph_index, excerpt, note, created_at "
                        + "FROM reader_bookmark "
                        + "WHERE user_id = ? AND book_id = ? AND chapter_index = ? AND paragraph_index = ?",
                (rs, rowNum) -> new Bookmark(
                        rs.getLong("id"),
                        rs.getInt("chapter_index"),
                        rs.getInt("paragraph_index"),
                        rs.getString("excerpt"),
                        rs.getString("note"),
                        rs.getTimestamp("created_at").toInstant()),
                userId,
                bookId,
                chapterIndex,
                paragraphIndex);
        return rows.stream().findFirst();
    }

    public Optional<Bookmark> findBookmarkById(long bookmarkId, String userId) {
        List<Bookmark> rows = jdbc.query(
                "SELECT id, chapter_index, paragraph_index, excerpt, note, created_at "
                        + "FROM reader_bookmark WHERE id = ? AND user_id = ?",
                (rs, rowNum) -> new Bookmark(
                        rs.getLong("id"),
                        rs.getInt("chapter_index"),
                        rs.getInt("paragraph_index"),
                        rs.getString("excerpt"),
                        rs.getString("note"),
                        rs.getTimestamp("created_at").toInstant()),
                bookmarkId,
                userId);
        return rows.stream().findFirst();
    }

    public Bookmark insertBookmark(
            String userId,
            long bookId,
            int chapterIndex,
            int paragraphIndex,
            String excerpt,
            String note,
            Instant now) {
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("book_id", bookId);
        params.put("chapter_index", chapterIndex);
        params.put("paragraph_index", paragraphIndex);
        params.put("excerpt", excerpt);
        params.put("note", note);
        params.put("created_at", Timestamp.from(now));
        Number key = bookmarkInsert.executeAndReturnKey(params);
        return new Bookmark(
                key.longValue(),
                chapterIndex,
                paragraphIndex,
                excerpt,
                note,
                now);
    }

    public boolean deleteBookmark(long bookmarkId, String userId) {
        return jdbc.update(
                "DELETE FROM reader_bookmark WHERE id = ? AND user_id = ?",
                bookmarkId,
                userId) > 0;
    }
}
