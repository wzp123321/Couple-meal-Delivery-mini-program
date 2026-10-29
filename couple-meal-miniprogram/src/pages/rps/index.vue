<script setup lang="ts">
// phase-2 石头剪刀布协商页（openspec/changes/phase-2/specs/rps-negotiation）。
// 交互：发起对局（按 mealType/planDate 幂等复用）→ 出拳（1石头 2剪刀 3布）→ 平局重出
// → 分出胜负后「采纳结果」生成「输家主动带」约定（carrier=输家），再跳转约定详情。
// 状态来源：后端 RpsService.RpsGameVO（myChoice=我本回合出拳，opponentThrown=对方是否已出拳）。
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { adoptRps, createRps, currentRps, getRps, throwRps } from '@/utils/api'
import { appState } from '@/stores/app'
import { toast } from '@/utils/toast'
import { formatDate, isWorkday } from '@/utils/date'
import type { MealType, RpsChoice, RpsGameVO } from '@/types/api'

/* ---------------- 出拳元数据 ---------------- */
const CHOICES: { value: RpsChoice; emoji: string; label: string }[] = [
  { value: 1, emoji: '✊', label: '石头' },
  { value: 2, emoji: '✌️', label: '剪刀' },
  { value: 3, emoji: '🖐', label: '布' },
]
function choiceMeta(c: RpsChoice | null): { value: RpsChoice; emoji: string; label: string } | null {
  return CHOICES.find((x) => x.value === c) || null
}

/* ---------------- 关系信息（优先专属称呼） ---------------- */
const myName = computed(() => appState.relation?.nicknamePartner || appState.relation?.myNickname || '我')
const partnerName = computed(
  () => appState.relation?.nicknameMine || appState.relation?.partnerNickname || 'TA',
)
const userId = computed(() => appState.user?.userId ?? 0)

/* ---------------- 发起参数（无进行中对局时用于新建） ---------------- */
const mealType = ref<MealType>(1)
const planDate = ref('')

const WEEK_LABELS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

/** 默认日期：从今天起第一个工作日（周末不可约） */
function firstSelectableDate(): string {
  const base = new Date()
  for (let i = 0; i <= 7; i++) {
    const d = new Date(base.getFullYear(), base.getMonth(), base.getDate() + i)
    if (isWorkday(d)) return formatDate(d)
  }
  return formatDate(base)
}

/** 日期条：今天起的 7 天，周末置灰（仅工作日可约） */
const dateOptions = computed(() => {
  const base = new Date()
  const out: { date: string; label: string; sub: string; disabled: boolean }[] = []
  for (let i = 0; i < 7; i++) {
    const d = new Date(base.getFullYear(), base.getMonth(), base.getDate() + i)
    const date = formatDate(d)
    out.push({
      date,
      label: i === 0 ? '今天' : WEEK_LABELS[d.getDay()],
      sub: `${d.getMonth() + 1}月${d.getDate()}日`,
      disabled: !isWorkday(d),
    })
  }
  return out
})

function onPickDate(o: { date: string; disabled: boolean }): void {
  if (o.disabled) {
    toast('周末不约哦～换个日子')
    return
  }
  planDate.value = o.date
}

/* ---------------- 对局状态 ---------------- */
const game = ref<RpsGameVO | null>(null)
const pendingGameId = ref<number | null>(null)
const loading = ref(false)
const creating = ref(false)
const throwing = ref(false)
const adopting = ref(false)

/** 我的回合是否已出拳 */
const myTurn = computed(() => {
  const g = game.value
  if (!g || g.status !== 0) return false
  return g.myChoice == null
})

/** 比分板提示文案（进行中） */
const playingHint = computed(() => {
  const g = game.value
  if (!g || g.status !== 0) return ''
  if (g.myChoice == null) return `轮到你出拳（第 ${g.currentRound} 回合）`
  if (!g.opponentThrown) return `你出了 ${choiceMeta(g.myChoice)?.label}，等 ${partnerName.value} 出拳…`
  return `平局！再来一局（第 ${g.currentRound} 回合）`
})

