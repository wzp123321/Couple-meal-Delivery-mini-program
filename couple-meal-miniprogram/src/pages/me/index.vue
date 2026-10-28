<script setup lang="ts">
// 我的（Tab4）：关系卡 + 五个入口（10-关系绑定/前端.md §3）。
// 关系卡数据来自 GET /api/couple/info：双人头像、称呼、在一起 N 天 + 一起吃饭 N 天（两个独立指标，
// 不可混用，见 §6 易错点）、累计爱心值。
// 切到本 Tab 时刷新关系卡与未读角标（前端-00-基础设施.md §5.3 / §10：重取放 onShow）。
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { get } from '@/utils/api'
import { appState, applyCoupleInfo, setUnread } from '@/stores/app'
import { resolveAsset } from '@/utils/asset'
import type { CoupleInfo, MsgUnreadResp } from '@/types/api'

const loading = ref(false)

// 称呼展示：优先用专属称呼替代微信昵称（nicknameMine = 我对 TA 的称呼，nicknamePartner = TA 对我的）
const myName = computed(() => appState.relation?.nicknamePartner || appState.relation?.myNickname || '我')
const partnerName = computed(
  () => appState.relation?.nicknameMine || appState.relation?.partnerNickname || 'TA',
)
const myAvatar = computed(() => resolveAsset(appState.relation?.myAvatar))
const partnerAvatar = computed(() => resolveAsset(appState.relation?.partnerAvatar))
const loveTotal = computed(() => (appState.relation?.myLove ?? 0) + (appState.relation?.partnerLove ?? 0))

// —— 五个入口（只是入口聚合，自身不发业务请求，见 §3）——
interface Entry {
  key: string
  icon: string
  label: string
  url: string
}

const entries: Entry[] = [
  { key: 'weekly', icon: '📅', label: '共同周历', url: '/pages/week/index' },
  // 月度轻报告是历史页内的弹层（§3），与「历史」同一个落地页
  { key: 'report', icon: '📊', label: '月度轻报告', url: '/pages/history/index' },
  { key: 'album', icon: '🖼️', label: '饭桌相册', url: '/pages/album/index' },
  { key: 'history', icon: '📜', label: '历史', url: '/pages/history/index' },
  { key: 'settings', icon: '⚙️', label: '设置', url: '/pages/settings/index' },
]

function onEntry(e: Entry): void {
  uni.navigateTo({ url: e.url })
}

// —— 未读角标：>99 显示 99+，=0 隐藏（PRD §12.5）——
const MESSAGE_TAB_INDEX = 2 // tabBar：0 今天 / 1 菜单 / 2 消息 / 3 我的

function syncBadge(n: number): void {
  if (n > 0) {
    uni.setTabBarBadge({ index: MESSAGE_TAB_INDEX, text: n > 99 ? '99+' : String(n) })
  } else {
    uni.removeTabBarBadge({ index: MESSAGE_TAB_INDEX })
  }
}

async function loadUnread(): Promise<void> {
  try {
    const resp = await get<MsgUnreadResp>('/api/msg/unread', undefined, { silent: true })
    setUnread(resp.unread)
    syncBadge(resp.unread)
  } catch {
    // 角标类请求静默失败：不打断页面
  }
}

async function loadInfo(): Promise<void> {
  if (loading.value) return
  loading.value = true
  try {
    const info = await get<CoupleInfo>('/api/couple/info')
    applyCoupleInfo(info)
  } catch {
    // 1002（已解绑）→ 请求层已 reLaunch 配对页；其余 toast 已由请求层处理
  } finally {
    loading.value = false
  }
}

onShow(() => {
  void loadInfo()
  void loadUnread()
})

onPullDownRefresh(async () => {
  await Promise.all([loadInfo(), loadUnread()])
  uni.stopPullDownRefresh()
})
</script>

<template>
  <view class="me">
    <!-- 关系卡 -->
    <view class="card relation">
      <view class="avatars">
        <image v-if="myAvatar" class="ava" :src="myAvatar" mode="aspectFill" />
        <view v-else class="ava placeholder">🐰</view>
        <view class="heart">💞</view>
        <image v-if="partnerAvatar" class="ava" :src="partnerAvatar" mode="aspectFill" />
        <view v-else class="ava placeholder">🐻</view>
      </view>

      <view class="names">
        <text class="nm">{{ myName }}</text>
        <text class="amp">&amp;</text>
        <text class="nm">{{ partnerName }}</text>
      </view>

      <!-- 两个天数分别展示，文案不混（§5 验收） -->
      <view class="days">
        <view class="day-item">
          <view class="day-num">{{ appState.relation?.togetherDays ?? 0 }}</view>
          <view class="day-label">在一起（天）</view>
        </view>
        <view class="day-divider"></view>
        <view class="day-item">
          <view class="day-num">{{ appState.relation?.mealDays ?? 0 }}</view>
          <view class="day-label">一起吃饭（天）</view>
        </view>
      </view>

      <view class="love">
        <text class="love-num">❤️ {{ loveTotal }}</text>
        <text class="love-label">
          累计爱心值 · 我 {{ appState.relation?.myLove ?? 0 }} / TA {{ appState.relation?.partnerLove ?? 0 }}
        </text>
      </view>
    </view>

    <!-- 入口列表 -->
    <view class="entries">
      <view v-for="e in entries" :key="e.key" class="entry" @tap="onEntry(e)">
        <text class="entry-icon">{{ e.icon }}</text>
        <text class="entry-label">{{ e.label }}</text>
        <text class="entry-arrow">›</text>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.me {
  min-height: 100vh;
  padding: 32rpx 32rpx 60rpx;
  box-sizing: border-box;
  background: var(--cream-bg);
}
.relation {
  padding: 48rpx 32rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.avatars {
  display: flex;
  align-items: center;
  gap: 28rpx;
}
.ava {
  width: 128rpx;
  height: 128rpx;
  border-radius: 50%;
  border: 4rpx solid var(--peach);
  background: var(--line);
}
.ava.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 56rpx;
}
.heart {
  font-size: 44rpx;
}
.names {
  margin-top: 24rpx;
  font-size: 32rpx;
  font-weight: 600;
  color: var(--ink);
}
.amp {
  margin: 0 12rpx;
  color: var(--orange);
}
.days {
  margin-top: 40rpx;
  display: flex;
  align-items: center;
  width: 100%;
}
.day-item {
  flex: 1;
  text-align: center;
}
.day-num {
  font-size: 48rpx;
  font-weight: 800;
  color: var(--orange);
}
.day-label {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: var(--ink-2);
}
.day-divider {
  width: 2rpx;
  height: 64rpx;
  background: var(--line);
}
.love {
  margin-top: 36rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.love-num {
  font-size: 34rpx;
  font-weight: 700;
  color: var(--ink);
}
.love-label {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: var(--ink-2);
}
.entries {
  margin-top: 32rpx;
  background: #fff;
  border-radius: $radius;
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}
.entry {
  display: flex;
  align-items: center;
  padding: 30rpx 32rpx;
  border-bottom: 2rpx solid var(--line);
  &:last-child {
    border-bottom: none;
  }
}
.entry-icon {
  font-size: 36rpx;
  margin-right: 20rpx;
}
.entry-label {
  flex: 1;
  font-size: 30rpx;
  color: var(--ink);
}
.entry-arrow {
  color: var(--ink-2);
  font-size: 36rpx;
}
</style>
