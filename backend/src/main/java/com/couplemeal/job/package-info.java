/**
 * 定时任务包（M6/M8 填充）。
 *
 * <p>四个 Job 全部用 {@code @Scheduled}，单机无需分布式锁（见 00-全局约定.md D10 + 01-通用机制.md §4）：
 *   PlanExpireJob / AutoCompleteJob / InviteExpireJob / DailyRemindJob。
 * 每个 Job 复用 Service 方法，不直接写 SQL 改状态。
 */
package com.couplemeal.job;
