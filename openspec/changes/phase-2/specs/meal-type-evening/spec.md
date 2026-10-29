# Spec: meal-type-evening

## ADDED Requirements

### Requirement: 晚饭时段支持
The system SHALL 支持 `meal_type=2`（晚饭）的约定，与现有 `meal_type=1`（午饭）并列。创建约定时可由调用方指定 `meal_type`，缺省为 `1`。

#### Scenario: 创建晚饭约定
- **Given** 一对已绑定情侣，当前为工作日
- **When** 其中一方创建 `meal_type=2` 的约定
- **Then** 约定落库且 `meal_type=2`，状态机与午饭一致（待认领/待接收 → 备餐中 → 已交付 → 已完成）

#### Scenario: 缺省为午饭
- **Given** 创建约定时未传 `meal_type`
- **When** 提交
- **Then** 按 `meal_type=1` 处理

### Requirement: 按餐段配置发起截止时间
The system SHALL 对午饭与晚饭分别配置"当日约定发起截止小时"。午饭默认 `11`，晚饭默认 `18`。超过截止小时不允许发起当日该餐段约定（失败码 `1010` TOO_LATE_TODAY）。

#### Scenario: 超过晚饭截止时间发起被拒
- **Given** 当前为工作日 `18:30`
- **When** 发起 `meal_type=2` 的当日约定
- **Then** 返回 `1010`（今天太晚了，约明天吧），约定不落库

#### Scenario: 午饭截止时间独立
- **Given** 当前为工作日 `11:30`（已过午饭截止但未过晚饭截止）
- **When** 发起 `meal_type=2` 的当日约定
- **Then** 创建成功（仅午饭受 `11:00` 限制）

### Requirement: 超时与打卡窗口按餐段适配
The system SHALL 对晚饭约定应用与午饭一致的超时流转（次日 `02:30` 未交付转过期、已交付超 `24h` 自动完成）与打卡窗口（交付当天 `10:00` 至次日 `02:00`）。

#### Scenario: 晚饭已交付超 24h 自动完成
- **Given** 一个已交付的晚饭约定，交付时间已超过 `24h`
- **When** 定时任务扫描
- **Then** 该约定自动转为已完成并发放爱心值

### Requirement: 前端创建页餐段选择
The system SHALL 在创建约定页提供午饭/晚饭餐段选择（默认午饭），选择结果随 create 请求提交 `mealType`。今日/列表页对已创建的约定按 `mealType` 展示餐段标签，与详情页一致。

#### Scenario: 选择晚饭创建
- **Given** 用户在创建页选择"晚饭"
- **When** 提交
- **Then** 请求携带 `mealType=2`，后端落库晚饭约定

#### Scenario: 默认午饭
- **Given** 未手动选择餐段
- **When** 提交
- **Then** 按 `mealType=1` 提交

## ADRs

### ADR-1: 截止时间按餐段而非统一
MVP 用单一 `plan.order-cutoff-hour=18`。二期改为按餐段双配置，避免午饭截止被晚饭时间带偏。配置键：`couple-meal.plan.cutoff.lunch`（默认 11）/ `couple-meal.plan.cutoff.dinner`（默认 18），未配置时回落默认值，无需改动 application.yml。

### ADR-2: 周末仍不自动排班
二期仅补晚市时段，周末（周六/日）仍不自动发起约定，需手动，与 MVP 一致。
