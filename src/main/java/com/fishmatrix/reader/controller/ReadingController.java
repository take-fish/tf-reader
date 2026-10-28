package com.fishmatrix.reader.controller;

import com.fishmatrix.common.auth.UserContext;
import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.reader.model.Bookmark;
import com.fishmatrix.reader.model.BookmarkList;
import com.fishmatrix.reader.model.BookmarkRequest;
import com.fishmatrix.reader.model.ProgressRequest;
import com.fishmatrix.reader.model.ProgressResponse;
import com.fishmatrix.reader.service.ReadingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reader")
public class ReadingController {

    private final ReadingService readingService;

    public ReadingController(ReadingService readingService) {
        this.readingService = readingService;
    }

    @GetMapping("/books/{bookId}/progress")
    public ProgressResponse progress(@PathVariable long bookId) {
        return new ProgressResponse(
                readingService.getProgress(UserContext.require(), bookId).orElse(null));
    }

    @PutMapping("/books/{bookId}/progress")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void saveProgress(@PathVariable long bookId, @Valid @RequestBody ProgressRequest request) {
        if (request == null) {
            throw ApiException.badRequest("请求体不能为空");
        }
        readingService.saveProgress(UserContext.require(), bookId, request.chapterIndex(), request.scrollRatio());
    }

    @GetMapping("/books/{bookId}/bookmarks")
    public BookmarkList bookmarks(@PathVariable long bookId) {
        return new BookmarkList(readingService.listBookmarks(UserContext.require(), bookId));
    }

    @PostMapping("/books/{bookId}/bookmarks")
    @ResponseStatus(HttpStatus.CREATED)
    public Bookmark addBookmark(@PathVariable long bookId, @Valid @RequestBody BookmarkRequest request) {
        return readingService.addBookmark(bookId, request, UserContext.require());
    }

    @DeleteMapping("/bookmarks/{bookmarkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBookmark(@PathVariable long bookmarkId) {
        readingService.deleteBookmark(bookmarkId, UserContext.require());
    }
}
