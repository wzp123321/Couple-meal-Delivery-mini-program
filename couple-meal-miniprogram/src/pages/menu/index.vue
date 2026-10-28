<script setup lang="ts">
// 30-菜单与偏好/前端.md —— 菜单库（Tab2）
// 覆盖：归属 / 分类 / 标记三维筛选、最近点过排序、口味画像卡与详情弹层、智能推荐区、
//       新增 / 编辑弹层、删除 L3 三级确认、空态。
// 契约见 30-菜单与偏好/后端.md §2；色标规则见 前端-00-基础设施.md §6；文案调性见 PRD §12.11。
import { computed, reactive, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { del, get, post, put } from '@/utils/api'
import { appState } from '@/stores/app'
import { toast } from '@/utils/toast'
import { formatDate } from '@/utils/date'
import type {
  AvoidLevel,
  MenuCategory,
  MenuCreateResp,
  MenuItem,
  MenuListResp,
  MenuProfileResp,
  MenuRecommendResp,
  MenuSaveReq,
  MenuSort,
} from '@/types/api'
import BottomSheet from '@/components/bottom-sheet.vue'

/** 分类维度：0 表示「全部」（后端 category 只接受 1–4） */
type CategoryKey = MenuCategory | 0
/** 归属维度：all 不筛 / me 我加的 / ta TA 加的 */
type OwnerKey = 'all' | 'me' | 'ta'
/** 标记维度：常点走服务端 onlyFavorite；忌口接口无参数，本地过滤 */
type MarkKey = 'all' | 'favorite' | 'avoid'

const CATEGORY_TABS: { value: CategoryKey; label: string }[] = [
  { value: 0, label: '全部' },
  { value: 1, label: '荤' },
  { value: 2, label: '素' },
  { value: 3, label: '汤' },
  { value: 4, label: '主食' },
]
const OWNER_TABS: { value: OwnerKey; label: string }[] = [
  { value: 'all', label: '全部' },
  { value: 'me', label: '我加的' },
  { value: 'ta', label: 'TA 加的' },
]
const MARK_TABS: { value: MarkKey; label: string }[] = [
  { value: 'all', label: '全部' },
  { value: 'favorite', label: '常点★' },
  { value: 'avoid', label: '忌口⚠' },
]
const CATEGORY_OPTIONS: { value: MenuCategory; label: string; emoji: string }[] = [
  { value: 1, label: '荤', emoji: '🍖' },
  { value: 2, label: '素', emoji: '🥬' },
  { value: 3, label: '汤', emoji: '🍲' },
  { value: 4, label: '主食', emoji: '🍚' },
]
const AVOID_OPTIONS: { value: AvoidLevel; label: string }[] = [
  { value: 0, label: '无' },
  { value: 1, label: '少吃' },
  { value: 2, label: '不吃' },
  { value: 3, label: '过敏' },
]
const CATEGORY_LABEL: Record<MenuCategory, string> = { 1: '荤', 2: '素', 3: '汤', 4: '主食' }
const AVOID_LABEL: Record<AvoidLevel, string> = { 0: '无', 1: '少吃', 2: '不吃', 3: '过敏' }
const RECOMMEND_LIMIT = 5
const NAME_MAX = 20
const NOTE_MAX = 200

// —— 列表与筛选状态 ——
const dishes = ref<MenuItem[]>([])
const recommends = ref<MenuItem[]>([])
const profile = ref<MenuProfileResp | null>(null)
const category = ref<CategoryKey>(0)
const owner = ref<OwnerKey>('all')
const mark = ref<MarkKey>('all')
const sort = ref<MenuSort>('name')
const loading = ref(false)

// 「TA 加的」需要 creatorId，但 couple/info 不下发 partnerId，
// 只能从历次列表结果里反推（任一条 creatorId != 我的 id 即为 TA）。
const partnerId = ref(0)
const myId = computed(() => appState.user?.userId ?? 0)

// —— 编辑弹层 ——
const editVisible = ref(false)
const editingId = ref(0) // 0 = 新增；>0 = 编辑已有菜品
const submitting = ref(false)
const nameError = ref('')
const form = reactive({
  name: '',
  category: 1 as MenuCategory,
  isFavorite: false,
  avoidLevel: 0 as AvoidLevel,
  note: '',
})

// —— 删除 L3 确认层 ——
const deleteVisible = ref(false)
const pendingDelete = ref<MenuItem | null>(null)
const deleting = ref(false)

// —— 口味画像详情弹层 ——
const profileVisible = ref(false)

const avoidCount = computed(() => profile.value?.avoidDishes.length ?? 0)
const topDishName = computed(() => profile.value?.topDishes[0]?.name || '还不知道')

/** 记住 TA 的 userId：任一条 creatorId 不等于我的，即为对方 */
function learnPartnerId(list: MenuItem[]): void {
  if (partnerId.value > 0) return
  const mine = myId.value
  if (mine <= 0) return
  const other = list.find((d) => d.creatorId !== mine)
  if (other) partnerId.value = other.creatorId
}

async function loadList(): Promise<void> {
  const mine = myId.value
  // 还不知道 TA 的 id：这一轮不带 creatorId 请求，拿到结果后本地按「不是我」过滤
  const ownerByLocal = owner.value === 'ta' && partnerId.value === 0

  const params: Record<string, string | number | boolean> = { sort: sort.value }
  if (category.value !== 0) params.category = category.value
  if (mark.value === 'favorite') params.onlyFavorite = true
  if (owner.value === 'me' && mine > 0) params.creatorId = mine
  if (owner.value === 'ta' && !ownerByLocal) params.creatorId = partnerId.value

  try {
    const resp = await get<MenuListResp>('/api/menu/list', params)
    learnPartnerId(resp.list)
    let list = resp.list
    if (ownerByLocal) list = list.filter((d) => d.creatorId !== mine)
    if (mark.value === 'avoid') list = list.filter((d) => d.avoidLevel >= 1)
    dishes.value = list
  } catch {
    // 1002 → request 层已 reLaunch 配对页；其余后端文案已 toast，列表保持上一次内容
  }
}

async function loadRecommend(): Promise<void> {
  try {
    const resp = await get<MenuRecommendResp>('/api/menu/recommend', { limit: RECOMMEND_LIMIT })
    recommends.value = resp.list
  } catch {
    recommends.value = []
  }
}

async function loadProfile(): Promise<void> {
  try {
    profile.value = await get<MenuProfileResp>('/api/menu/profile')
  } catch {
    // 画像失败不影响列表，卡片回落到 0
  }
}

/** 三个请求互不依赖，各自吞掉异常（request 层已 toast），页面不因一个失败而空白 */
async function refreshAll(): Promise<void> {
  loading.value = true
  try {
    await Promise.all([loadList(), loadRecommend(), loadProfile()])
  } finally {
    loading.value = false
  }
}

// —— 筛选切换：任一维度变化都统一重取列表（前端.md §5 易错点：不在本地增删后凑数）——
function pickCategory(v: CategoryKey): void {
  if (category.value === v) return
  category.value = v
  void loadList()
}
function pickOwner(v: OwnerKey): void {
  if (owner.value === v) return
  owner.value = v
  void loadList()
}
function pickMark(v: MarkKey): void {
  if (mark.value === v) return
  mark.value = v
  void loadList()
}
function pickSort(v: MenuSort): void {
  if (sort.value === v) return
  sort.value = v
  void loadList()
}

// —— 新增 / 编辑 ——
function openCreate(): void {
  editingId.value = 0
  form.name = ''
  form.category = 1
  form.isFavorite = false
  form.avoidLevel = 0
  form.note = ''
  nameError.value = ''
  editVisible.value = true
}

/** 编辑弹层回填当前值（验收要点：编辑必须回填，不能是空表单） */
function openEdit(item: MenuItem): void {
  editingId.value = item.id
  form.name = item.name
  form.category = item.category
  form.isFavorite = item.favoriteFlag === 1
  form.avoidLevel = item.avoidLevel
  form.note = item.note || ''
  nameError.value = ''
  editVisible.value = true
}

function closeEdit(): void {
  editVisible.value = false
}

function pickFormCategory(v: MenuCategory): void {
  form.category = v
}
function pickFormAvoid(v: AvoidLevel): void {
  form.avoidLevel = v
}

async function submit(): Promise<void> {
  const name = form.name.trim()
  if (!name) {
    nameError.value = '先给这道菜起个名字吧～'
    return
  }
  if (name.length > NAME_MAX) {
    nameError.value = `菜名最多 ${NAME_MAX} 个字哦`
    return
  }
  nameError.value = ''
  if (submitting.value) return // 防双击（PRD §12.2）
  submitting.value = true

  const body: MenuSaveReq = {
    name,
    category: form.category,
    isFavorite: form.isFavorite,
    avoidLevel: form.avoidLevel,
    note: form.note.trim() || undefined,
  }
  try {
    if (editingId.value > 0) {
      await put<void>(`/api/menu/${editingId.value}`, body)
      toast(`「${name}」改好啦`)
    } else {
      await post<MenuCreateResp>('/api/menu', body)
      toast(`已把「${name}」收进菜单库`)
    }
    editVisible.value = false
    await refreshAll() // 铁律：写成功后统一重取，不在前端手改
  } catch {
    // 2002 重名 / 2004 超长：request 层已 toast 后端文案。
    // 弹层与输入一律保留（前端.md §5 易错点：重名报错后清空表单）
  } finally {
    submitting.value = false
  }
}

// —— 长按操作面板 ——
function onLongPress(item: MenuItem): void {
  uni.showActionSheet({
    itemList: ['编辑', '删除'],
    success: (res) => {
      if (res.tapIndex === 0) openEdit(item)
      else askDelete(item)
    },
    fail: () => {},
  })
}

// —— 删除 L3 确认（PRD §12.9）——
function askDelete(item: MenuItem): void {
  pendingDelete.value = item
  deleteVisible.value = true
}
function cancelDelete(): void {
  deleteVisible.value = false
  pendingDelete.value = null
}
async function confirmDelete(): Promise<void> {
  const item = pendingDelete.value
  if (!item || deleting.value) return
  deleting.value = true
  try {
    await del<void>(`/api/menu/${item.id}`)
    deleteVisible.value = false
    pendingDelete.value = null
    toast(`「${item.name}」已经从菜单里移走了`)
    await refreshAll() // 软删后重取，避免筛选态下错位
  } catch {
    // 2001 菜品不存在等：后端文案已 toast
  } finally {
    deleting.value = false
  }
}

// —— 口味画像 ——
async function openProfile(): Promise<void> {
  profileVisible.value = true
  await loadProfile()
}

// —— 展示辅助 ——
/** 忌口色标（前端-00-基础设施.md §6）：无/少吃 → 中性灰；不吃 → 暖橘描边；过敏 → 红底白字 */
function avoidClass(level: AvoidLevel): string {
  if (level === 3) return 'mk mk-allergy'
  if (level === 2) return 'mk mk-avoid'
  if (level === 1) return 'mk mk-less'
  return 'mk mk-none'
}
function categoryLabel(c: MenuCategory): string {
  return CATEGORY_LABEL[c] || '其他'
}
function ownerLabel(item: MenuItem): string {
  if (myId.value > 0 && item.creatorId === myId.value) return '我加的'
  return `${item.creatorNickname || 'TA'}加的`
}
/** 最近点过：sort=recent 时后端才下发 lastOrdered；从未点过显示「还没点过」 */
function lastOrderedText(item: MenuItem): string {
  if (!item.lastOrdered) return '还没点过'
  return `最近点过 ${formatDate(item.lastOrdered, 'MM-dd')}`
}
function categoryCount(c: MenuCategory): number {
  return profile.value?.categories.find((x) => x.category === c)?.count ?? 0
}

onShow(() => {
  void refreshAll()
})

onPullDownRefresh(async () => {
  await refreshAll()
  uni.stopPullDownRefresh()
})
</script>

<template>
  <view class="menu-page">
    <!-- 顶部：口味画像入口 -->
    <view class="card profile-card" @tap="openProfile">
      <view class="pc-main">
        <view class="pc-total">
          {{ profile?.total ?? 0 }}<text class="pc-unit">道菜</text>
        </view>
        <view class="pc-sub">忌口 {{ avoidCount }} 道 · 最常点「{{ topDishName }}」</view>
      </view>
      <text class="pc-arrow">›</text>
    </view>

    <!-- 智能推荐：为空时整块隐藏，不留空壳 -->
    <view v-if="recommends.length > 0" class="card rec-card">
      <view class="rec-head">✨ 今天适合吃这些</view>
      <view class="rec-list">
        <view v-for="r in recommends" :key="r.id" class="rec-chip" @tap="openEdit(r)">
          {{ r.name }}
        </view>
      </view>
      <view class="rec-foot">按你们的口味挑的，点一下就能改</view>
    </view>

    <!-- 筛选条：分类（横向滚动） -->
    <scroll-view class="filters" scroll-x :show-scrollbar="false">
      <view class="filters-inner">
        <view
          v-for="c in CATEGORY_TABS"
          :key="c.value"
          :class="category === c.value ? 'f on' : 'f'"
          @tap="pickCategory(c.value)"
        >
          {{ c.label }}
        </view>
      </view>
    </scroll-view>

    <!-- 筛选条：归属 + 标记 -->
    <view class="filter-row">
      <view class="seg">
        <view
          v-for="o in OWNER_TABS"
          :key="o.value"
          :class="owner === o.value ? 's on' : 's'"
          @tap="pickOwner(o.value)"
        >
          {{ o.label }}
        </view>
      </view>
      <view class="seg">
        <view
          v-for="m in MARK_TABS"
          :key="m.value"
          :class="mark === m.value ? 's on' : 's'"
          @tap="pickMark(m.value)"
        >
          {{ m.label }}
        </view>
      </view>
    </view>

    <!-- 筛选条：排序 -->
    <view class="sort-row">
      <text class="sort-label">共 {{ dishes.length }} 道</text>
      <view class="seg seg-sm">
        <view :class="sort === 'name' ? 's on' : 's'" @tap="pickSort('name')">默认</view>
        <view :class="sort === 'recent' ? 's on' : 's'" @tap="pickSort('recent')">最近点过</view>
      </view>
    </view>

    <!-- 列表 -->
    <view v-if="dishes.length > 0" class="list">
      <view
        v-for="d in dishes"
        :key="d.id"
        class="dish"
        @tap="openEdit(d)"
        @longpress="onLongPress(d)"
      >
        <view class="d-left">
          <view class="d-name">
            <text class="d-nm">{{ d.name }}</text>
            <text v-if="d.favoriteFlag === 1" class="mk mk-star">★常点</text>
          </view>
          <view class="d-meta">{{ categoryLabel(d.category) }} · {{ ownerLabel(d) }}</view>
          <view v-if="sort === 'recent'" class="d-last">{{ lastOrderedText(d) }}</view>
          <view v-else-if="d.note" class="d-note">{{ d.note }}</view>
        </view>
        <text :class="avoidClass(d.avoidLevel)">{{ AVOID_LABEL[d.avoidLevel] }}</text>
      </view>
    </view>

    <!-- 空态（PRD §12.10） -->
    <view v-else-if="!loading" class="empty-card">
      <view class="empty-ico">📖</view>
      <view class="empty-t">菜单库还是空的</view>
      <view class="empty-d">把彼此爱吃的、不吃记下来，点餐更省心</view>
      <view class="empty-btn" @tap="openCreate">添加第一道菜</view>
    </view>

    <view class="fab" @tap="openCreate">＋</view>

    <!-- 新增 / 编辑弹层 -->
    <bottom-sheet :visible="editVisible" title="加一道菜" :mask-closable="false" @close="closeEdit">
      <view class="form">
        <view class="field">
          <view class="f-label">菜名</view>
          <input
            v-model="form.name"
            class="f-input"
            :maxlength="NAME_MAX"
            placeholder="比如 红烧肉"
            placeholder-class="f-ph"
          />
          <view class="f-foot">
            <text class="f-error">{{ nameError }}</text>
            <text class="f-count">{{ form.name.length }}/{{ NAME_MAX }}</text>
          </view>
        </view>

        <view class="field">
          <view class="f-label">分类</view>
          <view class="chips">
            <view
              v-for="c in CATEGORY_OPTIONS"
              :key="c.value"
              :class="form.category === c.value ? 'chip on' : 'chip'"
              @tap="pickFormCategory(c.value)"
            >
              {{ c.emoji }} {{ c.label }}
            </view>
          </view>
        </view>

        <view class="field">
          <view class="f-label">忌口</view>
          <view class="chips">
            <view
              v-for="a in AVOID_OPTIONS"
              :key="a.value"
              :class="form.avoidLevel === a.value ? 'chip on' : 'chip'"
              @tap="pickFormAvoid(a.value)"
            >
              {{ a.label }}
            </view>
          </view>
          <view class="f-tip">点餐命中「不吃 / 过敏」会提醒一下，但不会拦着你提交</view>
        </view>

        <view class="switch-row" @tap="form.isFavorite = !form.isFavorite">
          <text class="sw-label">标为常点★</text>
          <view :class="form.isFavorite ? 'sw on' : 'sw'"><view class="sw-dot"></view></view>
        </view>

        <view class="field">
          <view class="f-label">菜谱笔记（选填）</view>
          <textarea
            v-model="form.note"
            class="f-area"
            :maxlength="NOTE_MAX"
            placeholder="红烧肉要加冰糖…"
            placeholder-class="f-ph"
          />
          <view class="f-count f-count-right">{{ form.note.length }}/{{ NOTE_MAX }}</view>
        </view>

        <view class="btns">
          <view class="btn btn-ghost" @tap="closeEdit">取消</view>
          <view class="btn btn-primary" @tap="submit">
            {{ submitting ? '保存中…' : '保存' }}
          </view>
        </view>
      </view>
    </bottom-sheet>

    <!-- 删除 L3 三级确认层 -->
    <bottom-sheet
      :visible="deleteVisible"
      title="删掉这道菜？"
      :mask-closable="false"
      @close="cancelDelete"
    >
      <view class="del-body">
        <view class="del-name">「{{ pendingDelete?.name }}」</view>
        <view class="del-text">
          删了就不能再从菜单里选它了。<br />
          不过<b>历史约定与口味画像都不受影响</b>——那些是已经发生过的事，删了这道菜也带不走。
        </view>
        <view class="btns">
          <view class="btn btn-ghost" @tap="cancelDelete">再想想</view>
          <view class="btn btn-danger" @tap="confirmDelete">
            {{ deleting ? '删除中…' : '删除' }}
          </view>
        </view>
      </view>
    </bottom-sheet>

    <!-- 口味画像详情弹层 -->
    <bottom-sheet :visible="profileVisible" title="你们的口味画像" @close="profileVisible = false">
      <view class="pf">
        <view class="pf-total">
          <text class="pf-num">{{ profile?.total ?? 0 }}</text>
          <text class="pf-unit">道菜在菜单里</text>
        </view>

        <view class="pf-block">
          <view class="pf-title">分类分布</view>
          <view class="pf-cats">
            <view v-for="c in CATEGORY_OPTIONS" :key="c.value" class="pf-cat">
              <text class="pf-cat-ico">{{ c.emoji }}</text>
              <text class="pf-cat-n">{{ categoryCount(c.value) }}</text>
              <text class="pf-cat-l">{{ c.label }}</text>
            </view>
          </view>
        </view>

        <view v-if="(profile?.topDishes.length ?? 0) > 0" class="pf-block">
          <view class="pf-title">最常点的菜</view>
          <view v-for="t in profile?.topDishes ?? []" :key="t.name" class="pf-row">
            <text class="pf-row-n">{{ t.name }}</text>
            <text class="pf-row-c">{{ t.count }} 次</text>
          </view>
        </view>

        <view v-if="(profile?.avoidDishes.length ?? 0) > 0" class="pf-block">
          <view class="pf-title">忌口清单</view>
          <view v-for="a in profile?.avoidDishes ?? []" :key="a.id" class="pf-row">
            <text class="pf-row-n">{{ a.name }}</text>
            <text :class="avoidClass(a.avoidLevel)">{{ AVOID_LABEL[a.avoidLevel] }}</text>
          </view>
        </view>

        <view v-if="(profile?.topDishes.length ?? 0) === 0 && avoidCount === 0" class="pf-empty">
          还没有足够的数据，一起吃几顿就有啦
        </view>
      </view>
    </bottom-sheet>
  </view>
</template>

<style scoped lang="scss">
.menu-page {
  min-height: 100vh;
  padding: 24rpx 0 calc(180rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
  background: var(--cream-bg);
}

/* —— 顶部画像入口 —— */
.profile-card {
  margin: 0 32rpx;
  padding: 28rpx 32rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.pc-total {
  font-size: 48rpx;
  font-weight: 800;
  color: var(--orange);
  line-height: 1.1;
}
.pc-unit {
  font-size: 24rpx;
  font-weight: 500;
  color: var(--ink-2);
  margin-left: 8rpx;
}
.pc-sub {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: var(--ink-2);
}
.pc-arrow {
  font-size: 40rpx;
  color: var(--ink-2);
}

/* —— 智能推荐 —— */
.rec-card {
  margin: 24rpx 32rpx 0;
  padding: 28rpx 32rpx;
}
.rec-head {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--ink);
}
.rec-list {
  margin-top: 20rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}
.rec-chip {
  padding: 12rpx 24rpx;
  border-radius: $radius-sm;
  background: var(--peach);
  color: var(--ink);
  font-size: 26rpx;
  font-weight: 600;
}
.rec-foot {
  margin-top: 16rpx;
  font-size: 22rpx;
  color: var(--ink-2);
}

/* —— 筛选条 —— */
.filters {
  margin-top: 24rpx;
  white-space: nowrap;
}
.filters-inner {
  display: inline-flex;
  padding: 0 32rpx;
  gap: 16rpx;
}
.f {
  flex: none;
  padding: 14rpx 26rpx;
  border-radius: $radius-sm;
  background: #fff;
  border: 2rpx solid var(--line);
  font-size: 24rpx;
  font-weight: 600;
  color: var(--ink-2);
}
.f.on {
  background: var(--peach);
  border-color: var(--orange);
  color: var(--ink);
}
.filter-row {
  margin: 16rpx 32rpx 0;
  display: flex;
  gap: 16rpx;
}
.seg {
  display: flex;
  background: #fff;
  border: 2rpx solid var(--line);
  border-radius: $radius-sm;
  overflow: hidden;
}
.seg .s {
  padding: 12rpx 20rpx;
  font-size: 24rpx;
  font-weight: 600;
  color: var(--ink-2);
}
.seg .s.on {
  background: var(--orange);
  color: #fff;
}
.sort-row {
  margin: 16rpx 32rpx 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.sort-label {
  font-size: 24rpx;
  color: var(--ink-2);
}
.seg-sm .s {
  padding: 10rpx 18rpx;
  font-size: 22rpx;
}

/* —— 列表 —— */
.list {
  margin: 24rpx 32rpx 0;
}
.dish {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-radius: $radius-sm;
  padding: 28rpx;
  margin-bottom: 16rpx;
  @include soft-shadow(1);
}
.d-left {
  flex: 1;
  min-width: 0;
}
.d-name {
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.d-nm {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--ink);
}
.d-meta {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: var(--ink-2);
}
.d-last {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: var(--orange);
}
.d-note {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: var(--ink-2);
}

/* 忌口色标 */
.mk {
  flex: none;
  font-size: 20rpx;
  font-weight: 700;
  padding: 4rpx 14rpx;
  border-radius: 16rpx;
}
.mk-star {
  background: #fff3d6;
  color: #e0912f;
}
.mk-none {
  background: var(--line);
  color: var(--ink-2);
}
.mk-less {
  background: #f2efe9;
  color: #8c7b6b;
}
.mk-avoid {
  background: transparent;
  border: 2rpx solid var(--orange);
  color: var(--orange);
}
.mk-allergy {
  background: #e4573d;
  color: #fff;
}

/* —— 空态 —— */
.empty-card {
  margin: 80rpx 32rpx 0;
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
  text-align: center;
}
.empty-btn {
  margin-top: 32rpx;
  padding: 20rpx 48rpx;
  border-radius: $radius-sm;
  background: var(--orange);
  color: #fff;
  font-size: 28rpx;
  font-weight: 700;
}

/* —— 悬浮新增 —— */
.fab {
  position: fixed;
  right: 36rpx;
  bottom: calc(140rpx + env(safe-area-inset-bottom));
  width: 100rpx;
  height: 100rpx;
  border-radius: 50%;
  background: var(--orange);
  color: #fff;
  font-size: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 24rpx rgba(255, 155, 106, 0.4);
}

/* —— 弹层内表单 —— */
.form {
  padding-bottom: 8rpx;
}
.field {
  margin-bottom: 28rpx;
}
.f-label {
  font-size: 26rpx;
  font-weight: 600;
  color: var(--ink);
  margin-bottom: 12rpx;
}
.f-input {
  width: 100%;
  box-sizing: border-box;
  background: #fff;
  border: 2rpx solid var(--line);
  border-radius: $radius-sm;
  padding: 20rpx 24rpx;
  font-size: 28rpx;
  color: var(--ink);
}
.f-area {
  width: 100%;
  box-sizing: border-box;
  height: 140rpx;
  background: #fff;
  border: 2rpx solid var(--line);
  border-radius: $radius-sm;
  padding: 20rpx 24rpx;
  font-size: 26rpx;
  color: var(--ink);
}
.f-ph {
  color: #c4b7a8;
}
.f-foot {
  margin-top: 8rpx;
  display: flex;
  justify-content: space-between;
}
.f-error {
  font-size: 22rpx;
  color: #e4573d;
}
.f-count {
  font-size: 22rpx;
  color: var(--ink-2);
}
.f-count-right {
  margin-top: 8rpx;
  text-align: right;
}
.f-tip {
  margin-top: 10rpx;
  font-size: 22rpx;
  color: var(--ink-2);
  line-height: 1.6;
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}
.chip {
  padding: 14rpx 24rpx;
  border-radius: $radius-sm;
  background: #fff;
  border: 2rpx solid var(--line);
  font-size: 26rpx;
  font-weight: 600;
  color: var(--ink-2);
}
.chip.on {
  background: var(--peach);
  border-color: var(--orange);
  color: var(--ink);
}
.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12rpx 0 28rpx;
}
.sw-label {
  font-size: 26rpx;
  font-weight: 600;
  color: var(--ink);
}
.sw {
  width: 84rpx;
  height: 48rpx;
  border-radius: 24rpx;
  background: var(--line);
  padding: 4rpx;
  box-sizing: border-box;
  transition: background 0.2s;
}
.sw.on {
  background: var(--orange);
}
.sw-dot {
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  background: #fff;
}
.sw.on .sw-dot {
  transform: translateX(36rpx);
}
.btns {
  display: flex;
  gap: 20rpx;
  margin-top: 12rpx;
}
.btn {
  flex: 1;
  text-align: center;
  padding: 24rpx 0;
  border-radius: $radius-sm;
  font-size: 28rpx;
  font-weight: 700;
}
.btn-ghost {
  background: #fff;
  border: 2rpx solid var(--line);
  color: var(--ink-2);
}
.btn-primary {
  background: var(--orange);
  color: #fff;
}
.btn-danger {
  background: #e4573d;
  color: #fff;
}

/* —— 删除确认层 —— */
.del-body {
  padding-bottom: 8rpx;
}
.del-name {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--ink);
  text-align: center;
}
.del-text {
  margin-top: 16rpx;
  font-size: 26rpx;
  color: var(--ink-2);
  line-height: 1.8;
}

/* —— 口味画像详情 —— */
.pf {
  padding-bottom: 8rpx;
}
.pf-total {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 8rpx;
}
.pf-num {
  font-size: 56rpx;
  font-weight: 800;
  color: var(--orange);
}
.pf-unit {
  font-size: 24rpx;
  color: var(--ink-2);
}
.pf-block {
  margin-top: 32rpx;
}
.pf-title {
  font-size: 26rpx;
  font-weight: 700;
  color: var(--ink);
  margin-bottom: 16rpx;
}
.pf-cats {
  display: flex;
  gap: 16rpx;
}
.pf-cat {
  flex: 1;
  background: #fff;
  border-radius: $radius-sm;
  padding: 20rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.pf-cat-ico {
  font-size: 32rpx;
}
.pf-cat-n {
  margin-top: 6rpx;
  font-size: 30rpx;
  font-weight: 700;
  color: var(--ink);
}
.pf-cat-l {
  font-size: 22rpx;
  color: var(--ink-2);
}
.pf-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 0;
  border-bottom: 2rpx solid var(--line);
}
.pf-row-n {
  font-size: 26rpx;
  color: var(--ink);
}
.pf-row-c {
  font-size: 24rpx;
  color: var(--ink-2);
}
.pf-empty {
  margin-top: 40rpx;
  text-align: center;
  font-size: 24rpx;
  color: var(--ink-2);
}
</style>
