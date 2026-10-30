# AGENT.md · 项目作业手册

> **所有 AI Agent 进入本项目的唯一入口。** 开始任何工作前先读本文件，再按第 3 节的指针读具体文档。
> 面向人：这是「谁做什么、什么算做完」的说明书。面向 Agent：这是硬约束，**违反即产出无效**。

---

## 1. 项目是什么

情侣之间「约今天吃什么、谁带饭」的**私密协作微信小程序**。固定 2 人、仅工作日午饭、一方点菜另一方带饭。

一句话：**不是外卖，是「我记得你爱吃什么，并愿意为你带饭」的专属记录仪。**

**明确不做**（动手前先记住，别顺手扩范围）：

- ❌ 不做外卖 / 代投喂 / 代买 / 第三方配送（2026-10-27 拍板）
- ❌ 不碰真实金钱：无支付、无 AA、无饭债互欠
- ❌ 不做公开社交、排行榜、分享裂变
- ❌ 不做多人群组，只有一对一情侣关系

---

## 2. 当前阶段

**MVP 与 phase-2 已全部落地并归档，当前不写新功能，处在「真机联调 + 上线准备」。**

| 维度 | 状态 |
|---|---|
| 产品 | PRD 已冻结，无未决项（Q1–Q6 全闭合） |
| 交互 | 高保真可交互原型：18 屏 + 9 弹层，可直接点击 |
| 技术 | `docs/design/` 按**需求**拆分：9 个需求目录（每个内含 `后端.md` + `前端.md`）+ 4 份公共文档，`db/schema.sql` 可直接执行 |
| 规格 | `openspec/specs/` 已落 **12 个能力**（MVP 9 + phase-2 3），`validate --strict` 通过 |
| 代码 | 后端 15 个 Controller / 前端 15 个页面，MVP + phase-2 全量实现 |
| 验收 | 机械层：`bash scripts/verify.sh`（见 §10）。**真机运行未验证**——appid 仍是 `touristappid`，登录拿不到 openid |

---

## 3. 文档基线（唯一真相源）

| 文档 | 管什么 | 冲突时怎么办 |
|---|---|---|
| `docs/产品需求文档.md` | 定位、突出点、功能、状态机、数据模型、**§12 交互规范**、**§13 风险合规** | **最高优先级** |
| `docs/design/README.md` | **设计文档导航**：按任务类型查该读哪几份 | 入口，**先读它** |
| `docs/design/00-全局约定.md` | D1–D16 决策、数据表一览、鉴权与数据隔离、错误码全表、配置项 | 与 PRD 冲突时，以本文 **§1 设计决策** 为准（含理由），未列出的条款以 PRD 为准 |
| `docs/design/01-通用机制.md` | 状态机与转移白名单、并发幂等、事务边界、定时任务总表 | — |
| `docs/design/前端-00-基础设施.md` | uni-app 工程结构、请求层、状态管理、样式 tokens、组件、错误码映射 | — |
| `docs/design/后端-00-架构.md` | 架构图、包结构、分层职责、关键类清单 | 仅 M0 搭骨架时读 |
| `docs/design/10~90-*/` | 9 个需求目录，每个含 `后端.md`（表 / 接口契约 / 实现 / Job）与 `前端.md`（页面 / 交互 / 状态），与 OpenSpec capability 一一对应 | 与 `db/schema.sql` 冲突，以 schema 为准 |
| `db/schema.sql` | **数据库结构唯一真相源**（12 张表 / 字段 / 索引 / 约束） | PRD §7 只列表清单与模块归属，**字段级一律以本文件为准** |
| `docs/开发前准备与详细设计.md` | 环境清单、**接口路径总表（§2.4，索引性质）**、数据字典、异常边界、上线审核、里程碑 | 与 PRD 冲突，以 PRD 为准；接口细节以 `design/NN-*/后端.md §2` 为准 |
| `docs/部署与运维.md` | 服务器规格、MySQL 参数、systemd、Nginx、日志轮转、备份恢复、上线检查清单、故障对照 | 仅部署 / 排障时读，日常开发不必读 |
| `docs/ui-prototype-cream.html` | 视觉与交互基线（配色 / 圆角 / 微交互） | 交互细节以 PRD §12 为准 |
| `openspec/specs/` | **行为契约（验收标准）** | 每次变更归档后落库；与 PRD 不一致时按 §4.0 硬规则 5 处理（先报告 → 拍板 → 回写 PRD → 再改 specs） |
| `openspec/changes/<name>/` | 进行中的变更四件套 | proposal / specs / design / tasks |
| `.workbuddy/memory/MEMORY.md` | 已拍板决策记忆（防跨会话漂移） | 只读参考，**不是交付物** |

