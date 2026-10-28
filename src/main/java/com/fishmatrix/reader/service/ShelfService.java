package com.fishmatrix.reader.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fishmatrix.common.auth.CurrentUser;
import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.reader.entity.ReaderShelf;
import com.fishmatrix.reader.entity.ReaderShelfBook;
import com.fishmatrix.reader.error.NotFoundException;
import com.fishmatrix.reader.mapper.ReaderShelfBookMapper;
import com.fishmatrix.reader.mapper.ReaderShelfMapper;
import com.fishmatrix.reader.model.BookSummary;
import com.fishmatrix.reader.model.Shelf;
import com.fishmatrix.reader.model.ShelfDetail;
import com.fishmatrix.reader.model.ShelfRequest;
import com.fishmatrix.reader.policy.BookAccessPolicy;
import com.fishmatrix.reader.repository.BookRepository;
import com.fishmatrix.reader.repository.BookRepository.BookRecord;
import com.fishmatrix.reader.util.Validation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShelfService {

    private final ReaderShelfMapper shelfMapper;
    private final ReaderShelfBookMapper shelfBookMapper;
    private final BookRepository bookRepository;
    private final LibraryService libraryService;
    private final BookAccessPolicy accessPolicy;

    public ShelfService(
            ReaderShelfMapper shelfMapper,
            ReaderShelfBookMapper shelfBookMapper,
            BookRepository bookRepository,
            LibraryService libraryService,
            BookAccessPolicy accessPolicy) {
        this.shelfMapper = shelfMapper;
        this.shelfBookMapper = shelfBookMapper;
        this.bookRepository = bookRepository;
        this.libraryService = libraryService;
        this.accessPolicy = accessPolicy;
    }

    public List<Shelf> list(CurrentUser viewer) {
        List<ReaderShelf> shelves = shelfMapper.selectList(Wrappers.<ReaderShelf>lambdaQuery()
                .eq(ReaderShelf::getOwnerId, viewer.id())
                .orderByDesc(ReaderShelf::getUpdatedAt)
                .orderByDesc(ReaderShelf::getId));
        if (shelves.isEmpty()) {
            return List.of();
        }
        // 一次查出这些书单下的所有关联行再本地计数，避免每个书单查一次
        Map<Long, Integer> counts = bookCounts(
                shelves.stream().map(ReaderShelf::getId).toList());
        return shelves.stream()
                .map(entity -> toView(entity, counts.getOrDefault(entity.getId(), 0)))
                .toList();
    }

    public ShelfDetail get(long shelfId, CurrentUser viewer) {
        ReaderShelf entity = requireOwned(shelfId, viewer);
        List<Long> bookIds = shelfBookMapper
                .selectList(Wrappers.<ReaderShelfBook>lambdaQuery()
                        .eq(ReaderShelfBook::getShelfId, shelfId)
                        .orderByDesc(ReaderShelfBook::getAddedAt)
                        .orderByAsc(ReaderShelfBook::getBookId))
                .stream().map(ReaderShelfBook::getBookId).toList();
        Shelf shelf = toView(entity, bookIds.size());
        if (bookIds.isEmpty()) {
            return new ShelfDetail(shelf, List.of());
        }
        // 固定 4 次查询：书单、书籍 id 列表、书籍本体、已获授权的书籍集合。
        Map<Long, BookRecord> byId = bookRepository.findByIdIn(bookIds).stream()
                .collect(Collectors.toMap(BookRecord::id, book -> book));
        Set<Long> approvedBookIds = bookRepository.findApprovedBookIds(viewer.id(), bookIds);
        List<BookSummary> books = bookIds.stream()
                // 保持书架里的加入顺序（最近加入在前），并跳过已被删除的书。
                .map(byId::get)
                .filter(Objects::nonNull)
                .filter(book -> accessPolicy.canRead(book, viewer, approvedBookIds))
                .map(book -> libraryService.summary(book, viewer))
                .toList();
        return new ShelfDetail(shelf, books);
    }

    @Transactional
    public Shelf create(ShelfRequest request, CurrentUser viewer) {
        if (request == null) {
            throw ApiException.badRequest("请求体不能为空");
        }
        ReaderShelf entity = new ReaderShelf();
        entity.setOwnerId(viewer.id());
        entity.setName(Validation.text(request.name(), 64, "书单名称", true));
        entity.setDescription(Validation.truncate(request.description(), 255));
        shelfMapper.insert(entity);
        return toView(entity, 0);
    }

    @Transactional
    public Shelf update(long shelfId, ShelfRequest request, CurrentUser viewer) {
        ReaderShelf entity = requireOwned(shelfId, viewer);
        if (request == null) {
            throw ApiException.badRequest("请求体不能为空");
        }
        if (request.name() != null) {
            entity.setName(Validation.text(request.name(), 64, "书单名称", true));
        }
        if (request.description() != null) {
            entity.setDescription(Validation.truncate(request.description(), 255));
        }
        shelfMapper.updateById(entity);
        return toView(entity, currentBookCount(shelfId));
    }

    @Transactional
    public void delete(long shelfId, CurrentUser viewer) {
        requireOwned(shelfId, viewer);
        shelfBookMapper.delete(Wrappers.<ReaderShelfBook>lambdaQuery()
                .eq(ReaderShelfBook::getShelfId, shelfId));
        shelfMapper.deleteById(shelfId);
    }

    @Transactional
    public void addBook(long shelfId, long bookId, CurrentUser viewer) {
        requireOwned(shelfId, viewer);
        libraryService.requireReadable(bookId, viewer);
        ReaderShelfBook link = new ReaderShelfBook();
        link.setShelfId(shelfId);
        link.setBookId(bookId);
        try {
            shelfBookMapper.insert(link);
        } catch (DuplicateKeyException exception) {
            // 重复加入视为成功，保持幂等
        }
    }

    @Transactional
    public void removeBook(long shelfId, long bookId, CurrentUser viewer) {
        requireOwned(shelfId, viewer);
        shelfBookMapper.delete(Wrappers.<ReaderShelfBook>lambdaQuery()
                .eq(ReaderShelfBook::getShelfId, shelfId)
                .eq(ReaderShelfBook::getBookId, bookId));
    }

    private ReaderShelf requireOwned(long shelfId, CurrentUser viewer) {
        ReaderShelf entity = shelfMapper.selectById(shelfId);
        if (entity == null || !Objects.equals(entity.getOwnerId(), viewer.id())) {
            throw new NotFoundException("书单不存在");
        }
        return entity;
    }

    private int currentBookCount(long shelfId) {
        return Math.toIntExact(shelfBookMapper.selectCount(
                Wrappers.<ReaderShelfBook>lambdaQuery().eq(ReaderShelfBook::getShelfId, shelfId)));
    }

    private Map<Long, Integer> bookCounts(List<Long> shelfIds) {
        List<ReaderShelfBook> links = shelfBookMapper.selectList(
                Wrappers.<ReaderShelfBook>lambdaQuery().in(ReaderShelfBook::getShelfId, shelfIds));
        Map<Long, Integer> counts = new HashMap<>();
        for (ReaderShelfBook link : links) {
            counts.merge(link.getShelfId(), 1, Integer::sum);
        }
        return counts;
    }

    private static Shelf toView(ReaderShelf entity, int bookCount) {
        return new Shelf(entity.getId(), entity.getName(), entity.getDescription(),
                bookCount, entity.getUpdatedAt());
    }
}
