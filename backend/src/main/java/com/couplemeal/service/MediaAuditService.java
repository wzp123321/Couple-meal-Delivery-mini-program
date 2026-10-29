package com.couplemeal.service;

/**
 * 图片内容安全：微信 mediaCheckAsync 异步送审 + 回调回写 + 补偿重试。
 *
 * <p>见 60-打卡/后端.md §3.3 / §4：
 *  - 按单张原图送审，返回 trace_id 存进 meal_checkin_image（缩略图继承原图结果，不重复送审）
 *  - 回调按 trace_id 定位单张，回写 audit_status 后重算 meal_checkin 的汇总态
 *  - 送审失败的图（trace_id 为空）由补偿任务重试，避免接口抖动期间照片永远停在「审核中」
 */
public interface MediaAuditService {

    /**
     * 异步送审一张原图。
     *
     * @param relativeUrl 原图相对路径 /upload/yyyyMM/x.jpg
     * @return 微信返回的 trace_id
     * @throws com.couplemeal.common.BizException 微信接口异常时为 WX_API_FAILED（5001）
     */
    String submit(String relativeUrl);

    /**
     * 处理审核回调：按 trace_id 回写单张状态，再重算所属打卡的汇总态。
     *
     * @param traceId 微信 trace_id（库里不存在则忽略，防伪造回调）
     * @param suggest 微信建议，pass=通过，其余按屏蔽处理
     */
    void handleCallback(String traceId, String suggest);

    /**
     * 补偿：重新送审 trace_id 为空且创建超过 5 分钟的图片，单次上限 200 条。
     *
     * @return 本轮成功回填 trace_id 的条数
     */
    int retryPendingAudit();

    /**
     * 兜底：把「已送审（有 trace_id）但创建超过 pending-timeout-minutes 仍停在审核中」的图片
     * 置为屏蔽（audit_status=2），并重算所属打卡汇总态。
     *
     * <p>用于回调丢失（回调地址未配 / 网络丢包）的场景——否则图片会永久停在审核中、
     * 永不展示且用户毫不知情。只处理 audit_status=0 的记录，绝不回退已通过 / 已屏蔽的图。
     *
     * @return 本轮实际由审核中改为屏蔽的条数
     */
    int expirePendingAudit();
}
