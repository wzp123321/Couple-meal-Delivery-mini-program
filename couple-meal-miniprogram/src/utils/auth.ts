import type { User } from '@/stores/app'

// 前端-00-基础设施.md §5.1 / F8：登录态落盘，schemaVersion 变更即失效（换账号清旧缓存）
const SCHEMA_VERSION = 1
const TOKEN_KEY = 'cm:token'
const USER_KEY = 'cm:user'
const SCHEMA_KEY = 'cm:schemaVersion'

export function getToken(): string {
  // schemaVersion 不一致（升级 / 换账号）直接清掉旧登录态
  if (uni.getStorageSync(SCHEMA_KEY) !== SCHEMA_VERSION) {
    clearToken()
    return ''
  }
  return uni.getStorageSync(TOKEN_KEY) || ''
}

export function setToken(token: string, user?: User): void {
  uni.setStorageSync(SCHEMA_KEY, SCHEMA_VERSION)
  uni.setStorageSync(TOKEN_KEY, token)
  if (user) uni.setStorageSync(USER_KEY, user)
}

export function getPersistedUser(): User | null {
  return uni.getStorageSync(USER_KEY) || null
}

export function clearToken(): void {
  uni.removeStorageSync(TOKEN_KEY)
  uni.removeStorageSync(USER_KEY)
}

/**
 * 静默重登（供 request.ts 的 1001 调用）。
 * 走原生 uni.login + uni.request，避免与 request.ts 形成循环依赖。
 * 重登接口 = 后端 `POST /api/auth/login`（见 10-关系绑定/后端.md §2 / §3.1），
 * 带最新 code + agreedPrivacy:true（用户此前已授权，重登直接带 true，防后端再判 1001）。
 */
export function relogin(): Promise<void> {
  return new Promise((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: (res) => {
        uni.request({
          url: import.meta.env.VITE_API_BASE + '/api/auth/login',
          method: 'POST',
          data: { code: res.code, agreedPrivacy: true },
          success: (r) => {
            const body = r.data as {
              code: number
              data?: { token: string; userId: number; nickname?: string; avatar?: string }
            }
            if (body.code === 0 && body.data?.token) {
              setToken(body.data.token, {
                userId: body.data.userId,
                nickname: body.data.nickname || '',
                avatar: body.data.avatar || '',
              })
              resolve()
            } else {
              clearToken()
              reject(body)
            }
          },
          fail: (e) => {
            clearToken()
            reject(e)
          },
        })
      },
      fail: (e) => {
        clearToken()
        reject(e)
      },
    })
  })
}
