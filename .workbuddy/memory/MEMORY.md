# 情侣带饭点餐小程序 - 项目备忘录

## 定位
情侣间"约今天吃啥、谁带饭"的私密协作小程序。非外卖、非账本、非严肃AA。
一句话：不是外卖，是"我记得你爱吃什么，并愿意为你带饭"的专属记录仪。

## 已确认决策（2026-10-26 与用户拍板）
- 形态：纯点餐约定（约谁带、带啥），无严肃饭债互欠
- 货币：纯虚拟记账（轻量爱心值，正向激励，不碰真实钱，不做欠债）
- 后端：自建 Spring Boot 3.4 + MyBatis-Plus + MySQL 8（沿用备课系统栈）
- 前端：微信小程序原生 / uni-app
- 推送：系统内红点/角标（MVP） + **微信订阅消息（phase-2 已实现，模板未配置时优雅跳过）**
- 菜品图片：菜单库仍纯文字起步；**打卡照片是 MVP 功能**（两件事分开，2026-10-27）
- 时段：**午饭 + 晚饭**（phase-2 已支持；发起截止 午饭 11:00 / 晚饭 18:00，按餐段取配置）
- 场景：仅工作日（周一~周五）带午饭；周末不自动排班，需要可手动发起

## 核心突出点（差异化卖点）
A. 被记住的私密偏好库：忌口/爱吃自动沉淀+点餐拦截（外卖每次重选）
B. 投喂养成的情感数据：爱心值/连续投喂/在一起吃饭天数/年度带饭年报
C. 线下交付仪式感：门口交付标记+便签留言，非陌生人送餐
D. 协商式"今天谁带"：轮流制/小程序内石头剪刀布/协商，把扯皮变有趣
E. 情感化表达替代交易语言：投喂/想你/留了汤，而非下单/支付/配送
F. 看得见的日常（饭桌相册）：吃饭时拍照打卡+三档点评，串成时间线相册（2026-10-27 新增）

## 数据模型（草案）
- couple(user_a,user_b,status,created_at)
- user(openid,nickname,avatar)
- meal_plan(couple_id,orderer_id,carrier_id,meal_type,title,remark,status,plan_date,created_at)
- menu_item(couple_id,name,category,is_favorite,avoid_level,creator_id,note)
- love_value(couple_id,user_id,plan_id,delta,reason,created_at) — `(plan_id,user_id)` 复合唯一做幂等
- shopping_list / cancel_log(plan 唯一) / notification(receiver_id,is_read)
- meal_checkin(plan_id 唯一, user_id 打卡人, images≤3, audit_status, rating(1-3), content, reply, reply_by)

## 状态机（2026-10-27 定案 D15）
待认领 --claim--> **备餐中**（一步到位，无二次确认）；「待接收」**只属主动带**（source=1）；
主动带：创建即 status=1 → 确认→备餐中 / 婉拒→已取消。withdraw 仅在 source=0 的备餐中可用（退回待认领），
主动带回退等于 cancel。任意非终态可取消（记 cancel_log，不发爱心值）。爱心值按 plan_id 幂等+1。
status 枚举 0待认领 1待接收 2备餐中 3已交付 4已完成 5已取消 **6已过期**。
超时自动流转：次日 02:30 仍未交付→已过期归档（已交付不参与）；已交付超 24h 未领取→自动完成并照发爱心值（PRD §12.6）。
**石头剪刀布协商（谁带饭）已在 phase-2 实现**（输家带饭，采纳生成 source=1 约定，`rps_game`/`rps_round` 表）；**改期仍为二期**，尚未实现。

## 已确认规则（Q1-Q5，2026-10-26~27 拍板，详见 PRD §9）
- 解绑：逻辑删除 couple，记录保留（双方本地历史仍可看）
- 忌口：仅提醒不硬拦（不吃/过敏弹强提醒，仍可由人提交）
- 时段：MVP 仅午饭，午+晚二期
- 不做代投喂 / 代买 / 外卖：仅限情侣亲手带饭（2026-10-27 拍板）
- 超时（2026-10-27 拍板）：11:00 未认领催一次；已交付超 24h 未领取自动完成并照发爱心值；次日 02:30 仍未交付转已过期归档；工作日 18:00 后不可发起当日约定
- 打卡 Q6（2026-10-27 拍板）：仅接收方可打卡、一约定一次；照片必填 1–3 张；三档评价（🔥香/😋好吃/😐还差点意思）不用五星；**不额外加爱心值**；窗口=交付当天至次日 02:00

