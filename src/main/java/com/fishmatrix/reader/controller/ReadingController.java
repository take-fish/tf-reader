package com.fishmatrix.reader.controller;

import com.fishmatrix.reader.model.Bookmark;
import com.fishmatrix.reader.model.BookmarkRequest;
import com.fishmatrix.reader.model.ProgressRequest;
import com.fishmatrix.reader.model.ReadingProgress;
import com.fishmatrix.reader.service.ReadingService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public ProgressResponse progress(
            @PathVariable long bookId,
            @RequestParam String userId) {
        return new ProgressResponse(
                readingService.getProgress(userId, bookId).orElse(null));
    }

    @PutMapping("/books/{bookId}/progress")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void saveProgress(
            @PathVariable long bookId,
            @RequestBody ProgressRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        readingService.saveProgress(
                request.userId(),
                bookId,
                request.chapterIndex(),
                request.scrollRatio());
    }

    @GetMapping("/books/{bookId}/bookmarks")
    public Map<String, Object> bookmarks(
            @PathVariable long bookId,
            @RequestParam String userId) {
        List<Bookmark> bookmarks = readingService.listBookmarks(userId, bookId);
        return Map.of("bookmarks", bookmarks);
    }

    @PostMapping("/books/{bookId}/bookmarks")
    @ResponseStatus(HttpStatus.CREATED)
    public Bookmark addBookmark(
            @PathVariable long bookId,
            @RequestBody BookmarkRequest request) {
        return readingService.addBookmark(bookId, request);
    }

    @DeleteMapping("/bookmarks/{bookmarkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBookmark(
            @PathVariable long bookmarkId,
            @RequestParam String userId) {
        readingService.deleteBookmark(bookmarkId, userId);
    }

    public record ProgressResponse(ReadingProgress progress) {
    }
}
