<script setup lang="ts">
// 2.2 今日约定页（Tab1）—— 20-约定/前端.md §1 页面→接口映射。
// 关系卡（appState.relation，冷启动由 bootstrap 灌）+ 今日约定列表（GET /api/plan/today）
// + 「我想吃 / 我带饭」双入口（source=0 / source=1 → pages/plan/create）。
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { get, post } from '@/utils/api'
import { appState, applyCoupleInfo } from '@/stores/app'
import { resolveAsset } from '@/utils/asset'
import { toast } from '@/utils/toast'
import type { PlanBrief, PlanStatus, PlanTodayResp, CoupleInfo } from '@/types/api'

/* ---------------- 状态标签：七档全覆盖（Record<PlanStatus,...> 少了档位会编译报错） ---------------- */
interface StatusMeta {
  label: string
  cls: string
}
const STATUS_META: Record<PlanStatus, StatusMeta> = {
  0: { label: '待认领', cls: 'tag-wait' },
  1: { label: '待接收', cls: 'tag-wait' },
  2: { label: '备餐中', cls: 'tag-cook' },
  3: { label: '已交付', cls: 'tag-give' },
  4: { label: '已完成', cls: 'tag-done' },
  5: { label: '已取消', cls: 'tag-cancel' },
  6: { label: '已过期', cls: 'tag-expire' },
}

const WEEK_LABELS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

/* ---------------- 页面数据 ---------------- */
const today = ref('')
const isWorkday = ref(true)
const list = ref<PlanBrief[]>([])
const loading = ref(false)
const skeleton = ref(false)
const failed = ref(false)

// 骨架屏 >300ms 才显示、>8s 转错误态（PRD §12.2）；计时器句柄只活在一次请求内
let skeletonTimer: ReturnType<typeof setTimeout> | null = null
let timeoutTimer: ReturnType<typeof setTimeout> | null = null

function startTimers(): void {
  if (list.value.length > 0) return // 已有数据时刷新，不进骨架屏
  skeletonTimer = setTimeout(() => {
    if (loading.value) skeleton.value = true
  }, 300)
  timeoutTimer = setTimeout(() => {
    if (loading.value) failed.value = true
  }, 8000)
}

function stopTimers(): void {
  if (skeletonTimer) clearTimeout(skeletonTimer)
  if (timeoutTimer) clearTimeout(timeoutTimer)
  skeletonTimer = null
  timeoutTimer = null
}

/** 服务端下发的当天日期，前端不自己算（PlanTodayResp.date 注释） */
const dateLine = computed(() => {
  if (!today.value) return ''
  const t = new Date(today.value.replace(/-/g, '/'))
  if (isNaN(t.getTime())) return today.value
  return `${today.value} · ${WEEK_LABELS[t.getDay()]}`
})

/* ---------------- 关系卡：称呼优先于微信昵称（PRD §5.1） ---------------- */
const myName = computed(
  () => appState.relation?.nicknamePartner || appState.relation?.myNickname || '我',
)
const partnerName = computed(
  () => appState.relation?.nicknameMine || appState.relation?.partnerNickname || 'TA',
)
const myAvatar = computed(() => resolveAsset(appState.relation?.myAvatar))
const partnerAvatar = computed(() => resolveAsset(appState.relation?.partnerAvatar))
const loveTotal = computed(
  () => (appState.relation?.myLove || 0) + (appState.relation?.partnerLove || 0),
)

/* ---------------- 取数 ---------------- */
async function load(silent = false): Promise<void> {
  if (loading.value) return
  loading.value = true
  failed.value = false
  startTimers()
  try {
    const resp = await get<PlanTodayResp>('/api/plan/today')
    today.value = resp.date
    isWorkday.value = resp.isWorkday
    list.value = resp.list ?? []
  } catch {
    if (!silent) failed.value = true
  } finally {
    stopTimers()
    loading.value = false
    skeleton.value = false
  }
}

/** 关系信息不落盘（§5.1），首次进页若尚无则补拉一次 */
async function loadRelationIfEmpty(): Promise<void> {
  if (appState.relation) return
  try {
    const info = await get<CoupleInfo>('/api/couple/info')
    applyCoupleInfo(info)
  } catch {
    // 1002 → request.ts 已 reLaunch 配对页；其余保持占位文案
  }
}

