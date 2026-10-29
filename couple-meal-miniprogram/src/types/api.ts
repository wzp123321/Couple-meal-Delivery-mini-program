// 全量接口契约类型（前端地基）。
// 唯一真相源：docs/design/{10,20,30,40,50,60,70,80,90}-*/后端.md 的「§2 接口契约」，
// 枚举取值以 db/schema.sql 的字段注释为准。此处只做类型映射，不重复定义业务规则。
// 命名约定：请求 XxxReq，响应 XxxResp / 列表元素 XxxItem，分页 PageResult<T>，统一返回体 R<T>。

/* ============================================================
 * 0. 通用结构
 * ========================================================== */

/** 后端统一返回体：{ code, msg, data }。request 层已剥掉它，页面一般用不到 */
export interface R<T> {
  code: number
  msg?: string
  data?: T
}

/** 分页查询入参（msg/list、plan/history） */
export interface PageQuery {
  pageNo: number
  pageSize: number
}

/** 分页结果（msg/list、plan/history）。hasMore 供触底加载判断 */
export interface PageResult<T> {
  list: T[]
  total: number
  pageNo: number
  pageSize: number
  hasMore: boolean
}

/* ============================================================
 * 1. 枚举与字典（取值对齐 db/schema.sql 字段注释）
 * ========================================================== */

/** 餐别：1 午饭 / 2 晚饭（MVP 只发午饭） */
export type MealType = 1 | 2

/** 约定来源：0 点餐（我点你带）/ 1 主动带（我带给你） */
export type PlanSource = 0 | 1

/** 约定状态：0 待认领 1 待接收 2 备餐中 3 已交付 4 已完成 5 已取消 6 已过期 */
export type PlanStatus = 0 | 1 | 2 | 3 | 4 | 5 | 6

/** 我在约定里的角色，由后端按 orderer_id == me 计算后下发，前端不自己比 userId */
export type PlanRole = 'orderer' | 'carrier'

/**
 * 详情页可执行动作。后端 PlanStateMachine 按 (status, 我的角色) 算好下发，
 * 前端只按它决定按钮显隐——这是前后端唯一的状态判定来源（20-约定/后端.md §3.8）。
 */
export type PlanAction =
  | 'claim'
  | 'withdraw'
  | 'confirm'
  | 'reject'
  | 'deliver'
  | 'complete'
  | 'cancel'
  | 'reassign'
  | 'checkin'
  | 'shopping'

/** 忌口级别：0 无 / 1 少吃 / 2 不吃 / 3 过敏（只提醒不硬拦） */
export type AvoidLevel = 0 | 1 | 2 | 3

/** 菜品分类：1 荤 / 2 素 / 3 汤 / 4 主食 */
export type MenuCategory = 1 | 2 | 3 | 4

/** 打卡评价三档：1 还差点意思 / 2 好吃 / 3 香（不用五星） */
export type CheckinRating = 1 | 2 | 3

/** 通知类型：1 新约定 2 被认领 3 已送达 4 已完成 5 每日提醒 6 已打卡 */
export type MsgType = 1 | 2 | 3 | 4 | 5 | 6

/** 菜品列表排序：name 默认（创建时间倒序）/ recent 最近点过 */
export type MenuSort = 'name' | 'recent'

/** 报告范围：month 月报 / year 年报 */
export type ReportScope = 'month' | 'year'

/** 布尔开关（后端 tinyint）：0 否 / 1 是 */
export type Flag = 0 | 1

/* ============================================================
 * 2. auth —— 10-关系绑定/后端.md §2
 * ========================================================== */

/** POST /api/auth/login 入参（该接口免鉴权） */
export interface LoginReq {
  code: string
  agreedPrivacy: boolean
}

/** POST /api/auth/login —— 登录成功返回 */
export interface LoginResp {
  token: string
  userId: number
  nickname: string
  avatar: string // 相对路径，展示需经 utils/asset.resolveAsset
  hasCouple: boolean
}

/* ============================================================
 * 3. couple —— 10-关系绑定/后端.md §2
 * ========================================================== */