> **读设计文档的纪律**：按 `docs/design/README.md` 的任务索引，一次只读「所需公共文档 + 该模块的**一份**文档」——做后端读 `后端.md`，做前端读 `前端.md`，不要两份一起读。
> **禁止把 `docs/design` 全量读入**——所有设计塞进一份大文档时，正是因为要一次读上千行才导致代码生成走偏。
> 模块文档内部只做单向引用（`见 01-通用机制.md §4.2`），不做内容复制，避免同一规则两处漂移。

**读文档的规矩**

1. 不要凭印象。任何「PRD 里说过…」都要能指到章节号（如 `§12.6`）。
2. 文档 + 代码冲突：先报告，不擅自改文档迁就实现。
3. 需要新增规则：先问人，拍板后写进 PRD 再实现——**不允许规则只存在对话里**。

**写文档的规矩**

1. **只写最新结论**：文档是当前状态的快照，不是生成过程。**不设**「版本变更 / 修订记录 / 更新日志」小节，不写「v0.3 改成 X，v0.8 又改回 Y」——旧结论直接删掉，不留痕。
2. **版本号只在文件头出现一次**（版本 + 日期）。正文与跨文档引用只写 `文件名 + 章节号`，不带 `v0.x`；不要在别处写「已在 PRD v0.8 回写」这类话。
3. **同一规则只定义一次**，别处单向引用（`见 §12.6`），不复制内容。
4. **决策理由留一行**：「为什么这么定」写进决策表的一列即可，不展开成「曾考虑 A 又放弃 B」的叙事。

---

## 4. 协同开发流程（本项目强制执行）

本项目是**两层流程**，不是二选一：

| 层 | 负责什么 | 产物落地 |
|---|---|---|
| **规格与验收（主线）** | OpenSpec：设计 → 开发 → 验收的强制依赖链 | `openspec/` |
| **角色分工与门控（执行）** | `multi-agent-dev-workflow`：6 阶段 / 8 角色 / 4 道门控 | `.workbuddy/runs/<task_id>/` |

规格层决定"做什么、算不算做完"；执行层决定"谁来做、怎么交接"。冲突时以规格层为准。

### 4.0 OpenSpec 主线（设计 → 开发 → 验收）

CLI 已装，项目已初始化（`openspec/config.yaml`，schema `spec-driven`，语言 zh-CN）。

**产物依赖链是强制的**，任何时候用 `openspec status --change <name>` 确认当前解锁到哪一步：

```
proposal → specs → design → tasks → apply → archive
（提议）   （规格）  （设计）  （任务）  （开发）  （验收归档）
```

| 阶段 | 做什么 | 取指令 | 出口 |
|---|---|---|---|
| 设计 | 写提案：Why / What Changes / Capabilities / Impact | `openspec instructions proposal --change <name>` | 完成才解锁 specs 与 design |
| 设计 | 写能力规格：`### Requirement` + `#### Scenario`（WHEN/THEN），用 SHALL/MUST | `openspec instructions specs --change <name>` | 每个 capability 一个 `specs/<path>/spec.md` |
| 设计 | 写技术方案：Context / Goals / Decisions / Risks | `openspec instructions design --change <name>` | specs + design 都完成才解锁 tasks |
| 开发 | 严格按 tasks 逐项实施，完成后把 `- [ ]` 改 `- [x]` | `openspec instructions apply --change <name>` | **只允许做 tasks 里列的事** |
| 验收 | 严格校验 → 归档，主 specs 落 `openspec/specs/` | `openspec validate <name> --strict` → `openspec archive <name>` | 归档前 tasks 必须全勾 |

**当前变更**：`bootstrap-mvp` —— MVP 全量，9 个 capability，4/4 规划产物已完成，**尚未开始编码**。

**OpenSpec 硬规则**

