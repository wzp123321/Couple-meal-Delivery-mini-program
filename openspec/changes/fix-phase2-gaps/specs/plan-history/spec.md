## ADDED Requirements

### Requirement: 年度报告入口

前端历史页 SHALL 提供「月 / 年」两种报告范围切换，年度报告复用既有 `GET /api/report?scope=year&anchor=YYYY`。

#### Scenario: 切换到年度
- **WHEN** 用户在历史页把范围切到「年」
- **THEN** 以当前年份为 anchor 请求 `scope=year` 并展示年度统计
- **AND** 加载失败时保留上一次成功的数据，不把页面清空

#### Scenario: 默认月度
- **WHEN** 用户首次进入历史页
- **THEN** 默认按月度展示（与当前行为一致）
