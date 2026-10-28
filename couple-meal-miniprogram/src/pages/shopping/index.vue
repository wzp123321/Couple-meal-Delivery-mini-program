<script setup lang="ts">
// 采购清单（docs/design/80-采购清单/前端.md §2）：为这顿饭要买的东西。
// 勾选是全项目唯一允许的乐观更新点——操作可逆且高频：本地立即翻转再异步提交，失败必须翻回去并轻提示。
// 已买条目不隐藏，置底折叠 + 灰字，用户还能核对买了什么。
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { get, post } from '@/utils/api'
import { toast } from '@/utils/toast'
import type {
  Flag,
  PlanDetail,
  ShoppingGenerateResp,
  ShoppingItem,
  ShoppingListResp,
} from '@/types/api'

const planId = ref(0)
const items = ref<ShoppingItem[]>([])
const plan = ref<PlanDetail | null>(null)
const loading = ref(false)
const loaded = ref(false)
const boughtOpen = ref(false)

/** 手动增补 */
const addVisible = ref(false)
const addName = ref('')
const adding = ref(false)

const todo = computed(() => items.value.filter((i) => i.checkedFlag === 0))
const bought = computed(() => items.value.filter((i) => i.checkedFlag === 1))
const total = computed(() => items.value.length)
const boughtCount = computed(() => bought.value.length)
const progress = computed(() => {
  if (total.value === 0) return 0
  return Math.round((boughtCount.value / total.value) * 100)
})

async function loadList(): Promise<void> {
  if (loading.value) return
  loading.value = true
  try {
    const resp = await get<ShoppingListResp>('/api/shopping/list', {
      planId: planId.value || undefined,
    })
    planId.value = resp.planId
    items.value = [...(resp.list ?? [])]
    await loadPlan()
  } catch {
    // 1002 已由 request 层 reLaunch 配对页；其余已 toast
  } finally {
    loading.value = false
    loaded.value = true
  }
}

/** 顶部关联约定卡片（日期 + 菜品）；取不到就只留空壳不显示，不阻塞清单 */
async function loadPlan(): Promise<void> {
  if (!planId.value) {
    plan.value = null
    return
  }
  try {
    plan.value = await get<PlanDetail>(`/api/plan/${planId.value}`, undefined, { silent: true })
  } catch {
    plan.value = null
  }
}

/** 勾选：先本地翻转（即时反馈），再异步提交；失败翻回去 */
async function onToggle(item: ShoppingItem): Promise<void> {
  const prev = item.checkedFlag
  const next: Flag = prev === 1 ? 0 : 1
  item.checkedFlag = next
  try {
    await post('/api/shopping/check', { id: item.id, checkedFlag: next }, { silent: true })
  } catch {
    item.checkedFlag = prev
    toast('没勾上，再点一次试试')
  }
}

function openAdd(): void {
  addName.value = ''
  addVisible.value = true
}

/**
 * 手动增补：后端没有独立的新增接口，用 /generate 幂等重放——
 * 把现有条目名连同新的那一项一起传，靠 uk_shopping_list_plan_item 防重复。
 */
async function submitAdd(): Promise<void> {
  const name = addName.value.trim()
  if (!name) {
    toast('先写个名字吧')
    return
  }
  if (name.length > 20) {
    toast('名字太长啦，20 字以内')
    return
  }
  if (!planId.value) {
    toast('还没有关联的约定')
    return
  }
  adding.value = true
  try {
    const resp = await post<ShoppingGenerateResp>('/api/shopping/generate', {
      planId: planId.value,
      items: [...items.value.map((i) => i.itemName), name],
    })
    items.value = [...(resp.list ?? [])]
    toast('加上啦')
    addVisible.value = false
  } catch {
    // 失败已 toast
  } finally {
    adding.value = false
  }
}

/** 清单为空且有约定时，按约定菜品推一份 */
async function generate(): Promise<void> {
  if (!planId.value) return
  adding.value = true
  try {
    const resp = await post<ShoppingGenerateResp>('/api/shopping/generate', { planId: planId.value })
    items.value = [...(resp.list ?? [])]
    toast('清单生成好啦')
  } catch {
    // 1004（终态）等已 toast
  } finally {
    adding.value = false
  }
}

