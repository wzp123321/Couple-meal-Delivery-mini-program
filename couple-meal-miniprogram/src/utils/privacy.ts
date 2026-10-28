// 微信隐私授权（PRD §13 P0-1 / 隐私政策审核）。
// manifest.json 已开启 mp-weixin.__usePrivacyCheck__；开启后 wx.login / wx.chooseMedia 等隐私接口
// 在用户未授权时会被拦截并触发 onNeedPrivacyAuthorize。必须接入授权处理，否则真机登录直接失败。
// 两套机制：
//  1) initPrivacy()：App 冷启动注册全局兜底，覆盖所有隐私接口（含上传打卡照片）。
//  2) ensurePrivacyAuthorize()：登录页主动拉起官方授权窗，未授权先弹、已授权直接过。

export function initPrivacy(): void {
  // #ifdef MP-WEIXIN
  const w = wx as any
  if (w && typeof w.onNeedPrivacyAuthorize === 'function') {
    w.onNeedPrivacyAuthorize(
      (resolve: (o: { event: string }) => void, reject: () => void) => {
        if (typeof w.requirePrivacyAuthorize === 'function') {
          w.requirePrivacyAuthorize({
            success: () => resolve({ event: 'agree' }),
            fail: () => reject(),
          })
        } else {
          // 老基础库无该接口，直接放行，避免卡死登录
          resolve({ event: 'agree' })
        }
      },
    )
  }
  // #endif
}

export function ensurePrivacyAuthorize(): Promise<boolean> {
  return new Promise((resolve) => {
    // #ifdef MP-WEIXIN
    const w = wx as any
    if (!w || typeof w.getPrivacySetting !== 'function') {
      resolve(true)
      return
    }
    w.getPrivacySetting({
      success: (res: { needAuthorization: boolean }) => {
        if (res.needAuthorization && typeof w.requirePrivacyAuthorize === 'function') {
          w.requirePrivacyAuthorize({
            success: () => resolve(true),
            fail: () => resolve(false),
          })
        } else {
          resolve(true)
        }
      },
      fail: () => resolve(true),
    })
    // #endif
    // #ifndef MP-WEIXIN
    resolve(true)
    // #endif
  })
}
