package com.fishmatrix.reader.domain;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 私有书籍访问申请的状态机：
 * {@code PENDING → APPROVED / REJECTED / CANCELLED}，{@code APPROVED → REVOKED}。
 *
 * <p>枚举名与数据库存储值一致，对外 JSON 与改造前完全相同。</p>
 */
public enum AccessRequestStatus implements IEnum<String> {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED,
    REVOKED;

    @Override
    public String getValue() {
        return name();
    }

    /** 已占用（待处理或已通过），重复申请时直接返回原记录。 */
    public boolean isActive() {
        return this == PENDING || this == APPROVED;
    }

    public boolean isPending() {
        return this == PENDING;
    }

    public boolean isApproved() {
        return this == APPROVED;
    }

    public boolean isRejected() {
        return this == REJECTED;
    }
}
