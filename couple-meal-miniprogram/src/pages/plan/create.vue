<script setup lang="ts">
// 2.3 发起约定页 —— 20-约定/前端.md §2。
// 双角色共用一个表单，差异只在 source（0 我想吃 → 点餐让别人带 / 1 我带饭 → 主动带）。
// 三条硬规则：日期只给 [today, today+7] 内的工作日且按餐段锁当日（午饭 11:00 / 晚饭 18:00）；菜品 1–10 道；
// 忌口只提醒不硬拦（PRD §9 Q2）—— 弹 L3 强提醒后用户确认仍照常提交。
import { computed, ref } from 'vue'
import { onHide, onLoad, onShow } from '@dcloudio/uni-app'
import { get, post } from '@/utils/api'
import BottomSheet from '@/components/bottom-sheet.vue'
import { formatDate } from '@/utils/date'
import { appState } from '@/stores/app'
import { toast } from '@/utils/toast'
import { isWxRemindOn, requestSubscribeConsent } from '@/utils/subscribe'
import type {
  AvoidLevel,
  MealType,
  MenuItem,
  MenuListResp,
  PlanCreateReq,
  PlanCreateResp,
  PlanCreateWarning,
  PlanSource,
} from '@/types/api'

/* ---------------- 常量 ---------------- */
const MAX_DISHES = 10
const MAX_NAME = 20
const MAX_REMARK = 100
/** 发起截止小时按餐段取：午饭 11:00 / 晚饭 18:00（与后端 plan.cutoff.* 一致，PRD §12.6） */
const CUTOFF_HOUR: Record<MealType, number> = { 1: 11, 2: 18 }
const DATE_RANGE = 7 // [today, today+7]
const DRAFT_KEY = 'draft:plan-create' // 前端.md §2 指定的草稿键
const WEEK_LABELS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

/**
 * uni 组件事件对象的结构壳（与 settings 页同一写法）：detail 声明成 unknown，
 * 由 eventValue 取字符串，避免模板里被 vue-tsc 按宿主事件校验时报 TS2345。
 */
interface UniDetailEvent {
  type?: string
  detail?: unknown
}

function eventValue(e: UniDetailEvent): string {
  const d = e.detail as { value?: string } | null | undefined
  return String(d?.value ?? '')
}

/** 一道草稿菜：menuItemId 为空表示手输菜名（PlanItemInput 的语义） */
interface DishDraft {
  key: string
  menuItemId?: number
  name: string
  avoidLevel: AvoidLevel
}

interface DateOption {
  date: string
  label: string
  sub: string
  disabled: boolean
  reason: string
}

interface Draft {
  source: PlanSource
  planDate: string
  dishes: DishDraft[]
  remark: string
}

/* ---------------- 表单状态 ---------------- */
const source = ref<PlanSource>(0)
const planDate = ref('')
const dishes = ref<DishDraft[]>([])
const remark = ref('')
const mealType = ref<MealType>(1) // 默认午饭（PRD §9 Q3），可切晚饭
const submitting = ref(false)

const isOrderer = computed(() => source.value === 0)
const partnerName = computed(
  () => appState.relation?.nicknameMine || appState.relation?.partnerNickname || 'TA',
)

/* ---------------- 可选日期：工作日 + 可约范围 + 按餐段截止 ---------------- */
const dateOptions = ref<DateOption[]>([])

/** 当前餐段的发起截止小时 */
const cutoffHour = computed(() => CUTOFF_HOUR[mealType.value])

function buildDateOptions(): DateOption[] {
  const now = new Date()
  const todayStr = formatDate(now)
  const late = now.getHours() >= cutoffHour.value
  const out: DateOption[] = []

  for (let i = 0; i <= DATE_RANGE; i++) {
    const cur = new Date(now.getFullYear(), now.getMonth(), now.getDate() + i)
    const date = formatDate(cur)
    const day = cur.getDay()
    const sub = `${cur.getMonth() + 1}月${cur.getDate()}日`

    if (day < 1 || day > 5) {
      // 周末置灰但占位显示（前端.md §2：日期选择只给工作日，周末置灰）
      out.push({ date, label: WEEK_LABELS[day], sub, disabled: true, reason: '周末不排班，换个日子吧' })
      continue
    }

    const tooLate = date === todayStr && late
    out.push({
      date,
      label: date === todayStr ? '今天' : WEEK_LABELS[day],
      sub,
      disabled: tooLate,
      reason: tooLate ? `今天${mealType.value === 1 ? '午饭' : '晚饭'}约不了啦，约明天吧` : '',
    })
  }
  return out
}