/** 是否分出胜负、谁赢 */
const iAmWinner = computed(() => {
  const g = game.value
  return !!g && g.status === 1 && g.winnerId != null && g.winnerId === userId.value
})
/** 建议带饭方 = 输家 */
const carrierName = computed(() => (iAmWinner.value ? partnerName.value : myName.value))
const mealLabel = computed(() => (mealType.value === 2 ? '晚饭' : '午饭'))
/** 进行中对局展示的餐段/日期以对局自身为准，避免与发起参数混淆 */
const gameMealLabel = computed(() => (game.value?.mealType === 2 ? '晚饭' : '午饭'))

/* ---------------- 取数 ---------------- */
async function load(): Promise<void> {
  loading.value = true
  try {
    const gid = game.value?.gameId ?? pendingGameId.value
    if (gid) game.value = await getRps(gid)
    else game.value = await currentRps(mealType.value, planDate.value)
  } catch {
    // 请求层已统一 toast；关系未绑定会 reLaunch 配对页
  } finally {
    loading.value = false
  }
}

/* ---------------- 动作 ---------------- */
async function onCreate(): Promise<void> {
  if (creating.value || loading.value) return
  creating.value = true
  try {
    const g = await createRps({ mealType: mealType.value, planDate: planDate.value })
    game.value = g
    toast('对局开始！出拳吧 👊')
  } catch {
    // 1002 等由请求层处理
  } finally {
    creating.value = false
  }
}

async function onThrow(c: RpsChoice): Promise<void> {
  const g = game.value
  if (!g || g.status !== 0 || throwing.value) return
  throwing.value = true
  try {
    const ng = await throwRps(g.gameId, { choice: c })
    game.value = ng
    if (ng.status === 1) toast('分出胜负啦 🎉')
  } catch {
    // 6004 非法出拳 / 对方已出等由请求层 toast
  } finally {
    throwing.value = false
  }
}

async function onAdopt(): Promise<void> {
  const g = game.value
  if (!g || g.status !== 1 || adopting.value) return
  adopting.value = true
  try {
    const resp = await adoptRps(g.gameId, {
      items: [{ name: g.mealType === 2 ? '今晚的饭' : '今天的饭' }],
      remark: '',
    })
    toast('已生成约定 💌')
    uni.redirectTo({ url: `/pages/plan/detail?id=${resp.planId}` })
  } catch {
    // 6003 等由请求层 toast
  } finally {
    adopting.value = false
  }
}

function goDetail(): void {
  const pid = game.value?.planId
  if (pid) uni.redirectTo({ url: `/pages/plan/detail?id=${pid}` })
}

/* ---------------- 生命周期 ---------------- */
onLoad((query?: Record<string, string>) => {
  const mt = Number(query?.mealType)
  mealType.value = mt === 2 ? 2 : 1
  const qDate = query?.planDate
  planDate.value = qDate && qDate.length >= 10 ? qDate : firstSelectableDate()
  const qGame = Number(query?.gameId)
  if (query?.gameId && qGame > 0) pendingGameId.value = qGame
  void load()
})

onShow(() => {
  void load()
})
</script>

