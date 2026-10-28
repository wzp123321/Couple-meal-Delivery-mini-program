// 冷启动静默恢复门控（10-关系绑定/前端.md §2 / 前端-00-基础设施.md §5.1）。
// 在 App.vue onLaunch 中调用：读 storage token → 有则 GET /api/couple/info 静默恢复，
// 有关系进 tabBar 首页（today）/ 1002 进配对页；无 token 进登录页。
import { request } from './request'
import { getToken } from './auth'
import { applyCoupleInfo } from '@/stores/app'
import type { CoupleInfo } from '@/types/api'

export function bootstrapApp(): void {
  if (!getToken()) {
    // 无登录态：进登录页（白名单，guard 不拦截）
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }

  // 有 token：静默恢复关系信息。
  // - code 0（有关系）→ 写全局态并 reLaunch 到 tabBar 首页
  // - 1002（已解绑/无关系）→ request.ts 已自动 reLaunch 到 /pages/pair/index，这里不再处理
  // - 1001 → request.ts 已静默重登并重放；重登失败再 reLaunch 登录页
  request<CoupleInfo>({ url: '/api/couple/info', auth: true })
    .then((info) => {
      applyCoupleInfo(info)
      uni.reLaunch({ url: '/pages/today/index' })
    })
    .catch((body) => {
      const code = (body as { code?: number } | undefined)?.code
      if (code === 1002) return // request.ts 已跳转配对页
      // 其余（网络/5001 等）：兜底回登录页，避免卡在启动页
      uni.reLaunch({ url: '/pages/login/index' })
    })
}