/** 日期条常驻说明：把「为什么有些日子不能选」讲清楚（PRD §12.3 内联校验） */
const dateHint = computed(
  () => `只能约工作日；工作日 ${cutoffHour.value}:00 后当天就约不了啦`,
)

/** 首个可选工作日作为默认值；全被锁时用第一个可用项兜底 */
function firstSelectable(): string {
  const ok = dateOptions.value.find((o) => !o.disabled)
  return ok ? ok.date : dateOptions.value.length ? dateOptions.value[0].date : ''
}

function onPickDate(o: DateOption): void {
  if (o.disabled) {
    toast(o.reason)
    return
  }
  planDate.value = o.date
}

/** 切换餐段：截止小时随之变化，需重算可选日期并校正已选日期 */
function onMealChange(mt: MealType): void {
  if (mealType.value === mt) return
  mealType.value = mt
  dateOptions.value = buildDateOptions()
  const cur = dateOptions.value.find((o) => o.date === planDate.value)
  if (!cur || cur.disabled) planDate.value = firstSelectable()
}

/* ---------------- 菜品：菜单弹层多选 + 手输 ---------------- */
const menuVisible = ref(false)
const menuList = ref<MenuItem[]>([])
const picked = ref<number[]>([])
const manualName = ref('')

function avoidText(level: AvoidLevel): string {
  if (level === 3) return '过敏'
  if (level === 2) return '不吃'
  if (level === 1) return '少吃'
  return ''
}

async function openMenuPicker(): Promise<void> {
  menuVisible.value = true
  picked.value = dishes.value.filter((d) => d.menuItemId).map((d) => d.menuItemId as number)
  if (menuList.value.length) return
  try {
    const resp = await get<MenuListResp>('/api/menu/list')
    menuList.value = resp.list ?? []
  } catch {
    // 列表拉取失败由 request.ts toast；弹层保持空态可手输
  }
}

function togglePick(item: MenuItem): void {
  const at = picked.value.indexOf(item.id)
  if (at >= 0) {
    picked.value.splice(at, 1)
    return
  }
  if (chosenCount.value >= MAX_DISHES) {
    toast(`一顿最多 ${MAX_DISHES} 道菜哦`)
    return
  }
  picked.value.push(item.id)
}

const chosenCount = computed(() => picked.value.length + dishes.value.filter((d) => !d.menuItemId).length)

function confirmPicker(): void {
  for (const item of menuList.value) {
    if (!picked.value.includes(item.id)) continue
    if (dishes.value.some((d) => d.menuItemId === item.id)) continue
    if (dishes.value.length >= MAX_DISHES) break
    dishes.value.push({
      key: `m-${item.id}`,
      menuItemId: item.id,
      name: item.name,
      avoidLevel: item.avoidLevel,
    })
  }
  menuVisible.value = false
}

function onManualInput(e: UniDetailEvent): void {
  manualName.value = eventValue(e)
}

function addManual(): void {
  const name = manualName.value.trim()
  if (!name) {
    toast('先写下菜名呀')
    return
  }
  if (name.length > MAX_NAME) {
    toast(`菜名最多 ${MAX_NAME} 字`)
    return
  }
  if (dishes.value.length >= MAX_DISHES) {
    toast(`一顿最多 ${MAX_DISHES} 道菜哦`)
    return
  }
  if (dishes.value.some((d) => d.name === name)) {
    toast('这道菜已经在啦')
    return
  }
  dishes.value.push({ key: `n-${Date.now()}-${name}`, name, avoidLevel: 0 })
  manualName.value = ''
}

function removeDish(index: number): void {
  dishes.value.splice(index, 1)
}

