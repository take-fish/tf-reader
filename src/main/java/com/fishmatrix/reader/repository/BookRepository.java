package com.fishmatrix.reader.repository;

import com.fishmatrix.reader.model.ChapterContent;
import com.fishmatrix.reader.model.ChapterSummary;
import com.fishmatrix.reader.util.Texts;
import java.sql.ResultSet;
import java.sql.SQLException;
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

    private static final String SELECT_BOOK = """
            SELECT b.id, b.title, b.author, b.description, b.chapter_count, b.scope,
                   b.owner_id, b.owner_nickname, b.category_id, c.name AS category_name,
                   b.status, b.share_token, b.storage_key, b.source_filename, b.file_size,
                   b.sha256, b.created_at, b.updated_at
              FROM reader_book b
              LEFT JOIN reader_category c ON c.id = b.category_id
            """;

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

    public List<BookRecord> findPublic(Long categoryId, String keyword, long offset, int limit) {
        StringBuilder sql = new StringBuilder(SELECT_BOOK)
                .append(" WHERE b.scope = 'PUBLIC' AND b.status = 'VISIBLE'");
        List<Object> args = new java.util.ArrayList<>();
        if (categoryId != null) {
            sql.append(" AND b.category_id = ?");
            args.add(categoryId);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (LOWER(b.title) LIKE ? OR LOWER(b.author) LIKE ?)");
            String like = "%" + keyword.strip().toLowerCase() + "%";
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY b.updated_at DESC, b.id DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), this::mapBook, args.toArray());
    }

    public long countPublic(Long categoryId, String keyword) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM reader_book b WHERE b.scope = 'PUBLIC' AND b.status = 'VISIBLE'");
        List<Object> args = new java.util.ArrayList<>();
        if (categoryId != null) {
            sql.append(" AND b.category_id = ?");
            args.add(categoryId);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (LOWER(b.title) LIKE ? OR LOWER(b.author) LIKE ?)");
            String like = "%" + keyword.strip().toLowerCase() + "%";
            args.add(like);
            args.add(like);
        }
        Long count = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    public List<BookRecord> findOwned(long ownerId) {
        return jdbc.query(SELECT_BOOK + " WHERE b.scope = 'PRIVATE' AND b.owner_id = ?"
                + " ORDER BY b.updated_at DESC, b.id DESC", this::mapBook, ownerId);
    }

    /** 已批准访问的私有书籍；不含本人拥有的书。 */
    public List<BookRecord> findShared(long viewerId) {
        return jdbc.query(SELECT_BOOK
                + " JOIN reader_access_request r ON r.book_id = b.id"
                + " WHERE b.scope = 'PRIVATE' AND r.requester_id = ?"
                + " AND r.status = 'APPROVED' AND b.owner_id <> ?"
                + " ORDER BY b.updated_at DESC, b.id DESC",
                this::mapBook, viewerId, viewerId);
    }

    public Optional<BookRecord> findById(long bookId) {
        return jdbc.query(SELECT_BOOK + " WHERE b.id = ?", this::mapBook, bookId)
                .stream().findFirst();
    }

    public Optional<BookRecord> findByShareToken(String shareToken) {
        if (shareToken == null || shareToken.isBlank()) return Optional.empty();
        return jdbc.query(SELECT_BOOK + " WHERE b.share_token = ?", this::mapBook, shareToken)
                .stream().findFirst();
    }

    public boolean hasApprovedAccess(long bookId, long viewerId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM reader_access_request"
                        + " WHERE book_id = ? AND requester_id = ? AND status = 'APPROVED'",
                Long.class, bookId, viewerId);
        return count != null && count > 0;
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

    public long insertBook(String title, String author, String description, int chapterCount,
            String scope, Long ownerId, String ownerNickname, Long categoryId,
            String sourceFilename, String storageKey, long fileSize, String sha256,
            Instant now) {
        Map<String, Object> params = new HashMap<>();
        params.put("title", title);
        params.put("author", author);
        params.put("description", description);
        params.put("chapter_count", chapterCount);
        params.put("scope", scope);
        params.put("owner_id", ownerId);
        params.put("owner_nickname", ownerNickname);
        params.put("category_id", categoryId);
        params.put("status", "VISIBLE");
        params.put("source_filename", sourceFilename);
        params.put("storage_key", storageKey);
        params.put("file_size", fileSize);
        params.put("sha256", sha256);
        params.put("created_at", Timestamp.from(now));
        params.put("updated_at", Timestamp.from(now));
        Number key = bookInsert.executeAndReturnKey(params);
        return key.longValue();
    }

    public void insertChapter(long bookId, int chapterIndex, String title, String content,
            int wordCount) {
        Map<String, Object> params = new HashMap<>();
        params.put("book_id", bookId);
        params.put("chapter_index", chapterIndex);
        params.put("title", title);
        params.put("content", content);
        params.put("word_count", wordCount);
        chapterInsert.execute(params);
    }

    public void updateMetadata(long bookId, String title, String author, String description,
            Long categoryId, String status, Instant now) {
        jdbc.update("UPDATE reader_book SET title = ?, author = ?, description = ?,"
                        + " category_id = ?, status = ?, updated_at = ? WHERE id = ?",
                title, author, description, categoryId, status, Timestamp.from(now), bookId);
    }

    public void updateShareToken(long bookId, String shareToken, Instant now) {
        jdbc.update("UPDATE reader_book SET share_token = ?, updated_at = ? WHERE id = ?",
                shareToken, Timestamp.from(now), bookId);
    }

    public void deleteBook(long bookId) {
        jdbc.update("DELETE FROM reader_shelf_book WHERE book_id = ?", bookId);
        jdbc.update("DELETE FROM reader_access_request WHERE book_id = ?", bookId);
        jdbc.update("DELETE FROM reader_annotation WHERE book_id = ?", bookId);
        jdbc.update("DELETE FROM reader_bookmark WHERE book_id = ?", bookId);
        jdbc.update("DELETE FROM reader_progress WHERE book_id = ?", bookId);
        jdbc.update("DELETE FROM reader_chapter WHERE book_id = ?", bookId);
        jdbc.update("DELETE FROM reader_book WHERE id = ?", bookId);
    }

    private BookRecord mapBook(ResultSet rs, int rowNum) throws SQLException {
        return new BookRecord(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("author"),
                rs.getString("description"),
                rs.getInt("chapter_count"),
                rs.getString("scope"),
                rs.getObject("owner_id", Long.class),
                rs.getString("owner_nickname"),
                rs.getObject("category_id", Long.class),
                rs.getString("category_name"),
                rs.getString("status"),
                rs.getString("share_token"),
                rs.getString("storage_key"),
                rs.getString("source_filename"),
                rs.getLong("file_size"),
                rs.getString("sha256"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    public record BookRecord(
            long id,
            String title,
            String author,
            String description,
            int chapterCount,
            String scope,
            Long ownerId,
            String ownerNickname,
            Long categoryId,
            String categoryName,
            String status,
            String shareToken,
            String storageKey,
            String sourceFilename,
            long fileSize,
            String sha256,
            Instant createdAt,
            Instant updatedAt) {

        public boolean isPublic() {
            return "PUBLIC".equals(scope);
        }

        public boolean isPrivate() {
            return "PRIVATE".equals(scope);
        }

        public boolean isVisible() {
            return "VISIBLE".equals(status);
        }

        public boolean isOwnedBy(long userId) {
            return ownerId != null && ownerId == userId;
        }
    }
}
