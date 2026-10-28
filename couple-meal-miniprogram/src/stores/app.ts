// 前端-00-基础设施.md §5 —— 状态管理（F3：不引 Pinia，reactive 单模块）
import { reactive } from 'vue'
import { getToken, getPersistedUser } from '@/utils/auth'
import type { CoupleInfo, LoginResp } from '@/types/api'

export interface User {
  userId: number
  nickname: string
  avatar: string
}

// 关系信息不落盘（前端-00-基础设施.md §5.1），每次冷启动 / 进「我的」页重取。
// 字段对齐 GET /api/couple/info 的全部出参，me / settings 直接消费。
export interface Relation {
  coupleId: number
  myAvatar: string
  myNickname: string
  partnerAvatar: string
  partnerNickname: string
  nicknameMine: string // 我对对方的专属称呼
  nicknamePartner: string // 对方对我的专属称呼
  togetherDays: number // 在一起天数
  mealDays: number // 一起吃饭天数（独立指标）
  myLove: number
  partnerLove: number
  anniversary: string | null
  theme: string
  remindEnabled: boolean
}

export const appState = reactive<{
  user: User | null
  relation: Relation | null
  unread: number
  /** 登录态 token：与 storage 同源，冷启动由 restoreLogin 灌入，供页面判断「是否已登录」 */
  token: string
  /** 是否有有效伴侣关系。login 接口直接给出，早于 couple/info 拉取，未获关系前页面靠它分流 */
  hasCouple: boolean
}>({
  user: null,
  relation: null,
  unread: 0,
  token: '',
  hasCouple: false,
})

// 三条约束（§5.2）：不要解构、不要整体替换（用 Object.assign / 直接赋值属性）、只有三类全局态。
export function setUser(u: User | null): void {
  appState.user = u
}

export function setRelation(r: Relation | null): void {
  appState.relation = r
  appState.hasCouple = !!r
}

export function setUnread(n: number): void {
  appState.unread = n
}

// —— 以下为地基补齐项：token / userId / hasCouple / coupleInfo 的读写入口 ——

/** 写 token（落盘在 utils/auth.setToken，这里只同步内存态，避免页面直接读 storage） */
export function setTokenState(token: string): void {
  appState.token = token
}

/** 显式标记「是否已绑定关系」。applyCoupleInfo 成功后会自动置 true，无需重复调用 */
export function setHasCouple(has: boolean): void {
  appState.hasCouple = has
}

/** 当前登录用户 id；未登录返回 0（页面判断用 `> 0` 即可） */
export function getUserId(): number {
  return appState.user?.userId ?? 0
}

/** 关系信息（coupleInfo 即 relation，两者是同一份数据，只是叫法不同） */
export function getCoupleInfo(): Relation | null {
  return appState.relation
}

/** setRelation 的同义入口，方便按契约名调用 */
export function setCoupleInfo(r: Relation | null): void {
  setRelation(r)
}

// 冷启动恢复登录态（token 在 auth 层带 schemaVersion 校验；关系信息不落盘，按 §5.1 每次重取）
export function restoreLogin(): void {
  const t = getToken()
  if (!t) return
  appState.token = t
  const u = getPersistedUser()
  if (u) appState.user = u
}

// 登录成功一次性写入内存态（落盘仍由 utils/auth.setToken 负责，两者必须成对调用）
export function applyLogin(resp: LoginResp): void {
  appState.token = resp.token
  appState.user = {
    userId: resp.userId,
    nickname: resp.nickname,
    avatar: resp.avatar,
  }
  appState.hasCouple = resp.hasCouple
}

// 把 GET /api/couple/info 的响应映射进全局关系态（§5.1 铁律：服务端状态不本地臆造，成功才写）
export function applyCoupleInfo(info: CoupleInfo): void {
  appState.relation = {
    coupleId: info.coupleId,
    myAvatar: info.myAvatar,
    myNickname: info.myNickname,
    partnerAvatar: info.partnerAvatar,
    partnerNickname: info.partnerNickname,
    nicknameMine: info.nicknameMine,
    nicknamePartner: info.nicknamePartner,
    togetherDays: info.togetherDays,
    mealDays: info.mealDays,
    myLove: info.myLove,
    partnerLove: info.partnerLove,
    anniversary: info.anniversary,
    theme: info.theme,
    remindEnabled: info.remindEnabled,
  }
  appState.hasCouple = true
}

// 退出 / 解绑：关系与未读数不落盘，直接清空（§5.1）
export function logout(): void {
  appState.user = null
  appState.relation = null
  appState.unread = 0
  appState.token = ''
  appState.hasCouple = false
}
