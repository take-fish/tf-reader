package com.fishmatrix.reader.domain;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 书籍可见状态。隐藏的书只有本人与内容管理员可见。
 *
 * <p>枚举名与数据库存储值一致，对外 JSON 与改造前完全相同。</p>
 */
public enum BookStatus implements IEnum<String> {
    VISIBLE,
    HIDDEN;

    @Override
    public String getValue() {
        return name();
    }

    public boolean isVisible() {
        return this == VISIBLE;
    }
}