function goPlan(): void {
  if (!planId.value) return
  uni.navigateTo({ url: `/pages/plan/detail?id=${planId.value}` })
}

onLoad((query) => {
  planId.value = Number((query as { planId?: string } | undefined)?.planId ?? 0)
  void loadList()
})
</script>

<template>
  <view class="shopping">
    <!-- 关联约定卡片：说明这是「为这顿饭要买的东西」 -->
    <view v-if="plan" class="pcard" @tap="goPlan">
      <view class="ptop">
        <text class="pdate">{{ plan.planDate }}</text>
        <text class="pgo">回到约定 ›</text>
      </view>
      <text class="pdish">{{ plan.items.map((i) => i.name).join(' · ') }}</text>
    </view>

    <view v-if="loaded && total > 0" class="prog">
      <view class="prow">
        <text class="ptext">已买 {{ boughtCount }}/{{ total }}</text>
        <text class="ppct">{{ progress }}%</text>
      </view>
      <view class="pbar">
        <view class="pfill" :style="{ width: progress + '%' }" />
      </view>
    </view>

    <view v-if="!loaded" class="skeleton">
      <view v-for="i in 4" :key="i" class="sk-row" />
    </view>

    <view v-else-if="total === 0" class="empty">
      <text class="empty-ico">🛒</text>
      <text class="empty-t">还没有清单</text>
      <text class="empty-d">从约定详情里生成一份，到超市照着买就好。</text>
      <view v-if="planId" class="empty-btn" :class="{ off: adding }" @tap="generate">
        {{ adding ? '正在生成…' : '生成清单' }}
      </view>
    </view>

    <template v-else>
      <view class="sec">
        <view v-for="item in todo" :key="item.id" class="sitem" @tap="onToggle(item)">
          <view class="scheck"><text v-if="item.checkedFlag === 1" class="stick">✓</text></view>
          <text class="sname">{{ item.itemName }}</text>
        </view>
      </view>

      <view class="sec sec-bought">
        <view class="sec-head" @tap="boughtOpen = !boughtOpen">
          <text class="sec-t">已买 {{ boughtCount }}</text>
          <text class="sec-a">{{ boughtOpen ? '收起' : '展开' }}</text>
        </view>
        <view v-if="boughtOpen">
          <view v-for="item in bought" :key="item.id" class="sitem done" @tap="onToggle(item)">
            <view class="scheck on"><text class="stick">✓</text></view>
            <text class="sname done">{{ item.itemName }}</text>
            <text class="sundo">拉回未买</text>
          </view>
          <view v-if="bought.length === 0" class="sec-empty">还没勾过一项</view>
        </view>
      </view>

      <view class="add" @tap="openAdd">＋ 添一项</view>
    </template>
  </view>

  <!-- 手动增补：底部弹层，20 字以内 -->
  <view v-if="addVisible" class="mask" @tap="addVisible = false">
    <view class="sheet" @tap.stop>
      <view class="stitle">添一样要买的</view>
      <input
        v-model="addName"
        class="sinput"
        :maxlength="20"
        placeholder="比如：香菜"
        placeholder-style="color:#B8A99A"
      />
      <view class="sbtn" :class="{ off: adding }" @tap="submitAdd">
        {{ adding ? '正在添加…' : '加上' }}
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.shopping {
  min-height: 100vh;
  padding: 24rpx 0 60rpx;
  box-sizing: border-box;
  background: var(--cream-bg);
}
.pcard {
  margin: 0 32rpx 16rpx;
  padding: 24rpx 28rpx;
  border-radius: $radius;
  background: linear-gradient(135deg, #fff, #fff3e6);
  box-shadow: var(--shadow-sm);
}
.ptop {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.pdate {
  font-size: 24rpx;
  color: var(--ink-2);
}
.pgo {
  font-size: 22rpx;
  color: var(--orange);
}
.pdish {
  display: block;
  margin-top: 10rpx;
  font-size: 30rpx;
  font-weight: 700;
  color: var(--ink);
}
.prog {
  margin: 0 32rpx 8rpx;
}
.prow {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.ptext {
  font-size: 26rpx;
  font-weight: 700;
  color: var(--ink);
}
.ppct {
  font-size: 22rpx;
  color: var(--ink-2);
}
.pbar {
  margin-top: 10rpx;
  height: 12rpx;
  border-radius: 8rpx;
  background: #f2e7dc;
  overflow: hidden;
}
.pfill {
  height: 100%;
  border-radius: 8rpx;
  background: linear-gradient(135deg, #ffb391, var(--orange));
}
.skeleton {
  padding: 12rpx 32rpx 0;
}
.sk-row {
  height: 110rpx;
  border-radius: $radius;
  background: #fff;
  margin-bottom: 16rpx;
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
.empty-btn {
  margin-top: 28rpx;
  padding: 16rpx 48rpx;
  border-radius: 44rpx;
  background: var(--orange);
  color: #fff;
  font-size: 28rpx;
  font-weight: 700;
}
.empty-btn.off {
  background: #e7ddd4;
  color: #a99b8d;
}
.sec {
  margin: 16rpx 32rpx 0;
  background: #fff;
  border-radius: $radius;
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}
.sec-bought {
  background: #fbf6f1;
  box-shadow: none;
}
.sitem {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 26rpx 28rpx;
  border-bottom: 2rpx solid var(--line);
}
.sitem:last-child {
  border-bottom: none;
}
.sitem.done {
  opacity: 0.7;
}
.scheck {
  width: 44rpx;
  height: 44rpx;
  border-radius: 14rpx;
  border: 3rpx solid var(--peach);
  background: #fff;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}
.scheck.on {
  background: linear-gradient(135deg, #ffb391, var(--orange));
  border-color: transparent;
}
.stick {
  color: #fff;
  font-size: 26rpx;
  font-weight: 700;
}
.sname {
  flex: 1;
  font-size: 30rpx;
  font-weight: 600;
  color: var(--ink);
}
.sname.done {
  text-decoration: line-through;
  color: var(--ink-2);
}
.sundo {
  font-size: 22rpx;
  color: var(--ink-2);
}
.sec-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 22rpx 28rpx;
}
.sec-t {
  font-size: 26rpx;
  font-weight: 700;
  color: var(--ink-2);
}
.sec-a {
  font-size: 22rpx;
  color: var(--orange);
}
.sec-empty {
  padding: 10rpx 28rpx 24rpx;
  font-size: 24rpx;
  color: var(--ink-2);
}
.add {
  margin: 24rpx 32rpx 0;
  padding: 22rpx 0;
  border-radius: $radius;
  border: 3rpx dashed var(--peach);
  text-align: center;
  font-size: 28rpx;
  font-weight: 700;
  color: var(--orange);
}
.mask {
  position: fixed;
  inset: 0;
  background: rgba(74, 63, 53, 0.45);
  display: flex;
  align-items: flex-end;
  z-index: 999;
}
.sheet {
  width: 100%;
  background: var(--cream-bg);
  border-radius: 32rpx 32rpx 0 0;
  padding: 40rpx 32rpx calc(40rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}
.stitle {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--ink);
  text-align: center;
}
.sinput {
  margin-top: 28rpx;
  height: 88rpx;
  padding: 0 24rpx;
  border-radius: 24rpx;
  background: #fff;
  border: 3rpx solid var(--line);
  font-size: 28rpx;
  color: var(--ink);
  box-sizing: border-box;
}
.sbtn {
  margin-top: 28rpx;
  height: 88rpx;
  border-radius: 44rpx;
  background: var(--orange);
  color: #fff;
  font-size: 30rpx;
  font-weight: 700;
  line-height: 88rpx;
  text-align: center;
}
.sbtn.off {
  background: #e7ddd4;
  color: #a99b8d;
}
</style>
