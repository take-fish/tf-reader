package com.fishmatrix.reader.controller;

import com.fishmatrix.reader.model.BookDetail;
import com.fishmatrix.reader.model.BookSummary;
import com.fishmatrix.reader.model.ChapterContent;
import com.fishmatrix.reader.service.LibraryService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/reader")
public class BookController {

    private final LibraryService libraryService;

    public BookController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    @GetMapping("/books")
    public Map<String, Object> books() {
        List<BookSummary> books = libraryService.listBooks();
        return Map.of("books", books);
    }

    @GetMapping("/books/{bookId}")
    public BookDetail book(@PathVariable long bookId) {
        return libraryService.getBook(bookId);
    }

    @GetMapping("/books/{bookId}/chapters/{chapterIndex}")
    public ChapterContent chapter(
            @PathVariable long bookId,
            @PathVariable int chapterIndex) {
        return libraryService.getChapter(bookId, chapterIndex);
    }

    @PostMapping(value = "/books/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public BookSummary importBook(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String author) {
        return libraryService.importBook(title, author, file.getOriginalFilename(), file);
    }
}
