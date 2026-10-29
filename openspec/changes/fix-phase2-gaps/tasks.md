# Tasks: fix-phase2-gaps

## 1. 订阅授权前端接入（subscribe-message）

- [x] 1.1 新增 `couple-meal-miniprogram/src/utils/subscribe.ts`：拉取 `GET /api/subscribe/templates`，过滤空串后调 `uni.requestSubscribeMessage`；未配置模板 / 微信环境不支持 / 用户拒绝时**静默跳过**，不得抛错打断主流程
- [x] 1.2 `types/api.ts` 补 `SubscribeTemplates` 类型（claimed / delivered / checkinRemind 三键，值可能为空串）；`utils/api.ts` 补 `getSubscribeTemplates()`
- [x] 1.3 `pages/settings/index.vue` 新增「微信消息提醒」开关（本地持久化，默认关），打开时立即请求一次授权；关闭后不再主动请求
- [x] 1.4 关键动作成功后静默补授权：创建约定成功、认领成功后各请求一次（用户开关开启时）
- [x] 1.5 前端 `npm run type-check`：本次改动文件 0 错误

## 2. 创建页截止按餐段（meal-plan）

- [x] 2.1 `pages/plan/create.vue`：截止小时改为按 `mealType` 取值（午饭 11 / 晚饭 18），替换写死的 `LATE_HOUR = 18`
- [x] 2.2 切换餐段时重算可选日期并校正已选日期（若原选日期在新餐段下不可选，回落到首个可选）
- [x] 2.3 日期条说明文案随餐段变化（午饭 11:00 后约不了当天 / 晚饭 18:00 后约不了当天），不再写死 18:00
- [x] 2.4 前端 `npm run type-check` 通过

## 3. 每日提醒尊重开关并按餐段（notifications）

- [x] 3.1 `DailyRemindJob` 查询待认领约定时联表读取 `couple.remind_enabled`，为 `false` 的关系整条跳过
- [x] 3.2 提醒按餐段拆分：新增 `daily-remind-dinner-cron`（默认工作日 17:00），午饭走 11:00、晚饭走 17:00，各自只催对应 `meal_type`
- [x] 3.3 每日提醒通知携带 `plan_id`（替代原先 `planId=null` + 手工 count 去重），依赖 `uk_notification_plan_type_receiver` 保证同一约定同一人只催一次，午饭/晚饭互不干扰
- [x] 3.4 文案区分餐段（「今天午饭谁带呀」/「今天晚饭谁带呀」）
- [x] 3.5 新增 `itest/itest_remind.sh`：覆盖「开关关闭不发」「开关开启午饭催一次且幂等」「晚饭单独催」「周末跳过」
- [x] 3.6 后端 `mvn -o package -DskipTests` 通过，`itest_remind.sh` 全 PASS

## 4. 固定应用时区

- [x] 4.1 `CoupleMealApplication.main` 在 `SpringApplication.run` 之前 `TimeZone.setDefault(Asia/Shanghai)`，不依赖服务器 TZ / 容器环境
- [x] 4.2 回归 `itest.sh` 主流程（截止与过期判断依赖时区）

## 5. 收尾

- [x] 5.1 `openspec validate fix-phase2-gaps` 通过
- [x] 5.2 全量回归：`itest.sh` / `itest_dinner.sh` / `itest_rps.sh` / `itest_subscribe.sh`

## 6. 审核挂起兜底 + 前端审核态（meal-checkin）

- [x] 6.1 后端：`audit_status=0` 且 `create_time` 早于 `pending-timeout-minutes`（默认 120）的图片 → 置 2（屏蔽）+ 写 `audited_time` + 重算打卡汇总态 + 打日志（含 imageId/traceId）；已通过/已屏蔽不参与
- [x] 6.2 `application.yml` 补 `couple-meal.audit.pending-timeout-minutes: 120`，并挂到既有 `MediaAuditServiceImpl` 定时任务（独立键 `audit-pending-expire-cron`，默认 03:50）
- [x] 6.3 前端相册卡片按 `auditStatus` 展示「审核中 / 未通过」角标与说明（`GalleryCard.auditStatus` 已有，勿改后端 VO）；顺带修正相册前后端契约不一致（`months/cards/nextCursor`），此前相册页取不到任何数据
- [x] 6.4 前端打卡详情大图同样标注审核态 —— 详情页不渲染打卡图片（图只出现在相册），已由相册卡片角标 + 无图时的审核态轻提示覆盖
- [x] 6.5 `itest/itest_audit.sh`：覆盖「超时未回调 → 置屏蔽 + 汇总重算」「已通过不被回退」
- [x] 6.6 前端 `type-check` 通过

## 7. 上传孤儿文件清理

- [x] 7.1 新增每日清理任务（默认 03:40，offset 于 02:30 归档后）：扫 `upload.base-dir`，只删「mtime 早于 24 小时」且「库内无引用」的文件；打卡图比对 `meal_checkin_image.url/thumb_url`，头像比对 `user.avatar`
- [x] 7.2 目录不存在 / 无权限 / 单文件删除失败只记日志，不中断整轮；每轮打统计（扫描数 / 删除数 / 保留数）
- [x] 7.3 验证：造「24h 前的孤儿文件」与「被引用的正常文件」，跑任务后孤儿被删、引用保留（已用临时 base-dir + 真实 cron 跑过，见日志 `UploadCleanupJob 扫描 N / 删除 M / 保留 K`）

## 8. 年度报告入口（plan-history）

- [x] 8.1 `pages/history/index.vue` 加「月 / 年」切换，年度走 `GET /api/report?scope=year&anchor=YYYY`
- [x] 8.2 切换请求失败时保留上一次成功数据，不清空页面
- [x] 8.3 前端 `type-check` 通过

## 9. 文档与收尾

- [x] 9.1 同步过时设计文档中「每日提醒 planId 为 NULL / 手工 count 去重 / 单个 11:00 cron」的表述（`docs/design/50-通知/后端.md`、`01-通用机制.md`、`00-全局约定.md`）；另同步：订阅消息旁路、餐段截止配置、周历「二期协商」、菜单 `note` 字段
- [x] 9.2 `openspec validate fix-phase2-gaps` 通过
- [x] 9.3 全量回归：6 套 itest（含 remind/audit）+ 前端 `type-check`
- [x] 9.4 提交并推送到 GitHub（SSH 通道）
