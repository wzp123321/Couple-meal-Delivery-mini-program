// 前端-00-基础设施.md §4.1 规则 1：文案只信后端，toast 直接显示 body.msg，前端不维护第二份文案。

export interface ToastOpts {
  icon?: 'none' | 'success' | 'error' | 'loading'
  duration?: number
}

export function toast(msg: string, opts: ToastOpts = {}): void {
  uni.showToast({
    title: msg,
    icon: opts.icon ?? 'none',
    duration: opts.duration ?? 2000,
  })
}