onShow(() => {
  void load(true)
  void loadRelationIfEmpty()
})

onPullDownRefresh(() => {
  void load(true).then(() => uni.stopPullDownRefresh())
})

/* ---------------- 卡片文案 ---------------- */
function statusMeta(status: PlanStatus): StatusMeta {
  return STATUS_META[status]
}

/** 谁发起的这顿（role 由后端下发，前端不比 userId） */
function whoLine(p: PlanBrief): string {
  if (p.role === 'orderer') {
    return p.carrierNickname ? `${partnerName.value} 给你带` : `我点的 · 等 ${partnerName.value} 来带`
  }
  return `${partnerName.value} 想吃`
}

/** 餐段标签，与详情页文案一致（mealType 1=午饭 2=晚饭） */
function mealLabel(p: PlanBrief): string {
  return p.mealType === 1 ? '午饭' : '晚饭'
}

/** 一句话状态说明，替代冷冰冰的状态名（PRD §12.11 文案调性） */
function hintLine(p: PlanBrief): string {
  const mine = p.role === 'orderer'
  if (p.status === 0) return mine ? `等 ${partnerName.value} 来带` : 'TA 在等你认领'
  if (p.status === 1) return mine ? 'TA 说要带给你，等你收下' : `等 ${partnerName.value} 确认接收`
  if (p.status === 2) return mine ? 'TA 正在给你准备' : '记得早点准备哦'
  if (p.status === 3) return mine ? '饭到了，去拿吧' : '等 TA 来拿啦'
  if (p.status === 4) return '已收到爱心值 ❤'
  if (p.status === 5) return '这次取消了'
  return '错过了这次'
}

/** 只有「对方点的、还没人带」时才给快捷认领（后端仍会二次校验） */
function canClaim(p: PlanBrief): boolean {
  return p.status === 0 && p.role === 'carrier'
}

async function onClaim(p: PlanBrief): Promise<void> {
  if (loading.value) return
  loading.value = true
  try {
    await post(`/api/plan/${p.id}/claim`, {})
    toast('接下来交给你啦')
  } catch {
    // 1004/1005 等文案由 request.ts 统一 toast
  } finally {
    loading.value = false
    await load(true)
  }
}

/* ---------------- 跳转 ---------------- */
function goCreate(source: 0 | 1): void {
  uni.navigateTo({ url: `/pages/plan/create?source=${source}` })
}

function goDetail(id: number): void {
  uni.navigateTo({ url: `/pages/plan/detail?id=${id}` })
}
</script>

