package com.fishmatrix.reader.service;

import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.reader.error.NotFoundException;
import com.fishmatrix.reader.model.Category;
import com.fishmatrix.reader.model.CategoryRequest;
import com.fishmatrix.reader.repository.CategoryRepository;
import com.fishmatrix.reader.util.Validation;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository repository;

    public List<Category> list() {
        return repository.findAll();
    }

    @Transactional
    public Category create(CategoryRequest request) {
        if (request == null) throw ApiException.badRequest("请求体不能为空");
        String name = Validation.text(request.name(), 64, "分类名称", true);
        int sortOrder = request.sortOrder() == null ? 0 : request.sortOrder();
        long id = repository.insert(name, sortOrder, Instant.now());
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("分类不存在"));
    }

    @Transactional
    public Category update(long id, CategoryRequest request) {
        if (request == null) throw ApiException.badRequest("请求体不能为空");
        Category existing = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("分类不存在"));
        String name = request.name() == null
                ? existing.name() : Validation.text(request.name(), 64, "分类名称", true);
        int sortOrder = request.sortOrder() == null ? existing.sortOrder() : request.sortOrder();
        repository.update(id, name, sortOrder);
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("分类不存在"));
    }

    @Transactional
    public void delete(long id) {
        repository.delete(id);
    }
}
