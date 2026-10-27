package com.fishmatrix.reader.model;

/** 访问申请的审批动作。{@code message} 为可选的拒绝理由。 */
public record AccessDecisionRequest(String message) {
}
