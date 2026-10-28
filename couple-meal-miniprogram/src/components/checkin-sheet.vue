<script setup lang="ts">
// 打卡弹层（docs/design/60-打卡/前端.md §2 §3）：选图 → 逐张上传 → 提交，两步走。
// 只负责「收集 + 提交」，不关心谁拉起它：宿主用 v-if 显隐，监听 @close / @submit。
// 三条硬约束来自设计：串行上传（同一时刻只有 1 个 uploadFile）、单张失败只重传那一张、
// 全部图片成功才可提交（有失败的图提交会让后端因 URL 缺失报错）。
import { computed, ref, watch } from 'vue'
import { post, upload } from '@/utils/api'
import { toast } from '@/utils/toast'
import type { CheckinRating, CheckinSubmitResp, CheckinUploadResp } from '@/types/api'
import BottomSheet from '@/components/bottom-sheet.vue'

/** 最多 2 张照片（PRD §12.3） */
const MAX_COUNT = 2
/** 点评 ≤100 字（PRD §12.3） */
const MAX_CONTENT = 100

const props = defineProps<{
  visible: boolean
  planId: number
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'submit', checkinId: number): void
}>()

/** 单张照片的上传状态机：上传中 → 成功 / 失败（失败可单张重试） */
type PhotoStatus = 'uploading' | 'done' | 'error'

interface PhotoSlot {
  key: number
  /** 本地临时路径，用于预览；提交只传服务端的 url */
  localPath: string
  url: string
  thumbUrl: string
  status: PhotoStatus
}

/** 三档评价（PRD Q6：不用五星） */
const RATING_OPTIONS: { value: CheckinRating; emoji: string; label: string }[] = [
  { value: 3, emoji: '🔥', label: '香' },
  { value: 2, emoji: '😋', label: '好吃' },
  { value: 1, emoji: '😐', label: '还差点意思' },
]

/** 快捷短语：点一下拼进点评，减少打字 */
const QUICK_PHRASES = ['光盘啦', '汤很好喝', '明天还想吃', '下次少放点盐', '谢谢你带饭']

/**
 * 打卡错误码 → 温情文案。
 * 提交走 silent（请求层不再 toast 后端原文），这里统一出口，避免同一次失败弹两条。
 */
const WARM_ERROR: Record<number, string> = {
  3001: '这顿饭已经打过卡啦，去相册里看看它吧',
  3003: '打卡的时间窗已经过去了，明天早点告诉我呀',
  3004: '这顿是 TA 带的，等 TA 来打卡就好啦',
  3010: '饭还没送到手上，先别急着打卡哦',
}

/** 这些码意味着「打卡入口已不可用」，弹层留在屏幕上只会让人反复点，直接关掉让宿主重取详情 */
const CLOSE_ON_ERROR = [3001, 3003, 3004, 3010]

const photos = ref<PhotoSlot[]>([])
const rating = ref<CheckinRating | null>(null)
const content = ref('')
const submitting = ref(false)
const photoSeed = ref(0)

const uploading = computed(() => photos.value.some((p) => p.status === 'uploading'))
const busy = computed(() => uploading.value || submitting.value)
const doneCount = computed(() => photos.value.filter((p) => p.status === 'done').length)
const canSubmit = computed(
  () =>
    !busy.value &&
    photos.value.length >= 1 &&
    doneCount.value === photos.value.length &&
    rating.value !== null,
)

/** 上传队列：串行跑，同一时刻只 1 个请求；已成功的不会被再取出来重传 */
let draining = false
async function drainQueue(): Promise<void> {
  if (draining) return
  draining = true
  try {
    for (;;) {
      const slot = photos.value.find((p) => p.status === 'uploading')
      if (!slot) break
      try {
        const resp = await upload<CheckinUploadResp>('/api/checkin/upload', slot.localPath)
        slot.url = resp.url
        slot.thumbUrl = resp.thumbUrl
        slot.status = 'done'
      } catch {
        // 只把这一张标红，队列继续跑下一张
        slot.status = 'error'
      }
    }
  } finally {
    draining = false
  }
}

function onChoose(): void {
  if (busy.value) return
  const remain = MAX_COUNT - photos.value.length
  if (remain <= 0) return

  uni.chooseMedia({
    count: remain,
    mediaType: ['image'],
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: (res) => {
      res.tempFiles.forEach((file) => {
        photos.value.push({
          key: ++photoSeed.value,
          localPath: file.tempFilePath,
          url: '',
          thumbUrl: '',
          status: 'uploading',
        })
      })
      void drainQueue()
    },
    fail: (err) => {
      // 用户主动取消也是 fail，别弹提示打扰
      if (!String(err.errMsg || '').includes('cancel')) toast('没拿到照片，再试一次吧')
    },
  })
}

