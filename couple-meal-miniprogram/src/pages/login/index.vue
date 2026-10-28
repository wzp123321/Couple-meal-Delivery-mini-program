<script setup lang="ts">
// 登录页 + 隐私协议弹窗（10-关系绑定/前端.md §2）。
// 硬规则：未勾选同意 → 按钮置灰且点击不发请求（PRD §5.1 / 前端.md §5 验收）。
// 登录走普通按钮 + uni.login 拿 code → POST /api/auth/login（getUserProfile 已废弃，见基础设施 §2）。
// 登录后按 hasCouple 分流：已有关系进 tabBar 首页，否则进配对页。
import { computed, ref } from 'vue'
import { get, post } from '@/utils/api'
import { setToken } from '@/utils/auth'
import { applyCoupleInfo, applyLogin } from '@/stores/app'
import { wxLogin } from '@/utils/wx-capabilities'
import { toast } from '@/utils/toast'
import { ensurePrivacyAuthorize } from '@/utils/privacy'
import type { CoupleInfo, LoginResp } from '@/types/api'
import BottomSheet from '@/components/bottom-sheet.vue'

// 是否已阅读并同意隐私协议
const agreed = ref(false)
// 登录中：按钮 loading + 防连点
const logging = ref(false)
const privacyVisible = ref(false)

const canLogin = computed(() => agreed.value && !logging.value)

function toggleAgree(): void {
  agreed.value = !agreed.value
}

function openPrivacy(): void {
  privacyVisible.value = true
}

// 弹窗内「同意并继续」：替用户勾上，省一次点击
function agreeAndClose(): void {
  agreed.value = true
  privacyVisible.value = false
}

async function onLogin(): Promise<void> {
  if (logging.value) return
  if (!agreed.value) {
    toast('先看看隐私协议，同意了才能继续哦')
    return
  }
  logging.value = true
  try {
    const authorized = await ensurePrivacyAuthorize()
    if (!authorized) {
      toast('需要同意隐私协议才能登录哦')
      return
    }
    const { code } = await wxLogin()
    // 登录接口免鉴权（10-关系绑定/后端.md §2）
    const resp = await post<LoginResp>(
      '/api/auth/login',
      { code, agreedPrivacy: true },
      { auth: false },
    )
    // 落盘（storage）+ 内存态（appState）必须成对写入
    setToken(resp.token, {
      userId: resp.userId,
      nickname: resp.nickname,
      avatar: resp.avatar,
    })
    applyLogin(resp)

    if (resp.hasCouple) {
      // 已有关系：拉关系信息填满「我的」与首页卡片，再进 tabBar 首页
      const info = await get<CoupleInfo>('/api/couple/info')
      applyCoupleInfo(info)
      uni.reLaunch({ url: '/pages/today/index' })
    } else {
      // 无关系：进配对页（请求层 1002 也会兜底到这里）
      uni.reLaunch({ url: '/pages/pair/index' })
    }
  } catch {
    // 错误码 → toast 已由请求层处理；5001「微信那边没响应，稍后再试」亦如此
  } finally {
    logging.value = false
  }
}
</script>

