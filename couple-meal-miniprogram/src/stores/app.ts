// 前端-00-基础设施.md §5 —— 状态管理（F3：不引 Pinia，reactive 单模块）
import { reactive } from 'vue'
import { getToken, getPersistedUser } from '@/utils/auth'

export interface User {
  userId: number
  nickname: string
  avatar: string
}

export interface Relation {
  coupleId: number
  partnerNickname: string
  togetherDays: number
  myLove: number
  partnerLove: number
}

export const appState = reactive<{
  user: User | null
  relation: Relation | null
  unread: number
}>({
  user: null,
  relation: null,
  unread: 0,
})

// 三条约束（§5.2）：不要解构、不要整体替换（用 Object.assign / 直接赋值属性）、只有三类全局态。
export function setUser(u: User | null): void {
  appState.user = u
}

export function setRelation(r: Relation | null): void {
  appState.relation = r
}

export function setUnread(n: number): void {
  appState.unread = n
}

// 冷启动恢复登录态（token 在 auth 层带 schemaVersion 校验；关系信息不落盘，按 §5.1 每次重取）
export function restoreLogin(): void {
  if (!getToken()) return
  const u = getPersistedUser()
  if (u) appState.user = u
}

// 退出 / 解绑：关系与未读数不落盘，直接清空（§5.1）
export function logout(): void {
  appState.user = null
  appState.relation = null
  appState.unread = 0
}
