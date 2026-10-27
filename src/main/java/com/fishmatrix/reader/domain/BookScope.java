package com.fishmatrix.reader.domain;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 书库类型。
 *
 * <p>枚举名与数据库 VARCHAR 中存的值一致，Jackson 默认按名称序列化，
 * 因此对外 JSON 与改造前完全相同，前端不需要改动。</p>
 */
public enum BookScope implements IEnum<String> {
    PUBLIC,
    PRIVATE;

    @Override
    public String getValue() {
        return name();
    }

    public boolean isPublic() {
        return this == PUBLIC;
    }

    public boolean isPrivate() {
        return this == PRIVATE;
    }
}
