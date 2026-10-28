package com.fishmatrix.reader.service;

import com.fishmatrix.common.auth.CurrentUser;
import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.reader.error.NotFoundException;
import com.fishmatrix.reader.model.BookSummary;
import com.fishmatrix.reader.model.Shelf;
import com.fishmatrix.reader.model.ShelfDetail;
import com.fishmatrix.reader.model.ShelfRequest;
import com.fishmatrix.reader.policy.BookAccessPolicy;
import com.fishmatrix.reader.repository.BookRepository;
import com.fishmatrix.reader.repository.BookRepository.BookRecord;
import com.fishmatrix.reader.repository.ShelfRepository;
import com.fishmatrix.reader.util.Validation;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShelfService {

    private final ShelfRepository shelfRepository;
    private final BookRepository bookRepository;
    private final LibraryService libraryService;
    private final BookAccessPolicy accessPolicy;

    public List<Shelf> list(CurrentUser viewer) {
        return shelfRepository.findByOwner(viewer.id());
    }

    public ShelfDetail get(long shelfId, CurrentUser viewer) {
        Shelf shelf = requireOwned(shelfId, viewer);
        List<Long> bookIds = shelfRepository.findBookIds(shelfId);
        if (bookIds.isEmpty()) {
            return new ShelfDetail(shelf, List.of());
        }
        // 固定 4 次查询：书单、书籍 id 列表、书籍本体、已获授权的书籍集合。
        // 书单里放多少本书都不再增加查询次数（此前是每本一次，私有书还要再查一次审批）。
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
        if (request == null) throw ApiException.badRequest("请求体不能为空");
        String name = Validation.text(request.name(), 64, "书单名称", true);
        String description = Validation.truncate(request.description(), 255);
        long id = shelfRepository.insert(viewer.id(), name, description, Instant.now());
        return shelfRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("书单不存在"));
    }

    @Transactional
    public Shelf update(long shelfId, ShelfRequest request, CurrentUser viewer) {
        Shelf shelf = requireOwned(shelfId, viewer);
        if (request == null) throw ApiException.badRequest("请求体不能为空");
        String name = request.name() == null
                ? shelf.name() : Validation.text(request.name(), 64, "书单名称", true);
        String description = request.description() == null
                ? shelf.description() : Validation.truncate(request.description(), 255);
        shelfRepository.update(shelfId, name, description, Instant.now());
        return shelfRepository.findById(shelfId)
                .orElseThrow(() -> new NotFoundException("书单不存在"));
    }

    @Transactional
    public void delete(long shelfId, CurrentUser viewer) {
        requireOwned(shelfId, viewer);
        shelfRepository.delete(shelfId);
    }

    @Transactional
    public void addBook(long shelfId, long bookId, CurrentUser viewer) {
        requireOwned(shelfId, viewer);
        libraryService.requireReadable(bookId, viewer);
        shelfRepository.addBook(shelfId, bookId, Instant.now());
    }

    @Transactional
    public void removeBook(long shelfId, long bookId, CurrentUser viewer) {
        requireOwned(shelfId, viewer);
        shelfRepository.removeBook(shelfId, bookId);
    }

    private Shelf requireOwned(long shelfId, CurrentUser viewer) {
        if (!shelfRepository.ownedBy(shelfId, viewer.id())) {
            throw new NotFoundException("书单不存在");
        }
        return shelfRepository.findById(shelfId)
                .orElseThrow(() -> new NotFoundException("书单不存在"));
    }
}
