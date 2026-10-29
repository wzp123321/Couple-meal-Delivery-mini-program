# Tasks: phase-2

## 1. meal-type-evening（晚市）
- [x] 1.1 配置：新增 `plan.cutoff.lunch` / `plan.cutoff.dinner`，取代单一 `plan.order-cutoff-hour`
- [x] 1.2 `meal_plan.meal_type` 语义确认（1 午饭 / 2 晚饭），无表结构变更（已是 int）
- [x] 1.3 PlanController/Service：create 支持 `meal_type`；发起截止校验按餐段读取配置
- [x] 1.4 超时任务 / 打卡窗口：确认餐段无关部分复用，记录适配点
- [x] 1.5 itest：晚饭创建 / 生命周期 / 截止校验用例
- [x] 1.6 前端 `plan/create.vue`：餐段选择（午饭/晚饭，默认午饭）写入 `mealType`；今日/列表页按 `mealType` 展示餐段标签

## 2. rps-negotiation（石头剪刀布）
- [x] 2.1 建表 `rps_game` / `rps_round`（+ 索引 `uk_rps_couple_date_meal`）
- [x] 2.2 RpsController/Service：create / play / get / adopt
- [x] 2.3 胜负判定（平局重出）、输家带饭、采纳生成 `source=1` 约定
- [x] 2.4 数据隔离 + 进行中对局幂等复用
- [x] 2.5 itest：发起 / 出拳 / 平局 / 胜负 / 采纳用例（25/0 通过）
- [ ] 2.6 前端 `pages/rps/index.vue`：协商页（发起 / 出拳 / 平局重出 / 胜负 / 采纳）+ `pages.json` 注册 + `api.ts` 封装；入口挂在今日页（待真机端落地）

## 3. subscribe-message（订阅消息）
- [x] 3.1 `WxConfig`/TokenCache：`access_token` 缓存与刷新
- [x] 3.2 `SubscribeMessageService.send`（短超时、优雅失败）
- [x] 3.3 `NotifyService` 关键事件接入订阅消息通道（模板可配置）：认领/送达/打卡提醒
- [x] 3.4 模板ID 暴露接口 `GET /subscribe/templates`
- [x] 3.5 itest：dev 下优雅失败分支验证（4/0 通过）
