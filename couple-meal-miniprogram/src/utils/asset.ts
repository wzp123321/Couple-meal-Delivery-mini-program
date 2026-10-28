// 资源路径解析：服务端只存相对路径（如 /upload/avatar/xxx.jpg），展示需拼成完整 URL。
// 图片与 API 同域，base = VITE_API_BASE。微信临时路径（http://tmp/...）已是完整地址，原样返回。

const BASE = import.meta.env.VITE_API_BASE || ''

export function resolveAsset(path?: string | null): string {
  if (!path) return ''
  if (/^https?:\/\//.test(path)) return path // 已是完整 URL（含微信 http://tmp/ 临时路径）
  return BASE + path
}
