## ADDED Requirements

### Requirement: 审核挂起兜底

系统 SHALL 对长时间停留在「审核中」的打卡图片做兜底处置，避免回调丢失导致图片永久不展示、用户又无从知情。

#### Scenario: 超时未回调置为屏蔽
- **WHEN** 某张图片 `audit_status = 0`（审核中）且 `create_time` 早于 `audit.pending-timeout-minutes`（默认 120）分钟
- **THEN** 该图置为屏蔽（2），`audited_time` 记为处置时刻
- **AND** 按「仍有审核中 → 0 / 全通过 → 1 / 存在屏蔽 → 2」重算所属打卡的汇总态
- **AND** 打日志（含 imageId / traceId）便于排查回调链路

#### Scenario: 已通过不被回退
- **WHEN** 图片已是通过（1）或屏蔽（2）
- **THEN** 兜底任务不改动它

### Requirement: 前端展示审核状态

前端 SHALL 在相册与打卡详情中区分「审核中 / 未通过」，不得让用户误以为照片丢了。

#### Scenario: 审核中
- **WHEN** 卡片 `auditStatus = 0`
- **THEN** 展示「审核中」角标；缩略图正常显示

#### Scenario: 未通过
- **WHEN** 卡片 `auditStatus = 2`
- **THEN** 展示「未通过审核」提示，并说明可重新打卡替换照片