/* ---------------- 忌口：只提醒不硬拦 ---------------- */
const warnVisible = ref(false) // 提交前 L3 强提醒（命中 avoidLevel ≥ 2）
const noticeVisible = ref(false) // 提交后告知后端回的 warnings
const noticeWarnings = ref<PlanCreateWarning[]>([])
const createdId = ref(0)

const riskyDishes = computed(() => dishes.value.filter((d) => d.avoidLevel >= 2))

const warnText = computed(() => {
  const risky = riskyDishes.value
  if (!risky.length) return ''
  const names = risky.map((d) => d.name).join('、')
  const allergy = risky.some((d) => d.avoidLevel === 3)
  return allergy
    ? `${names} —— ${partnerName.value}过敏哦，还要点吗？`
    : `${names} —— ${partnerName.value}不吃哦，还要点吗？`
})

/* ---------------- 草稿：onHide / 提交失败存，提交成功清 ---------------- */
const pendingDraft = ref<Draft | null>(null)
const restoreVisible = ref(false)

function saveDraft(): void {
  if (!dishes.value.length && !remark.value) return
  const draft: Draft = {
    source: source.value,
    planDate: planDate.value,
    dishes: dishes.value,
    remark: remark.value,
  }
  uni.setStorageSync(DRAFT_KEY, draft)
}

function clearDraft(): void {
  uni.removeStorageSync(DRAFT_KEY)
}

function readDraft(): void {
  const raw: unknown = uni.getStorageSync(DRAFT_KEY)
  if (!raw || typeof raw !== 'object') return
  const draft = raw as Partial<Draft>
  const rawDishes = Array.isArray(draft.dishes) ? (draft.dishes as DishDraft[]) : []
  if (!rawDishes.length) {
    clearDraft() // 空草稿不留残余，避免下次反复提示
    return
  }
  pendingDraft.value = {
    source: draft.source === 1 ? 1 : 0,
    planDate: String(draft.planDate || ''),
    dishes: rawDishes,
    remark: String(draft.remark || ''),
  }
  restoreVisible.value = true
}

function applyDraft(): void {
  const draft = pendingDraft.value
  if (!draft) return
  dishes.value = draft.dishes
  remark.value = draft.remark
  // 草稿里的日期可能已过期或被当前餐段的截止规则锁掉，先校正再回填
  const stillOk = dateOptions.value.some((o) => o.date === draft.planDate && !o.disabled)
  planDate.value = stillOk ? draft.planDate : firstSelectable()
  restoreVisible.value = false
}

function dropDraft(): void {
  clearDraft()
  pendingDraft.value = null
  restoreVisible.value = false
}

/* ---------------- 提交 ---------------- */
function validate(): string {
  if (!planDate.value) return '选个日子吧～'
  if (!dishes.value.length) return '先选一道想吃的吧～'
  if (dishes.value.length > MAX_DISHES) return `一顿最多 ${MAX_DISHES} 道菜哦`
  if (remark.value.length > MAX_REMARK) return '备注最多 100 字哦'
  return ''
}

function buildBody(): PlanCreateReq {
  const body: PlanCreateReq = {
    mealType: mealType.value,
    source: source.value,
    planDate: planDate.value,
    items: dishes.value.map((d) =>
      d.menuItemId ? { menuItemId: d.menuItemId, name: d.name } : { name: d.name },
    ),
  }
  const text = remark.value.trim()
  if (text) body.remark = text
  return body
}

function onRemarkInput(e: UniDetailEvent): void {
  remark.value = eventValue(e)
}

function gotoDetail(planId: number): void {
  uni.redirectTo({ url: `/pages/plan/detail?id=${planId}` })
}