<template>
  <view class="login">
    <view class="brand">
      <view class="logo">🍱</view>
      <view class="name">情侣带饭</view>
      <view class="slogan">今天想吃什么，我带给你 🍚</view>
      <view class="slogan sub">只属于你们两个人的私密约定</view>
    </view>

    <button class="login-btn" :class="{ disabled: !canLogin }" :disabled="!canLogin" @tap="onLogin">
      {{ logging ? '正在敲门…' : '微信一键登录' }}
    </button>

    <view class="agree" @tap="toggleAgree">
      <view class="checkbox" :class="{ on: agreed }"></view>
      <view class="agree-text">
        我已阅读并同意
        <text class="link" @tap.stop="openPrivacy">《隐私协议》</text>
      </view>
    </view>

    <view class="foot">仅收集 openid / 昵称 / 头像，用于你们两个人的配对</view>

    <!-- 隐私协议：bottom-sheet 容器（基础设施 §7），内容走默认 slot -->
    <BottomSheet :visible="privacyVisible" title="隐私协议" @close="privacyVisible = false">
      <view class="privacy">
        <view class="pp">欢迎使用「情侣带饭」。我们深知个人信息对你的重要性，并会尽力保护你的隐私。本《隐私协议》说明我们如何收集、使用、存储与保护你的信息。</view>

        <view class="pp title">一、我们收集的信息及用途</view>
        <view class="pp">1. 微信 openid：你使用微信登录时获取，仅用于身份识别与登录，不对外展示。</view>
        <view class="pp">2. 头像、昵称、专属称呼、纪念日：你主动设置，仅用于你和伴侣彼此的展示。</view>
        <view class="pp">3. 带饭约定：包括点餐人、带饭人、时段、菜品、备注等，用于你们二人之间的约定流转。</view>
        <view class="pp">4. 菜单与偏好：你记录的忌口、爱吃与备注，用于点餐时的偏好提示。</view>
        <view class="pp">5. 打卡照片与点评：你上传的饭桌照片及三档点评，仅双方可见，构成你们的私密相册。</view>
        <view class="pp">6. 设备与操作日志：登录凭证、登录时间等，用于安全保障与服务运行。</view>

        <view class="pp title">二、我们如何使用信息</view>
        <view class="pp">除上述目的外，我们不会将你的信息用于其他用途。衍生数据（如爱心值、连续投喂天数、在一起吃饭天数）仅基于你和伴侣的约定与打卡自动生成，仅你们双方可见。</view>

        <view class="pp title">三、信息的存储与保护</view>
        <view class="pp">1. 你的数据存储在我们的自建服务器（数据库与图片存储），不会出售或提供给无关第三方。</view>
        <view class="pp">2. 上传的照片会剥离拍摄位置等元数据（EXIF），避免泄露你的住址等隐私。</view>
        <view class="pp">3. 用户生成内容（照片、文本）会经过内容安全检测，未通过的不予展示。</view>
        <view class="pp">4. 我们采取访问控制等措施，确保只有你和你的伴侣能够查看彼此的数据。</view>

        <view class="pp title">四、信息共享与公开</view>
        <view class="pp">除以下情形外，我们不会向任何第三方共享或公开你的信息：（1）为完成微信登录与内容安全检测，必要地向微信平台提供相关信息；（2）依据法律法规或有权机关的要求。你的带饭约定、照片、点评等仅你和伴侣可见，不做公开、不对外展示。</view>

        <view class="pp title">五、你的权利</view>
        <view class="pp">1. 查阅与更正：你可随时在「设置」中查看或修改你的资料与约定。</view>
        <view class="pp">2. 删除：你可解绑关系或删除特定数据。</view>
        <view class="pp">3. 注销账号：在「设置」中注销账号将真实删除你的个人身份数据（解绑不等于删除）。</view>
        <view class="pp">4. 撤回同意：你可通过注销账号等方式撤回授权。</view>

        <view class="pp title">六、未成年人</view>
        <view class="pp">本小程序用于情侣之间的私密协作。建议你在使用前确认已具备完全民事行为能力。</view>

        <view class="pp title">七、协议的更新</view>
        <view class="pp">我们可能不时更新本协议。更新后会在小程序内提示，你继续使用即视为同意更新内容。</view>

        <view class="pp title">八、联系我们</view>
        <view class="pp">如你对本协议或个人信息处理有任何疑问，可通过小程序内「设置」的反馈入口与我们联系。</view>
      </view>
      <template #footer>
        <button class="sheet-btn" @tap="agreeAndClose">同意并继续</button>
      </template>
    </BottomSheet>
  </view>
</template>

<style scoped lang="scss">
.login {
  min-height: 100vh;
  padding: calc(var(--status-bar-height) + 120rpx) 48rpx 80rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  background: var(--cream-bg);
}
.brand {
  margin-top: 80rpx;
  text-align: center;
}
.logo {
  font-size: 120rpx;
  line-height: 1;
}
.name {
  margin-top: 24rpx;
  font-size: 44rpx;
  font-weight: 700;
  color: var(--ink);
}
.slogan {
  margin-top: 16rpx;
  font-size: 26rpx;
  color: var(--ink-2);
  &.sub {
    margin-top: 8rpx;
    font-size: 24rpx;
  }
}
.login-btn {
  margin-top: 120rpx;
  width: 100%;
  height: 92rpx;
  line-height: 92rpx;
  border-radius: $radius;
  background: var(--orange);
  color: #fff;
  font-size: 32rpx;
  font-weight: 600;
  border: none;
  &::after {
    border: none;
  }
  &.disabled {
    background: var(--peach);
    color: rgba(255, 255, 255, 0.85);
  }
}
.agree {
  margin-top: 32rpx;
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.checkbox {
  width: 32rpx;
  height: 32rpx;
  border-radius: 50%;
  border: 2rpx solid var(--ink-2);
  box-sizing: border-box;
  flex-shrink: 0;
  &.on {
    background: var(--orange);
    border-color: var(--orange);
    position: relative;
  }
  &.on::after {
    content: '';
    position: absolute;
    left: 10rpx;
    top: 5rpx;
    width: 8rpx;
    height: 14rpx;
    border: solid #fff;
    border-width: 0 3rpx 3rpx 0;
    transform: rotate(45deg);
  }
}
.agree-text {
  font-size: 24rpx;
  color: var(--ink-2);
}
.link {
  color: var(--orange);
}
.foot {
  margin-top: 24rpx;
  font-size: 22rpx;
  color: var(--ink-2);
  text-align: center;
}
  .privacy {
    .pp {
      margin-bottom: 16rpx;
    }
    .title {
      margin-top: 24rpx;
      margin-bottom: 8rpx;
      font-weight: 700;
      color: var(--ink);
    }
    .muted {
      color: var(--ink-2);
      font-size: 24rpx;
    }
  }
.sheet-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: $radius;
  background: var(--orange);
  color: #fff;
  font-size: 30rpx;
  border: none;
  &::after {
    border: none;
  }
}
</style>