1. **不允许跳过 specs 直接写代码**：没有 spec 描述的行为，不实现。
2. **不允许 `--no-validate`**；**不允许 `--skip-specs`**（本项目每次变更都在改行为）。
3. **不允许提前 archive**：tasks 未全部勾选时留在 change 里，不归档。
4. **新增需求开新 change**（`openspec new change <name>`），不在已归档的 spec 上手改。
5. **冲突处理**：specs 与 PRD 不一致 → 先报告，拍板后回写 PRD，再改 specs。

### 4.1 多智能体执行层（原 6 阶段流程）

以下为多 Agent 分工方式，与上面 OpenSpec 阶段的对应关系见 4.4。

采用 `multi-agent-dev-workflow` 多智能体协同流程：**6 阶段 / 8 角色 / 4 道门控**。
流程定义在本地 skill 目录：

```
D:/workspace/private/collect/vue-plugins-components-collect/knowledge/multi-agent-dev-workflow/
├── SKILL.md                  流程总纲（先读这个）
├── agents/00~08-*.md         8 个角色说明书
├── checklists/gates.md       门控出口标准
├── schemas/handoff.schema.json   Agent 间交接结构
└── templates/*.md            各阶段产出模板
```

### 4.2 六阶段

| Phase | 名称 | 并行性 | 角色 | 产出 | 门控 |
|---|---|---|---|---|---|
| 1 | 需求澄清 | 串行多轮 | 01 分析师 / 02 挑战者 | `requirement-spec.md` | GATE-1 人确认 |
| 2 | 契约对齐 | 并行→汇聚 | 03 前端 / 04 后端 / 05 协调 | `interface-contract.md`（LOCKED） | 契约零冲突 |
| 3 | 详细设计 | 并行 | 03 / 04 | `frontend-design.md` / `backend-design.md` | 符合契约 |
| 4 | 设计评审 | 串行 | 06 评审官 | `design-review-report.md` | GATE-2 人确认 |
| 5 | 开发实现 | 并行→联调 | 07 开发 | 代码 + 契约测试 + 联调对拍 | GATE-3 自测通过 |
| 6 | 验收自测 | 分层串行 | 08a 契约层 → **08c 契约守门** → 08b UI 层 | `acceptance-report.md` | GATE-4 人确认 |

### 4.3 产物落地

所有流程文档写到 `.workbuddy/runs/<task_id>/`，**不要**散落在项目根目录：

```
.workbuddy/runs/<task_id>/
├── state.json                运行唯一真相源（断点恢复读它）
├── handoff/*.json            Agent 间交接
├── requirement-spec.md
├── interface-contract.md
├── frontend-design.md / backend-design.md
├── design-review-report.md
└── acceptance-report.md
```

### 4.4 档位选择

| 档 | 判据 | 流程 |
|---|---|---|
| **S** | 单模块、≤1 个新接口、无数据模型变更、AC ≤ 3 | 跳过 02；GATE-1/2 合并 |
| **M** | 跨 1–2 模块、接口 ≤ 5、简单数据变更、AC ≤ 10 | 完整流程，02 可只跑一轮 |
| **L** | 跨 ≥ 3 模块、接口 > 5、数据模型不可逆、AC > 10 | 一道门不少 |

**本项目默认档位**

- 搭骨架（工程 + 12 张表 + 登录/约定接口）→ **M**
- MVP 全量（约定闭环 + 菜单库 + 爱心值 + 消息 + 历史）→ **L**
- 拿不准按 M 跑；**契约先行与假设可验证，任何档位都不跳过**。

### 4.5 本项目各角色的上下文预算

Agent 只读自己 `context_budget` 里的东西，**编排者不得转发全量历史**。

| 角色 | 必读 | 不必读 |
|---|---|---|
| 01 需求分析师 | PRD §1–§5、§9、§12 | 技术文档、原型代码 |
| 02 挑战者 | PRD 全文（重点 §12 / §13） | 技术文档 |
| 03 前端架构师 | PRD §4、§5、§12 + 原型 + 契约 | 后端设计、SQL |
| 04 后端架构师 | PRD §5–§7、§13 + 配套文档 §2–§5 | 原型 HTML 细节 |
| 05 契约协调 | 双方契约诉求（只这一份） | 各阶段设计稿 |
| 06 评审官 | 契约 + 双端设计 + rubric | 需求原始讨论 |
| 07 开发 | **LOCKED 契约** + 自己那侧的设计文档 | 对侧设计细节 |
| 08 验收 | 契约 + AC + 可运行产物 | 设计推演过程 |
| 08c 契约守门 | LOCKED 契约（`后端.md §2`） + `前端.md` 的页面→接口映射 + `src/types/api.ts` + 真实响应 | 实现代码 |