/** POST /api/couple/invite —— 生成邀请码（无入参） */
export interface InviteResp {
  code: string // 6 位纯数字
  expireAt: string // ISO 时间字符串，用于前端倒计时
}

/** POST /api/couple/join 入参 */
export interface JoinReq {
  code: string
}

/** POST /api/couple/join —— 输码加入 */
export interface JoinResp {
  coupleId: number
  partner: {
    nickname: string
    avatar: string // 相对路径
  }
}

/**
 * GET /api/couple/info —— 关系主页数据。
 * 双方头像/昵称为微信昵称；nicknameMine = 我对对方的专属称呼，nicknamePartner = 对方对我的专属称呼。
 * 展示时优先用专属称呼替代微信昵称。
 */
export interface CoupleInfo {
  coupleId: number
  myAvatar: string
  myNickname: string
  partnerAvatar: string
  partnerNickname: string
  nicknameMine: string
  nicknamePartner: string
  togetherDays: number // 在一起天数：纪念日优先，否则绑定日
  mealDays: number // 一起吃饭天数：打卡去重，与 togetherDays 是两个独立指标
  myLove: number
  partnerLove: number
  anniversary: string | null
  theme: string
  remindEnabled: boolean
}

/** GET /api/couple/info 的响应别名，与命名约定对齐 */
export type CoupleInfoResp = CoupleInfo

/** POST /api/couple/settings —— 关系级设置入参（全部可选，专属称呼走这里而非 user/profile） */
export interface CoupleSettingsReq {
  anniversary?: string | null
  theme?: string
  remindEnabled?: boolean
  nicknameMine?: string
}

/** POST /api/couple/unbind —— 无入参无出参 */

/* ============================================================
 * 4. user —— 10-关系绑定/后端.md §2
 * ========================================================== */

/** POST /api/user/avatar —— 头像上传返回（multipart 单张，name=file） */
export interface AvatarUploadResp {
  avatar: string
}

/** POST /api/user/profile 入参（nickname / avatar 至少传一个） */
export interface ProfileReq {
  nickname?: string
  avatar?: string
}

/** POST /api/user/profile —— 资料保存返回 */
export interface ProfileResp {
  nickname: string
  avatar: string
}

/** POST /api/user/delete —— 注销账号，无入参无出参 */

/* ============================================================
 * 5. plan —— 20-约定/后端.md §2
 * ========================================================== */

/** 约定里的单道菜（入参）：menuItemId 为空表示手输菜名 */
export interface PlanItemInput {
  menuItemId?: number
  name: string
}

/** POST /api/plan/create 入参 */
export interface PlanCreateReq {
  mealType: MealType
  source: PlanSource
  planDate: string // yyyy-MM-dd，必须是工作日且在 [today, today+7]
  items: PlanItemInput[] // 1–10 道
  remark?: string // ≤100 字
}

/** 命中忌口的强提醒项（只提醒不硬拦，提交后由前端弹 L3 确认） */
export interface PlanCreateWarning {
  name: string
  avoidLevel: AvoidLevel
}

/** POST /api/plan/create 出参 */
export interface PlanCreateResp {
  planId: number
  warnings: PlanCreateWarning[]
}

/** 约定菜品快照（出参）：菜单改名/删除不影响历史，这里存的是当时的菜名 */
export interface PlanItem {
  id: number
  menuItemId: number | null
  name: string
  seq: number // 1–10
}

/** 约定内的打卡信息（详情页打卡区消费；未打卡时为 null） */
export interface PlanCheckin {
  checkinId: number
  rating: CheckinRating | null
  content: string | null // 点评 ≤100 字
  reply: string | null // 带饭方回应 ≤50 字
  images: CheckinImage[]
}

/** GET /api/plan/{id} 出参 */
export interface PlanDetail {
  id: number
  ordererId: number
  carrierId: number | null
  ordererNickname: string
  carrierNickname: string | null
  mealType: MealType
  source: PlanSource
  status: PlanStatus
  planDate: string
  remark: string | null
  deliverLocation: string | null
  deliverNote: string | null
  claimedTime: string | null
  deliveredTime: string | null
  completedTime: string | null
  cancelledTime: string | null
  items: PlanItem[]
  /** 我在本约定里的角色，前端据此决定文案「我点的」/「我带的」 */
  role: PlanRole
  /** 当前用户可执行动作，按钮显隐的唯一来源 */
  actions: PlanAction[]
  checkin: PlanCheckin | null
}

