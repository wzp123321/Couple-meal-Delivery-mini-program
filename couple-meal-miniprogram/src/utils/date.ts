// 日期纯函数（前端地基）。零第三方依赖，只用宿主 Date，不碰任何 DOM / window。
// 入参统一接受 Date | 'yyyy-MM-dd' | 'yyyy-MM-dd HH:mm[:ss]' | 时间戳，页面不用先转类型。

/** 本工具接受的日期入参 */
export type DateLike = Date | string | number

/** weekdaysOf 返回的单个日期（带页面直接可用的元信息） */
export interface DateItem {
  date: string // 'yyyy-MM-dd'
  weekday: number // 1=周一 … 7=周日（与后端 weekday 口径一致）
  label: string // '周一' … '周日'
  isWorkday: boolean // 周一至周五
  isToday: boolean
}

const WEEKDAY_LABELS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

function pad(n: number): string {
  return n < 10 ? '0' + n : String(n)
}

/**
 * 解析成 Date。
 * 纯日期串（'2026-10-27'）手动按本地时间构造——交给 `new Date(str)` 会被当成 UTC 解析，
 * 东八区下跨月边界会整整差一天。带时间的串仍交给 Date 自己处理。
 */
function parse(d: DateLike): Date {
  if (d instanceof Date) return new Date(d.getTime())
  if (typeof d === 'number') return new Date(d)

  const s = String(d).trim()
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(s)
  if (m) return new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]))

  const t = new Date(s.replace(/-/g, '/'))
  return isNaN(t.getTime()) ? new Date(NaN) : t
}

/**
 * 格式化。默认 'yyyy-MM-dd'。
 * 支持的占位符：yyyy / MM / dd / HH / mm / ss
 */
export function formatDate(d: DateLike, fmt = 'yyyy-MM-dd'): string {
  const t = parse(d)
  if (isNaN(t.getTime())) return ''

  const map: Record<string, string> = {
    yyyy: String(t.getFullYear()),
    MM: pad(t.getMonth() + 1),
    dd: pad(t.getDate()),
    HH: pad(t.getHours()),
    mm: pad(t.getMinutes()),
    ss: pad(t.getSeconds()),
  }
  return fmt.replace(/yyyy|MM|dd|HH|mm|ss/g, (k) => map[k])
}

/** 是否工作日（周一至周五）。约定只能约工作日，日期选择器据此置灰周末 */
export function isWorkday(d: DateLike): boolean {
  const day = parse(d).getDay() // 0=周日
  return day >= 1 && day <= 5
}

/**
 * 取 date 所在自然周（周一至周日）的 7 个日期，顺序固定周一在前。
 * getDay() 周日是 0，这里换算成「周一=1 … 周日=7」的口径，与后端 weekday 一致。
 */
export function weekdaysOf(d: DateLike = new Date()): DateItem[] {
  const base = parse(d)
  const jsDay = base.getDay() // 0=周日
  const weekday = jsDay === 0 ? 7 : jsDay // 转成 1=周一
  const monday = new Date(base.getFullYear(), base.getMonth(), base.getDate() - (weekday - 1))
  const today = formatDate(new Date())

  const out: DateItem[] = []
  for (let i = 0; i < 7; i++) {
    const cur = new Date(monday.getFullYear(), monday.getMonth(), monday.getDate() + i)
    const date = formatDate(cur)
    out.push({
      date,
      weekday: i + 1,
      label: WEEKDAY_LABELS[i],
      isWorkday: i < 5,
      isToday: date === today,
    })
  }
  return out
}

/**
 * 在一起天数（含当天，口径与后端一致：DATEDIFF(今天, 纪念日) + 1）。
 * 纪念日为空 / 非法 / 晚于今天时返回 0，由调用方决定展示还是隐藏。
 */
export function togetherDays(anniversary: DateLike | null | undefined): number {
  if (anniversary === null || anniversary === undefined || anniversary === '') return 0
  const start = parse(anniversary)
  if (isNaN(start.getTime())) return 0

  const today = new Date()
  const a = new Date(start.getFullYear(), start.getMonth(), start.getDate()).getTime()
  const b = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime()
  const days = Math.floor((b - a) / 86400000) + 1
  return days > 0 ? days : 0
}

/** 月键 'yyyy-MM'，相册分组与月报 anchor 都用它 */
export function monthKey(d: DateLike = new Date()): string {
  return formatDate(d, 'yyyy-MM')
}

/** 月键转中文标题：'2026-10' → '2026 年 10 月' */
export function monthLabel(month: string): string {
  const m = /^(\d{4})-(\d{2})$/.exec(String(month || '').trim())
  if (!m) return ''
  return `${Number(m[1])} 年 ${Number(m[2])} 月`
}

/** 相对今天的天数差：正数=未来，0=今天，负数=已过。用于「可约范围 [today, today+7]」判断 */
export function daysFromToday(d: DateLike): number {
  const t = parse(d)
  if (isNaN(t.getTime())) return NaN
  const now = new Date()
  const a = new Date(t.getFullYear(), t.getMonth(), t.getDate()).getTime()
  const b = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  return Math.round((a - b) / 86400000)
}