<template>
  <view class="rps">
    <!-- 头部 -->
    <view class="head">
      <view class="title">猜拳决定谁带 🎮</view>
      <view class="sub">输家带饭，愿赌服输～</view>
    </view>

    <!-- 加载态 -->
    <view v-if="loading && !game" class="card empty">
      <view class="empty-ico">🎲</view>
      <view class="empty-t">加载中…</view>
    </view>

    <!-- 无进行中对局：发起区 -->
    <view v-else-if="!game" class="card block">
      <view class="label">哪一餐</view>
      <view class="seg">
        <view class="seg-item" :class="{ on: mealType === 1 }" @tap="mealType = 1">🍱 午饭</view>
        <view class="seg-item" :class="{ on: mealType === 2 }" @tap="mealType = 2">🌙 晚饭</view>
      </view>

      <view class="label" style="margin-top: 24rpx">哪天</view>
      <scroll-view class="dates" scroll-x :show-scrollbar="false">
        <view class="dates-inner">
          <view
            v-for="o in dateOptions"
            :key="o.date"
            class="date"
            :class="{ on: o.date === planDate, off: o.disabled }"
            @tap="onPickDate(o)"
          >
            <text class="d-label">{{ o.label }}</text>
            <text class="d-sub">{{ o.sub }}</text>
          </view>
        </view>
      </scroll-view>

      <view class="btn-primary" :class="{ disabled: creating }" @tap="onCreate">
        {{ creating ? '发起中…' : '开始对局 ✊' }}
      </view>
      <view class="tip">同一天同一餐只会有一次对局，重复发起会接着上一局</view>
    </view>

    <!-- 进行中 / 已完成：对局板 -->
    <template v-else>
      <!-- 上下文 -->
      <view class="card ctx">
        <text class="ctx-meal">{{ gameMealLabel }} · {{ game?.planDate }}</text>
        <text class="ctx-round">第 {{ game?.currentRound }} 回合</text>
      </view>

      <!-- 进行中 -->
      <view v-if="game && game.status === 0" class="card board">
        <!-- 我的出拳 -->
        <view class="row">
          <view class="who">你</view>
          <view class="pick">
            <text v-if="game.myChoice != null" class="big">{{ choiceMeta(game.myChoice)?.emoji }}</text>
            <text v-else class="big ph">？</text>
            <text class="who-sub">{{ game.myChoice != null ? choiceMeta(game.myChoice)?.label : '待出拳' }}</text>
          </view>
        </view>

        <view class="vs">VS</view>

        <!-- 对方出拳（VO 不暴露对方具体出拳，仅提示是否已出） -->
        <view class="row">
          <view class="who">{{ partnerName }}</view>
          <view class="pick">
            <text v-if="game.opponentThrown" class="big">🤝</text>
            <text v-else class="big ph">？</text>
            <text class="who-sub">{{ game.opponentThrown ? '已出拳' : '等待出拳' }}</text>
          </view>
        </view>

        <view class="hint">{{ playingHint }}</view>

        <!-- 出拳按钮：轮到我且本回合未出 -->
        <view v-if="myTurn" class="choices">
          <view
            v-for="c in CHOICES"
            :key="c.value"
            class="choice"
            :class="{ on: game.myChoice === c.value }"
            @tap="onThrow(c.value)"
          >
            <text class="c-emoji">{{ c.emoji }}</text>
            <text class="c-label">{{ c.label }}</text>
          </view>
        </view>
        <view v-else class="wait">
          {{ game.myChoice != null && !game.opponentThrown ? '已出拳，等对方开奖 🎰' : '平局就重出，直到分出胜负～' }}
        </view>
      </view>

      <!-- 已完成：结果与采纳 -->
      <view v-else-if="game && game.status === 1" class="card board result">
        <view class="result-emoji">{{ iAmWinner ? '🎉' : '😜' }}</view>
        <view class="result-title">{{ iAmWinner ? '你赢啦！' : '你输啦～' }}</view>
        <view class="result-line">
          建议带饭方：<text class="hl">{{ carrierName }}</text>（输家）
        </view>

        <view v-if="game.planId" class="btn-primary" @tap="goDetail">查看约定 💌</view>
        <view v-else class="btn-primary" :class="{ disabled: adopting }" @tap="onAdopt">
          {{ adopting ? '生成中…' : '采纳结果，生成约定 🍱' }}
        </view>
        <view class="tip">采纳才会生成「主动带」约定，也可以不改，由胜者主动请客～</view>
      </view>
    </template>

    <view class="foot-note">猜拳结果仅供参考，带饭是甜蜜的惩罚 💕</view>
  </view>
</template>

<style scoped lang="scss">
.rps {
  min-height: 100vh;
  padding: 24rpx 0 200rpx;
  box-sizing: border-box;
  background: var(--cream-bg);
}

.head {
  padding: 8rpx 40rpx 24rpx;
}
.title {
  font-size: 40rpx;
  font-weight: 800;
  color: var(--ink);
}
.sub {
  margin-top: 8rpx;
  font-size: 26rpx;
  color: var(--ink-2);
}

.block {
  margin: 24rpx 32rpx 0;
  padding: 28rpx 32rpx;
}

.label {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--ink);
}

