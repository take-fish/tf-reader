package com.fishmatrix.reader.controller;

import com.fishmatrix.common.auth.CurrentUser;
import com.fishmatrix.common.auth.UserContext;
import com.fishmatrix.common.autoconfigure.FishCommonProperties;
import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.common.error.GlobalExceptionHandler;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fishmatrix.reader.error.NotFoundException;
import com.fishmatrix.reader.model.BookDetail;
import com.fishmatrix.reader.model.BookPage;
import com.fishmatrix.reader.model.BookSummary;
import com.fishmatrix.reader.model.ChapterContent;
import com.fishmatrix.reader.model.ChapterSummary;
import com.fishmatrix.reader.service.LibraryService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BookControllerTest {

    private static final CurrentUser VIEWER = new CurrentUser(
            7L, "reader", "读书人", "USER", List.of("USER"),
            java.util.Set.of("reader.public.upload"));

    private LibraryService libraryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        libraryService = mock(LibraryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new BookController(libraryService))
                .setControllerAdvice(new GlobalExceptionHandler(new FishCommonProperties()))
                .build();
        UserContext.set(VIEWER);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private static BookSummary summary(long id, String title) {
        return new BookSummary(id, title, "作者", "描述", 3, "PUBLIC", null, "", null, null,
                "VISIBLE", false, false, false,
                Instant.parse("2026-10-18T09:00:00Z"), Instant.parse("2026-10-18T09:00:00Z"));
    }

    @Test
    void listsPublicBooks() throws Exception {
        when(libraryService.listPublic(eq(null), eq(null), eq(0), eq(20), any()))
                .thenReturn(new BookPage(List.of(summary(1L, "演示书")), 1, 0, 20));

        mockMvc.perform(get("/api/v1/reader/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books[0].title").value("演示书"))
                .andExpect(jsonPath("$.books[0].chapterCount").value(3))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void listsPrivateBooks() throws Exception {
        when(libraryService.listMine(any())).thenReturn(List.of(summary(2L, "私有书")));

        mockMvc.perform(get("/api/v1/reader/books").param("scope", "private"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books[0].title").value("私有书"));
    }

    @Test
    void returnsBookDetail() throws Exception {
        when(libraryService.getBook(eq(1L), any())).thenReturn(new BookDetail(
                summary(1L, "演示书"), List.of(new ChapterSummary(0, "第一章", 100))));

        mockMvc.perform(get("/api/v1/reader/books/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.book.title").value("演示书"))
                .andExpect(jsonPath("$.chapters[0].title").value("第一章"));
    }

    @Test
    void returnsChapter() throws Exception {
        when(libraryService.getChapter(eq(1L), eq(0), any()))
                .thenReturn(new ChapterContent(0, "第一章", "正文", 1));

        mockMvc.perform(get("/api/v1/reader/books/1/chapters/0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("正文"));
    }

    @Test
    void importsTxt() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "novel.txt", "text/plain", "第一章 开始\n正文".getBytes());
        when(libraryService.importBook(any(), any(), any(), any(), any(), any()))
                .thenReturn(summary(9L, "novel"));

        mockMvc.perform(multipart("/api/v1/reader/books/import")
                        .file(file)
                        .param("author", "作者"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(9));
    }

    @Test
    void mapsNotFound() throws Exception {
        when(libraryService.getBook(eq(99L), any())).thenThrow(new NotFoundException("书籍不存在"));

        mockMvc.perform(get("/api/v1/reader/books/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void mapsBadRequest() throws Exception {
        when(libraryService.getChapter(eq(1L), eq(5), any()))
                .thenThrow(ApiException.badRequest("章节不存在"));

        mockMvc.perform(get("/api/v1/reader/books/1/chapters/5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("章节不存在"));
    }

    @Test
    void rejectsAnonymousRequestAtController() throws Exception {
        UserContext.clear();

        mockMvc.perform(get("/api/v1/reader/books"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listsPublicBooksWithCategory() throws Exception {
        when(libraryService.listPublic(anyLong(), eq("诗"), eq(1), eq(10), any()))
                .thenReturn(new BookPage(List.of(), 0, 1, 10));

        mockMvc.perform(get("/api/v1/reader/books")
                        .param("categoryId", "3")
                        .param("q", "诗")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1));
    }
}
