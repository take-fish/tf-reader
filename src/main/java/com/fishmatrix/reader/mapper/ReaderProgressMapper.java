package com.fishmatrix.reader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fishmatrix.reader.entity.ReaderProgress;
import org.apache.ibatis.annotations.Mapper;

/**
 * reader_progress 是复合主键表（user_id + book_id），实体上没有 {@code @TableId}：
 * {@code selectById} / {@code updateById} / {@code deleteById} 运行时会抛
 * {@code can not find column for id}。复合主键表，写操作走 XML；不要使用
 * selectById / updateById / deleteById。
 */
@Mapper
public interface ReaderProgressMapper extends BaseMapper<ReaderProgress> {
}