## 打卡（饭桌相册）关键约定
- 照片是本项目 **MVP 唯一图片场景**；本机磁盘 `/data/upload/yyyyMM/` + Nginx，域名须 HTTPS+备案
- **UGC 图片必须接 `mediaCheckAsync`**（审核硬门槛），未过审不展示；文本走 `msgSecCheck`
- 上传需重编码压 ≤1MB/长边 1080 + **剥离 EXIF**（防泄露住址）
- 打卡不删只改（删了记忆就断），真删走设置里的数据删除

## UI 风格（2026-10-26 选定）
- 温暖奶油风：奶油白底 #FFF9F2 + 蜜桃粉 #FFC9B0 + 暖橘 #FF9B6A 强调 + 深褐文字 #4A3F35
- 大圆角(22px)、柔和阴影、emoji 轻盈点缀；温情+高端，非模板化
- 高保真可交互原型：docs/ui-prototype-cream.html（18 屏 + 8 弹层，可直接点击体验）
- 交互契约以 PRD **§12 交互规范**为准（反馈层级 / 权限矩阵 / 红点已读 / 超时流转 / 文案调性）；风险合规见 **§13**

## 设计文档结构（2026-10-27 定稿，用户明确要求）
- `docs/design/` 按**需求**拆：9 个需求目录（`10-关系绑定/`…`90-周历/`），**每个目录内含 `后端.md` + `前端.md`**（原先一份合写是错的）
- 公共 4 份在根：`00-全局约定`、`01-通用机制`（跨端）、`02-前端基础设施`（前端公共）、`03-架构与包结构`（后端公共）
- **接口契约只在 `后端.md §2` 定义**；`前端.md` 只写「页面→接口」映射，不重复入参出参
- 后端固定顺序：数据表→接口契约→实现→定时任务→验收→易错点；前端：页面→接口→交互→状态→验收→易错点
- 引用写法：模块内 `../00-全局约定.md`、`../20-约定/后端.md §3.1`、同级 `前端.md`；不写 `NN/后端.md` 简写
- 旧的合写模块文档归档在 `docs/_legacy/merged/`（已 gitignore）

## 开发流程（2026-10-27 确立）
- **OpenSpec `spec-driven` 为主线**：proposal → specs → design → tasks → apply → archive（= 设计→开发→验收）
- CLI 1.14.1 已装；项目已 `openspec init`（`openspec/config.yaml`，schema spec-driven，语言 zh-CN）
- **已归档两个变更**：`bootstrap-mvp`（MVP 9 能力）与 `2026-10-29-phase-2`（晚市/石头剪刀布/订阅消息），均已 archive 提升为 specs 基线
- **`openspec/specs/` 现有 12 个能力**：couple-binding / meal-plan / menu-prefs / love-value / notifications / meal-checkin / plan-history / shopping-list / weekly-schedule（MVP）+ meal-type-evening / rps-negotiation / subscribe-message（phase-2）
- 验收全部通过：MVP 主 itest 50/0、晚市 6/6、石头剪刀布 25/0、订阅消息 4/0；前端 type-check 除 `privacy.ts` 预置 `wx` 错误外干净
- **两层分工**：OpenSpec 管规格与验收（`openspec/`）、duoagent 管多 Agent 分工与门控（`.workbuddy/runs/`）；**S 档只走 OpenSpec，M/L 档才拉 8 角色**，但产物必须收敛回 OpenSpec 四件套
- 硬规则：没 spec 不写代码 / 只做 tasks 内的事 / 不许 `--no-validate` 与 `--skip-specs` / 不提前 archive / 冲突以 specs 为准

### 本地跑 itest 的环境要点（2026-10-29 实测）
- **后端后台进程跨 Bash 调用会被沙箱回收**：`nohup java -jar &` 启动的进程在 Bash 调用结束即消失。跑 itest 必须**在同一个 Bash 调用内**完成「启动后端 → 等 `curl 8080/api/health`=200 → 跑 itest」。
- 长命令前台会被 SIGTERM 截断（主 itest、`openspec archive` 均遇到）：改用 Bash 工具 `run_in_background: true` 执行、输出写 `/tmp/*.log` 再读。
- **端口**：jar 默认被注入绑 57230（被占用则启动失败），必须 `java -jar ... --server.port=8080` 覆盖；**8080 是代理入口**，`curl 127.0.0.1:8080` 走 `http_proxy`（端口每次会变）能达后端，直连其他端口（如 8091）反而 502。
- `openspec archive <change> --yes` 用后台任务执行。

## 文档写作规范（2026-10-27 用户要求，已写入 AGENT.md §3）
- **只写最新结论**，不留版本变更史 / 修订记录；旧结论直接删
- 版本号只在文件头出现一次；正文与跨文档引用只写「文件名 + 章节号」
- 同一规则只定义一处，别处单向引用
- 已落地位置：`AGENT.md §3「写文档的规矩」`、`CLAUDE.md「工作方式」`
