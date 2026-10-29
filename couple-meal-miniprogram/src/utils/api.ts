// 请求薄封装（前端地基）——在 utils/request.ts 之上补一层语义化方法。
// 与 request.ts 的分工：
//   request.ts 负责 注入 token / 1001 静默重登并重放一次 / 非 0 错误码转 toast（§4.1 三条规则，语义不变）
//   api.ts     只负责 动词化 + 剥掉 { code, msg, data } 外壳，页面拿到的就是 data
// 因此这里**不再做任何错误处理**，reject 出来的就是后端 body 或 uni 的 fail 对象，直接透传。
import { request } from './request'
import { getToken } from './auth'
import { toast } from './toast'
import type {
  MealType,
  RpsAdoptReq,
  RpsAdoptResp,
  RpsCreateReq,
  RpsGameVO,
  RpsThrowReq,
} from '@/types/api'

/** 额外选项，与 request.ts 的同名参数一一对应 */
export interface ApiOptions {
  /** 是否带 Authorization，默认 true；仅 /api/auth/login 传 false */
  auth?: boolean
  /** 静默：不弹 toast。只有轮询类和角标类请求用（§4.1 规则 3：写接口不静默） */
  silent?: boolean
}

type Params = Record<string, string | number | boolean | undefined | null>

/** GET：params 会被 uni.request 拼到 query 上 */
export function get<T>(url: string, params?: Params, opt: ApiOptions = {}): Promise<T> {
  return request<T>({
    url,
    method: 'GET',
    data: params as Record<string, unknown>,
    auth: opt.auth ?? true,
    silent: opt.silent ?? false,
  })
}

/** POST：body 走 JSON */
export function post<T>(url: string, body?: unknown, opt: ApiOptions = {}): Promise<T> {
  return request<T>({
    url,
    method: 'POST',
    data: (body ?? {}) as Record<string, unknown>,
    auth: opt.auth ?? true,
    silent: opt.silent ?? false,
  })
}

/** PUT：菜单编辑走全量覆盖，入参同样放 body */
export function put<T>(url: string, body?: unknown, opt: ApiOptions = {}): Promise<T> {
  return request<T>({
    url,
    method: 'PUT',
    data: (body ?? {}) as Record<string, unknown>,
    auth: opt.auth ?? true,
    silent: opt.silent ?? false,
  })
}

/** DELETE：无 body，资源 id 拼在 url 上 */
export function del<T>(url: string, opt: ApiOptions = {}): Promise<T> {
  return request<T>({
    url,
    method: 'DELETE',
    auth: opt.auth ?? true,
    silent: opt.silent ?? false,
  })
}

/**
 * 文件上传：uni.uploadFile 是独立链路，不走 request.ts（§4.2），
 * 因此这里自己注入 token、自己 JSON.parse（res.data 是字符串，直接当对象用会拿到 undefined）。
 * 错误码沿用 §4.1 规则 1「文案只信后端」，只 toast 一次后 reject，不做重登重放。
 */
export function upload<T>(url: string, filePath: string, name = 'file'): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    uni.uploadFile({
      url: import.meta.env.VITE_API_BASE + url,
      filePath,
      name,
      header: { Authorization: 'Bearer ' + getToken() },
      success: (res) => {
        let body: { code: number; msg?: string; data?: T }
        try {
          body = JSON.parse(res.data) as { code: number; msg?: string; data?: T }
        } catch (e) {
          toast('出了点小状况')
          return reject(e)
        }
        if (body.code === 0) return resolve(body.data as T)
        toast(body.msg || '出了点小状况')
        reject(body)
      },
      fail: (err) => {
        toast('网络不太顺，重试一下？')
        reject(err)
      },
    })
  })
}

/* ============================================================
 * 石头剪刀布协商（phase-2）
 * 端点前缀 /api/rps，与后端 RpsController 一一对应。
 * ========================================================== */

/** 发起 / 复用进行中对局（幂等），返回对局视图 */
export function createRps(req: RpsCreateReq): Promise<RpsGameVO> {
  return post<RpsGameVO>('/api/rps/create', req)
}

/** 当前用户对局出拳，返回出拳后的对局视图 */
export function throwRps(gameId: number, req: RpsThrowReq): Promise<RpsGameVO> {
  return post<RpsGameVO>(`/api/rps/${gameId}/throw`, req)
}

/** 采纳结果，生成「输家主动带」约定（幂等），返回 planId */
export function adoptRps(gameId: number, req: RpsAdoptReq = {}): Promise<RpsAdoptResp> {
  return post<RpsAdoptResp>(`/api/rps/${gameId}/adopt`, req)
}

/** 按 id 取对局视图（已完成的对局也能取，便于回到页面继续采纳） */
export function getRps(gameId: number): Promise<RpsGameVO> {
  return get<RpsGameVO>(`/api/rps/${gameId}`)
}

/** 取 (couple, date, meal_type) 进行中的对局，无则返回 null */
export function currentRps(mealType: MealType, planDate: string): Promise<RpsGameVO | null> {
  return get<RpsGameVO | null>('/api/rps/current', { mealType, planDate })
}
