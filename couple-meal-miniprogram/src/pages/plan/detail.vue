<script setup lang="ts">
// 2.4 约定详情页 —— 20-约定/前端.md §3 / §4。
// 按钮**只由后端下发的 actions[] 驱动**（后端.md §3.8 / PRD §12.4 权限矩阵），前端不按 status 硬推；
// 任一动作成功后统一重新 GET /api/plan/{id}，绝不做本地乐观更新。
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { get, post } from '@/utils/api'
import BottomSheet from '@/components/bottom-sheet.vue'
import { appState } from '@/stores/app'
import { toast } from '@/utils/toast'
import type { PlanAction, PlanCompleteResp, PlanDetail, PlanStatus } from '@/types/api'

/* ---------------- 状态标签（七档全覆盖，漏一档编译报错） ---------------- */
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

/**
 * actions[] → 按钮的映射表。key 必须是 PlanAction 全量（Record 强制），
 * ORDER 决定渲染顺序，actions[] 决定出现与否。
 */
interface ActionMeta {
  action: PlanAction
  text: string
  emoji: string
  kind: 'primary' | 'ghost' | 'danger'
}
const ACTION_META: Record<PlanAction, ActionMeta> = {
  claim: { action: 'claim', text: '我来带', emoji: '🛍', kind: 'primary' },
  withdraw: { action: 'withdraw', text: '这次带不了', emoji: '↩️', kind: 'ghost' },
  confirm: { action: 'confirm', text: '确认接收', emoji: '🤝', kind: 'primary' },
  reject: { action: 'reject', text: '婉拒', emoji: '🙈', kind: 'danger' },
  deliver: { action: 'deliver', text: '标记送达', emoji: '📦', kind: 'primary' },
  complete: { action: 'complete', text: '已领取 · 完成', emoji: '❤', kind: 'primary' },
  reassign: { action: 'reassign', text: '改派', emoji: '🔁', kind: 'ghost' },
  cancel: { action: 'cancel', text: '取消', emoji: '✖️', kind: 'danger' },
  // 打卡 / 采购属其它模块：这里只留出入口，不在此实现（见 §6 汇报）
  checkin: { action: 'checkin', text: '吃饭打卡', emoji: '📷', kind: 'ghost' },
  shopping: { action: 'shopping', text: '采购清单', emoji: '🛒', kind: 'ghost' },
}
const ACTION_ORDER: PlanAction[] = [
  'claim',
  'confirm',
  'reject',
  'deliver',
  'complete',
  'withdraw',
  'reassign',
  'cancel',
  'checkin',
  'shopping',
]

/* ---------------- 页面数据 ---------------- */
const planId = ref(0)
const plan = ref<PlanDetail | null>(null)
const loading = ref(false)
const failed = ref(false)
const acting = ref('')

const partnerName = computed(
  () => appState.relation?.nicknameMine || appState.relation?.partnerNickname || 'TA',
)

