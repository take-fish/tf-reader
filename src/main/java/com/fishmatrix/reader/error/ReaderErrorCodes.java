package com.fishmatrix.reader.error;

/**
 * 阅读服务的细分错误码。取值一律保持不变——前端与运维告警按这些字符串分支，
 * 改名等于破坏契约。
 */
public final class ReaderErrorCodes {

    /** 访问申请状态不允许当前操作（如对已处理的申请再次审批）。 */
    public static final String ACCESS_INVALID_STATE = "INVALID_STATE";

    /** 申请被拒后处于冷却期。 */
    public static final String ACCESS_COOLDOWN = "REQUEST_COOLDOWN";

    private ReaderErrorCodes() {
    }
}
