<script setup lang="ts">
// 共同周历（docs/design/90-周历/前端.md §2）：只读视图，「协商谁带」是二期，不接入口。
// 两条硬规则：7 行必须画满（只渲染有约定的天会让布局塌）；周末灰底标「休息」，不可点进发起页。
// 主体用 utils/date 的 weekdaysOf 打底（保证 7 行 + 周末判定），再按 date 合并后端返回的约定。
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { get } from '@/utils/api'
import { daysFromToday, weekdaysOf } from '@/utils/date'
import type { DateItem } from '@/utils/date'
import { appState } from '@/stores/app'
import type { PlanStatus, PlanWeekResp, WeekPlanItem } from '@/types/api'

/** 周历一行：本地日期骨架 + 后端约定 */
interface WeekRow extends DateItem {
  plans: WeekPlanItem[]
}

/** 左右切换上限 ±4 周，超出箭头置灰 */
const MAX_OFFSET = 4

const STATUS_TEXT: Record<PlanStatus, string> = {
  0: '待认领',
  1: '待接收',
  2: '备餐中',
  3: '已交付',
  4: '已完成',
  5: '已取消',
  6: '已过期',
}

const weekOffset = ref(0)
const rows = ref<WeekRow[]>([])
const loading = ref(false)
const loaded = ref(false)

/** 对方称呼：优先专属称呼，其次微信昵称 */
const partnerName = computed(
  () => appState.relation?.nicknameMine || appState.relation?.partnerNickname || 'TA',
)

const canPrev = computed(() => weekOffset.value > -MAX_OFFSET)
const canNext = computed(() => weekOffset.value < MAX_OFFSET)

/** 本周一：按整周跳，不做逐日偏移 */
const monday = computed(() => {
  const base = new Date()
  base.setDate(base.getDate() + weekOffset.value * 7)
  return weekdaysOf(base)[0].date
})

const titleText = computed(() => {
  const n = weekOffset.value
  if (n === 0) return '本周'
  if (n === -1) return '上周'
  if (n === 1) return '下周'
  return n < 0 ? `${Math.abs(n)} 周前` : `${n} 周后`
})

function statusText(status: PlanStatus): string {
  return STATUS_TEXT[status] ?? ''
}

/** 带饭人：role 由后端按 carrier_id == me 算好下发，前端不比 userId */
function carrierText(plan: WeekPlanItem): string {
  return plan.role === 'carrier' ? '我带' : `${partnerName.value}带`
}

function dishesText(items: string[]): string {
  return items.length > 0 ? items.join(' · ') : ''
}

function dateText(date: string): string {
  return date.slice(5).replace('-', '/')
}

async function loadWeek(): Promise<void> {
  if (loading.value) return
  loading.value = true
  try {
    const resp = await get<PlanWeekResp>('/api/plan/week', { startDate: monday.value })
    const dayMap = new Map((resp.days ?? []).map((d) => [d.date, d.plans]))
    // 用本地 7 天打底，后端缺哪天也不会少一行
    rows.value = weekdaysOf(monday.value).map((d) => ({
      ...d,
      plans: dayMap.get(d.date) ?? [],
    }))
  } catch {
    // 1002 已由 request 层 reLaunch 配对页；失败时保留上一次的行，不留白屏
  } finally {
    loading.value = false
    loaded.value = true
  }
}

function shiftWeek(delta: number): void {
  const next = weekOffset.value + delta
  if (next < -MAX_OFFSET || next > MAX_OFFSET) return
  weekOffset.value = next
  void loadWeek()
}

function backToThisWeek(): void {
  if (weekOffset.value === 0) return
  weekOffset.value = 0
  void loadWeek()
}

function onRow(row: WeekRow): void {
  // 周末是休息日，点进去后端必然 1009，前端直接禁用
  if (!row.isWorkday) return
  if (row.plans.length > 0) {
    uni.navigateTo({ url: `/pages/plan/detail?id=${row.plans[0].id}` })
    return
  }
  // 过期的工作日不能再发起
  if (daysFromToday(row.date) < 0) return
  uni.navigateTo({ url: `/pages/plan/create?planDate=${row.date}` })
}

onLoad(() => {
  void loadWeek()
})