---

### 4.6 两层流程的对应关系

| OpenSpec | 多 Agent 阶段 | 对应说明 |
|---|---|---|
| `proposal` | Phase 1 需求澄清 | proposal 是需求收敛结果，其中 Capabilities 一节即契约入口 |
| `specs` | Phase 2 契约对齐 | 能力规格即行为契约；每个 `Scenario` 就是一条验收用例 |
| `design` | Phase 3 详细设计 + Phase 4 评审 | 前后端设计结论与评审意见收敛进 design.md 的 Decisions / Risks |
| `tasks` | Phase 5 的任务拆分 | 任务组即工作拆分，颗粒度到可勾选 |
| `apply` | Phase 5 开发实现 | 只做 tasks 里列出的事 |
| `validate` + `archive` | Phase 6 验收自测 | 验收结论作为归档依据，归档后行为契约落 `openspec/specs/` |

**档位与流程的取舍（避免小改动也拉满 8 个角色）**

- **S 档**：只走 OpenSpec（`proposal` + `specs` + `tasks` → apply → archive），不启用多 Agent 分工。
- **M / L 档**：启用多 Agent 分工，但所有阶段的结论 MUST 收敛回 OpenSpec 四件套，不允许只在 `.workbuddy/runs/` 里存在。

### 4.7 契约守门（08c）：08a 与 08b 之间必须补的一层

**为什么有这一层**：08a 只看后端接口自身对不对，08b 只看界面行为与状态渲染，**两者都不管「前端读的字段名是否等于后端返回的字段名」**。这类缺口在两端各自的自测里都是绿的，到真机上是静默空白——列表永远为空、爱心值永远 0、控制台不报错。本项目踩过一次：相册页读 `resp.list` / `resp.cursor`，后端实际返回 `months[]` / `nextCursor` / `hasMore`，接口 200、页面空白。

**08c 判定不合规 → 直接记 BLOCKER，不进入 08b。**

**机械部分**（脚本，随 `bash scripts/verify.sh` 自动跑）

| 检查 | 判据 | 失败含义 |
|---|---|---|
| 前端调用 → 后端路由 | `src/` 里每个 `/api/...` 都有同形状后端路由 | 必然 404 |
| 后端路由 → 前端调用 | 只提示，不判失败 | 微信回调 / 预留 / 真没接 |
| `pages.json` ↔ 页面文件 | 双向一一对应 | 注册了不存在的页 / 新页忘注册 → 白屏 |

动态尾段（如 `/api/plan/${id}/${action}`）脚本无法静态确认，会打 `!` 提示，**必须人工核对**。

**手工部分**（脚本查不出，结论必须贴进验收记录）

1. 用 dev JWT 打一次真实接口，把**响应顶层 key 原样抄成一行**放进报告。
2. 打开该页面的 `前端.md`「页面 → 接口」映射与 `src/types/api.ts`，逐字段对照：前端写 `resp.x` 的每个 x 必须在响应里存在。
3. 列表类响应必须确认真实容器名（`list` / `items` / `months` / `cards` 各家不同），**不许按「一般是 list」写代码**。
4. 嵌套结构至少验两层（如 `months[].cards[].images[]`）。

> 判据一句话：**接口返回的每个字段，前端要么用到、要么明确忽略；前端读的每个字段，接口必须真的返回。**

---

## 5. 硬规则（违反即返工）

1. **契约先行**：接口契约的唯一落点是 `docs/design/NN-*/后端.md §2`（入参 / 出参 / 错误码只在那里定义），前端的 `前端.md` 只写「页面 → 接口」映射。
   M / L 档可在 `.workbuddy/runs/` 下先出 `interface-contract.md` 做双方对齐，但**结论必须回写 `后端.md §2`**，只存在于 runs 里的契约视为未锁定。
2. **改契约走 CCR**：任何一方发现契约不合理 → 提 `contract-change-request` 给 05 裁决，**不得私自改**。
3. **假设必须给路径**：写「复用既有 X / 依赖已有 Y」必须附代码库路径；给不出就标 `UNVERIFIED`，**不得进入下游文档**。
   > 本项目当前没有任何业务代码，因此**一切「复用既有」默认都是 UNVERIFIED**，需自行新建。
