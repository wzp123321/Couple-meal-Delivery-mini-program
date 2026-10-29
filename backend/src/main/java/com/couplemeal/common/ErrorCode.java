package com.couplemeal.common;

import lombok.Getter;

/**
 * 错误码枚举（全表见 docs/design/00-全局约定.md §4）。
 * 文案直接写在这里，前端 toast 直接显示 msg，不在前端维护第二份文案。
 *
 * <p>约定：0=成功；1xxx=账户/关系；2xxx=约定/菜单；3xxx=打卡；4xxx=素材/审核；5xxx=微信。
 * 注：PARAM_INVALID / SYS_ERROR 为框架级兜底码，§4 业务表中未列，见汇报 CCR 候选。
 */
@Getter
public enum ErrorCode {

    // ===== 成功 =====
    SUCCESS(0, "ok"),

    // ===== 1xxx 账户 / 关系（全局） =====
    NOT_LOGIN(1001, "未登录或登录已过期"),
    NO_COUPLE(1002, "还没有绑定情侣关系"),
    INVITE_INVALID(1003, "邀请码无效或已过期"),
    STATUS_CONFLICT(1004, "当前状态不允许该操作"),
    CANNOT_CLAIM_SELF(1005, "不能认领自己发起的约定"),
    INVITE_TOO_FREQUENT(1006, "邀请码生成太频繁了，稍后再试"),
    ALREADY_BOUND(1007, "你们已经绑定啦"),
    CANNOT_BIND_SELF(1008, "不能和自己绑定哦"),
    WEEKEND_NOT_ALLOWED(1009, "只能约工作日"),
    TOO_LATE_TODAY(1010, "今天太晚了，约明天吧"),
    DATE_OUT_OF_RANGE(1011, "日期超出可约范围"),
    NICKNAME_INVALID(1012, "昵称不太合适，换一个试试"),
    PET_NAME_INVALID(1013, "起个 2–12 字的称呼吧"),

    // ===== 2xxx 约定 / 菜单 =====
    MENU_ITEM_NOT_FOUND(2001, "菜品不存在"),
    MENU_NAME_DUPLICATE(2002, "菜名重复了"),
    ITEMS_TOO_MANY(2003, "一次最多选 10 道菜"),
    MENU_NAME_TOO_LONG(2004, "菜名不能超过 20 个字"),
    REMARK_TOO_LONG(2005, "备注不能超过 100 个字"),
    CANCEL_REASON_REQUIRED(2006, "取消要写个原因哦"),

    // ===== 3xxx 打卡 =====
    ALREADY_CHECKED(3001, "这个约定已经打过卡了"),
    CHECKIN_IMAGE_COUNT(3002, "打卡需要 1–2 张照片"),
    CHECKIN_WINDOW_CLOSED(3003, "打卡时间已经过了"),
    CHECKIN_ONLY_RECEIVER(3004, "只有收到饭的人可以打卡"),
    CHECKIN_NOT_DELIVERED(3010, "还没送达，先别急着打卡"),

    // ===== 4xxx 素材 / 审核 =====
    IMAGE_FORMAT_UNSUPPORTED(4001, "图片格式不支持"),
    IMAGE_TOO_LARGE(4002, "图片太大了"),
    AVATAR_AUDIT_FAILED(4003, "这张头像没通过审核，换一张试试"),
    AVATAR_INVALID(4004, "头像无效，请重新上传"),

    // ===== 5xxx 微信 =====
    WX_API_FAILED(5001, "微信接口调用失败，请稍后再试"),

    // ===== 6xxx 石头剪刀布协商 =====
    RPS_GAME_NOT_FOUND(6001, "这局石头剪刀布不见了"),
    RPS_ALREADY_THROWN(6002, "这一局你已经出过拳了"),
    RPS_GAME_FINISHED(6003, "这局已经分胜负啦"),
    RPS_INVALID_CHOICE(6004, "出拳只能是石头、剪刀或布"),

    // ===== 框架级兜底（§4 未列业务归属，全局通用） =====
    PARAM_INVALID(4000, "参数有误"),
    SYS_ERROR(5000, "系统开小差了，请稍后再试"),
    SERVICE_BUSY(5002, "服务繁忙，请稍后再试");

    private final int code;
    private final String msg;

    ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
