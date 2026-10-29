# rps-negotiation Specification

## Purpose
TBD - created by archiving change phase-2. Update Purpose after archive.

## Requirements

### Requirement: 创建石头剪刀布对局
The system SHALL 允许一方针对 `(couple_id, plan_date, meal_type)` 发起一局石头剪刀布，默认 best-of-1。对局初始状态为"待对方出拳"。

#### Scenario: 发起对局
- **Given** 一对已绑定情侣
- **When** 用户 A 针对今日午饭发起对局
- **Then** 生成 `rps_game`（status=待对方出拳），返回 `gameId`

### Requirement: 双方出拳与胜负判定
The system SHALL 记录双方出拳（rock/paper/scissors），按规则判定胜负；平局时状态回到"待对方出拳"并要求重出，直至分出胜负。

#### Scenario: 分出胜负
- **Given** 对局待对方出拳，A 已出拳
- **When** B 出拳且不与 A 相同
- **Then** 对局 status=已完成，写入胜方 `winner_id`

#### Scenario: 平局重出
- **Given** A、B 出拳相同
- **When** 判定
- **Then** 对局保持待对方出拳，需重新出拳

### Requirement: 输家带饭规则
The system SHALL 采用"输家带饭"的趣味规则：对局胜者之外的另一方（输家）为建议带饭方。

#### Scenario: 采纳结果生成约定
- **Given** 一局已分出胜负（winner=A）
- **When** 任一方点击"采纳结果"
- **Then** 系统创建 `source=1`（主动带）约定，`carrier_id=输家(B)`，`status=待接收(1)`，并返回 `planId`

### Requirement: 对局数据隔离与幂等
The system SHALL 保证对局仅双方可见；同一 `(couple, date, meal_type)` 进行中的对局不应重复创建（复用进行中的对局）。

#### Scenario: 一方重复发起
- **Given** 已有一局进行中的对局
- **When** 同一方再次发起相同 `(date, meal_type)` 对局
- **Then** 返回已存在的 `gameId`（幂等），不新建

### Requirement: 前端石头剪刀布页
The system SHALL 提供石头剪刀布协商页：展示对局状态（待出拳 / 平局重出 / 已完成）、双方出拳（1 石头 2 剪刀 3 布）、胜负与建议带饭方；任一方在分出胜负后可「采纳结果」生成主动带约定（输家带饭）。页内支持发起对局、出拳、平局重出、采纳。

#### Scenario: 分出胜负后采纳
- **Given** 一局已分出胜负（winner 已写）
- **When** 任一方点击「采纳结果」
- **Then** 生成约定（carrier=输家，source=1，status=待接收）并跳转约定详情

#### Scenario: 平局提示重出
- **Given** 双方出拳相同
- **When** 判定为平局
- **Then** 页面提示「平局，再来一局」并允许重出（对局保持进行中）
