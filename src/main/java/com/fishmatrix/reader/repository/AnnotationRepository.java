package com.fishmatrix.reader.repository;

import com.fishmatrix.reader.model.Annotation;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class AnnotationRepository {

    private final JdbcTemplate jdbc;
    private final SimpleJdbcInsert annotationInsert;

    public AnnotationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.annotationInsert = new SimpleJdbcInsert(jdbc)
                .withTableName("reader_annotation")
                .usingGeneratedKeyColumns("id");
    }

    public List<Annotation> findAfter(long bookId, int chapterIndex, long afterId, int limit) {
        return jdbc.query(
                "SELECT id, paragraph_index, user_id, nickname, content, created_at "
                        + "FROM reader_annotation "
                        + "WHERE book_id = ? AND chapter_index = ? AND id > ? "
                        + "ORDER BY id ASC LIMIT ?",
                (rs, rowNum) -> new Annotation(
                        rs.getLong("id"),
                        rs.getInt("paragraph_index"),
                        rs.getString("user_id"),
                        rs.getString("nickname"),
                        rs.getString("content"),
                        rs.getTimestamp("created_at").toInstant()),
                bookId,
                chapterIndex,
                afterId,
                limit);
    }

    public Annotation insert(
            long bookId,
            int chapterIndex,
            int paragraphIndex,
            long userId,
            String nickname,
            String content,
            Instant now) {
        Map<String, Object> params = new HashMap<>();
        params.put("book_id", bookId);
        params.put("chapter_index", chapterIndex);
        params.put("paragraph_index", paragraphIndex);
        params.put("user_id", userId);
        params.put("nickname", nickname);
        params.put("content", content);
        params.put("created_at", Timestamp.from(now));
        Number key = annotationInsert.executeAndReturnKey(params);
        return new Annotation(
                key.longValue(),
                paragraphIndex,
                // 对外 DTO 的 userId 仍是字符串；数据库已统一成 BIGINT，这里转成数字字符串
                String.valueOf(userId),
                nickname,
                content,
                now);
    }
}