async function doCreate(): Promise<void> {
  if (submitting.value) return
  submitting.value = true
  try {
    const resp = await post<PlanCreateResp>('/api/plan/create', buildBody())
    clearDraft() // 不清会导致下次进页反复提示恢复
    // 创建成功顺手补一次订阅授权（开关开着才做），失败不影响下面的结果展示
    if (isWxRemindOn()) void requestSubscribeConsent()
    if (resp.warnings && resp.warnings.length > 0) {
      // 后端不硬拦（只回 warnings），这里同样只提示：发出后告知，用户再决定怎么处理
      createdId.value = resp.planId
      noticeWarnings.value = resp.warnings
      noticeVisible.value = true
      return
    }
    toast(isOrderer.value ? '发出去啦，等 TA 来带' : '告诉 TA 啦，等 TA 收下')
    gotoDetail(resp.planId)
  } catch (e) {
    saveDraft() // 失败保留全部输入（PRD §12.7）
    // 后端按餐段判定已过发起截止（1010）：前端可能因设备时钟偏差放过，重算日期条并校正已选日期
    if ((e as { code?: number } | null)?.code === 1010) {
      dateOptions.value = buildDateOptions()
      const cur = dateOptions.value.find((o) => o.date === planDate.value)
      if (!cur || cur.disabled) planDate.value = firstSelectable()
    }
  } finally {
    submitting.value = false
  }
}

function onSubmit(): void {
  const err = validate()
  if (err) {
    toast(err)
    return
  }
  // 命中 avoidLevel ≥ 2：先弹 L3 强提醒，确认后才走接口（用户仍可照常提交）
  if (riskyDishes.value.length > 0) {
    warnVisible.value = true
    return
  }
  void doCreate()
}

function onWarnConfirm(): void {
  warnVisible.value = false
  void doCreate()
}

function onNoticeClose(): void {
  noticeVisible.value = false
  if (createdId.value) gotoDetail(createdId.value)
}

/* ---------------- 生命周期 ---------------- */
onLoad((query) => {
  const s = Number(query?.source)
  source.value = s === 1 ? 1 : 0
  dateOptions.value = buildDateOptions()
  planDate.value = firstSelectable()
  readDraft()
})

// 从菜单页 / 后台切回：时间可能已越过当前餐段的截止点，重算可用日期
onShow(() => {
  if (!dateOptions.value.length) return
  const next = buildDateOptions()
  dateOptions.value = next
  const cur = next.find((o) => o.date === planDate.value)
  if (!cur || cur.disabled) planDate.value = firstSelectable()
})

onHide(() => {
  saveDraft()
})
</script>