function onRetry(slot: PhotoSlot): void {
  if (busy.value) return
  slot.status = 'uploading'
  void drainQueue()
}

function onRemove(slot: PhotoSlot): void {
  if (busy.value) return
  photos.value = photos.value.filter((p) => p.key !== slot.key)
}

function appendPhrase(phrase: string): void {
  if (content.value.length >= MAX_CONTENT) return
  const next = content.value ? `${content.value} · ${phrase}` : phrase
  content.value = next.slice(0, MAX_CONTENT)
}

function onClose(): void {
  if (busy.value) return
  emit('close')
}

/** reject 出来的可能是后端 body，也可能是 uni 的 fail 对象，两边都要能读 */
function codeOf(err: unknown): number {
  if (err && typeof err === 'object' && 'code' in err) {
    const code = (err as { code?: unknown }).code
    return typeof code === 'number' ? code : 0
  }
  return 0
}

function msgOf(err: unknown): string {
  if (err && typeof err === 'object' && 'msg' in err) {
    const msg = (err as { msg?: unknown }).msg
    return typeof msg === 'string' ? msg : ''
  }
  return ''
}

async function onSubmit(): Promise<void> {
  if (!canSubmit.value) {
    if (photos.value.length === 0) toast('至少拍一张呀')
    else if (rating.value === null) toast('说说今天味道怎么样')
    else toast('照片还在上传，稍等一下')
    return
  }

  submitting.value = true
  try {
    const resp = await post<CheckinSubmitResp>(
      '/api/checkin/submit',
      {
        planId: props.planId,
        images: photos.value.map((p) => p.url),
        rating: rating.value,
        content: content.value || undefined,
      },
      { silent: true },
    )
    toast('打卡成功，这顿饭记下来了')
    emit('submit', resp.checkinId)
  } catch (err) {
    const code = codeOf(err)
    toast(WARM_ERROR[code] || msgOf(err) || '出了点小状况，稍后再试试')
    if (CLOSE_ON_ERROR.includes(code)) emit('close')
  } finally {
    submitting.value = false
  }
}

function reset(): void {
  photos.value = []
  rating.value = null
  content.value = ''
  submitting.value = false
}

// 每次被拉起都是一次全新打卡，不保留上一顿的照片与点评
watch(
  () => props.visible,
  (visible) => {
    if (visible) reset()
  },
)
</script>

<template>
  <bottom-sheet :visible="visible" title="今天这顿怎么样" :mask-closable="!busy" @close="onClose">
    <view class="ci">
      <!-- 照片区：沿用 3 宫格布局，最多 2 张 + 加号格；满 2 张后加号隐藏 -->
      <view class="ci-grid">
        <view
          v-for="photo in photos"
          :key="photo.key"
          class="ci-cell"
          :class="{ 'is-error': photo.status === 'error' }"
        >
          <!-- 直接用微信临时路径预览（可能是 wxfile://，不能拿去拼域名） -->
          <image class="ci-img" :src="photo.localPath" mode="aspectFill" />
          <view v-if="photo.status === 'uploading'" class="ci-cover">
            <text class="ci-cover-t">上传中…</text>
          </view>
          <view v-else-if="photo.status === 'error'" class="ci-cover">
            <text class="ci-retry" @tap.stop="onRetry(photo)">重传这张</text>
          </view>
          <view v-else class="ci-del" @tap.stop="onRemove(photo)">×</view>
        </view>
        <view v-if="photos.length < MAX_COUNT" class="ci-cell ci-add" @tap="onChoose">
          <text class="ci-add-i">＋</text>
          <text class="ci-add-t">加照片</text>
        </view>
      </view>
      <view class="ci-hint">照片 1–2 张，评价必选 🍽</view>

      <!-- 三档评价 -->
      <view class="ci-label">今天味道怎么样</view>
      <view class="ci-rate">
        <view
          v-for="opt in RATING_OPTIONS"
          :key="opt.value"
          class="ci-rate-i"
          :class="{ on: rating === opt.value }"
          @tap="rating = opt.value"
        >
          <text class="ci-rate-e">{{ opt.emoji }}</text>
          <text class="ci-rate-t">{{ opt.label }}</text>
        </view>
      </view>

      <!-- 点评 + 快捷短语 -->
      <view class="ci-quick">
        <text v-for="phrase in QUICK_PHRASES" :key="phrase" class="ci-quick-i" @tap="appendPhrase(phrase)">
          {{ phrase }}
        </text>
      </view>
      <view class="ci-box">
        <textarea
          v-model="content"
          class="ci-ta"
          :maxlength="MAX_CONTENT"
          placeholder="想对 TA 说点什么？（选填）"
        />
        <text class="ci-count" :class="{ over: content.length >= MAX_CONTENT }">
          {{ content.length }}/{{ MAX_CONTENT }}
        </text>
      </view>
    </view>

    <template #footer>
      <view class="ci-submit" :class="{ disabled: !canSubmit }" @tap="onSubmit">
        {{ submitting ? '正在提交…' : '提交打卡' }}
      </view>
    </template>
  </bottom-sheet>