<template>
  <view class="today">
    <!-- 关系卡（10-关系绑定/前端.md §2.9：专属称呼优先、两个天数、累计爱心值） -->
    <view class="card relcard">
      <view class="avatars">
        <image v-if="myAvatar" class="ava" :src="myAvatar" mode="aspectFill" />
        <view v-else class="ava ph">🐰</view>
        <view class="heart">💞</view>
        <image v-if="partnerAvatar" class="ava" :src="partnerAvatar" mode="aspectFill" />
        <view v-else class="ava ph">🐻</view>
      </view>
      <view class="rel-name">{{ myName }} & {{ partnerName }}</view>
      <view class="rel-meta">
        在一起 {{ appState.relation?.togetherDays ?? 0 }} 天 · 一起吃饭
        {{ appState.relation?.mealDays ?? 0 }} 天
      </view>
      <view class="love-pill">❤ 总爱心值 {{ loveTotal }}</view>
    </view>

    <view class="sec-head">
      <text class="sec-title">今天</text>
      <text class="sec-sub">{{ dateLine }}</text>
    </view>

    <!-- 三级错误态（PRD §12.2） -->
    <view v-if="failed" class="card err">
      <view class="err-ico">🍳</view>
      <view class="err-t">网络开小差了</view>
      <view class="err-d">没能拿到今天的约定，重试一下？</view>
      <view class="btn-ghost" @tap="load()">重新加载</view>
    </view>

    <!-- 骨架屏：>300ms 才显示 -->
    <view v-else-if="skeleton" class="sk-wrap">
      <view class="sk-card"><view class="sk-line w60" /><view class="sk-line w40" /></view>
      <view class="sk-card"><view class="sk-line w50" /><view class="sk-line w70" /></view>
    </view>

    <template v-else>
      <!-- 非工作日提示：周末不自动排班，但可以随时手动发起（PRD §2） -->
      <view v-if="!isWorkday" class="card weekend">
        <text class="wk-ico">🌿</text>
        <text class="wk-t">今天是周末，不用排班</text>
        <text class="wk-d">想给 TA 带饭的话，随时可以发起一条</text>
      </view>

      <!-- 空态（PRD §12.10 四类空态之一，文案照抄） -->
      <view v-if="list.length === 0" class="card empty">
        <view class="empty-ico">🍽</view>
        <view class="empty-t">今天还没有约定</view>
        <view class="empty-d">发一条「我想吃」，让 TA 来带饭吧</view>
        <view class="btn-primary" @tap="goCreate(0)">我想吃</view>
      </view>

      <view v-for="p in list" :key="p.id" class="card plan" @tap="goDetail(p.id)">
        <view class="plan-top">
          <view class="plan-who">{{ whoLine(p) }}</view>
          <view class="plan-tags">
            <text class="meal-tag">{{ mealLabel(p) }}</text>
            <view class="tag" :class="statusMeta(p.status).cls">
              {{ statusMeta(p.status).label }}
            </view>
          </view>
        </view>
        <view class="plan-dish">{{ (p.items || []).join(' · ') }}</view>
        <view v-if="p.remark" class="plan-remark">{{ p.remark }}</view>
        <view class="plan-foot">
          <text class="plan-hint">{{ hintLine(p) }}</text>
          <view v-if="canClaim(p)" class="mini-btn" @tap.stop="onClaim(p)">我来带 🛍</view>
        </view>
      </view>
    </template>

    <!-- 双入口：第一人称动词（PRD §12.11） -->
    <view class="float-actions">
      <view class="btn btn-ghost" @tap="goCreate(0)">🍚 我想吃</view>
      <view class="btn btn-primary" @tap="goCreate(1)">🛍 我带饭</view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.today {
  min-height: 100vh;
  padding: 24rpx 0 260rpx;
  box-sizing: border-box;
  background: var(--cream-bg);
}

