# Spec Delta

## Purpose

把"今天到底谁带"从每天的口头扯皮变成一张看得见的周表。协商式决定谁带属二期，本期不做。

## ADDED Requirements

### Requirement: 周历视图

系统 SHALL 以周为单位展示每天由谁带饭、带什么菜，仅展示工作日安排。

#### Scenario: 查看本周

- **WHEN** 用户切换到本周视图
- **THEN** 系统展示周一至周五每天的带饭人与菜品，无安排的日期显示为未安排

#### Scenario: 周末展示

- **WHEN** 周历渲染到周六与周日
- **THEN** 显示为"休息"，MUST NOT 显示带饭安排
