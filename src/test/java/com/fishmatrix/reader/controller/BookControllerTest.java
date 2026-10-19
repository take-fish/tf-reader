package com.fishmatrix.reader.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fishmatrix.reader.model.BookDetail;
import com.fishmatrix.reader.model.BookSummary;
import com.fishmatrix.reader.model.ChapterContent;
import com.fishmatrix.reader.model.ChapterSummary;
import com.fishmatrix.reader.model.NotFoundException;
import com.fishmatrix.reader.service.LibraryService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BookControllerTest {

    private LibraryService libraryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        libraryService = mock(LibraryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new BookController(libraryService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void listsBooks() throws Exception {
        when(libraryService.listBooks()).thenReturn(List.of(new BookSummary(
                1L, "演示书", "作者", "描述", 3, Instant.parse("2026-10-18T09:00:00Z"))));

        mockMvc.perform(get("/api/v1/reader/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books[0].title").value("演示书"))
                .andExpect(jsonPath("$.books[0].chapterCount").value(3));
    }

    @Test
    void returnsBookDetail() throws Exception {
        when(libraryService.getBook(1L)).thenReturn(new BookDetail(
                1L,
                "演示书",
                "作者",
                "描述",
                1,
                List.of(new ChapterSummary(0, "第一章", 100))));

        mockMvc.perform(get("/api/v1/reader/books/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chapters[0].title").value("第一章"));
    }

    @Test
    void returnsChapter() throws Exception {
        when(libraryService.getChapter(1L, 0))
                .thenReturn(new ChapterContent(0, "第一章", "正文", 1));

        mockMvc.perform(get("/api/v1/reader/books/1/chapters/0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("正文"));
    }

    @Test
    void importsTxt() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "novel.txt", "text/plain", "第一章 开始\n正文".getBytes());
        when(libraryService.importBook(any(), any(), anyString(), any())).thenReturn(
                new BookSummary(9L, "novel", "", "导入", 1, Instant.now()));

        mockMvc.perform(multipart("/api/v1/reader/books/import")
                        .file(file)
                        .param("author", "作者"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(9));
    }

    @Test
    void mapsNotFound() throws Exception {
        when(libraryService.getBook(99L)).thenThrow(new NotFoundException("书籍不存在"));

        mockMvc.perform(get("/api/v1/reader/books/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void mapsBadRequest() throws Exception {
        when(libraryService.getChapter(eq(1L), eq(5)))
                .thenThrow(new IllegalArgumentException("章节不存在"));

        mockMvc.perform(get("/api/v1/reader/books/1/chapters/5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("章节不存在"));
    }
}
