/// <reference types="vite/client" />
/// <reference types="@dcloudio/types" />

// 微信小程序全局对象。@dcloudio/types 不导出它，仅在 `#ifdef MP-WEIXIN` 分支使用，
// 故按 any 声明，避免 privacy.ts 的类型检查报 TS2304。
declare const wx: any

interface ImportMetaEnv {
  readonly VITE_API_BASE: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '*.vue' {
  import { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>
  export default component
}

declare module 'weapp-qrcode' {
  interface DrawQrcodeOptions {
    width?: number
    height?: number
    x?: number
    y?: number
    typeNumber?: number
    correctLevel?: number
    background?: string
    foreground?: string
    canvasId?: string
    ctx?: unknown
    _this?: unknown
    text: string
    callback?: (e?: unknown) => void
  }
  export default function drawQrcode(options: DrawQrcodeOptions): void
}