</template>

<style scoped lang="scss">
.ci {
  display: flex;
  flex-direction: column;
}
.ci-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}
.ci-cell {
  position: relative;
  width: 200rpx;
  height: 200rpx;
  border-radius: 24rpx;
  overflow: hidden;
  background: var(--line);
}
.ci-img {
  width: 100%;
  height: 100%;
}
.ci-cell.is-error {
  border: 3rpx solid #ff6b6b;
  box-sizing: border-box;
}
.ci-cover {
  position: absolute;
  inset: 0;
  background: rgba(74, 63, 53, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
}
.ci-cover-t {
  color: #fff;
  font-size: 24rpx;
}
.ci-retry {
  color: #fff;
  font-size: 24rpx;
  font-weight: 600;
  border: 2rpx solid #fff;
  border-radius: 20rpx;
  padding: 6rpx 16rpx;
}
.ci-del {
  position: absolute;
  top: 6rpx;
  right: 6rpx;
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  background: rgba(74, 63, 53, 0.55);
  color: #fff;
  font-size: 26rpx;
  line-height: 40rpx;
  text-align: center;
}
.ci-add {
  border: 3rpx dashed var(--peach);
  background: var(--cream-bg);
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.ci-add-i {
  font-size: 48rpx;
  color: var(--orange);
  line-height: 1;
}
.ci-add-t {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: var(--ink-2);
}
.ci-hint {
  margin-top: 12rpx;
  font-size: 22rpx;
  color: var(--ink-2);
}
.ci-label {
  margin-top: 28rpx;
  font-size: 26rpx;
  font-weight: 600;
  color: var(--ink);
}
.ci-rate {
  margin-top: 16rpx;
  display: flex;
  gap: 12rpx;
}
.ci-rate-i {
  flex: 1;
  padding: 16rpx 4rpx;
  border: 3rpx solid var(--line);
  border-radius: 24rpx;
  background: var(--cream-bg);
  text-align: center;
}
.ci-rate-i.on {
  background: #ffe9dc;
  border-color: var(--peach);
}
.ci-rate-e {
  font-size: 40rpx;
  line-height: 1.2;
}
.ci-rate-t {
  display: block;
  margin-top: 4rpx;
  font-size: 22rpx;
  font-weight: 700;
  color: var(--ink-2);
}
.ci-rate-i.on .ci-rate-t {
  color: #d2683c;
}
.ci-quick {
  margin-top: 24rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}
.ci-quick-i {
  padding: 8rpx 18rpx;
  border-radius: 20rpx;
  background: var(--cream-bg);
  border: 2rpx solid var(--line);
  font-size: 22rpx;
  color: var(--ink-2);
}
.ci-box {
  position: relative;
  margin-top: 16rpx;
  background: var(--cream-bg);
  border: 3rpx solid var(--line);
  border-radius: 24rpx;
  padding: 16rpx 16rpx 40rpx;
}
.ci-ta {
  width: 100%;
  height: 120rpx;
  font-size: 26rpx;
  color: var(--ink);
}
.ci-count {
  position: absolute;
  right: 16rpx;
  bottom: 12rpx;
  font-size: 22rpx;
  color: var(--ink-2);
}
.ci-count.over {
  color: #ff6b6b;
}
.ci-submit {
  height: 88rpx;
  border-radius: 44rpx;
  background: linear-gradient(135deg, #ffb391, var(--orange));
  color: #fff;
  font-size: 30rpx;
  font-weight: 700;
  line-height: 88rpx;
  text-align: center;
  box-shadow: var(--shadow-sm);
}
.ci-submit.disabled {
  background: #e7ddd4;
  color: #a99b8d;
  box-shadow: none;
}
</style>