4. **REJECTED 必须可执行**：「这设计不好」不是理由；「P2 接口缺分页，列表超 1000 条会 OOM」才是。
5. **开发不改契约、验收不修 bug**。修验一体必然掩盖问题。
6. **缺陷先判根因再回流**：契约问题→Phase 2、设计问题→Phase 3、需求问题→Phase 1、实现偏离→Phase 5。
7. **门控只有人能点头**：GATE-1 / GATE-2 / GATE-4 必须停等用户确认，工具不得代签。
8. **两个架构师不许直接对话**：走 05 协调者，避免发散和无仲裁记录。
9. **没有 spec 不写代码**：任何行为改动 MUST 先有对应的 `openspec/changes/<name>/specs/` 描述；直接照 PRD 或对话记忆动手视为违规。
10. **不做 tasks 之外的事**：apply 阶段只勾 tasks 里的条目；发现的额外问题**只汇报**，等点头再开新 change。

---

## 6. 本项目已被拍板的红线

> 来自用户拍板，**Agent 无权自行放宽**。要改先问人。

| # | 红线 | 出处 |
|---|---|---|
| 1 | 仅工作日（周一–周五）午饭；周末不自动排班 | PRD §2 |
| 2 | 不做代投喂 / 代买 / 外卖，只限亲手带饭 | PRD §2、§9 Q4 |
| 3 | 忌口**只提醒不硬拦**（含过敏，仍可由人提交） | PRD §9 Q2 |
| 4 | 爱心值只做正向累计，**不抵扣、不欠债** | PRD §5.4 |
| 5 | 解绑为逻辑删除，历史记录保留 | PRD §9 Q1 |
| 6 | 后端单体 Spring Boot 3.4 + MyBatis-Plus + MySQL 8；**不引 MQ / xxl-job**，定时任务用 `@Scheduled` | PRD §2、§12.6 |
| 7 | 小程序类目选「工具 / 生活服务」，**不可选「餐饮-外卖」**（否则要食品经营许可证） | PRD §13 P0-2 |
| 8 | `meal_plan.status` 枚举固定为 `0待认领 1待接收 2备餐中 3已交付 4已完成 5已取消 6已过期` | `db/schema.sql` |
| 9 | 打卡：**仅接收方**可打、一约定一次；照片必填 1–3 张；三档评价不用五星；**不额外加爱心值**；只可改不可删 | PRD §9 Q6、§5.7 |
| 10 | UGC 图片必须接 `mediaCheckAsync` 内容检测，未过审不展示；上传须重编码压 ≤1MB 并**剥离 EXIF** | PRD §13 P0-8 |

---

## 7. 技术约定（工程建立后生效）

> 骨架尚未创建，以下为**已定方向**，实现时不得偏离；如需调整走 CCR。

- **包名**：`com.couplemeal`
- **分层**：`common / config / controller / service / domain / mapper / entity / job`（不设 `dto` 包，请求/响应对象就近放各层；详见 `docs/design/后端-00-架构.md §2`）
- **接口前缀**：`/api`
- **统一返回**：`{ "code": 0, "msg": "ok", "data": ... }`，错误码表见配套文档 §2.3
- **鉴权**：`uni.login` → code 换 openid → 自签 token → 请求头 `Authorization` → 拦截器注入当前用户与 `couple_id`
- **越权**：每个接口必须校验 `plan.couple_id == 当前用户 couple_id`（PRD §13 P0-5）
- **字符集**：建库建表 `utf8mb4`（emoji 不能炸）
- **主键**：`id BIGINT` + **53bit 雪花 ID，禁自增**；公共字段只有 `create_time` / `update_time`；布尔字段用 `_flag` / `_status` 后缀，**禁 `is_` 开头**（详见 `docs/design/00-全局约定.md §2.1`）
- **幂等**：`love_value` 的 `uk_love_value_plan_user (plan_id, user_id)` 唯一索引

---

## 8. 提交规范

- 提交信息用中文，格式 `类型: 说明`，类型取 `feat / fix / docs / refactor / chore`
- 一次提交只做一件事；**不要**把文档改动和代码改动混在一个 commit
- 不提交构建产物、`node_modules`、本机路径配置

---

## 9. 下一步

