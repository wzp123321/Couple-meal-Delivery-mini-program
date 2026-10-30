# CLAUDE.md

本项目为**情侣带饭点餐微信小程序**：固定 2 人、仅工作日午饭、一方点菜另一方带饭。不涉及外卖、不碰钱。

> 完整协作规则见 **[AGENT.md](./AGENT.md)**——多智能体流程、门控、硬规则、红线都在那里。本文件只放最常用的上下文。

## 先读什么

| 顺序 | 文件 | 用途 |
|---|---|---|
| 1 | `AGENT.md` | 流程与硬规则（必读） |
| 2 | `openspec/specs/` | **已归档的行为契约**（12 个能力：MVP 9 + phase-2 3）；改行为前先读对应能力 |
| 3 | `docs/产品需求文档.md` | 产品背景与细节（14 章） |
| 4 | `docs/design/README.md` | 设计导航：按任务查该读哪几份（**按模块读，禁止全量读**） |
| 5 | `docs/design/00-全局约定.md` + 该需求目录下**一份**文档 | 全局约定（D1–D16 / 分层 / 鉴权 / 错误码）+ `后端.md`（表·接口·实现）或 `前端.md`（页面·交互·状态） |
| 6 | `db/schema.sql` | 表结构（12 张，可直接执行；**字段级唯一真相源**） |
| 7 | `docs/开发前准备与详细设计.md` | 环境清单 / 接口路径总表（索引）/ 数据字典 / 上线审核 / 里程碑 |
| 8 | `docs/ui-prototype-cream.html` | 视觉与交互基线（18 屏 + 9 弹层） |
| 9 | `docs/部署与运维.md` | 服务器规格 / Nginx / systemd / 备份 / 上线检查清单（**仅部署排障时读**） |

**当前状态：MVP 与 phase-2 已全部落地并归档；机械层验收跑 `bash scripts/verify.sh`；真机联调未做（appid 仍是 `touristappid`）。**

> 开发流程走 **OpenSpec**（`spec-driven`）：`proposal → specs → design → tasks → apply → archive`。
> 没有 spec 描述的行为不实现；只做 tasks 里列出的事；归档前 tasks 必须全勾。

## 技术栈（已定）

- 前端：uni-app（Vue3 + Vite + TypeScript），编译目标仅微信小程序
- 后端：Spring Boot 3.4 + MyBatis-Plus + MySQL 8（单体，**不引 MQ / xxl-job**）
- 定时任务：`@Scheduled`
- 包名：`com.couplemeal`；接口前缀 `/api`；统一返回 `{code,msg,data}`

## 红线（不得自行放宽）

1. 仅工作日午饭；周末不自动排班
2. 不做代投喂 / 代买 / 外卖，只限亲手带饭
3. 忌口**只提醒不硬拦**（含过敏，仍可由人提交）
4. 爱心值只做正向累计，不抵扣、不欠债
5. 解绑为逻辑删除，历史保留
6. `meal_plan.status`：`0待认领 1待接收 2备餐中 3已交付 4已完成 5已取消 6已过期`
7. 小程序类目**不可选「餐饮-外卖」**（要食品经营许可证）
8. 打卡：仅接收方可打、一约定一次、照片必填 1–3 张、三档评价、**不加爱心值**、可改不可删
9. UGC 图片必须接 `mediaCheckAsync` 检测，未过审不展示；上传须压 ≤1MB 并剥离 EXIF

## 工作方式

- **最小改动**：只做被要求的那件事。发现范围外的问题（重复代码、潜在 bug、可优化点）**只汇报、不动手**，等人点头。
- **先读后改**：改任何文件前先读现状；不要凭印象断言「项目里已经有 X」——本项目还没有业务代码，一律按新建处理。
- **文档优先于代码**：规则冲突或需要新规则时，先写进 PRD 再实现，不允许规则只存在对话里。
- **文档只写最新结论**：不留版本变更史 / 修订记录，旧结论直接删；版本号只在文件头出现一次，正文引用别处只写「文件名 + 章节号」。
- **不擅自扩范围**：不新增未被要求的功能、不顺手重构、不替人做决定。
- **不确定就问**：歧义处停下来问，不要用「合理猜测」推进关键决策。
- **验收跑脚本，不手搓**：`bash scripts/verify.sh` 一条命令跑完后端构建 / 两组 itest / 契约对拍 / 前端类型检查与构建。结论按 AGENT.md §10.2 分层说，**别把「可编译」说成「已联调通过」**。

## 目录约定

```
openspec/                    OpenSpec 规格与变更（设计 → 开发 → 验收）
  ├── config.yaml            schema: spec-driven，语言 zh-CN
  ├── specs/                 已归档的行为契约，12 个能力（验收标准）
  └── changes/               进行中的变更（archive/ 下是历史归档）
      └── <name>/            proposal / specs / design / tasks 四件套
docs/                        产品与技术文档（交付物）
  ├── 产品需求文档.md         产品背景与细节
  ├── 开发前准备与详细设计.md
  ├── 部署与运维.md           从空服务器到上线 + 排障
  ├── design/                 设计文档：1 个需求 1 个目录，内含 后端.md / 前端.md
  └── ui-prototype-cream.html
backend/                     Spring Boot 单体（含 itest/ 集成自测脚本）
couple-meal-miniprogram/     uni-app 前端（编译目标 mp-weixin）
scripts/                     verify.sh（一键验收）+ contract-check.mjs（契约对拍）
db/schema.sql                建表脚本（字段级唯一真相源）
.workbuddy/
  ├── memory/               项目决策记忆（防漂移，非交付物）
  └── runs/<task_id>/       多智能体流程产物（handoff / 各阶段文档）
AGENT.md                    协作规则总纲
CLAUDE.md                   本文件（精简入口）
```

## 常用命令

```bash
# 一键验收：后端构建 → 起后端 → 两组 itest → 契约对拍 → 前端类型检查/构建
bash scripts/verify.sh
bash scripts/verify.sh --no-build --no-frontend   # 只跑后端用例，复用已有 jar

# 契约对拍单独跑（前端 /api 调用 ↔ 后端路由、pages.json ↔ 页面文件）
node scripts/contract-check.mjs

# 查看已归档的能力规格
openspec list --specs
openspec show <capability>

# 新变更：规划 → 取指令 → 校验 → 归档
openspec new change <name>
openspec instructions apply --change <name>
openspec validate <name> --strict      # 不许加 --no-validate
openspec archive <name> --yes          # tasks 全勾后才归档
```
