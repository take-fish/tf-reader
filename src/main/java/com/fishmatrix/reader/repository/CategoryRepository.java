package com.fishmatrix.reader.repository;

import com.fishmatrix.reader.model.Category;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class CategoryRepository {

    private final JdbcTemplate jdbc;
    private final SimpleJdbcInsert insert;

    public CategoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.insert = new SimpleJdbcInsert(jdbc)
                .withTableName("reader_category")
                .usingGeneratedKeyColumns("id");
    }

    public List<Category> findAll() {
        return jdbc.query("SELECT id, name, sort_order FROM reader_category"
                        + " ORDER BY sort_order, id",
                (rs, rowNum) -> new Category(
                        rs.getLong("id"), rs.getString("name"), rs.getInt("sort_order")));
    }

    public Optional<Category> findById(long id) {
        return jdbc.query("SELECT id, name, sort_order FROM reader_category WHERE id = ?",
                (rs, rowNum) -> new Category(
                        rs.getLong("id"), rs.getString("name"), rs.getInt("sort_order")),
                id).stream().findFirst();
    }

    public long insert(String name, int sortOrder, Instant now) {
        return insert.executeAndReturnKey(java.util.Map.of(
                "name", name,
                "sort_order", sortOrder,
                "created_at", Timestamp.from(now))).longValue();
    }

    public void update(long id, String name, int sortOrder) {
        jdbc.update("UPDATE reader_category SET name = ?, sort_order = ? WHERE id = ?",
                name, sortOrder, id);
    }

    public void delete(long id) {
        jdbc.update("UPDATE reader_book SET category_id = NULL WHERE category_id = ?", id);
        jdbc.update("DELETE FROM reader_category WHERE id = ?", id);
    }
}
