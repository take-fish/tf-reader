package com.fishmatrix.reader.controller;

import com.fishmatrix.common.auth.CurrentUser;
import com.fishmatrix.common.auth.UserContext;
import com.fishmatrix.reader.model.BookDetail;
import com.fishmatrix.reader.model.BookList;
import com.fishmatrix.reader.model.BookPage;
import com.fishmatrix.reader.model.BookSummary;
import com.fishmatrix.reader.model.BookUpdateRequest;
import com.fishmatrix.reader.model.ChapterContent;
import com.fishmatrix.reader.model.ShareLink;
import com.fishmatrix.reader.service.LibraryService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/reader")
@RequiredArgsConstructor
public class BookController {

    private final LibraryService libraryService;

    @GetMapping("/books")
    public BookPage books(
            @RequestParam(defaultValue = "public") String scope,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CurrentUser viewer = UserContext.require();
        if ("private".equalsIgnoreCase(scope)) {
            List<BookSummary> books = libraryService.listMine(viewer);
            return new BookPage(books, books.size(), 0, books.size());
        }
        return libraryService.listPublic(categoryId, q, page, size, viewer);
    }

    @GetMapping("/books/mine")
    public BookList myBooks() {
        return new BookList(libraryService.listMine(UserContext.require()));
    }

    @GetMapping("/books/shared")
    public BookList sharedBooks() {
        return new BookList(libraryService.listShared(UserContext.require()));
    }

    @GetMapping("/books/shared/{shareToken}")
    public BookSummary previewShared(@PathVariable String shareToken) {
        return libraryService.previewByShareToken(shareToken, UserContext.require());
    }

    @GetMapping("/books/{bookId}")
    public BookDetail book(@PathVariable long bookId) {
        return libraryService.getBook(bookId, UserContext.require());
    }

    @GetMapping("/books/{bookId}/chapters/{chapterIndex}")
    public ChapterContent chapter(@PathVariable long bookId, @PathVariable int chapterIndex) {
        return libraryService.getChapter(bookId, chapterIndex, UserContext.require());
    }

    @PostMapping(value = "/books/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public BookSummary importBook(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) Long categoryId) {
        return libraryService.importBook(scope, title, author, categoryId, file,
                UserContext.require());
    }

    @PatchMapping("/books/{bookId}")
    public BookSummary updateBook(@PathVariable long bookId,
            @Valid @RequestBody BookUpdateRequest request) {
        return libraryService.update(bookId, request, UserContext.require());
    }

    @DeleteMapping("/books/{bookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBook(@PathVariable long bookId) {
        libraryService.delete(bookId, UserContext.require());
    }

    @PostMapping("/books/{bookId}/share")
    public ShareLink share(@PathVariable long bookId) {
        return libraryService.share(bookId, UserContext.require());
    }
}
