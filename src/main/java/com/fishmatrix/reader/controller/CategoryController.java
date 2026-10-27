package com.fishmatrix.reader.controller;

import com.fishmatrix.common.auth.RequirePermission;
import com.fishmatrix.reader.model.Category;
import com.fishmatrix.reader.model.CategoryList;
import com.fishmatrix.reader.model.CategoryRequest;
import com.fishmatrix.reader.service.CategoryService;
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
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/categories")
    public CategoryList list() {
        return new CategoryList(categoryService.list());
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission("reader.category.manage")
    public Category create(@Valid @RequestBody CategoryRequest request) {
        return categoryService.create(request);
    }

    @PutMapping("/categories/{categoryId}")
    @RequirePermission("reader.category.manage")
    public Category update(@PathVariable long categoryId,
            @Valid @RequestBody CategoryRequest request) {
        return categoryService.update(categoryId, request);
    }

    @DeleteMapping("/categories/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission("reader.category.manage")
    public void delete(@PathVariable long categoryId) {
        categoryService.delete(categoryId);
    }
}
