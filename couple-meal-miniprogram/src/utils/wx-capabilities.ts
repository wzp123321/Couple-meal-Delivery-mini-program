// ⚠️ 1.6.1 微信独有能力封装 —— 四项必须真机验证，开发者工具 / 模拟器无法完整跑通。
//    see 前端-00-基础设施.md §2 / §11。本文件只搭代码路径 + 可复用封装，是否可用以真机为准。
//
// 四项：chooseAvatar / type="nickname" / uni.login / uni.chooseMedia
//   - uni.login / uni.chooseMedia 是跨端 API，可直接调用；
//   - chooseAvatar / type="nickname" 是微信专有标签，必须在模板里用条件编译包住（见文件底部示例）。

/**
 * ① uni.login 换 code（跨端 API，真机 / 开发者工具均可）
 * 拿到 code 后交给后端 1.4 的 code2session 换身份 + 签发 token。
 */
export function wxLogin(): Promise<{ code: string }> {
  return new Promise((resolve, reject) => {
    // #ifdef MP-WEIXIN
    uni.login({
      provider: 'weixin',
      success: (res) => resolve({ code: res.code }),
      fail: (err) => reject(err),
    })
    // #else
    reject(new Error('wxLogin 仅微信小程序端可用'))
    // #endif
  })
}

/**
 * ② uni.chooseMedia 选图 / 拍照（跨端 API，打卡上传用，见 §7 F7）
 * @param opts count 默认 1；mediaType 默认 ['image']；sourceType 默认 ['album','camera']
 */
export function wxChooseMedia(opts: {
  count?: number
  mediaType?: Array<'image' | 'video'>
  sourceType?: Array<'album' | 'camera'>
} = {}): Promise<UniApp.ChooseMediaSuccessCallbackResult> {
  return new Promise((resolve, reject) => {
    uni.chooseMedia({
      count: opts.count ?? 1,
      mediaType: opts.mediaType ?? ['image'],
      sourceType: opts.sourceType ?? ['album', 'camera'],
      success: (res) => resolve(res),
      fail: (err) => reject(err),
    })
  })
}

/**
 * ③ chooseAvatar 事件回调（微信专有，按钮 open-type="chooseAvatar" 触发）
 * @returns 临时头像路径（http://tmp/...），必须上传换成永久 URL 才能存库（§2 易错点 1）
 */
export function onChooseAvatar(e: { detail: { avatarUrl: string } }): string {
  return e.detail.avatarUrl
}

/**
 * ④ type="nickname" 取值（微信专有，blur 时才可靠，见 §2 易错点 2）
 * @returns 用户输入 / 选中的微信昵称
 */
export function onNicknameBlur(e: { detail: { value: string } }): string {
  return e.detail.value
}

/*
 * ── 模板示例（设置页换头像 / 昵称，必须条件编译，见 §2）────────────────────
 *
 * <script setup lang="ts">
 *   import { ref } from 'vue'
 *   import { onChooseAvatar, onNicknameBlur } from '@/utils/wx-capabilities'
 *   const form = ref({ avatarUrl: '', nickname: '' })
 *   const defaultAvatar = '/static/default-avatar.png'
 *   const handleAvatar = (e: any) => { form.value.avatarUrl = onChooseAvatar(e) /* 临时路径，需上传 *\/ }
 *   const handleNickname = (e: any) => { form.value.nickname = onNicknameBlur(e) }
 * </script>
 *
 * <template>
 *   <!-- #ifdef MP-WEIXIN -->
 *   <button class="avatar-btn" open-type="chooseAvatar" @chooseavatar="handleAvatar">
 *     <image class="avatar" :src="form.avatarUrl || defaultAvatar" mode="aspectFill" />
 *   </button>
 *   <input class="nickname" type="nickname" :value="form.nickname" @blur="handleNickname" />
 *   <!-- #endif -->
 *
 *   <!-- #ifndef MP-WEIXIN -->
 *   <image class="avatar" :src="form.avatarUrl || defaultAvatar" mode="aspectFill" />
 *   <!-- #endif -->
 * </template>
 *
 * 注意：条件编译注释必须顶格写成 `<!-- #ifdef MP-WEIXIN -->`，缩进会失效（§2 易错点 3）。
 */
