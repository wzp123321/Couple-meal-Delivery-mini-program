// 前端-00-基础设施.md §5.4 —— 全局路由门控（一处配置，页面不各写一遍）
import { getToken } from './auth'

const WHITE_LIST = ['/pages/login/index', '/pages/pair/index']

export function setupGuard(): void {
  const check = (url: string): boolean => {
    if (getToken()) return true
    if (WHITE_LIST.some((w) => url.startsWith(w))) return true
    uni.reLaunch({ url: '/pages/login/index' })
    return false
  }

  ;(['navigateTo', 'redirectTo', 'reLaunch', 'switchTab'] as const).forEach((api) =>
    uni.addInterceptor(api, {
      invoke: (args) => check((args as { url: string }).url),
    }),
  )
}
