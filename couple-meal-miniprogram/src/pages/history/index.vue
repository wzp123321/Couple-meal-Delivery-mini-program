<script setup lang="ts">
// 历史（docs/design/70-历史/前端.md §2–§4）：每一次投喂都留了痕。
// 三条硬规则：切筛选必须 pageNo 归 1 并清列表（否则新旧数据拼一起）；
// 每页 20 条上拉加载，到底显示「就这些啦」不再重复请求；月报 planCount=0 时整卡不渲染。
import { computed, ref } from 'vue'
import { onLoad, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import { get } from '@/utils/api'
import { formatDate, monthLabel } from '@/utils/date'
import type {
  CheckinRating,
  PlanHistoryItem,
  PlanHistoryResp,
  PlanRole,
  PlanStatus,
  ReportResp,
} from '@/types/api'
import BottomSheet from '@/components/bottom-sheet.vue'

/** 每页 20 条（PRD §12.2） */
const PAGE_SIZE = 20

/** 状态筛选 chips：空数组 = 全部（后端 status 可多选） */
const STATUS_CHIPS: { label: string; value: PlanStatus }[] = [
  { label: '已完成', value: 4 },
  { label: '已取消', value: 5 },
  { label: '已过期', value: 6 },
]

/** 角色切换：不传 role 时后端返回双方的全部记录 */
const ROLE_CHIPS: { label: string; value: PlanRole | '' }[] = [
  { label: '全部', value: '' },
  { label: '我点的', value: 'orderer' },
  { label: '我带的', value: 'carrier' },
]

/** 状态文案与配色：已取消 / 已过期走灰调，不做删除线（太「账本感」） */
const STATUS_META: Record<number, { text: string; gray: boolean }> = {
  0: { text: '待认领', gray: false },
  1: { text: '待接收', gray: false },
  2: { text: '备餐中', gray: false },
  3: { text: '已交付', gray: false },
  4: { text: '已完成', gray: false },
  5: { text: '已取消', gray: true },
  6: { text: '已过期', gray: true },
}

/** 打卡评价三档（历史里只做标记，不五星） */
const RATING_EMOJI: Record<CheckinRating, string> = { 3: '🔥', 2: '😋', 1: '😐' }

const statusFilter = ref<PlanStatus[]>([])
const roleFilter = ref<PlanRole | ''>('')
const pageNo = ref(1)
const list = ref<PlanHistoryItem[]>([])
const total = ref(0)
const hasMore = ref(true)
const loading = ref(false)
const loaded = ref(false)

const report = ref<ReportResp | null>(null)
const reportVisible = ref(false)

/** 按月分组；列表本身按 plan_date 倒序，分组只做切片不改顺序 */
const months = computed(() => {
  const groups: { month: string; title: string; list: PlanHistoryItem[] }[] = []
  list.value.forEach((item) => {
    const month = formatDate(item.planDate, 'yyyy-MM')
    const hit = groups.find((g) => g.month === month)
    if (hit) hit.list.push(item)
    else groups.push({ month, title: monthLabel(month), list: [item] })
  })
  return groups
})

const isEmpty = computed(() => loaded.value && list.value.length === 0)

function statusMeta(status: PlanStatus): { text: string; gray: boolean } {
  return STATUS_META[status] ?? { text: '已完成', gray: false }
}

function ratingEmoji(rating: CheckinRating | null): string {
  return rating === null ? '' : RATING_EMOJI[rating]
}

function dateText(date: string): string {
  return formatDate(date, 'MM-dd')
}

function dishesText(items: string[]): string {
  return items.join(' · ')
}

function toggleStatus(value: PlanStatus): void {
  const idx = statusFilter.value.indexOf(value)
  if (idx >= 0) statusFilter.value.splice(idx, 1)
  else statusFilter.value.push(value)
  resetAndLoad()
}

function setRole(value: PlanRole | ''): void {
  if (roleFilter.value === value) return
  roleFilter.value = value
  resetAndLoad()
}

function resetAndLoad(): void {
  pageNo.value = 1
  list.value = []
  total.value = 0
  hasMore.value = true
  loaded.value = false
  void loadPage()
}

async function loadPage(): Promise<void> {
  if (loading.value || !hasMore.value) return
  loading.value = true
  try {
    const resp = await get<PlanHistoryResp>('/api/plan/history', {
      pageNo: pageNo.value,
      pageSize: PAGE_SIZE,
      // 多选状态按逗号拼接（Spring 的 List 绑定同时接受逗号串与重复参数）
      status: statusFilter.value.length > 0 ? statusFilter.value.join(',') : undefined,
      role: roleFilter.value || undefined,
    })
    const page = resp.list ?? []
    list.value.push(...page)
    total.value = resp.total ?? 0
    hasMore.value = resp.hasMore && page.length > 0
    if (hasMore.value) pageNo.value += 1
  } catch {
    // 1002 已由 request 层 reLaunch 配对页；其余已 toast
  } finally {
    loading.value = false
    loaded.value = true
  }
}

async function loadReport(): Promise<void> {
  try {
    const resp = await get<ReportResp>('/api/report', { scope: 'month' }, { silent: true })
    // planCount=0 时整卡不渲染，不留空壳
    report.value = resp.planCount > 0 ? resp : null
  } catch {
    report.value = null
  }
}

function openReport(): void {
  if (!report.value) return
  reportVisible.value = true
}

function onItem(item: PlanHistoryItem): void {
  // 历史是只读视图，要操作得进详情页
  uni.navigateTo({ url: `/pages/plan/detail?id=${item.id}` })
}

onLoad(() => {
  void loadPage()
  void loadReport()
})

onReachBottom(() => {
  void loadPage()
})

onPullDownRefresh(() => {
  resetAndLoad()
  void loadReport()
  uni.stopPullDownRefresh()
})
</script>

<template>
  <view class="history">
    <!-- 月度轻报告：planCount=0 时整卡不渲染 -->
    <view v-if="report" class="rcard" @tap="openReport">
      <view class="rtitle">💞 上月小结</view>
      <view class="rline">
        你带了 {{ report.carrierCount.me }} 次，TA 带了 {{ report.carrierCount.partner }} 次
      </view>
      <view class="rmeta">
        打卡 {{ report.checkinDays }} 天 · 爱心值 +{{ report.loveValueDelta }}
      </view>
      <view class="rmore">点开看看 ›</view>
    </view>

    <view class="filters">
      <view class="frow">
        <text class="chip" :class="{ on: statusFilter.length === 0 }" @tap="statusFilter = []; resetAndLoad()">
          全部
        </text>
        <text
          v-for="chip in STATUS_CHIPS"
          :key="chip.value"
          class="chip"
          :class="{ on: statusFilter.includes(chip.value) }"
          @tap="toggleStatus(chip.value)"
        >
          {{ chip.label }}
        </text>
      </view>
      <view class="frow frow-role">
        <text
          v-for="chip in ROLE_CHIPS"
          :key="chip.label"
          class="chip"
          :class="{ on: roleFilter === chip.value }"
          @tap="setRole(chip.value)"
        >
          {{ chip.label }}
        </text>
      </view>
    </view>

    <view v-if="!loaded" class="skeleton">
      <view v-for="i in 4" :key="i" class="sk-card" />
    </view>

    <view v-else-if="isEmpty" class="empty">
      <text class="empty-ico">🗓</text>
      <text class="empty-t">还没有一起吃过的饭</text>
      <text class="empty-d">从今天开始吧，第一顿就会留在这里。</text>
    </view>

    <view v-else class="months">
      <view v-for="group in months" :key="group.month" class="group">
        <view class="gtitle">{{ group.title }}</view>
        <view
          v-for="item in group.list"
          :key="item.id"
          class="hcard"
          :class="{ dim: statusMeta(item.status).gray }"
          @tap="onItem(item)"
        >
          <view class="htop">
            <text class="hwho">{{ item.partnerNickname }} · {{ item.role === 'carrier' ? '我带的' : '我点的' }}</text>
            <text class="htag" :class="{ gray: statusMeta(item.status).gray }">
              {{ statusMeta(item.status).text }}
            </text>
          </view>
          <text class="hdish">{{ dishesText(item.items) }}</text>
          <view class="hmeta">
            <text>{{ dateText(item.planDate) }}</text>
            <text v-if="item.hasCheckin" class="hcheck">
              📸 已打卡 {{ ratingEmoji(item.checkinRating) }}
            </text>
          </view>
        </view>
      </view>

      <view v-if="loading" class="foot">正在翻更早的记录…</view>
      <view v-else-if="!hasMore" class="foot">就这些啦，共 {{ total }} 条 🍚</view>
    </view>
  </view>

  <bottom-sheet :visible="reportVisible" title="上月小结" @close="reportVisible = false">
    <view v-if="report" class="rpanel">
      <view class="rline">
        你带了 {{ report.carrierCount.me }} 次，TA 带了 {{ report.carrierCount.partner }} 次
      </view>
      <view v-if="report.topDishes.length > 0" class="rdishes">
        <text class="rlabel">最常出现的味道</text>
        <view class="rdish-row">
          <text v-for="dish in report.topDishes" :key="dish.name" class="rdish">
            {{ dish.name }} · {{ dish.count }} 次
          </text>
        </view>
      </view>
      <view class="rmeta">打卡 {{ report.checkinDays }} 天 · 爱心值 +{{ report.loveValueDelta }}</view>
      <view class="rtip">这些数字背后，是一次次「我带给你」。</view>
    </view>
  </bottom-sheet>
</template>

<style scoped lang="scss">
.history {
  min-height: 100vh;
  padding: 24rpx 0 60rpx;
  box-sizing: border-box;
  background: var(--cream-bg);
}
.rcard {
  margin: 0 32rpx 8rpx;
  padding: 28rpx 32rpx;
  border-radius: $radius;
  background: linear-gradient(135deg, #fff, #fff3e6);
  box-shadow: var(--shadow);
}
.rtitle {
  font-size: 30rpx;
  font-weight: 800;
  color: var(--ink);
}
.rline {
  margin-top: 12rpx;
  font-size: 28rpx;
  color: var(--ink);
  line-height: 1.6;
}
.rmeta {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: var(--ink-2);
}
.rmore {
  margin-top: 12rpx;
  font-size: 22rpx;
  color: var(--orange);
}
.filters {
  padding: 12rpx 32rpx 4rpx;
}
.frow {
  display: flex;
  gap: 12rpx;
  flex-wrap: wrap;
}
.frow-role {
  margin-top: 12rpx;
}
.chip {
  padding: 10rpx 24rpx;
  border-radius: 24rpx;
  background: #fff;
  border: 2rpx solid var(--line);
  font-size: 24rpx;
  color: var(--ink-2);
}
.chip.on {
  background: #ffe9dc;
  border-color: var(--peach);
  color: #d2683c;
  font-weight: 700;
}
.skeleton {
  padding: 12rpx 32rpx 0;
}
.sk-card {
  height: 160rpx;
  border-radius: $radius;
  background: #fff;
  margin-bottom: 20rpx;
  box-shadow: var(--shadow-sm);
}
.empty {
  margin: 120rpx 32rpx 0;
  padding: 64rpx 40rpx;
  background: #fff;
  border-radius: $radius;
  box-shadow: var(--shadow-sm);
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}
.empty-ico {
  font-size: 80rpx;
}
.empty-t {
  margin-top: 18rpx;
  font-size: 32rpx;
  font-weight: 800;
  color: var(--ink);
}
.empty-d {
  margin-top: 10rpx;
  font-size: 26rpx;
  color: var(--ink-2);
  line-height: 1.7;
}
.gtitle {
  padding: 20rpx 40rpx 6rpx;
  font-size: 24rpx;
  font-weight: 800;
  color: var(--ink-2);
}
.hcard {
  margin: 0 32rpx 20rpx;
  padding: 24rpx 28rpx;
  background: #fff;
  border-radius: $radius;
  box-shadow: var(--shadow-sm);
}
.hcard.dim {
  opacity: 0.68;
}
.htop {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.hwho {
  font-size: 26rpx;
  font-weight: 700;
  color: var(--ink);
}
.htag {
  padding: 4rpx 16rpx;
  border-radius: 18rpx;
  background: #ffe9dc;
  color: #d2683c;
  font-size: 22rpx;
  font-weight: 700;
}
.htag.gray {
  background: #efe9e3;
  color: var(--ink-2);
}
.hdish {
  display: block;
  margin-top: 12rpx;
  font-size: 30rpx;
  color: var(--ink);
}
.hmeta {
  margin-top: 12rpx;
  display: flex;
  align-items: center;
  gap: 16rpx;
  font-size: 22rpx;
  color: var(--ink-2);
}
.hcheck {
  color: var(--orange);
}
.foot {
  padding: 20rpx 0 8rpx;
  text-align: center;
  font-size: 24rpx;
  color: var(--ink-2);
}
.rpanel {
  display: flex;
  flex-direction: column;
}
.rdishes {
  margin-top: 20rpx;
}
.rlabel {
  font-size: 24rpx;
  color: var(--ink-2);
}
.rdish-row {
  margin-top: 10rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}
.rdish {
  padding: 8rpx 20rpx;
  border-radius: 20rpx;
  background: var(--cream-bg);
  font-size: 24rpx;
  color: var(--ink);
}
.rtip {
  margin-top: 20rpx;
  font-size: 24rpx;
  color: var(--ink-2);
}
</style>
