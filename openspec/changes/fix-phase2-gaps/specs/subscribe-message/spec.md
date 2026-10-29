## ADDED Requirements

### Requirement: 前端申请订阅授权

前端 SHALL 在用户明确开启「微信消息提醒」后，向微信申请一次性订阅授权，并把授权结果交由微信侧记录；未授权时全部订阅事件静默跳过，不得影响站内通知与主流程。

模板 ID 来源：`GET /api/subscribe/templates` 返回 `claimed` / `delivered` / `checkinRemind` 三键，值为空串表示该事件未配置模板。

#### Scenario: 模板未配置时静默跳过
- **WHEN** `/api/subscribe/templates` 返回的模板值全为空串，或运行环境不支持 `uni.requestSubscribeMessage`
- **THEN** 前端不弹授权框、不提示错误，关键动作照常完成

#### Scenario: 用户开启开关即申请授权
- **WHEN** 用户在设置页把「微信消息提醒」从关打到开
- **THEN** 前端用非空模板 ID 调起 `uni.requestSubscribeMessage`，并把开关状态持久化到本地
- **AND** 用户拒绝授权时开关保持关闭态并提示一次，不重复弹窗

#### Scenario: 关键动作后补授权
- **WHEN** 用户开关为开启态，且创建约定成功或认领约定成功
- **THEN** 前端静默再申请一次订阅授权，为用户后续接收该事件的通知累积一次配额
- **AND** 授权失败不影响动作结果展示

### Requirement: 订阅事件与模板对齐

后端 SHALL 仅对 `claimed`（被认领）、`delivered`（已送达）、`checkinRemind`（待打卡提醒）三类事件下发订阅消息，其余通知类型只写站内信。

#### Scenario: 非订阅事件不下发
- **WHEN** 触发 `NEW_PLAN` / `COMPLETED` / `DAILY_REMIND` / `CHECKED_IN` 通知
- **THEN** 不调用微信订阅消息接口，仅落 `notification` 表
