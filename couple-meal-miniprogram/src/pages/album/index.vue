<script setup lang="ts">
// 饭桌相册（docs/design/60-打卡/前端.md §4）：一起吃的每顿饭，按月串成相册。
// 两条硬规则来自设计：列表与卡片一律用 thumbUrl（320 长边），只有点开大图预览才取 url；
// 后端只回已过审图片，未过审的整条由 auditStatus 表达（0 审核中 / 1 通过 / 2 含屏蔽图），
// 前端按它出角标——不给用户「照片上传失败/丢图」的错觉（fix-phase2-gaps 6.3）。
import { computed, ref } from 'vue'
import { onLoad, onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { get, post } from '@/utils/api'
import { toast } from '@/utils/toast'
import { resolveAsset } from '@/utils/asset'
import { formatDate } from '@/utils/date'
import type {
  CheckinRating,
  GalleryGroup,
  GalleryItem,
  GalleryResp,
} from '@/types/api'
import BottomSheet from '@/components/bottom-sheet.vue'

/** 每页条数（游标分页，后端按 create_time 倒序） */
const PAGE_SIZE = 20

/** 评分展示：三档，不用五星（PRD Q6） */
const RATING_META: Record<CheckinRating, { emoji: string; text: string }> = {
  3: { emoji: '🔥', text: '香！' },
  2: { emoji: '😋', text: '好吃' },
  1: { emoji: '😐', text: '还差点意思' },
}

/** 改评价时复用的三档选项 */
const RATING_OPTIONS: { value: CheckinRating; emoji: string; label: string }[] = [
  { value: 3, emoji: '🔥', label: '香' },
  { value: 2, emoji: '😋', label: '好吃' },
  { value: 1, emoji: '😐', label: '还差点意思' },
]

/** 页面自己的分组结构：跨页时同月的卡片要并到同一个月标题下 */
interface AlbumMonth {
  month: string
  list: GalleryItem[]
}

const months = ref<AlbumMonth[]>([])
const cursor = ref<string | null>(null)
const hasMore = ref(true)
const loading = ref(false)
const loaded = ref(false)

/** 改评价弹层（打卡当天内可改，不删只改） */
const editVisible = ref(false)
const editTarget = ref<GalleryItem | null>(null)
const editRating = ref<CheckinRating>(3)
const editContent = ref('')

/** 带饭方回应弹层（≤50 字，覆盖式更新） */
const replyVisible = ref(false)
const replyTarget = ref<GalleryItem | null>(null)
const replyText = ref('')

const isEmpty = computed(() => loaded.value && months.value.length === 0)
const today = formatDate(new Date())

/** 只有打卡当天能改（60-打卡/后端.md §3.5） */
function isToday(item: GalleryItem): boolean {
  return formatDate(timeOf(item)) === today
}

/** 评分文本；未打分返回空串，模板里直接 v-if 掉 */
function ratingText(rating: CheckinRating | null): string {
  if (rating === null) return ''
  const meta = RATING_META[rating]
  return meta ? `${meta.emoji} ${meta.text}` : ''
}

/** 后端 createTime 是 'yyyy-MM-ddTHH:mm:ss'，date 工具只认空格分隔，先归一 */
function timeOf(item: GalleryItem): string {
  return item.createTime.replace('T', ' ')
}

/** 审核态角标文案：1 通过（正常态）不出角标；其余未知值按审核中处理，不报错 */
function auditTag(item: GalleryItem): string {
  if (item.auditStatus === 1) return ''
  return item.auditStatus === 2 ? '未通过' : '审核中'
}

function isRejected(item: GalleryItem): boolean {
  return item.auditStatus === 2
}

function coverOf(item: GalleryItem): string {
  return item.images.length > 0 ? resolveAsset(item.images[0].thumbUrl) : ''
}

/** 同一自然月的卡片并进已有分组，避免翻页后出现两个「2026 年 10 月」 */
function mergeGroups(groups: GalleryGroup[]): void {
  groups.forEach((group) => {
    const hit = months.value.find((m) => m.month === group.month)
    if (hit) hit.list.push(...group.cards)
    else months.value.push({ month: group.month, list: [...group.cards] })
  })
}

function monthTitle(month: string): string {
  const m = /^(\d{4})-(\d{2})$/.exec(month)
  if (!m) return month
  return `${Number(m[1])} 年 ${Number(m[2])} 月`
}

async function loadMore(): Promise<void> {
  if (loading.value || !hasMore.value) return
  loading.value = true
  try {
    const resp = await get<GalleryResp>('/api/checkin/gallery', {
      cursor: cursor.value ?? undefined,
      size: PAGE_SIZE,
    })
    mergeGroups(resp.months ?? [])
    cursor.value = resp.nextCursor
    hasMore.value = resp.hasMore
  } catch {
    // 1002 已由 request 层 reLaunch 配对页；其余已 toast
  } finally {
    loading.value = false
    loaded.value = true
  }
}

function refresh(): void {
  months.value = []
  cursor.value = null
  hasMore.value = true
  loaded.value = false
  void loadMore()
}

/** 点卡片：有照片才进大图预览，用的是原图 url；一张可看的都没有时把审核态说清楚 */
function onPreview(item: GalleryItem): void {
  if (item.images.length === 0) {
    if (isRejected(item)) toast('照片没过审，重新打卡换一张吧')
    else if (item.auditStatus !== 1) toast('照片还在审核中，过审后就出现啦')
    return
  }
  uni.previewImage({
    current: resolveAsset(item.images[0].url),
    urls: item.images.map((img) => resolveAsset(img.url)),
  })
}

function openEdit(item: GalleryItem): void {
  editTarget.value = item
  editRating.value = item.rating && item.rating >= 1 ? item.rating : 3
  editContent.value = item.content ?? ''
  editVisible.value = true
}

async function submitEdit(): Promise<void> {
  const target = editTarget.value
  if (!target) return
  try {
    await post('/api/checkin/update', {
      checkinId: target.checkinId,
      rating: editRating.value,
      content: editContent.value || undefined,
    })
    toast('改好啦，还是那顿饭')
    editVisible.value = false
    refresh()
  } catch {
    // 3003（过了当天）等由 request 层 toast
  }
}

function openReply(item: GalleryItem): void {
  replyTarget.value = item
  replyText.value = item.reply ?? ''
  replyVisible.value = true
}

async function submitReply(): Promise<void> {
  const target = replyTarget.value
  if (!target) return
  const reply = replyText.value.trim()
  if (!reply) {
    toast('回一句再送出去吧')
    return
  }
  try {
    await post('/api/checkin/reply', { checkinId: target.checkinId, reply })
    toast('回应送到了')
    replyVisible.value = false
    refresh()
  } catch {
    // 失败已 toast
  }
}

function goToday(): void {
  uni.switchTab({ url: '/pages/today/index' })
}

onLoad(() => {
  refresh()
})

// 返回本页时补一次（前端-00 基础设施 §10）：只在首次取数还没落地时才补，避免重复请求
onShow(() => {
  if (!loaded.value && !loading.value) refresh()
})

onReachBottom(() => {
  void loadMore()
})

onPullDownRefresh(() => {
  refresh()
  uni.stopPullDownRefresh()
})
</script>

<template>
  <view class="album">
    <view v-if="!loaded" class="skeleton">
      <view v-for="i in 4" :key="i" class="sk-card" />
    </view>

    <view v-else-if="isEmpty" class="empty">
      <text class="empty-ico">📷</text>
      <text class="empty-t">相册还空着呢</text>
      <text class="empty-d">一起吃的第一顿饭，值得留张照片。以后翻回来，全是烟火气。</text>
      <view class="empty-btn" @tap="goToday">去今天看看</view>
    </view>

    <view v-else class="months">
      <view v-for="month in months" :key="month.month" class="month">
        <view class="month-title">
          {{ monthTitle(month.month) }} · {{ month.list.length }} 顿
        </view>
        <view class="grid">
          <view v-for="item in month.list" :key="item.checkinId" class="gcard" @tap="onPreview(item)">
            <image v-if="coverOf(item)" class="gphoto" :src="coverOf(item)" mode="aspectFill" />
            <view v-else class="gphoto gphoto-none">🍽</view>
            <text v-if="auditTag(item)" class="gaudit" :class="{ bad: isRejected(item) }">
              {{ auditTag(item) }}
            </text>
            <text v-if="item.images.length > 1" class="gcount">{{ item.images.length }} 张</text>

            <view class="gbody">
              <text class="gdate">{{ formatDate(timeOf(item), 'MM-dd') }}</text>
              <text v-if="isRejected(item)" class="gtip">照片没过审，重新打卡换一张吧</text>
              <text v-if="ratingText(item.rating)" class="grate">{{ ratingText(item.rating) }}</text>
              <text v-if="item.content" class="gtxt">{{ item.content }}</text>
              <text v-if="item.reply" class="greply">💬 回应：{{ item.reply }}</text>
              <text class="gauthor">{{ item.mine ? '我' : 'TA' }} 打卡</text>

              <view class="gops">
                <text v-if="isToday(item)" class="gop" @tap.stop="openEdit(item)">改评价</text>
                <text class="gop" @tap.stop="openReply(item)">回应 TA</text>
              </view>
            </view>
          </view>
        </view>
      </view>

      <view v-if="loading" class="foot">正在翻更早的照片…</view>
      <view v-else-if="!hasMore" class="foot">就这些啦，剩下的等你们慢慢吃 🍚</view>
    </view>
  </view>

  <bottom-sheet :visible="editVisible" title="改改当时的评价" @close="editVisible = false">
    <view class="form">
      <view class="rate">
        <view
          v-for="opt in RATING_OPTIONS"
          :key="opt.value"
          class="rate-i"
          :class="{ on: editRating === opt.value }"
          @tap="editRating = opt.value"
        >
          <text class="rate-e">{{ opt.emoji }}</text>
          <text class="rate-t">{{ opt.label }}</text>
        </view>
      </view>
      <view class="box">
        <textarea
          v-model="editContent"
          class="ta"
          :maxlength="100"
          placeholder="当时的心情，可以再补两句"
        />
        <text class="count">{{ editContent.length }}/100</text>
      </view>
    </view>
    <template #footer>
      <view class="submit" @tap="submitEdit">保存</view>
    </template>
  </bottom-sheet>

  <bottom-sheet :visible="replyVisible" title="回应这顿饭" @close="replyVisible = false">
    <view class="form">
      <view class="box">
        <textarea v-model="replyText" class="ta" :maxlength="50" placeholder="说句好听的，≤50 字" />
        <text class="count">{{ replyText.length }}/50</text>
      </view>
    </view>
    <template #footer>
      <view class="submit" @tap="submitReply">送出回应</view>
    </template>
  </bottom-sheet>
</template>

<style scoped lang="scss">
.album {
  min-height: 100vh;
  padding: 24rpx 0 60rpx;
  box-sizing: border-box;
  background: var(--cream-bg);
}
.skeleton {
  padding: 0 32rpx;
}
.sk-card {
  height: 320rpx;
  border-radius: $radius;
  background: #fff;
  margin-bottom: 24rpx;
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
  font-size: 88rpx;
}
.empty-t {
  margin-top: 20rpx;
  font-size: 34rpx;
  font-weight: 800;
  color: var(--ink);
}
.empty-d {
  margin-top: 12rpx;
  font-size: 26rpx;
  color: var(--ink-2);
  line-height: 1.7;
}
.empty-btn {
  margin-top: 32rpx;
  padding: 16rpx 48rpx;
  border-radius: 44rpx;
  background: var(--orange);
  color: #fff;
  font-size: 28rpx;
  font-weight: 700;
}
.month-title {
  padding: 24rpx 40rpx 8rpx;
  font-size: 26rpx;
  font-weight: 800;
  color: var(--ink-2);
}
.grid {
  display: flex;
  flex-wrap: wrap;
  gap: 20rpx;
  padding: 12rpx 32rpx 0;
}
.gcard {
  position: relative;
  width: calc(50% - 10rpx);
  background: #fff;
  border-radius: 32rpx;
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}
.gphoto {
  width: 100%;
  height: 320rpx;
  background: linear-gradient(140deg, #ffe0cb, #ffc9b0);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 72rpx;
}
.gphoto-none {
  background: linear-gradient(140deg, #f3ece6, #e6ddd5);
}
.gcount {
  position: absolute;
  top: 16rpx;
  right: 16rpx;
  padding: 4rpx 14rpx;
  border-radius: 18rpx;
  background: rgba(74, 63, 53, 0.5);
  color: #fff;
  font-size: 20rpx;
}
.gaudit {
  position: absolute;
  top: 16rpx;
  left: 16rpx;
  padding: 4rpx 16rpx;
  border-radius: 999rpx;
  background: #fff0e2;
  color: #ff9b6a;
  font-size: 20rpx;
  font-weight: 700;
}
.gaudit.bad {
  background: #efe9e3;
  color: var(--ink-2);
}
.gtip {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: var(--orange);
  line-height: 1.5;
}
.gbody {
  padding: 16rpx 20rpx 20rpx;
  display: flex;
  flex-direction: column;
}
.gdate {
  font-size: 22rpx;
  color: var(--ink-2);
}
.grate {
  margin-top: 6rpx;
  font-size: 24rpx;
  font-weight: 700;
  color: #d2683c;
}
.gtxt {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: var(--ink);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.greply {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: var(--orange);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.gauthor {
  margin-top: 10rpx;
  font-size: 20rpx;
  color: var(--ink-2);
}
.gops {
  margin-top: 12rpx;
  display: flex;
  gap: 16rpx;
}
.gop {
  padding: 6rpx 18rpx;
  border-radius: 18rpx;
  background: var(--cream-bg);
  border: 2rpx solid var(--line);
  font-size: 22rpx;
  color: var(--ink-2);
}
.foot {
  padding: 32rpx 0 8rpx;
  text-align: center;
  font-size: 24rpx;
  color: var(--ink-2);
}
.form {
  display: flex;
  flex-direction: column;
}
.rate {
  display: flex;
  gap: 12rpx;
}
.rate-i {
  flex: 1;
  padding: 16rpx 4rpx;
  border: 3rpx solid var(--line);
  border-radius: 24rpx;
  background: var(--cream-bg);
  text-align: center;
}
.rate-i.on {
  background: #ffe9dc;
  border-color: var(--peach);
}
.rate-e {
  font-size: 40rpx;
  line-height: 1.2;
}
.rate-t {
  display: block;
  margin-top: 4rpx;
  font-size: 22rpx;
  font-weight: 700;
  color: var(--ink-2);
}
.box {
  position: relative;
  margin-top: 20rpx;
  background: var(--cream-bg);
  border: 3rpx solid var(--line);
  border-radius: 24rpx;
  padding: 16rpx 16rpx 40rpx;
}
.ta {
  width: 100%;
  height: 140rpx;
  font-size: 26rpx;
  color: var(--ink);
}
.count {
  position: absolute;
  right: 16rpx;
  bottom: 12rpx;
  font-size: 22rpx;
  color: var(--ink-2);
}
.submit {
  height: 88rpx;
  border-radius: 44rpx;
  background: var(--orange);
  color: #fff;
  font-size: 30rpx;
  font-weight: 700;
  line-height: 88rpx;
  text-align: center;
}
</style>
