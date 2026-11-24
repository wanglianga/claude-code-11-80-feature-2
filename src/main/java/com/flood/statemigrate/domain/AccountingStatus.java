package com.flood.statemigrate.domain;

/**
 * 核算窗口状态：
 * CLOSED         两路线在容差内闭合，统一估计生效
 * UNCLOSED       无法闭合（缺校准段无法交叉验证，或两路线偏差超容差），不给出统一单点量
 * PENDING_REVIEW 两路线闭合但校准误差跨过安全阈值，进入待核，不进入运行效率报表
 */
public enum AccountingStatus {
    CLOSED,
    UNCLOSED,
    PENDING_REVIEW
}
