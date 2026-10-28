// 前端-00-基础设施.md §5.4 —— 全局路由门控（一处配置，页面不各写一遍）
import { getToken } from './auth'

// 无需登录即可访问的页：登录页、配对页、冷启动过渡页。
// 1002（已解绑/无关系）的统一跳转在 request.ts 内处理（reLaunch 到 /pages/pair/index）。
const WHITE_LIST = ['/pages/login/index', '/pages/pair/index', '/pages/launch/index']

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
