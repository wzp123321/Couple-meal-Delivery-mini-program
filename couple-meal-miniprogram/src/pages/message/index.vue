<script setup lang="ts">
// 50-通知/前端.md —— 消息中心（Tab3）
// 覆盖：分页列表 + 下拉刷新 + 触底加载、进入即全量已读（先本地置 0 再异步清）、
//       未读小圆点、六类消息文案与跳转、空态。
// 契约见 50-通知/后端.md §2；角标规则见 PRD §12.5；文案调性见 PRD §12.11（不出现交易语言）。
import { ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { get, post } from '@/utils/api'
import { appState, setUnread } from '@/stores/app'
import { daysFromToday, formatDate } from '@/utils/date'
import type { MsgItem, MsgListResp, MsgType } from '@/types/api'

/** 六类消息的 emoji 与兜底文案（后端 title/content 为空时用它，PRD §12.11 温情调性） */
interface MsgMeta {
  emoji: string
  fallbackTitle: string
  fallbackContent: string
}
const MSG_META: Record<MsgType, MsgMeta> = {
  1: { emoji: '🍚', fallbackTitle: 'TA 有点想吃的', fallbackContent: '去看看 TA 想吃什么吧～' },
  2: { emoji: '🥰', fallbackTitle: 'TA 来带啦', fallbackContent: '这顿饭有人接了，安心等着就好' },
  3: { emoji: '🍱', fallbackTitle: '饭给你放好啦', fallbackContent: '去看看 TA 留的便签，趁热吃～' },
  4: { emoji: '❤️', fallbackTitle: '这顿饭记下了', fallbackContent: '又一起吃了一顿，爱心值 +1' },
  5: { emoji: '⏰', fallbackTitle: '该投喂 TA 啦', fallbackContent: '今天的饭还没人带，看看你方便不～' },
  6: { emoji: '📸', fallbackTitle: 'TA 给这顿饭打了分', fallbackContent: '去看看 TA 说了什么吧' },
}
function metaOf(type: MsgType): MsgMeta {
  return MSG_META[type] || MSG_META[1]
}

const PAGE_SIZE = 20
/** tabBar 里「消息」的下标，角标清零要按它定位 */
const MSG_TAB_INDEX = 2

const list = ref<MsgItem[]>([])
const pageNo = ref(1)
const hasMore = ref(false)
const loading = ref(false)
const loaded = ref(false)

/** 角标同步（PRD §12.5）：>99 显示 99+，=0 隐藏 */
function syncTabBadge(): void {
  const n = appState.unread
  if (n > 0) {
    uni.setTabBarBadge({ index: MSG_TAB_INDEX, text: n > 99 ? '99+' : String(n) })
  } else {
    uni.removeTabBarBadge({ index: MSG_TAB_INDEX })
  }
}

/**
 * 全量已读：先本地置 0 消掉角标，再异步调接口（50-通知/前端.md §5 易错点：
 * 先调接口再清角标，网络慢时用户会以为没清掉）。失败不回滚，下次 onShow 会重新校正。
 */
async function markAllRead(): Promise<void> {
  setUnread(0)
  syncTabBadge()
  try {
    await post<void>('/api/msg/read', undefined, { silent: true })
  } catch {
    // 静默：进页就弹「失败」很打扰，角标已清零，下次拉取会自愈
  }
}

async function loadList(reset = true): Promise<void> {
  if (loading.value) return
  if (!reset && !hasMore.value) return
  loading.value = true
  const no = reset ? 1 : pageNo.value + 1
  try {
    const resp = await get<MsgListResp>('/api/msg/list', { pageNo: no, pageSize: PAGE_SIZE })
    list.value = reset ? resp.list : list.value.concat(resp.list)
    pageNo.value = resp.pageNo || no
    hasMore.value = resp.hasMore
  } catch {
    // 1002 → request 层已 reLaunch 配对页；其余后端文案已 toast
  } finally {
    loading.value = false
    loaded.value = true
  }
}

// 顺序有意如此：先取列表（此时 read_flag 仍是 0，未读小圆点才看得见），再全量置已读。
async function enterPage(): Promise<void> {
  await loadList(true)
  void markAllRead()
}

onShow(() => {
  void enterPage()
})

onPullDownRefresh(async () => {
  await enterPage()
  uni.stopPullDownRefresh()
})

onReachBottom(() => {
  void loadList(false)
})

/** 点消息跳约定详情；type 6 多带一个 focus=checkin 让详情页自动打开打卡区 */
function onTap(item: MsgItem): void {
  if (item.type === 5) {
    uni.switchTab({ url: '/pages/today/index' })
    return
  }
  if (item.planId === null) return // 无关联约定，无处可跳
  const url =
    item.type === 6
      ? `/pages/plan/detail?id=${item.planId}&focus=checkin`
      : `/pages/plan/detail?id=${item.planId}`
  uni.navigateTo({ url })
}

function titleOf(item: MsgItem): string {
  return item.title || metaOf(item.type).fallbackTitle
}
function contentOf(item: MsgItem): string {
  return item.content || metaOf(item.type).fallbackContent
}
function emojiOf(item: MsgItem): string {
  return metaOf(item.type).emoji
}
/** 时间：今天显示时刻，昨天带前缀，更早显示月日 */
function timeText(t: string): string {
  if (!t) return ''
  const clock = formatDate(t, 'HH:mm')
  const day = daysFromToday(t)
  if (day === 0) return clock
  if (day === -1) return `昨天 ${clock}`
  return formatDate(t, 'MM-dd HH:mm')
}
</script>

<template>
  <view class="msg-page">
    <view v-if="list.length > 0" class="list">
      <view v-for="m in list" :key="m.id" class="msg" @tap="onTap(m)">
        <view class="mi">{{ emojiOf(m) }}</view>
        <view class="mb">
          <view class="mt">
            {{ titleOf(m) }}
            <text v-if="m.readFlag === 0" class="reddot"></text>
          </view>
          <view class="md">{{ contentOf(m) }}</view>
          <view class="mtime">{{ timeText(m.createTime) }}</view>
        </view>
      </view>
      <view v-if="!hasMore && list.length > 0" class="list-end">没有更多啦</view>
    </view>

    <!-- 空态（PRD §12.10：消息为空无按钮） -->
    <view v-else-if="loaded && !loading" class="empty-card">
      <view class="empty-ico">💬</view>
      <view class="empty-t">暂无消息</view>
      <view class="empty-d">约定有动静时，会在这里提醒你</view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.msg-page {
  min-height: 100vh;
  padding: 24rpx 0 60rpx;
  box-sizing: border-box;
  background: var(--cream-bg);
}
.list {
  margin: 0 32rpx;
}
.msg {
  display: flex;
  align-items: flex-start;
  gap: 20rpx;
  background: #fff;
  border-radius: $radius-sm;
  padding: 28rpx;
  margin-bottom: 16rpx;
  @include soft-shadow(1);
}
.mi {
  flex: none;
  width: 72rpx;
  height: 72rpx;
  border-radius: 20rpx;
  background: var(--peach);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36rpx;
}
.mb {
  flex: 1;
  min-width: 0;
}
.mt {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--ink);
}
.reddot {
  display: inline-block;
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: #e4573d;
  margin-left: 12rpx;
  vertical-align: middle;
}
.md {
  margin-top: 8rpx;
  font-size: 26rpx;
  color: var(--ink-2);
  line-height: 1.5;
}
.mtime {
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #c4b7a8;
}
.list-end {
  padding: 32rpx 0;
  text-align: center;
  font-size: 22rpx;
  color: var(--ink-2);
}

.empty-card {
  margin: 120rpx 32rpx 0;
  padding: 64rpx 32rpx;
  background: #fff;
  border-radius: $radius;
  @include soft-shadow(1);
  display: flex;
  flex-direction: column;
  align-items: center;
}
.empty-ico {
  font-size: 88rpx;
}
.empty-t {
  margin-top: 20rpx;
  font-size: 32rpx;
  font-weight: 700;
  color: var(--ink);
}
.empty-d {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: var(--ink-2);
}
</style>