<template>
  <view class="create">
    <view class="head">
      <view class="title">{{ isOrderer ? '我想吃 😋' : '我带饭 🛍' }}</view>
      <view class="sub">
        {{ isOrderer ? '告诉 TA 今天想被投喂什么' : '主动给 TA 带一份，TA 确认后生效' }}
      </view>
    </view>

    <!-- 菜品：菜单多选带回 + 手输，1–10 道 -->
    <view class="card block">
      <view class="label-row">
        <text class="label">{{ isOrderer ? '想吃的菜' : '带什么' }}</text>
        <text class="counter" :class="{ over: dishes.length > MAX_DISHES }">
          {{ dishes.length }} / {{ MAX_DISHES }}
        </text>
      </view>

      <view class="picker" @tap="openMenuPicker">
        <text class="picker-ph">从菜单选 / 自由填…</text>
        <text class="arrow">›</text>
      </view>

      <view v-if="dishes.length" class="chips">
        <view v-for="(d, i) in dishes" :key="d.key" class="chip" :class="{ risky: d.avoidLevel >= 2 }">
          <text>{{ d.name }}</text>
          <text v-if="d.avoidLevel >= 2" class="risk">{{ avoidText(d.avoidLevel) }}</text>
          <text class="del" @tap.stop="removeDish(i)">✕</text>
        </view>
      </view>

      <view class="manual">
        <input
          class="input"
          :maxlength="MAX_NAME"
          :value="manualName"
          placeholder="手写一个菜名"
          placeholder-class="ph"
          @input="onManualInput"
        />
        <view class="add-btn" @tap="addManual">添加</view>
      </view>

      <view v-if="!dishes.length" class="tip">先选一道想吃的吧～</view>
    </view>

    <!-- 日期：仅 [today, today+7] 内工作日 -->
    <view class="card block">
      <text class="label">哪天吃</text>
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
            <text v-if="o.disabled" class="d-off">{{ o.date === planDate ? '' : '休' }}</text>
          </view>
        </view>
      </scroll-view>
      <view class="tip">{{ dateHint }}</view>
    </view>

    <!-- 时段：午饭 / 晚饭，可切换，默认午饭 -->
    <view class="card block">
      <text class="label">时段</text>
      <view class="seg">
        <view class="seg-item" :class="{ on: mealType === 1 }" @tap="onMealChange(1)">🍱 午饭</view>
        <view class="seg-item" :class="{ on: mealType === 2 }" @tap="onMealChange(2)">🌙 晚饭</view>
      </view>
    </view>

    <!-- 备注 ≤100 字，实时计数 -->
    <view class="card block">
      <view class="label-row">
        <text class="label">{{ isOrderer ? '口味 / 备注' : '想对 TA 说' }}</text>
        <text class="counter" :class="{ over: remark.length > MAX_REMARK }">
          {{ remark.length }} / {{ MAX_REMARK }}
        </text>
      </view>
      <textarea
        class="ta"
        :maxlength="MAX_REMARK"
        :value="remark"
        :placeholder="isOrderer ? '少辣、公司门口、想加个汤…' : '今天给你带了爱吃的～'"
        placeholder-class="ph"
        @input="onRemarkInput"
      />
    </view>

    <view class="submit" :class="{ disabled: submitting }" @tap="onSubmit">
      {{ submitting ? '正在发出…' : isOrderer ? '发出约定 💌' : '提交 · 待 TA 接收' }}
    </view>

    <!-- L3 强提醒：命中忌口仍可提交 -->
    <bottom-sheet
      :visible="warnVisible"
      title="⚠️ 等一下"
      :mask-closable="false"
      @close="warnVisible = false"
    >
      <view class="s-text">{{ warnText }}</view>
      <template #footer>
        <view class="s-row">
          <view class="s-btn ghost" @tap="warnVisible = false">再看看</view>
          <view class="s-btn primary warn-bg" @tap="onWarnConfirm">还是要点</view>
        </view>
      </template>
    </bottom-sheet>

    <!-- 提交后告知后端回的 warnings（后端不硬拦，这里也不拦） -->
    <bottom-sheet
      :visible="noticeVisible"
      title="已经发出去啦 💌"
      :mask-closable="false"
      @close="onNoticeClose"
    >
      <view class="s-text">
        不过要提醒一下：
        <text v-for="(w, i) in noticeWarnings" :key="w.name">
          {{ i ? '、' : '' }}{{ w.name }}（{{ avoidText(w.avoidLevel) }}）
        </text>
      </view>
      <template #footer>
        <view class="s-row">
          <view class="s-btn primary" @tap="onNoticeClose">去看看</view>
        </view>
      </template>
    </bottom-sheet>

    <!-- 草稿恢复 -->
    <bottom-sheet
      :visible="restoreVisible"
      title="上次有没发出去的"
      :mask-closable="false"
      @close="dropDraft"
    >
      <view class="s-text">要接着写吗？内容都还在。</view>
      <template #footer>
        <view class="s-row">
          <view class="s-btn ghost" @tap="dropDraft">不用了</view>
          <view class="s-btn primary" @tap="applyDraft">继续</view>
        </view>
      </template>
    </bottom-sheet>

    <!-- 菜单多选弹层 -->
    <bottom-sheet
      :visible="menuVisible"
      title="菜单库 🍽"
      :mask-closable="false"
      @close="menuVisible = false"
    >
      <view class="pick-counter">已选 {{ chosenCount }} / {{ MAX_DISHES }}</view>
      <scroll-view class="pick-list" scroll-y>
        <view
          v-for="item in menuList"
          :key="item.id"
          class="dish"
          :class="{ on: picked.includes(item.id), risky: item.avoidLevel >= 2 }"
          @tap="togglePick(item)"
        >
          <view class="dish-main">
            <text class="dish-name">{{ item.name }}</text>
            <text v-if="item.avoidLevel >= 2" class="dish-risk">
              {{ avoidText(item.avoidLevel) }}
            </text>
          </view>
          <text class="dish-check">{{ picked.includes(item.id) ? '✓' : '' }}</text>
        </view>
        <view v-if="!menuList.length" class="pick-empty">菜单库还是空的，先手写一个吧</view>
      </scroll-view>
      <template #footer>
        <view class="s-row">
          <view class="s-btn ghost" @tap="menuVisible = false">取消</view>
          <view class="s-btn primary" @tap="confirmPicker">带回来</view>
        </view>
      </template>
    </bottom-sheet>
  </view>
