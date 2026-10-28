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
        <view class="pp">欢迎使用「情侣带饭」。我们非常重视你的隐私保护。</view>
        <view class="pp">1. 为完成微信登录，我们会获取你的微信 openid（仅用于身份识别，不对外展示）。</view>
        <view class="pp">2. 你主动设置的头像、昵称、专属称呼、纪念日，只用于你们彼此的展示。</view>
        <view class="pp">3. 你与伴侣的带饭约定、打卡照片仅双方可见，不做公开、不对外共享。</view>
        <view class="pp">4. 你可随时在「设置」里解绑或注销账号，注销将真实删除你的个人身份数据。</view>
        <view class="pp muted">（此为占位文案，上线前替换为经法务确认的正式隐私协议。）</view>
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