/** 列表/周历里的约定卡片（精简，不含 actions） */
export interface PlanBrief {
  id: number
  planDate: string
  mealType: MealType
  source: PlanSource
  status: PlanStatus
  role: PlanRole
  items: string[] // 菜名快照
  ordererNickname: string
  carrierNickname: string | null
  remark: string | null
  hasCheckin: boolean
}

/** GET /api/plan/today 出参 */
export interface PlanTodayResp {
  date: string // 服务端当天日期，前端不自己算
  isWorkday: boolean
  list: PlanBrief[]
}

/** GET /api/plan/week 入参（不传默认本周一） */
export interface PlanWeekReq {
  startDate?: string // yyyy-MM-dd
}

/** 周历单格里的约定（比 PlanBrief 更精简） */
export interface WeekPlanItem {
  id: number
  status: PlanStatus
  role: PlanRole
  items: string[]
}

/** 周历的一天：即使没有约定也要占位返回，前端才能画满 7 格 */
export interface WeekDayItem {
  date: string
  weekday: number // 1=周一 … 7=周日
  isWorkday: boolean
  isToday: boolean
  plans: WeekPlanItem[]
}

/** GET /api/plan/week 出参 */
export interface PlanWeekResp {
  weekStart: string
  days: WeekDayItem[]
}

/** GET /api/plan/history 入参 */
export interface PlanHistoryReq extends PageQuery {
  status?: PlanStatus[] // 0–6 多选，空数组等同不筛
  role?: PlanRole
}

/** 历史列表项（不含 actions，需要操作去详情页） */
export interface PlanHistoryItem {
  id: number
  planDate: string
  status: PlanStatus
  role: PlanRole
  items: string[]
  partnerNickname: string
  checkinRating: CheckinRating | null
  hasCheckin: boolean
}

/** GET /api/plan/history 出参 */
export type PlanHistoryResp = PageResult<PlanHistoryItem>

/** POST /api/plan/{id}/reject 入参 */
export interface PlanRejectReq {
  reason: string
}

/** POST /api/plan/{id}/deliver 入参 */
export interface PlanDeliverReq {
  deliverLocation: string
  deliverNote?: string // 便签留言 ≤80 字
}

/** POST /api/plan/{id}/cancel 入参（reason 必填，否则 2006） */
export interface PlanCancelReq {
  reason: string
}

/** POST /api/plan/{id}/reassign 入参（改「这顿饭谁带」，不改 orderer） */
export interface PlanReassignReq {
  carrierId: number
}

/** POST /api/plan/{id}/complete 出参（完成即 +1 爱心值，按 plan_id 幂等） */
export interface PlanCompleteResp {
  loveValue: number
}

/* ============================================================
 * 6. menu —— 30-菜单与偏好/后端.md §2
 * ========================================================== */

/** GET /api/menu/list 入参 */
export interface MenuListReq {
  category?: MenuCategory
  onlyFavorite?: boolean
  creatorId?: number
  sort?: MenuSort
}

/** 菜品 */
export interface MenuItem {
  id: number
  name: string
  category: MenuCategory
  /** 常点标记（库内 favorite_flag，UI 叫「常点」） */
  favoriteFlag: Flag
  avoidLevel: AvoidLevel
  creatorId: number
  creatorNickname: string
  note: string | null
  /** sort=recent 时才有：这道菜最近一次被点过的日期；从未点过为 null */
  lastOrdered?: string | null
}

/** GET /api/menu/list 出参 */
export interface MenuListResp {
  list: MenuItem[]
}

/** POST /api/menu、PUT /api/menu/{id} 入参（PUT 为全量覆盖） */
export interface MenuSaveReq {
  name: string // ≤20 字
  category: MenuCategory
  /** 入参用 isFavorite，出参用 favoriteFlag（后端口径如此，前端在两层各按各的） */
  isFavorite: boolean
  avoidLevel: AvoidLevel
  note?: string
}

