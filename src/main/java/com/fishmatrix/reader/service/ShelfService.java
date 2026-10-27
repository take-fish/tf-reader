package com.fishmatrix.reader.service;

import com.fishmatrix.common.auth.CurrentUser;
import com.fishmatrix.reader.model.BookSummary;
import com.fishmatrix.reader.model.NotFoundException;
import com.fishmatrix.reader.model.Shelf;
import com.fishmatrix.reader.model.ShelfDetail;
import com.fishmatrix.reader.model.ShelfRequest;
import com.fishmatrix.reader.repository.BookRepository;
import com.fishmatrix.reader.repository.ShelfRepository;
import com.fishmatrix.reader.util.Validation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShelfService {

    private final ShelfRepository shelfRepository;
    private final BookRepository bookRepository;
    private final LibraryService libraryService;

    public ShelfService(ShelfRepository shelfRepository, BookRepository bookRepository,
            LibraryService libraryService) {
        this.shelfRepository = shelfRepository;
        this.bookRepository = bookRepository;
        this.libraryService = libraryService;
    }

    public List<Shelf> list(CurrentUser viewer) {
        return shelfRepository.findByOwner(viewer.id());
    }

    public ShelfDetail get(long shelfId, CurrentUser viewer) {
        Shelf shelf = requireOwned(shelfId, viewer);
        List<BookSummary> books = new ArrayList<>();
        for (Long bookId : shelfRepository.findBookIds(shelfId)) {
            bookRepository.findById(bookId)
                    .filter(book -> canRead(book, viewer))
                    .ifPresent(book -> books.add(libraryService.summary(book, viewer)));
        }
        return new ShelfDetail(shelf, books);
    }

    @Transactional
    public Shelf create(ShelfRequest request, CurrentUser viewer) {
        if (request == null) throw new IllegalArgumentException("请求体不能为空");
        String name = Validation.text(request.name(), 64, "书单名称", true);
        String description = Validation.truncate(request.description(), 255);
        long id = shelfRepository.insert(viewer.id(), name, description, Instant.now());
        return shelfRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("书单不存在"));
    }

    @Transactional
    public Shelf update(long shelfId, ShelfRequest request, CurrentUser viewer) {
        Shelf shelf = requireOwned(shelfId, viewer);
        if (request == null) throw new IllegalArgumentException("请求体不能为空");
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

    private boolean canRead(BookRepository.BookRecord book, CurrentUser viewer) {
        if (book.isPublic()) {
            return book.isVisible() || viewer.hasPermission("reader.content.manage.any");
        }
        return book.isOwnedBy(viewer.id())
                || bookRepository.hasApprovedAccess(book.id(), viewer.id());
    }
}