代码侧已无待写功能，剩下的都是**需要外部条件才能推进**的事：

| 顺序 | 任务 | 卡点 |
|---|---|---|
| 1 | 真机联调：登录换 openid、打卡拍照上传、订阅授权弹窗 | `manifest.json` 的 appid 仍是 `touristappid`，且 `AuthServiceImpl.login()` 无 dev 兜底 → 游客身份登录不了 |
| 2 | 公众平台配置订阅消息模板 id | 需真实 appid（未配置时前端优雅跳过，不报错） |
| 3 | 打通 `mediaCheckAsync` 审核回调 | 需外网可达的 HTTPS 域名并备案 |
| 4 | 上线前检查 | 按 `docs/部署与运维.md` 的检查清单走 |

> 开新功能前先跑 `bash scripts/verify.sh` 确认基线全绿；改行为必须先开 OpenSpec change（§4.0 硬规则 9）。

---

## 10. 本地跑测（唯一机械判据）

```bash
bash scripts/verify.sh                 # 全量：构建 → 起后端 → 两组 itest → 契约对拍 → 前端类型检查 + 构建
bash scripts/verify.sh --no-build      # 复用已有 jar
bash scripts/verify.sh --no-frontend   # 只验后端
bash scripts/verify.sh --only=itest_rps
bash scripts/verify.sh --reuse         # 8080 已有服务则复用（默认拒绝）
```

日志落在 `backend/target/verify/`：`backend.log` / `backend-b.log` / `itest*.log` / `contract.log` / `type-check.log` / `build-fe.log`。

**为什么分两组起两次后端**：`itest_remind.sh` 与 `itest_audit.sh` 验的是定时任务，必须把 cron 调成每 5 秒、审核超时压到 1 分钟，否则要等到 11:00 / 17:00 / 次日 03:50。脚本自动带这些参数重启，不用手工准备。

### 10.1 为什么必须用脚本，不能手动一条条跑

| 坑 | 现象 | 脚本怎么处理 |
|---|---|---|
| 端口被注入 | JVM 环境把 `server.port` 注入成 `57230`，`java -jar` 绑错端口，测试全 502 | 强制 `--server.port=8080` |
| 进程被回收 | 后台起的后端在本轮 bash 结束即消失，下一轮 curl 全是「积极拒绝」 | 起服务 / 等健康 / 跑测在同一进程内一次完成 |
| 残留旧进程 | 8080 上还跑着旧 class，新代码不生效，整轮假失败 | 检测到端口在用就**拒绝**，必须显式 `--reuse` |
| 长命令被截断 | 前台长命令被 SIGTERM，输出全丢 | 各步输出落日志，由脚本汇总 |
| 用例依赖日期/时段 | 打卡用例只在 10:00–次日 02:00 能跑；「超 7 天」「明天」这类样本日期撞上周末就必挂 | 样本日期改为「往后找第一个工作日」；窗口外用例自己 SKIP，**不再中断整轮** |

### 10.2 结论怎么说话（重要）

脚本能证明的只有两层，**不要讲成三层**：

| 层 | 由谁证明 | 可以说什么 |
|---|---|---|
| 后端接口行为 | `itest/*.sh` | 「接口行为符合 specs」 |
| 前端可编译 | `vue-tsc` + `build:mp-weixin` | 「类型与构建通过」 |
| 前端运行 / 真机 | **只由开发者工具或真机证明** | 没跑过就只能说「未验证」 |

**禁止**在只跑过 `verify.sh` 的情况下写「已全部联调通过」。正确写法：

> 后端 6 组 itest 全绿，前端 type-check 与构建通过，契约对拍无不一致；**真机未验证**（登录换 openid / 打卡拍照上传 / 订阅授权弹窗 / 审核回调）。

### 10.3 失败定位

| 失败项 | 先看 |
|---|---|
| 环境预检 | MySQL 是否在跑、`couple_meal` 库是否已用 `db/schema.sql` 建好 |
| 启动后端 | `backend/target/verify/backend.log` 尾部（连接拒 = 库没起） |
| 单个 itest | 对应日志里的 `FAIL:` 行；`无统计输出` = 脚本自身报错，看尾部 `unbound variable` 一类 |
| 契约对拍 | `contract.log` 的 `✗` 行（`!` 只是提示，需人工判断） |
| 前端 | `type-check.log` / `build-fe.log` |
