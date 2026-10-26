package com.fishmatrix.reader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fishmatrix.reader.entity.ReaderBook;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReaderBookMapper extends BaseMapper<ReaderBook> {

    /** 公开书库：JOIN 分类名 + 可选关键字 + 分页。 */
    IPage<ReaderBook> selectPublicPage(
            Page<ReaderBook> page,
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword);
}
