<script setup lang="ts">
// 配对页：双 Tab —— 生成码 / 输入码（10-关系绑定/前端.md §3）。
// 生成码：POST /api/couple/invite → 6 位码 + 24h 倒计时 + 复制 + 二维码 + 轮询等待对方加入。
// 输入码：6 位数字键盘，输满自动提交 POST /api/couple/join（不必再点按钮，见 §5 验收）。
// MVP 无实时推送，等待靠「3s 轮询 + 手动刷新」双保险。
import { computed, getCurrentInstance, nextTick, ref } from 'vue'
import { onUnload } from '@dcloudio/uni-app'
import { get, post } from '@/utils/api'
import { applyCoupleInfo } from '@/stores/app'
import { toast } from '@/utils/toast'
import type { CoupleInfo, InviteResp, JoinResp } from '@/types/api'
// 二维码：weapp-qrcode（类型声明见 src/env.d.ts）。绘制失败不影响主流程——邀请码文本仍可复制。
import drawQrcode from 'weapp-qrcode'

/**
 * uni 组件事件对象的结构壳：只声明我们用得到的 detail。
 * 声明成 `detail?: unknown` 而不是 `detail: { value: string }`，是因为模板里 `@input` 会被
 * vue-tsc 按宿主 InputEvent 校验（参数逆变），收紧成具体形状会报 TS2345；
 * 真正的 detail.value 由 eventValue() 在运行时取。
 */
interface UniDetailEvent {
  detail?: unknown
}

/** 取 @input / @change 的 detail.value（uni 的事件都是 { detail: { value } }） */
function eventValue(e: UniDetailEvent): string {
  const d = e.detail as { value?: string } | null | undefined
  return String(d?.value ?? '')
}

const instance = getCurrentInstance()
const mode = ref<'generate' | 'input'>('generate')

// —— 生成码 ——
const generating = ref(false)
const checking = ref(false)
const inviteCode = ref('')
const expireTs = ref(0)
const remain = ref(0)

let countdownTimer: ReturnType<typeof setInterval> | null = null
let pollTimer: ReturnType<typeof setInterval> | null = null

/**
 * 解析 expireAt（ISO 字符串）为时间戳。
 * 不用 new Date(iso)：带 T 的串在部分基础库会被当 UTC 解析，倒计时整整差 8 小时。
 */
function parseExpireAt(iso: string): number {
  const m = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?/.exec(String(iso || '').trim())
  if (!m) return 0
  return new Date(
    Number(m[1]),
    Number(m[2]) - 1,
    Number(m[3]),
    Number(m[4]),
    Number(m[5]),
    Number(m[6] || '0'),
  ).getTime()
}

function pad2(n: number): string {
  return n < 10 ? '0' + n : String(n)
}

// 倒计时文案：超过 1 小时只显示到分，避免「23:59:59」这种压迫感
const remainText = computed(() => {
  const sec = remain.value
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  const s = sec % 60
  return h > 0 ? `${pad2(h)}:${pad2(m)}:${pad2(s)}` : `${pad2(m)}:${pad2(s)}`
})
const expired = computed(() => inviteCode.value !== '' && remain.value <= 0)

function startCountdown(): void {
  stopCountdown()
  const tick = (): void => {
    remain.value = Math.max(0, Math.floor((expireTs.value - Date.now()) / 1000))
    if (remain.value <= 0) stopCountdown()
  }
  tick()
  countdownTimer = setInterval(tick, 1000)
}

