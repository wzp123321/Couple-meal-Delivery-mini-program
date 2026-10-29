# subscribe-message Specification

## Purpose
TBD - created by archiving change phase-2. Update Purpose after archive.

## Requirements

### Requirement: 订阅消息下发基础设施
The system SHALL 实现微信订阅消息下发：缓存 `access_token`（带过期刷新），提供 `send(openid, templateId, data)` 调用微信 subscribe-message 接口，短超时、调用失败仅记录不阻断主流程。

#### Scenario: 调微信失败优雅跳过
- **Given** dev 环境微信不可达或模板未配置
- **When** 触发一次订阅消息推送
- **Then** 调用快速失败并被捕获，主流程不受影响，仅记日志

### Requirement: 关键事件触发订阅消息
The system SHALL 在关键事件（约定被认领 / 已交付 / 待打卡提醒）尝试用订阅消息推送对应模板；若未配置模板或用户未授权则跳过。

#### Scenario: 被认领提醒
- **Given** 一个待接收/备餐中的约定被对方认领
- **When** 推送通知时
- **Then** 若已配置认领模板且用户曾授权，发送订阅消息；否则仅系统内通知

### Requirement: 暴露前端需申请的模板ID
The system SHALL 暴露接口返回本项目使用的订阅消息模板ID列表，供前端在对应场景调用 `wx.requestSubscribeMessage` 申请授权。

#### Scenario: 获取模板ID
- **When** 前端请求订阅模板列表
- **Then** 返回各事件对应的 `templateId`（来自配置，未配置则为空）