// 从详情页返回本页时重取一次（前端-00 基础设施 §10）
onShow(() => {
  if (!loaded.value) void loadWeek()
})
</script>

<template>
  <view class="week">
    <view class="head">
      <text class="arrow" :class="{ off: !canPrev }" @tap="shiftWeek(-1)">‹</text>
      <text class="title">{{ titleText }}</text>
      <text class="arrow" :class="{ off: !canNext }" @tap="shiftWeek(1)">›</text>
      <text v-if="weekOffset !== 0" class="back" @tap="backToThisWeek">回到本周</text>
    </view>
    <view class="sub">周一到周五，谁带一目了然；周末就该好好休息 🌿</view>

    <view class="rows">
      <view
        v-for="row in rows"
        :key="row.date"
        class="row"
        :class="{ today: row.isToday, rest: !row.isWorkday }"
        @tap="onRow(row)"
      >
        <view class="rleft">
          <text class="rdate">{{ dateText(row.date) }}</text>
          <text class="rweek">{{ row.label }}</text>
        </view>

        <view v-if="!row.isWorkday" class="rrest">休息</view>

        <view v-else-if="row.plans.length > 0" class="rplans">
          <view v-for="plan in row.plans" :key="plan.id" class="rplan">
            <text class="rwho">{{ carrierText(plan) }}</text>
            <text class="rdish">{{ dishesText(plan.items) }}</text>
            <text class="rtag">{{ statusText(plan.status) }}</text>
          </view>
        </view>

        <view v-else class="rnone">
          <text>{{ daysFromToday(row.date) < 0 ? '那天没约' : '还没约' }}</text>
          <text v-if="daysFromToday(row.date) >= 0" class="rgo">去约一个 ›</text>
        </view>
      </view>
    </view>

    <view v-if="!loaded" class="foot">正在排这一周的饭…</view>
  </view>
</template>

<style scoped lang="scss">
.week {
  min-height: 100vh;
  padding: 24rpx 0 60rpx;
  box-sizing: border-box;
  background: var(--cream-bg);
}
.head {
  padding: 12rpx 32rpx 0;
  display: flex;
  align-items: center;
  gap: 20rpx;
}
.arrow {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: #fff;
  box-shadow: var(--shadow-sm);
  text-align: center;
  line-height: 56rpx;
  font-size: 36rpx;
  color: var(--ink);
}
.arrow.off {
  color: #cbbdae;
  background: #f6efe8;
  box-shadow: none;
}
.title {
  font-size: 34rpx;
  font-weight: 800;
  color: var(--ink);
}
.back {
  margin-left: auto;
  font-size: 24rpx;
  color: var(--orange);
}
.sub {
  padding: 8rpx 32rpx 16rpx;
  font-size: 24rpx;
  color: var(--ink-2);
}
.rows {
  padding: 0 32rpx;
}
.row {
  position: relative;
  margin-bottom: 16rpx;
  padding: 22rpx 24rpx;
  background: #fff;
  border-radius: $radius;
  box-shadow: var(--shadow-sm);
  display: flex;
  align-items: center;
  overflow: hidden;
}
.row.today::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 8rpx;
  background: var(--orange);
}
.row.today {
  padding-left: 32rpx;
}
.row.rest {
  background: #f6efe8;
  box-shadow: none;
}
.rleft {
  width: 120rpx;
  flex: none;
  display: flex;
  flex-direction: column;
}
.rdate {
  font-size: 26rpx;
  font-weight: 700;
  color: var(--ink);
}
.rweek {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: var(--ink-2);
}
.rrest {
  flex: 1;
  text-align: center;
  font-size: 26rpx;
  color: var(--ink-2);
}
.rplans {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.rplan {
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.rwho {
  padding: 4rpx 16rpx;
  border-radius: 18rpx;
  background: #ffe9dc;
  color: #d2683c;
  font-size: 22rpx;
  font-weight: 700;
  flex: none;
}
.rdish {
  flex: 1;
  font-size: 28rpx;
  color: var(--ink);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.rtag {
  font-size: 20rpx;
  color: var(--ink-2);
  flex: none;
}
.rnone {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 26rpx;
  color: var(--ink-2);
}
.rgo {
  font-size: 22rpx;
  color: var(--orange);
}
.foot {
  padding: 20rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: var(--ink-2);
}
</style>