async function load(): Promise<void> {
  if (!planId.value) return
  loading.value = true
  try {
    plan.value = await get<PlanDetail>(`/api/plan/${planId.value}`)
    failed.value = false
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

onLoad((query) => {
  planId.value = Number(query?.id) || 0
  void load()
})

// 从「送达 / 取消」等子流程返回或切前台时，重新拉一次，保证与后端一致
onShow(() => {
  if (planId.value) void load()
})

/* ---------------- 头部与卡片文案 ---------------- */
function statusMeta(status: PlanStatus): StatusMeta {
  return STATUS_META[status]
}

/** 我在这顿饭里的角色（后端下发，前端不比 userId） */
const roleText = computed(() => (plan.value?.role === 'orderer' ? '我点的' : '我带的'))

const counterpartLine = computed(() => {
  const p = plan.value
  if (!p) return ''
  return p.role === 'orderer'
    ? `等 ${p.carrierNickname || partnerName.value} 来带`
    : `${partnerName.value} 想吃`
})

/* ---------------- 四步状态进度 ---------------- */
const stepLabels = computed<string[]>(() => {
  // 第一步按来源二义：主动带是「待接收」，点餐是「待认领」（PRD §6.1）
  const first = plan.value?.source === 1 ? '待接收' : '待认领'
  return [first, '备餐中', '已交付', '已完成']
})

/** 当前所在步（1–4）；终态 5/6 返回 0 → 进度条改画「已结束」 */
const currentStep = computed(() => {
  const s = plan.value?.status
  if (s === 0 || s === 1) return 1
  if (s === 2) return 2
  if (s === 3) return 3
  if (s === 4) return 4
  return 0
})

const endedText = computed(() => {
  const s = plan.value?.status
  if (s === 5) return '这顿取消了'
  if (s === 6) return '错过了这顿'
  return ''
})

/* ---------------- 按钮：只取后端 actions[] ---------------- */
const buttons = computed<ActionMeta[]>(() => {
  const actions = plan.value?.actions ?? []
  return ACTION_ORDER.filter((a) => actions.includes(a)).map((a) => ACTION_META[a])
})

/* ---------------- 动作：统一回 success 前重取详情 ---------------- */
interface ActOptions {
  /** 成功后回今日页并刷新列表（取消 / 婉拒才需要） */
  backToList?: boolean
}

async function act<T>(
  path: PlanAction,
  body: Record<string, unknown>,
  okMsg: string,
  opts: ActOptions = {},
): Promise<void> {
  const key = String(path)
  if (acting.value || planId.value === 0) return
  acting.value = key
  try {
    await post<T>(`/api/plan/${planId.value}/${key}`, body)
    toast(okMsg)
    if (opts.backToList) {
      uni.switchTab({ url: '/pages/today/index' })
      return
    }
    await load()
  } catch {
    // 1004 / 1005 等已在 request.ts toast；这里只保证按钮解锁并重取（状态可能已变）
    await load()
  } finally {
    acting.value = ''
  }
}

function dispatch(action: PlanAction): void {
  if (acting.value) return
  switch (action) {
    case 'claim':
      void act('claim', {}, '接下来交给你啦')
      break
    case 'withdraw':
      sheet.value = 'withdraw'
      break
    case 'confirm':
      void act('confirm', {}, '收下啦，TA 可以开始准备咯')
      break
    case 'reject':
      openReasonSheet('reject')
      break
    case 'deliver':
      openDeliverSheet()
      break
    case 'complete':
      void complete()
      break
    case 'reassign':
      sheet.value = 'reassign'
      break
    case 'cancel':
      openReasonSheet('cancel')
      break
    case 'checkin':
      toast('吃饭打卡随后就到，先记着这顿 ❤')
      break
    case 'shopping':
      uni.navigateTo({ url: `/pages/shopping/index?planId=${planId.value}` })
      break
  }
}

async function complete(): Promise<void> {
  const action: PlanAction = 'complete'
  if (acting.value) return
  acting.value = action
  try {
    const resp = await post<PlanCompleteResp>(`/api/plan/${planId.value}/complete`, {})
    toast(`这顿饭完成啦 · 爱心值 ${resp.loveValue}`)
    await load()
  } catch {
    await load()
  } finally {
    acting.value = ''
  }
}

/* ---------------- 弹层：同一时刻只允许 1 个 L3（PRD §12.1） ---------------- */
type SheetKind = 'deliver' | 'cancel' | 'reject' | 'reassign' | 'withdraw' | ''
const sheet = ref<SheetKind>('')

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

function closeSheet(): void {
  sheet.value = ''
}

/* —— 送达：交付位置 + 便签（★C 仪式感）—— */
const LOCATION_PRESETS = ['公司门口柜 B12', '楼下大厅', '工位']
const NOTE_TEMPLATES = ['锅里留了汤', '趁热喝～', '记得穿外套']
const MAX_NOTE = 80

const deliverLocation = ref('')
const deliverNote = ref('')

function openDeliverSheet(): void {
  deliverLocation.value = plan.value?.deliverLocation ?? ''
  deliverNote.value = plan.value?.deliverNote ?? ''
  sheet.value = 'deliver'
}

function pickLocation(loc: string): void {
  deliverLocation.value = loc
}

function pickNote(note: string): void {
  deliverNote.value = deliverNote.value ? `${deliverNote.value}${note}` : note
}

function onLocationInput(e: UniDetailEvent): void {
  deliverLocation.value = eventValue(e)
}

function onNoteInput(e: UniDetailEvent): void {
  deliverNote.value = eventValue(e)
}

function submitDeliver(): void {
  const loc = deliverLocation.value.trim()
  if (!loc) {
    toast('先说一声放哪儿啦')
    return
  }
  const note = deliverNote.value.trim()
  closeSheet()
  const body: Record<string, unknown> = { deliverLocation: loc }
  if (note) body.deliverNote = note
  void act('deliver', body, '饭放好啦，TA 收到提醒了')
}

/** —— 取消 / 婉拒：原因必填（后端 2006；PRD §12.9 必须 L3）—— */
const REASON_PRESETS: Record<'cancel' | 'reject', string[]> = {
  cancel: ['今天加班，来不及', '临时有约', '身体不太舒服', '改了别的安排'],
  reject: ['今天不太饿', '已经吃过了', '今天自己带了', '下次吧'],
}
const MAX_REASON = 100

const reasonPicked = ref('')
const reasonExtra = ref('')

function openReasonSheet(kind: 'cancel' | 'reject'): void {
  reasonPicked.value = ''
  reasonExtra.value = ''
  sheet.value = kind
}

function reasonFilled(): string {
  const picked = reasonPicked.value.trim()
  const extra = reasonExtra.value.trim()
  if (picked && extra) return `${picked}：${extra}`
  return picked || extra
}

function onReasonInput(e: UniDetailEvent): void {
  reasonExtra.value = eventValue(e)
}

function submitReason(): void {
  const kind = sheet.value === 'reject' ? 'reject' : 'cancel'
  const reason = reasonFilled()
  if (!reason) return // 未填时按钮本就不可点（PRD §12.9）
  closeSheet()
  if (kind === 'reject') {
    void act('reject', { reason }, '已经委婉告诉 TA 啦', { backToList: true })
  } else {
    void act('cancel', { reason }, '已经取消', { backToList: true })
  }
}

/* —— 改派：只改「谁带」，不改 point 餐方 —— */
const reassignTargetId = computed(() => {
  const p = plan.value
  if (!p) return 0
  const me = appState.user?.userId ?? 0
  if (p.ordererId !== me) return p.ordererId
  return p.carrierId ?? 0
})

function submitReassign(): void {
  const target = reassignTargetId.value
  if (!target) {
    toast('暂时找不到可以改派的人')
    return
  }
  closeSheet()
  void act('reassign', { carrierId: target }, '这顿交给 TA 啦')
}

function submitWithdraw(): void {
  closeSheet()
  void act('withdraw', {}, '已经退回啦，等 TA 再认领')
}
</script>

<template>
  <view class="detail">
    <!-- 错误态 -->
    <view v-if="failed" class="card err">
      <view class="err-ico">🍳</view>
      <view class="err-t">网络开小差了</view>
      <view class="err-d">没能拿到这顿饭的详情，重试一下？</view>
      <view class="btn-ghost" @tap="load()">重新加载</view>
    </view>

    <template v-else-if="plan">
      <!-- 约定卡 -->
      <view class="card main">
        <view class="plan-top">
          <view class="plan-who">{{ counterpartLine }}</view>
          <view class="tag" :class="statusMeta(plan.status).cls">
            {{ statusMeta(plan.status).label }}
          </view>
        </view>
        <view class="plan-meta">
          {{ plan.planDate }} · {{ plan.mealType === 1 ? '午饭' : '晚饭' }} · {{ roleText }}
        </view>
        <view class="plan-dish">
          <text v-for="(it, i) in plan.items" :key="it.id">
            {{ i ? ' · ' : '' }}{{ it.name }}
          </text>
        </view>
        <view v-if="plan.remark" class="plan-remark">{{ plan.remark }}</view>
      </view>

      <!-- 四步状态进度 -->
      <view class="card steps-card">
        <view class="steps">
          <view
            v-for="(label, i) in stepLabels"
            :key="label"
            class="step"
            :class="{ on: currentStep > i, current: currentStep === i + 1 }"
          >
            <view class="dot">{{ i + 1 }}</view>
            <view class="st">{{ label }}</view>
          </view>
        </view>
        <view v-if="currentStep === 0" class="ended">🌙 {{ endedText }}</view>
      </view>

      <!-- 交付位置与便签（已交付起展示，★C） -->
      <view v-if="plan.status >= 3 && plan.deliverLocation" class="card deliver">
        <view class="d-loc">📍 {{ plan.deliverLocation }}</view>
        <view v-if="plan.deliverNote" class="d-note">✍️ {{ plan.deliverNote }}</view>
        <view v-else class="d-note none">这次没有留便签</view>
      </view>

      <!-- 按钮：完全由 actions[] 决定 -->
      <view class="actions">
        <view
          v-for="b in buttons"
          :key="b.action"
          class="act"
          :class="[b.kind, { busy: acting !== '' }]"
          @tap="dispatch(b.action)"
        >
          <text class="act-emoji">{{ b.emoji }}</text>
          <text>{{ b.text }}</text>
        </view>
        <view v-if="!buttons.length && currentStep === 0" class="act-tip">
          这顿饭已经结束啦，去今日页发起下一顿吧
        </view>
      </view>
    </template>

    <view v-else-if="loading" class="loading">正在端上来…</view>

    <!-- L3：送达（交付位置 + 便签，★C） -->
    <bottom-sheet
      :visible="sheet === 'deliver'"
      title="送到哪儿啦？📦"
      :mask-closable="false"
      @close="closeSheet"
    >
      <view class="s-label">交付位置</view>
      <view class="opts">
        <view
          v-for="loc in LOCATION_PRESETS"
          :key="loc"
          class="opt"
          :class="{ on: deliverLocation === loc }"
          @tap="pickLocation(loc)"
        >
          {{ loc }}
        </view>
      </view>
      <input
        class="s-input"
        :value="deliverLocation"
        placeholder="或自己写个位置"
        placeholder-class="ph"
        @input="onLocationInput"
      />
      <view class="s-label">留句话（可选）</view>
      <view class="opts">
        <view v-for="n in NOTE_TEMPLATES" :key="n" class="opt" @tap="pickNote(n)">{{ n }}</view>
      </view>
      <textarea
        class="s-ta"
        :maxlength="MAX_NOTE"
        :value="deliverNote"
        placeholder="想对 TA 说点什么"
        placeholder-class="ph"
        @input="onNoteInput"
      />
      <view class="s-count">{{ deliverNote.length }} / {{ MAX_NOTE }}</view>
      <template #footer>
        <view class="s-row">
          <view class="s-btn ghost" @tap="closeSheet">再等等</view>
          <view class="s-btn primary" @tap="submitDeliver">确认送达</view>
        </view>
      </template>
    </bottom-sheet>

    <!-- L3：取消 / 婉拒（原因必填） -->
    <bottom-sheet
      :visible="sheet === 'cancel' || sheet === 'reject'"
      :title="sheet === 'reject' ? '婉拒这次投喂？' : '取消这次约定？'"
      :mask-closable="false"
      @close="closeSheet"
    >
      <view class="s-text">
        <text v-if="sheet === 'reject'">
          说句软和点的就好，TA 不会难过的；投喂留到下次吧。
        </text>
        <text v-else>
          取消后<text class="warn">本次不计爱心值</text>，TA 会收到通知。
        </text>
      </view>
      <view class="opts col">
        <view
          v-for="r in REASON_PRESETS[sheet === 'reject' ? 'reject' : 'cancel']"
          :key="r"
          class="opt"
          :class="{ on: reasonPicked === r }"
          @tap="reasonPicked = r"
        >
          {{ r }}
        </view>
      </view>
      <textarea
        class="s-ta"
        :maxlength="MAX_REASON"
        :value="reasonExtra"
        placeholder="补充一句（可选）"
        placeholder-class="ph"
        @input="onReasonInput"
      />
      <template #footer>
        <view class="s-row">
          <view class="s-btn ghost" @tap="closeSheet">再想想</view>
          <!-- 原因未填时不可点（PRD §12.9） -->
          <view class="s-btn danger" :class="{ disabled: !reasonFilled() }" @tap="submitReason">
            {{ sheet === 'reject' ? '婉拒' : '确认取消' }}
          </view>
        </view>
      </template>
    </bottom-sheet>

    <!-- L3：改派 -->
    <bottom-sheet
      :visible="sheet === 'reassign'"
      title="这顿饭改 TA 来带？"
      :mask-closable="false"
      @close="closeSheet"
    >
      <view class="s-text">你还是吃饭的那个人，只是换 TA 来准备。</view>
      <template #footer>
        <view class="s-row">
          <view class="s-btn ghost" @tap="closeSheet">算了</view>
          <view class="s-btn primary" @tap="submitReassign">交给 TA</view>
        </view>
      </template>
    </bottom-sheet>

    <!-- L3：撤回认领 -->
    <bottom-sheet
      :visible="sheet === 'withdraw'"
      title="这次带不了？"
      :mask-closable="false"
      @close="closeSheet"
    >
      <view class="s-text">退回待认领后，TA 可以再找人带，不算取消。</view>
      <template #footer>
        <view class="s-row">
          <view class="s-btn ghost" @tap="closeSheet">再想想</view>
          <view class="s-btn primary" @tap="submitWithdraw">退回待认领</view>
        </view>
      </template>
    </bottom-sheet>
  </view>
</template>

<style scoped lang="scss">
.detail {
  min-height: 100vh;
  padding: 24rpx 0 200rpx;
  box-sizing: border-box;
  background: var(--cream-bg);
}

.main {
  margin: 0 32rpx;
  padding: 32rpx;
}
.plan-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.plan-who {
  font-size: 30rpx;
  font-weight: 800;
  color: var(--ink);
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
.plan-meta {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: var(--ink-2);
}
.plan-dish {
  margin-top: 16rpx;
  font-size: 32rpx;
  font-weight: 700;
  color: var(--ink);
}
.plan-remark {
  margin-top: 12rpx;
  font-size: 26rpx;
  color: var(--ink-2);
  line-height: 1.7;
}

/* 四步进度 */
.steps-card {
  margin: 24rpx 32rpx 0;
  padding: 32rpx 24rpx;
}
.steps {
  display: flex;
}
.step {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}
.dot {
  width: 52rpx;
  height: 52rpx;
  line-height: 52rpx;
  text-align: center;
  border-radius: 50%;
  background: var(--line);
  color: var(--ink-2);
  font-size: 24rpx;
  font-weight: 700;
}
.step.on .dot,
.step.current .dot {
  background: var(--orange);
  color: #fff;
}
.st {
  font-size: 22rpx;
  color: var(--ink-2);
}
.step.on .st,
.step.current .st {
  color: #f47b45;
  font-weight: 700;
}
.ended {
  margin-top: 16rpx;
  text-align: center;
  font-size: 26rpx;
  color: var(--ink-2);
}

/* 交付卡 */
.deliver {
  margin: 24rpx 32rpx 0;
  padding: 28rpx 32rpx;
  background: linear-gradient(135deg, #fff, #fff6ee);
}
.d-loc {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--ink);
}
.d-note {
  margin-top: 12rpx;
  font-size: 26rpx;
  color: var(--ink-2);
  line-height: 1.7;
}
.d-note.none {
  color: #b9ab9c;
}

/* 动作区 */
.actions {
  margin: 32rpx 32rpx 0;
  display: flex;
  flex-wrap: wrap;
  gap: 20rpx;
}
.act {
  flex: 1;
  min-width: 200rpx;
  height: 92rpx;
  line-height: 92rpx;
  text-align: center;
  border-radius: 999rpx;
  font-size: 28rpx;
  font-weight: 700;
}
.act-emoji {
  margin-right: 8rpx;
}
.act.primary {
  background: var(--orange);
  color: #fff;
}
.act.ghost {
  background: #fff;
  color: var(--ink-2);
  border: 2rpx solid var(--line);
}
.act.danger {
  background: #fff;
  color: #ff6b6b;
  border: 2rpx solid #ffe0dc;
}
.act.busy {
  opacity: 0.6;
}
.act-tip {
  width: 100%;
  text-align: center;
  font-size: 26rpx;
  color: var(--ink-2);
}

.loading {
  padding: 120rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: var(--ink-2);
}

/* 错误态 */
.err {
  margin: 32rpx;
  padding: 56rpx 32rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.err-ico {
  font-size: 80rpx;
}
.err-t {
  margin-top: 16rpx;
  font-size: 32rpx;
  font-weight: 700;
}
.err-d {
  margin-top: 8rpx;
  font-size: 26rpx;
  color: var(--ink-2);
}
.btn-ghost {
  margin-top: 24rpx;
  padding: 16rpx 56rpx;
  border-radius: 999rpx;
  background: #fff;
  color: var(--ink-2);
  border: 2rpx solid var(--line);
  font-size: 28rpx;
  font-weight: 700;
}

/* L3 弹层容器走公共组件 bottom-sheet（前端-00-基础设施.md §7），页面只写内容样式 */
.s-text {
  margin-top: 20rpx;
  font-size: 26rpx;
  color: var(--ink-2);
  line-height: 1.7;
}
.warn {
  color: #ff6b6b;
  font-weight: 700;
}
.s-label {
  margin-top: 28rpx;
  font-size: 26rpx;
  font-weight: 700;
  color: var(--ink);
}
.opts {
  margin-top: 16rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}
.opts.col {
  flex-direction: column;
}
.opt {
  padding: 14rpx 24rpx;
  border-radius: $radius-sm;
  background: #fff;
  color: var(--ink-2);
  font-size: 26rpx;
  border: 2rpx solid var(--line);
}
.opt.on {
  background: var(--peach);
  color: var(--ink);
  border-color: var(--peach);
  font-weight: 700;
}
.s-input,
.s-ta {
  margin-top: 16rpx;
  width: 100%;
  padding: 20rpx;
  box-sizing: border-box;
  border-radius: $radius-sm;
  background: var(--line);
  font-size: 26rpx;
  color: var(--ink);
}
.s-input {
  height: 72rpx;
}
.s-ta {
  min-height: 120rpx;
}
.s-count {
  margin-top: 8rpx;
  text-align: right;
  font-size: 22rpx;
  color: var(--ink-2);
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
.s-btn.danger {
  background: linear-gradient(135deg, #ff8f84, #ff6b6b);
  color: #fff;
}
.s-btn.danger.disabled {
  opacity: 0.45;
}
.s-btn.ghost {
  background: #fff;
  color: var(--ink-2);
  border: 2rpx solid var(--line);
}
</style>
