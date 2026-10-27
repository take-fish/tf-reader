package com.fishmatrix.reader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fishmatrix.reader.entity.ReaderShelfBook;
import org.apache.ibatis.annotations.Mapper;

/**
 * 复合主键表（无 @TableId）：写操作走 XML。
 * 不要使用 selectById / updateById / deleteById，这些方法在无主键实体上不可用。
 */
@Mapper
public interface ReaderShelfBookMapper extends BaseMapper<ReaderShelfBook> {
}