/** POST /api/menu 出参 */
export interface MenuCreateResp {
  id: number
}

/** 口味画像里的分类计数 */
export interface MenuCategoryCount {
  category: MenuCategory
  count: number
}

/** 口味画像里的「最常点的菜」 */
export interface MenuTopDish {
  name: string
  count: number
}

/** GET /api/menu/profile 出参（只统计 status=4 已完成的约定） */
export interface MenuProfileResp {
  total: number
  categories: MenuCategoryCount[]
  topDishes: MenuTopDish[]
  /** 忌口清单，按 avoidLevel 降序（3 过敏置顶），前端不再重排 */
  avoidDishes: MenuItem[]
}

/** GET /api/menu/recommend 入参 */
export interface MenuRecommendReq {
  limit?: number // 默认 5
}

/** GET /api/menu/recommend 出参（已排除 avoidLevel >= 2） */
export interface MenuRecommendResp {
  list: MenuItem[]
}

/* ============================================================
 * 7. love —— 40-爱心值/后端.md §2
 * ========================================================== */

/** 爱心值流水 */
export interface LoveRecord {
  delta: number
  reason: string
  createdAt: string // 'yyyy-MM-dd HH:mm'
}

/** GET /api/love/summary 出参（与 couple/info 共用同一统计口径） */
export interface LoveSummaryResp {
  total: number
  mine: number
  partner: number
  recent: LoveRecord[]
}

/* ============================================================
 * 8. msg —— 50-通知/后端.md §2
 * ========================================================== */

/** GET /api/msg/list 入参 */
export type MsgListReq = PageQuery

/** 通知条目 */
export interface MsgItem {
  id: number
  type: MsgType
  title: string | null
  content: string | null
  /** 关联约定；每日提醒（type=5）为空 */
  planId: number | null
  readFlag: Flag // 0 未读 1 已读
  createTime: string
}

/** GET /api/msg/list 出参 */
export type MsgListResp = PageResult<MsgItem>

/** GET /api/msg/unread 出参 */
export interface MsgUnreadResp {
  unread: number
}

/** POST /api/msg/read —— 全部已读，无入参无出参（不做单条已读） */

/* ============================================================
 * 9. checkin —— 60-打卡/后端.md §2
 * ========================================================== */

/** POST /api/checkin/upload 出参（multipart 单张，name=file） */
export interface CheckinUploadResp {
  url: string // 原图相对路径，长边 1080，仅大图预览用
  thumbUrl: string // 缩略图相对路径，长边 320，列表/网格一律用它
}

/** POST /api/checkin/submit 入参 */
export interface CheckinSubmitReq {
  planId: number
  /** 1–2 个 upload 返回的 url（只传原图 url，不传 thumbUrl） */
  images: string[]
  rating: CheckinRating
  content?: string // 点评 ≤100 字
}

/** POST /api/checkin/submit 出参 */
export interface CheckinSubmitResp {
  checkinId: number
}

/** POST /api/checkin/update 入参（打卡当天内可改评价/点评） */
export interface CheckinUpdateReq {
  checkinId: number
  rating: CheckinRating
  content?: string
}

/** POST /api/checkin/reply 入参（带饭方回应） */
export interface CheckinReplyReq {
  checkinId: number
  reply: string // ≤50 字
}

/** 打卡图片（两档地址） */
export interface CheckinImage {
  url: string
  thumbUrl: string
}

/** 相册里的一条打卡（只含已过审图片；全部被屏蔽时 images 为空，卡片只显示文字） */
export interface GalleryItem {
  checkinId: number
  planId: number
  planDate: string
  rating: CheckinRating | null
  content: string | null
  reply: string | null
  images: CheckinImage[]
  authorNickname: string
  createTime: string
}

/** 相册按月分组 */
export interface GalleryGroup {
  month: string // 'yyyy-MM'，标题渲染成「2026 年 10 月」
  list: GalleryItem[]
}