function stopCountdown(): void {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

// —— 等待对方加入 ——
// 轮询 GET /api/couple/info：1002 = 还没人加入（静默，不打扰），code 0 = 对方已加入
function startPolling(): void {
  stopPolling()
  pollTimer = setInterval(() => {
    void checkJoined(true)
  }, 3000)
}

function stopPolling(): void {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

/** 查一次对方是否已加入。fromPoll=true 时静默失败（轮询不打扰用户） */
async function checkJoined(fromPoll = false): Promise<void> {
  if (checking.value) return
  checking.value = true
  try {
    const info = await get<CoupleInfo>('/api/couple/info', undefined, { silent: fromPoll })
    applyCoupleInfo(info)
    stopPolling()
    stopCountdown()
    toast('配对成功，开始投喂吧 💞')
    uni.reLaunch({ url: '/pages/today/index' })
  } catch (body) {
    const code = (body as { code?: number } | undefined)?.code
    if (code === 1002) {
      if (!fromPoll) toast('TA 还没加入，再等等呀')
      return // 仍在等待，继续轮询
    }
    if (!fromPoll) return // 网络类失败：请求层已 toast
  } finally {
    checking.value = false
  }
}

function drawQr(): void {
  if (!inviteCode.value) return
  // #ifdef MP-WEIXIN
  try {
    drawQrcode({
      canvasId: 'inviteQrcode',
      text: inviteCode.value,
      width: 200,
      height: 200,
      ctx: uni.createCanvasContext('inviteQrcode', instance?.proxy),
    })
  } catch {
    // 二维码画不出来不影响主流程，邀请码文本仍可复制
  }
  // #endif
}

async function generate(): Promise<void> {
  if (generating.value) return
  generating.value = true
  try {
    const resp = await post<InviteResp>('/api/couple/invite')
    inviteCode.value = resp.code
    expireTs.value = parseExpireAt(resp.expireAt)
    remain.value = 0
    startCountdown()
    startPolling()
    await nextTick() // 等 canvas 挂载后再画
    drawQr()
  } catch {
    // 1006 限频 / 1007 已有关系：toast 已由请求层处理
  } finally {
    generating.value = false
  }
}

function copyCode(): void {
  if (!inviteCode.value) return
  uni.setClipboardData({
    data: inviteCode.value,
    success: () => toast('邀请码已复制，发给 TA 吧', { icon: 'success' }),
  })
}

// —— 输入码 ——
const joinCode = ref('')
const joining = ref(false)
const joinError = ref('')

function onJoinInput(e: UniDetailEvent): void {
  // 只留数字、只留 6 位（PRD §12.3：邀请码是 6 位纯数字）
  const v = eventValue(e).replace(/\D/g, '').slice(0, 6)
  joinCode.value = v
  joinError.value = ''
  if (v.length === 6) void submitJoin()
}

async function submitJoin(): Promise<void> {
  if (joining.value) return
  if (joinCode.value.length !== 6) {
    joinError.value = '邀请码是 6 位数字哦'
    return
  }
  joining.value = true
  try {
    await post<JoinResp>('/api/couple/join', { code: joinCode.value })
    // 绑定成功：重取关系信息（铁律：服务端状态不本地臆造）
    const info = await get<CoupleInfo>('/api/couple/info')
    applyCoupleInfo(info)
    toast('绑定成功，开始投喂吧 💞')
    uni.reLaunch({ url: '/pages/today/index' })
  } catch {
    // 1003 无效 / 1007 已有关系 / 1008 不能绑自己：toast 已由请求层处理
    joinCode.value = '' // 清空让用户可以重输
  } finally {
    joining.value = false
  }
}

onUnload(() => {
  stopCountdown()
  stopPolling()
})
</script>

<template>
  <view class="pair">
    <view class="head">
      <view class="title">配对 💞</view>
      <view class="sub">邀请 TA 一起，开始你们的带饭约定</view>
    </view>

    <view class="tabs">
      <view class="tab" :class="{ on: mode === 'generate' }" @tap="mode = 'generate'">我的邀请码</view>
      <view class="tab" :class="{ on: mode === 'input' }" @tap="mode = 'input'">输入 TA 的码</view>
    </view>

    <!-- 生成码 -->
    <view v-if="mode === 'generate'" class="card panel">
      <block v-if="!inviteCode">
        <view class="empty-ico">🍱</view>
        <view class="empty-tip">生成专属邀请码，发给 TA 完成配对</view>
        <button class="primary-btn" :disabled="generating" @tap="generate">
          {{ generating ? '生成中…' : '生成邀请码' }}
        </button>
      </block>

      <block v-else>
        <view class="code-label">我的邀请码</view>
        <view class="code">{{ inviteCode }}</view>

        <view v-if="!expired" class="wait">
          <text class="dot">⏳</text>
          <text>等待对方加入…</text>
        </view>
        <view v-else class="wait expired">邀请码已过期，重新生成一个吧</view>

        <view v-if="!expired" class="countdown">
          {{ `有效时间 ${remainText}` }} · 过期可重新生成
        </view>

        <!-- 二维码：编码邀请码文本（扫码即见码；扫一扫直达加入属后续里程碑） -->
        <view v-if="!expired" class="qr-wrap">
          <!-- #ifdef MP-WEIXIN -->
          <canvas canvas-id="inviteQrcode" class="qr" :style="{ width: '200px', height: '200px' }"></canvas>
          <!-- #endif -->
          <!-- #ifndef MP-WEIXIN -->
          <view class="qr-fallback">二维码仅微信端可用</view>
          <!-- #endif -->
        </view>

        <button class="ghost-btn" :disabled="expired" @tap="copyCode">复制邀请码</button>
        <view class="refresh" @tap="checkJoined(false)">
          {{ checking ? '正在看一眼…' : 'TA 加入了？点此刷新' }}
        </view>
        <button v-if="expired" class="primary-btn" :disabled="generating" @tap="generate">
          {{ generating ? '生成中…' : '重新生成' }}
        </button>
      </block>
    </view>

    <!-- 输入码 -->
    <view v-else class="card panel">
      <view class="empty-ico">🔑</view>
      <view class="empty-tip">输入 TA 发给你的 6 位邀请码</view>
      <input
        class="code-input"
        type="number"
        :maxlength="6"
        :value="joinCode"
        :disabled="joining"
        placeholder="______"
        @input="onJoinInput"
      />
      <view class="hint" :class="{ err: joinError }">
        {{ joinError || (joining ? '正在加入…' : '输满 6 位自动加入') }}
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.pair {
  min-height: 100vh;
  padding: 32rpx 32rpx 60rpx;
  box-sizing: border-box;
  background: var(--cream-bg);
}
.head {
  padding: 16rpx 8rpx 24rpx;
}
.title {
  font-size: 40rpx;
  font-weight: 700;
  color: var(--ink);
}
.sub {
  margin-top: 8rpx;
  font-size: 26rpx;
  color: var(--ink-2);
}
.tabs {
  display: flex;
  background: #fff;
  border-radius: $radius;
  padding: 8rpx;
  box-shadow: var(--shadow-sm);
}
.tab {
  flex: 1;
  text-align: center;
  height: 76rpx;
  line-height: 76rpx;
  border-radius: $radius-sm;
  font-size: 28rpx;
  color: var(--ink-2);
  &.on {
    background: var(--orange);
    color: #fff;
    font-weight: 600;
  }
}
.panel {
  margin-top: 24rpx;
  padding: 48rpx 40rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.empty-ico {
  font-size: 72rpx;
  line-height: 1;
}
.empty-tip {
  margin-top: 16rpx;
  margin-bottom: 32rpx;
  color: var(--ink-2);
  font-size: 28rpx;
}
.primary-btn {
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
  &[disabled] {
    opacity: 0.6;
  }
}
.code-label {
  font-size: 24rpx;
  color: var(--ink-2);
}
.code {
  margin-top: 8rpx;
  font-size: 72rpx;
  font-weight: 800;
  letter-spacing: 10rpx;
  color: var(--orange);
  font-family: 'Courier New', monospace;
}
.wait {
  margin-top: 20rpx;
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 10rpx 24rpx;
  border-radius: 999rpx;
  background: var(--peach);
  color: var(--ink);
  font-size: 26rpx;
  &.expired {
    background: var(--line);
    color: var(--ink-2);
  }
}
.dot {
  font-size: 24rpx;
}
.countdown {
  margin-top: 16rpx;
  color: var(--ink-2);
  font-size: 24rpx;
}
.qr-wrap {
  margin: 28rpx 0 8rpx;
}
.qr {
  display: block;
  width: 200px;
  height: 200px;
}
.qr-fallback {
  width: 400rpx;
  height: 400rpx;
  line-height: 400rpx;
  text-align: center;
  color: var(--ink-2);
  background: var(--line);
  border-radius: $radius-sm;
}
.ghost-btn {
  margin-top: 20rpx;
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: $radius;
  background: #fff;
  color: var(--orange);
  border: 2rpx solid var(--orange);
  font-size: 30rpx;
  &::after {
    border: none;
  }
  &[disabled] {
    opacity: 0.5;
  }
}
.refresh {
  margin-top: 24rpx;
  font-size: 26rpx;
  color: var(--ink-2);
  text-decoration: underline;
}
.code-input {
  width: 100%;
  height: 100rpx;
  line-height: 100rpx;
  text-align: center;
  font-size: 56rpx;
  letter-spacing: 16rpx;
  color: var(--ink);
  background: var(--cream-bg);
  border-radius: $radius-sm;
}
.hint {
  margin-top: 24rpx;
  color: var(--ink-2);
  font-size: 26rpx;
  &.err {
    color: #e85c5c;
  }
}
</style>
