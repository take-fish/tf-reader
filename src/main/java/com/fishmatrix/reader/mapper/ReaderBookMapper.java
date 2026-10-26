package com.fishmatrix.reader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fishmatrix.reader.entity.ReaderBook;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReaderBookMapper extends BaseMapper<ReaderBook> {

    /**
     * 公开书库分页查询。
     *
     * <p><b>分页基准</b>：对外 API 的 page 是 0-based（见 {@code BookController} / {@code LibraryService}），
     * 而 MyBatis-Plus 的 {@link Page} 是 1-based。Phase 2 接线时必须做 {@code page + 1} 换算，
     * 组装 {@code BookPage} 时再把 {@code IPage#getCurrent()} 换算回 0-based。
     *
     * <p><b>size 上限</b>：100 的上限由 {@code LibraryService} 钳制，本方法与分页拦截器都不设 maxLimit；
     * 若绕过 service 直接调用，需自行限制 size。
     */
    IPage<ReaderBook> selectPublicPage(
            Page<ReaderBook> page,
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword);
}