/** GET /api/checkin/gallery 入参 */
export interface GalleryReq {
  cursor?: string // 上一页返回的游标，首页不传
  size?: number
}

/** GET /api/checkin/gallery 出参 */
export interface GalleryResp {
  list: GalleryGroup[]
  cursor: string | null // 为 null 表示没有下一页
  hasMore: boolean
}

/* ============================================================
 * 10. shopping —— 80-采购清单/后端.md §2
 * ========================================================== */

/** POST /api/shopping/generate 入参 */
export interface ShoppingGenerateReq {
  planId: number
  /** 不传则由约定菜品推导 */
  items?: string[]
}

/** 采购条目 */
export interface ShoppingItem {
  id: number
  planId: number
  itemName: string
  checkedFlag: Flag // 0 未买 1 已买
  checkedTime: string | null
  seq: number
}

/** POST /api/shopping/generate 出参 */
export interface ShoppingGenerateResp {
  planId: number
  list: ShoppingItem[]
}

/** GET /api/shopping/list 入参（不传 planId 取最近未买的一份） */
export interface ShoppingListReq {
  planId?: number
}

/** GET /api/shopping/list 出参 */
export interface ShoppingListResp {
  planId: number
  list: ShoppingItem[]
}

/** POST /api/shopping/check 入参 */
export interface ShoppingCheckReq {
  id: number
  checkedFlag: Flag
}

/* ============================================================
 * 11. report —— 70-历史/后端.md §2 / §3.4
 * ========================================================== */

/** GET /api/report 入参（anchor 缺省：月报取上一自然月，年报取当年） */
export interface ReportReq {
  scope: ReportScope
  anchor?: string // 月报 'yyyy-MM' / 年报 'yyyy'
}

/** 报告里双方各自带饭次数 */
export interface CarrierCount {
  me: number
  partner: number
}

/** GET /api/report 出参。统计口径统一为「已完成」(status=4)，取消与过期不计入 */
export interface ReportResp {
  scope: ReportScope
  anchor: string
  planCount: number
  carrierCount: CarrierCount
  topDishes: MenuTopDish[] // 出现最多的 3 道菜
  checkinDays: number // 有打卡的天数（同一天多次只算一天）
  loveValueDelta: number
  /** 最长连续带饭天数，仅年报返回（相邻两日只跳过周末仍算连续） */
  longestStreak?: number
}

/* ============================================================
 * 12. rps —— 石头剪刀布协商（phase-2，见 openspec/changes/phase-2/specs/rps-negotiation）
 * ========================================================== */

/** 出拳：1 石头 / 2 剪刀 / 3 布 */
export type RpsChoice = 1 | 2 | 3

/** 对局状态：0 进行中（待出拳）/ 1 已完成 */
export type RpsGameStatus = 0 | 1

/** 石头剪刀布对局视图（对齐后端 RpsService.RpsGameVO 字段） */
export interface RpsGameVO {
  gameId: number
  coupleId: number
  /** 约定日期 yyyy-MM-dd（仅工作日） */
  planDate: string
  mealType: MealType
  initiatorId: number
  status: RpsGameStatus
  /** 胜方 user_id，未完成时为 null */
  winnerId: number | null
  /** 采纳后生成的约定 id，未采纳为 null */
  planId: number | null
  /** 当前回合号（平局重出自增） */
  currentRound: number
  /** 当前回合「我」的出拳（未出为 null） */
  myChoice: RpsChoice | null
  /** 当前回合对方是否已出拳 */
  opponentThrown: boolean
}

/** POST /api/rps/create 入参 */
export interface RpsCreateReq {
  mealType: MealType
  /** yyyy-MM-dd */
  planDate: string
}

/** POST /api/rps/{gameId}/throw 入参 */
export interface RpsThrowReq {
  choice: RpsChoice
}

/** POST /api/rps/{gameId}/adopt 入参（菜品留空则由后端给默认占位名） */
export interface RpsAdoptReq {
  items?: { name: string }[]
  remark?: string
}

/** POST /api/rps/{gameId}/adopt 出参 */
export interface RpsAdoptResp {
  planId: number
}