</template>

<style scoped lang="scss">
.create {
  min-height: 100vh;
  padding: 32rpx 0 200rpx;
  box-sizing: border-box;
  background: var(--cream-bg);
}
.head {
  padding: 0 40rpx 24rpx;
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
.label-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}
.label {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--ink);
}
.counter {
  font-size: 24rpx;
  color: var(--ink-2);
}
.counter.over {
  color: #ff6b6b;
}

.picker {
  margin-top: 20rpx;
  padding: 22rpx 24rpx;
  border-radius: $radius-sm;
  background: var(--line);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.picker-ph {
  font-size: 26rpx;
  color: var(--ink-2);
}
.arrow {
  font-size: 32rpx;
  color: var(--ink-2);
}

.chips {
  margin-top: 16rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}
.chip {
  padding: 10rpx 20rpx;
  border-radius: 999rpx;
  background: #ffe3d6;
  color: #f47b45;
  font-size: 26rpx;
  display: flex;
  align-items: center;
  gap: 8rpx;
}
.chip.risky {
  background: #ffe0dc;
  color: #ff6b6b;
}
.risk {
  font-size: 20rpx;
  font-weight: 700;
}
.del {
  font-size: 22rpx;
  opacity: 0.7;
}

.manual {
  margin-top: 16rpx;
  display: flex;
  gap: 16rpx;
  align-items: center;
}
.input {
  flex: 1;
  height: 72rpx;
  padding: 0 20rpx;
  border-radius: $radius-sm;
  background: var(--line);
  font-size: 26rpx;
  color: var(--ink);
}
.add-btn {
  padding: 0 28rpx;
  height: 72rpx;
  line-height: 72rpx;
  border-radius: $radius-sm;
  background: var(--orange);
  color: #fff;
  font-size: 26rpx;
  font-weight: 700;
}
.tip {
  margin-top: 12rpx;
  font-size: 22rpx;
  color: var(--ink-2);
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
.d-off {
  font-size: 18rpx;
  color: var(--ink-2);
}

/* 时段分段 */
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

.ta {
  margin-top: 16rpx;
  width: 100%;
  min-height: 140rpx;
  padding: 20rpx;
  box-sizing: border-box;
  border-radius: $radius-sm;
  background: var(--line);
  font-size: 26rpx;
  color: var(--ink);
}

.submit {
  margin: 48rpx 32rpx 0;
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
.submit.disabled {
  opacity: 0.6;
}

/* L3 弹层容器走公共组件 bottom-sheet（前端-00-基础设施.md §7），页面只写内容样式 */
.s-text {
  margin-top: 20rpx;
  font-size: 28rpx;
  color: var(--ink-2);
  line-height: 1.7;
}
.s-row {
  margin-top: 32rpx;
  display: flex;
  gap: 20rpx;
}
.s-btn {
  flex: 1;
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  border-radius: 999rpx;
  font-size: 28rpx;
  font-weight: 700;
}
.s-btn.primary {
  background: var(--orange);
  color: #fff;
}
.s-btn.warn-bg {
  background: linear-gradient(135deg, #ff8f84, #ff6b6b);
}
.s-btn.ghost {
  background: #fff;
  color: var(--ink-2);
  border: 2rpx solid var(--line);
}

/* 菜单弹层内容 */
.pick-counter {
  text-align: center;
  font-size: 24rpx;
  color: var(--ink-2);
}
.pick-list {
  margin-top: 20rpx;
  max-height: 640rpx;
}
.dish {
  margin-bottom: 12rpx;
  padding: 22rpx 24rpx;
  border-radius: $radius-sm;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.dish.on {
  background: #ffe3d6;
}
.dish-main {
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.dish-name {
  font-size: 28rpx;
  color: var(--ink);
}
.dish-risk {
  padding: 2rpx 12rpx;
  border-radius: 999rpx;
  background: #ffe0dc;
  color: #ff6b6b;
  font-size: 20rpx;
  font-weight: 700;
}
.dish-check {
  width: 40rpx;
  text-align: center;
  font-size: 30rpx;
  color: var(--orange);
}
.pick-empty {
  padding: 48rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: var(--ink-2);
}
</style>
