package com.fishmatrix.reader.repository;

import com.fishmatrix.reader.model.Shelf;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class ShelfRepository {

    private final JdbcTemplate jdbc;
    private final SimpleJdbcInsert insert;

    public ShelfRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.insert = new SimpleJdbcInsert(jdbc)
                .withTableName("reader_shelf")
                .usingGeneratedKeyColumns("id");
    }

    public List<Shelf> findByOwner(long ownerId) {
        return jdbc.query("SELECT s.id, s.name, s.description, s.updated_at,"
                        + " (SELECT COUNT(*) FROM reader_shelf_book i WHERE i.shelf_id = s.id) AS book_count"
                        + " FROM reader_shelf s WHERE s.owner_id = ? ORDER BY s.updated_at DESC, s.id DESC",
                (rs, rowNum) -> new Shelf(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getInt("book_count"),
                        rs.getTimestamp("updated_at").toInstant()),
                ownerId);
    }

    public Optional<Shelf> findById(long shelfId) {
        return jdbc.query("SELECT s.id, s.name, s.description, s.updated_at,"
                        + " (SELECT COUNT(*) FROM reader_shelf_book i WHERE i.shelf_id = s.id) AS book_count"
                        + " FROM reader_shelf s WHERE s.id = ?",
                (rs, rowNum) -> new Shelf(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getInt("book_count"),
                        rs.getTimestamp("updated_at").toInstant()),
                shelfId).stream().findFirst();
    }

    public boolean ownedBy(long shelfId, long ownerId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM reader_shelf WHERE id = ? AND owner_id = ?",
                Long.class, shelfId, ownerId);
        return count != null && count > 0;
    }

    public List<Long> findBookIds(long shelfId) {
        return jdbc.queryForList(
                "SELECT book_id FROM reader_shelf_book WHERE shelf_id = ? ORDER BY added_at DESC, book_id",
                Long.class, shelfId);
    }

    public long insert(long ownerId, String name, String description, Instant now) {
        return insert.executeAndReturnKey(java.util.Map.of(
                "owner_id", ownerId,
                "name", name,
                "description", description,
                "created_at", Timestamp.from(now),
                "updated_at", Timestamp.from(now))).longValue();
    }

    public void update(long shelfId, String name, String description, Instant now) {
        jdbc.update("UPDATE reader_shelf SET name = ?, description = ?, updated_at = ? WHERE id = ?",
                name, description, Timestamp.from(now), shelfId);
    }

    public boolean addBook(long shelfId, long bookId, Instant now) {
        try {
            return jdbc.update("INSERT INTO reader_shelf_book (shelf_id, book_id, added_at)"
                    + " VALUES (?, ?, ?)", shelfId, bookId, Timestamp.from(now)) > 0;
        } catch (DuplicateKeyException exception) {
            // 重复加入视为成功，保持幂等。
            return false;
        }
    }

    public boolean removeBook(long shelfId, long bookId) {
        return jdbc.update(
                "DELETE FROM reader_shelf_book WHERE shelf_id = ? AND book_id = ?",
                shelfId, bookId) > 0;
    }

    public void delete(long shelfId) {
        jdbc.update("DELETE FROM reader_shelf_book WHERE shelf_id = ?", shelfId);
        jdbc.update("DELETE FROM reader_shelf WHERE id = ?", shelfId);
    }
}
