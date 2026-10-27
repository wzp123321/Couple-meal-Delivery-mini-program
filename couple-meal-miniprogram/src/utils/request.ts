// 前端-00-基础设施.md §4.1 —— uni.request 全局封装
// 职责：统一注入 Authorization、1001 静默重登并重放一次、非 0 错误码转 toast。
import { getToken, relogin } from './auth'
import { toast } from './toast'

const BASE = import.meta.env.VITE_API_BASE
const inflight = new Map<string, Promise<unknown>>()

interface Options {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: Record<string, unknown>
  auth?: boolean
  silent?: boolean
  _retried?: boolean
}

export function request<T = unknown>(opt: Options): Promise<T> {
  const { url, method = 'GET', data, auth = true, silent = false, _retried = false } = opt
  const key = method + url + JSON.stringify(data || {})
  if (inflight.has(key)) return inflight.get(key) as Promise<T>

  const p = new Promise<T>((resolve, reject) => {
    const header: Record<string, string> = { 'content-type': 'application/json' }
    if (auth) header.Authorization = 'Bearer ' + getToken()

    uni.request({
      url: BASE + url,
      method,
      data,
      header,
      success(res) {
        const body = res.data as { code: number; msg?: string; data?: T }
        if (body.code === 0) return resolve(body.data as T)

        if (body.code === 1001 && !_retried && auth) {
          return relogin()
            .then(() => request<T>({ ...opt, _retried: true }))
            .then(resolve)
            .catch(() => {
              uni.reLaunch({ url: '/pages/login/index' })
              reject(body)
            })
        }
        if (body.code === 1002) {
          uni.reLaunch({ url: '/pages/pair/index' })
          return reject(body)
        }
        if (!silent) toast(body.msg || '出了点小状况')
        reject(body)
      },
      fail(err) {
        if (!silent) toast('网络不太顺，重试一下？')
        reject(err)
      },
    })
  }).finally(() => setTimeout(() => inflight.delete(key), 800))

  inflight.set(key, p)
  return p
}
