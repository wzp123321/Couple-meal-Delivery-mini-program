# fix-phase2-gaps

## Why

phase-2 三个能力（晚市 / 石头剪刀布 / 订阅消息）后端 itest 全部通过，但端到端复核发现 4 处「规格实现了、用户实际用不上」的缺口：

1. **订阅消息发不出去**：后端已实现 access_token 缓存、订阅下发、`NotifyServiceImpl.push` 接入通道，但前端从未调用 `uni.requestSubscribeMessage`，用户侧没有订阅记录。微信对未授权用户返回拒绝，后端按「优雅失败」只记日志 —— 站内红点正常，所以从外部完全看不出该能力实际未生效。
2. **创建页截止时间未按餐段**：phase-2 后端截止已拆成午饭 11:00 / 晚饭 18:00，前端仍用统一的 `LATE_HOUR = 18` 判断「今天能不能约」。午饭在 11:00–18:00 之间前端仍放开「今天」，提交后被后端 1010 拒绝，用户看到的是「选完才说不行」。
3. **每日提醒开关失效且不区分餐段**：设置页存了 `couple.remind_enabled`，但 `DailyRemindJob` 从未读取该字段，关掉开关照样催；同时 cron 固定 11:00、同一天只催一条，晚饭在 18:00 截止前没有提醒。
4. **时区未固定**：全后端依赖 `LocalDateTime.now()`，JDBC 只设了 `serverTimezone`，JVM 默认时区未固定。服务器若为 UTC，则 11:00/18:00 截止、02:30 归档、工作日判断整体偏移 8 小时。

## What Changes

1. 新增前端订阅授权链路：`utils/subscribe.ts` 拉取 `/api/subscribe/templates` 并调 `uni.requestSubscribeMessage`，设置页加「微信提醒」开关，创建/认领等关键动作成功后静默补授权。
2. `plan/create.vue` 的截止小时按当前餐段取值（午饭 11 / 晚饭 18），切换餐段时重算可选日期与说明文案。
3. `DailyRemindJob` 读取 `remind_enabled`；提醒按餐段拆分，新增晚饭提醒 cron；每日提醒通知携带 `plan_id`，用 `uk_notification_plan_type_receiver` 天然幂等，午/晚各一条互不干扰。
4. 应用启动时固定 JVM 默认时区为 `Asia/Shanghai`，不依赖部署脚本。

## Impact

- 前端：`pages/plan/create.vue`、`pages/settings/index.vue`、`utils/subscribe.ts`（新增）、`utils/api.ts`、`types/api.ts`、`pages/today/index.vue`（关键动作补授权）
- 后端：`job/DailyRemindJob.java`、`CoupleMealApplication.java`、`resources/application.yml`
- 自测：`itest/itest_remind.sh`（新增，覆盖开关与餐段）、既有 `itest.sh` 回归
- 不涉及表结构变更（`couple.remind_enabled` 与通知唯一索引均已存在）