/* 餐段分段 */
.seg {
  margin-top: 20rpx;
  display: flex;
  gap: 16rpx;
}
.seg-item {
  flex: 1;
  height: 72rpx;
  line-height: 72rpx;
  text-align: center;
  border-radius: $radius-sm;
  font-size: 26rpx;
}
.seg-item.on {
  background: var(--peach);
  color: var(--ink);
  font-weight: 700;
}

/* 日期条 */
.dates {
  margin-top: 20rpx;
  white-space: nowrap;
}
.dates-inner {
  display: inline-flex;
  gap: 16rpx;
}
.date {
  width: 132rpx;
  padding: 16rpx 0;
  border-radius: $radius-sm;
  background: var(--line);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
}
.date.on {
  background: var(--orange);
}
.date.off {
  opacity: 0.45;
}
.d-label {
  font-size: 26rpx;
  font-weight: 700;
  color: var(--ink);
}
.date.on .d-label {
  color: #fff;
}
.d-sub {
  font-size: 20rpx;
  color: var(--ink-2);
}
.date.on .d-sub {
  color: #fff;
}

/* 主按钮 */
.btn-primary {
  margin-top: 32rpx;
  height: 96rpx;
  line-height: 96rpx;
  text-align: center;
  border-radius: 999rpx;
  background: var(--orange);
  color: #fff;
  font-size: 30rpx;
  font-weight: 700;
  box-shadow: var(--shadow-sm);
}
.btn-primary.disabled {
  opacity: 0.6;
}

.tip {
  margin-top: 14rpx;
  font-size: 22rpx;
  color: var(--ink-2);
}

/* 上下文卡 */
.ctx {
  margin: 24rpx 32rpx 0;
  padding: 20rpx 32rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.ctx-meal {
  font-size: 26rpx;
  font-weight: 700;
  color: var(--ink);
}
.ctx-round {
  font-size: 24rpx;
  color: var(--ink-2);
}

/* 对局板 */
.board {
  margin: 16rpx 32rpx 0;
  padding: 36rpx 32rpx 40rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.row {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.who {
  font-size: 30rpx;
  font-weight: 800;
  color: var(--ink);
}
.pick {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6rpx;
}
.big {
  font-size: 96rpx;
  line-height: 1;
}
.big.ph {
  color: var(--line);
  font-weight: 700;
}
.who-sub {
  font-size: 24rpx;
  color: var(--ink-2);
}
.vs {
  margin: 12rpx 0;
  font-size: 28rpx;
  font-weight: 800;
  color: var(--peach);
}
.hint {
  margin-top: 16rpx;
  font-size: 26rpx;
  color: var(--ink-2);
  text-align: center;
}

/* 出拳按钮 */
.choices {
  margin-top: 28rpx;
  display: flex;
  gap: 20rpx;
}
.choice {
  flex: 1;
  height: 150rpx;
  border-radius: $radius;
  background: #fff;
  box-shadow: var(--shadow-sm);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6rpx;
  border: 4rpx solid transparent;
}
.choice.on {
  border-color: var(--orange);
}
.c-emoji {
  font-size: 64rpx;
  line-height: 1;
}
.c-label {
  font-size: 24rpx;
  color: var(--ink-2);
}
.wait {
  margin-top: 28rpx;
  font-size: 26rpx;
  color: var(--ink-2);
  text-align: center;
}

/* 结果 */
.result {
  align-items: center;
}
.result-emoji {
  font-size: 96rpx;
}
.result-title {
  margin-top: 8rpx;
  font-size: 36rpx;
  font-weight: 800;
  color: var(--ink);
}
.result-line {
  margin-top: 16rpx;
  font-size: 28rpx;
  color: var(--ink-2);
}
.hl {
  color: var(--orange);
  font-weight: 800;
}

/* 空态 */
.empty {
  margin: 24rpx 32rpx 0;
  padding: 56rpx 32rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.empty-ico {
  font-size: 80rpx;
}
.empty-t {
  margin-top: 16rpx;
  font-size: 30rpx;
  font-weight: 700;
  color: var(--ink);
}

.foot-note {
  margin-top: 40rpx;
  text-align: center;
  font-size: 22rpx;
  color: var(--ink-2);
}
</style>
