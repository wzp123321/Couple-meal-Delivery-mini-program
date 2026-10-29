// 微信订阅消息（fix-phase2-gaps / subscribe-message）。
// 分工：前端只负责「申请授权」；真正下发由后端在关键事件（被认领 / 已送达 / 待打卡）时调微信接口。
// 铁律：这条链路永远是旁路——模板未配置、非微信端、用户拒绝、接口报错一律静默跳过，
//      不得抛错、不得 toast 打断主流程（spec「模板未配置时静默跳过」）。
import { get } from './api'
import type { SubscribeTemplates } from '@/types/api'

/** 本地「微信消息提醒」开关。与 couple 表的 remindEnabled（关系级每日提醒）是两个独立开关，不要混用 */
const WX_REMIND_KEY = 'wx_remind_on'

/**
 * 申请结果：
 *   granted  至少一个模板拿到授权（一次性订阅每授权一次可下发一次）
 *   rejected 用户全拒 / 微信接口报错
 *   skipped  模板未配置或非微信端，什么都没发生（此时不要回滚开关）
 */
export type SubscribeConsent = 'granted' | 'rejected' | 'skipped'

/** 开关是否打开，默认关（storage 里没值即视为关） */
export function isWxRemindOn(): boolean {
  return uni.getStorageSync(WX_REMIND_KEY) === true
}

export function setWxRemindOn(v: boolean): void {
  uni.setStorageSync(WX_REMIND_KEY, v)
}

/** 三键过滤空串：值为空串表示该事件后端没配模板 */
function tmplIdsOf(t: SubscribeTemplates): string[] {
  return [t.claimed, t.delivered, t.checkinRemind].filter((id) => !!id)
}

/**
 * 静默申请一次订阅授权。
 * 任何失败都内部消化，resolve 出结果供调用方决定要不要回滚开关；不会 reject。
 */
export async function requestSubscribeConsent(): Promise<SubscribeConsent> {
  // #ifndef MP-WEIXIN
  return 'skipped' // 只有小程序端有订阅消息，其他端直接跳过
  // #endif
  // #ifdef MP-WEIXIN
  let tpl: SubscribeTemplates
  try {
    // silent：拉模板失败不该弹 toast，主流程照常走
    tpl = await get<SubscribeTemplates>('/api/subscribe/templates', undefined, { silent: true })
  } catch (e) {
    console.debug('[subscribe] 拉取模板失败，跳过', e)
    return 'skipped'
  }
  const tmplIds = tmplIdsOf(tpl)
  if (tmplIds.length === 0) return 'skipped' // 后端未配置模板，不弹框

  return new Promise<SubscribeConsent>((resolve) => {
    uni.requestSubscribeMessage({
      tmplIds,
      success: (res) => {
        // 微信把每个模板的结果挂在返回对象的同名键上（accept / reject / ban），类型声明里只有 errMsg
        const detail = res as unknown as Record<string, string>
        const accepted = tmplIds.filter((id) => detail[id] === 'accept')
        console.debug('[subscribe] 授权结果', detail)
        resolve(accepted.length > 0 ? 'granted' : 'rejected')
      },
      fail: (err) => {
        console.debug('[subscribe] 授权未完成', err)
        resolve('rejected')
      },
    })
  })
  // #endif
}