/* —— 关系卡 —— */
.relcard {
  margin: 0 32rpx;
  padding: 32rpx 32rpx 28rpx;
  background: linear-gradient(135deg, #fff, #fff1e8);
  display: flex;
  flex-direction: column;
  align-items: center;
}
.avatars {
  display: flex;
  align-items: center;
  gap: 20rpx;
}
.ava {
  width: 108rpx;
  height: 108rpx;
  border-radius: 50%;
  border: 4rpx solid var(--peach);
  background: var(--line);
}
.ava.ph {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48rpx;
}
.heart {
  font-size: 36rpx;
}
.rel-name {
  margin-top: 16rpx;
  font-size: 32rpx;
  font-weight: 800;
  color: var(--ink);
}
.rel-meta {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: var(--ink-2);
}
.love-pill {
  margin-top: 16rpx;
  padding: 8rpx 24rpx;
  border-radius: 999rpx;
  background: var(--peach);
  color: #fff;
  font-size: 24rpx;
  font-weight: 700;
}

/* —— 区块头 —— */
.sec-head {
  display: flex;
  align-items: baseline;
  gap: 16rpx;
  padding: 36rpx 40rpx 16rpx;
}
.sec-title {
  font-size: 34rpx;
  font-weight: 800;
  color: var(--ink);
}
.sec-sub {
  font-size: 24rpx;
  color: var(--ink-2);
}

/* —— 周末提示 —— */
.weekend {
  margin: 0 32rpx 8rpx;
  padding: 28rpx 32rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  background: linear-gradient(135deg, #fff, #f7fbf6);
}
.wk-ico {
  font-size: 52rpx;
}
.wk-t {
  margin-top: 8rpx;
  font-size: 30rpx;
  font-weight: 700;
  color: var(--ink);
}
.wk-d {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: var(--ink-2);
}

/* —— 约定卡 —— */
.plan {
  margin: 16rpx 32rpx 0;
  padding: 28rpx 32rpx;
}
.plan-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.plan-who {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--ink);
}
.plan-tags {
  display: flex;
  align-items: center;
  gap: 12rpx;
  flex: none;
}
.meal-tag {
  font-size: 22rpx;
  font-weight: 700;
  padding: 4rpx 16rpx;
  border-radius: 999rpx;
  background: #fff0e2;
  color: #ff9b6a;
}
.tag {
  font-size: 22rpx;
  font-weight: 700;
  padding: 4rpx 16rpx;
  border-radius: 999rpx;
}
.tag-wait {
  background: #ffe3d6;
  color: #f47b45;
}
.tag-cook {
  background: #fff0e2;
  color: #ff9b6a;
}
.tag-give {
  background: #ffe9cf;
  color: #d97a2b;
}
.tag-done {
  background: #eaf7ec;
  color: #3fa866;
}
.tag-cancel {
  background: #efe9e3;
  color: var(--ink-2);
}
.tag-expire {
  background: #fff3d6;
  color: #c58f34;
}
.plan-dish {
  margin-top: 12rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: var(--ink);
}
.plan-remark {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: var(--ink-2);
}
.plan-foot {
  margin-top: 16rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.plan-hint {
  font-size: 24rpx;
  color: var(--ink-2);
}
.mini-btn {
  padding: 10rpx 28rpx;
  border-radius: 999rpx;
  background: var(--orange);
  color: #fff;
  font-size: 26rpx;
  font-weight: 700;
}

/* —— 空态 / 错误态 —— */
.empty,
.err {
  margin: 16rpx 32rpx 0;
  padding: 56rpx 32rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.empty-ico,
.err-ico {
  font-size: 80rpx;
}
.empty-t,
.err-t {
  margin-top: 16rpx;
  font-size: 32rpx;
  font-weight: 700;
  color: var(--ink);
}
.empty-d,
.err-d {
  margin-top: 8rpx;
  font-size: 26rpx;
  color: var(--ink-2);
  text-align: center;
}
.btn-primary,
.btn-ghost {
  margin-top: 24rpx;
  padding: 16rpx 56rpx;
  border-radius: 999rpx;
  font-size: 28rpx;
  font-weight: 700;
}
.btn-primary {
  background: var(--orange);
  color: #fff;
}
.btn-ghost {
  background: #fff;
  color: var(--ink-2);
  border: 2rpx solid var(--line);
}

/* —— 骨架屏 —— */
.sk-wrap {
  padding: 0 32rpx;
}
.sk-card {
  margin-top: 16rpx;
  padding: 28rpx 32rpx;
  border-radius: $radius;
  background: #fff;
  box-shadow: var(--shadow-sm);
}
.sk-line {
  height: 28rpx;
  border-radius: 12rpx;
  background: linear-gradient(90deg, #f2e9e0 25%, #fbf3ec 37%, #f2e9e0 63%);
  background-size: 400% 100%;
  animation: shimmer 1.2s infinite linear;
}
.sk-line.w60 {
  width: 60%;
}
.sk-line.w50 {
  width: 50%;
}
.sk-line.w40 {
  width: 40%;
  margin-top: 16rpx;
}
.sk-line.w70 {
  width: 70%;
  margin-top: 16rpx;
}
@keyframes shimmer {
  from {
    background-position: 100% 50%;
  }
  to {
    background-position: 0 50%;
  }
}

/* —— 双入口 —— */
.float-actions {
  position: fixed;
  left: 32rpx;
  right: 32rpx;
  bottom: 40rpx;
  display: flex;
  gap: 24rpx;
  z-index: 10;
}
.btn {
  flex: 1;
  height: 96rpx;
  line-height: 96rpx;
  text-align: center;
  border-radius: 999rpx;
  font-size: 30rpx;
  font-weight: 700;
  margin-top: 0;
  box-shadow: var(--shadow-sm);
}
</style>
