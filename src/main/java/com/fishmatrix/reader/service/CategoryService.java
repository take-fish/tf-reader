package com.fishmatrix.reader.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.reader.entity.ReaderBook;
import com.fishmatrix.reader.entity.ReaderCategory;
import com.fishmatrix.reader.error.NotFoundException;
import com.fishmatrix.reader.mapper.ReaderBookMapper;
import com.fishmatrix.reader.mapper.ReaderCategoryMapper;
import com.fishmatrix.reader.model.Category;
import com.fishmatrix.reader.model.CategoryRequest;
import com.fishmatrix.reader.util.Validation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final ReaderCategoryMapper categoryMapper;
    private final ReaderBookMapper bookMapper;

    public List<Category> list() {
        return categoryMapper
                .selectList(Wrappers.<ReaderCategory>lambdaQuery()
                        .orderByAsc(ReaderCategory::getSortOrder)
                        .orderByAsc(ReaderCategory::getId))
                .stream()
                .map(CategoryService::toView)
                .toList();
    }

    @Transactional
    public Category create(CategoryRequest request) {
        if (request == null) {
            throw ApiException.badRequest("请求体不能为空");
        }
        ReaderCategory entity = new ReaderCategory();
        entity.setName(Validation.text(request.name(), 64, "分类名称", true));
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        categoryMapper.insert(entity);
        return toView(require(entity.getId()));
    }

    @Transactional
    public Category update(long id, CategoryRequest request) {
        if (request == null) {
            throw ApiException.badRequest("请求体不能为空");
        }
        ReaderCategory existing = require(id);
        if (request.name() != null) {
            existing.setName(Validation.text(request.name(), 64, "分类名称", true));
        }
        if (request.sortOrder() != null) {
            existing.setSortOrder(request.sortOrder());
        }
        categoryMapper.updateById(existing);
        return toView(require(id));
    }

    /** 删除分类，同时把引用它的书置为未分类——原来靠两条手写 SQL，现在一条批量更新完成。 */
    @Transactional
    public void delete(long id) {
        bookMapper.update(
                null,
                Wrappers.<ReaderBook>lambdaUpdate()
                        .set(ReaderBook::getCategoryId, null)
                        .eq(ReaderBook::getCategoryId, id));
        categoryMapper.deleteById(id);
    }

    private ReaderCategory require(long id) {
        ReaderCategory entity = categoryMapper.selectById(id);
        if (entity == null) {
            throw new NotFoundException("分类不存在");
        }
        return entity;
    }

    private static Category toView(ReaderCategory entity) {
        return new Category(entity.getId(), entity.getName(), entity.getSortOrder());
    }
}
