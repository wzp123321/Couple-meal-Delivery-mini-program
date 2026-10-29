<script setup lang="ts">
// 设置页（10-关系绑定/前端.md §3 / PRD §5.8）：头像、昵称、专属称呼、纪念日、主题色、提醒开关、
// 解绑、注销、退出登录。
// 头像走「两步」：chooseAvatar 临时路径 → uploadFile 到 /api/user/avatar 换相对路径 → 再随资料保存。
// 上传中锁按钮（§6 易错点：连点会并发覆盖，最后留下的可能是旧图）。
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { get, post, upload } from '@/utils/api'
import { clearToken, getToken, setToken } from '@/utils/auth'
import { appState, applyCoupleInfo, logout, setRelation } from '@/stores/app'
import { resolveAsset } from '@/utils/asset'
import { formatDate } from '@/utils/date'
import { toast } from '@/utils/toast'
import { onChooseAvatar } from '@/utils/wx-capabilities'
import { isWxRemindOn, requestSubscribeConsent, setWxRemindOn } from '@/utils/subscribe'
import type { AvatarUploadResp, CoupleInfo, CoupleSettingsReq, ProfileResp } from '@/types/api'
import BottomSheet from '@/components/bottom-sheet.vue'

/**
 * uni 组件事件对象的结构壳：只声明我们用得到的 detail。
 * 声明成 `detail?: unknown` 而非具体形状，是因为模板里 `@blur` 会被 vue-tsc 按宿主事件校验
 * （参数逆变），收紧成 { value: string } 会报 TS2345；真正的 detail 由下面两个取值函数取。
 */
interface UniDetailEvent {
  /** 与宿主事件类型保有的公共属性，避免弱类型检查报 TS2345 */
  type?: string
  detail?: unknown
}

function eventValue(e: UniDetailEvent): string {
  const d = e.detail as { value?: string } | null | undefined
  return String(d?.value ?? '')
}

function eventBool(e: UniDetailEvent): boolean {
  const d = e.detail as { value?: boolean } | null | undefined
  return !!d?.value
}

// —— 表单：昵称 / 头像属个人（user/profile），其余属关系（couple/settings）——
const form = reactive({
  avatarUrl: '', // 相对路径或临时路径，展示统一走 resolveAsset
  nickname: '',
  nicknameMine: '', // 专属称呼：我对 TA 的叫法
  anniversary: '',
  theme: 'cream',
  remindEnabled: true,
})
const nicknameError = ref('')
const callError = ref('')

// 头像两步上传状态
const avatarUploading = ref(false)
const avatarSaveFailed = ref(false)
const uploadedAvatarPath = ref('') // 已上传待保存的相对路径：保存失败时留住，重试不重传
let oldAvatar = ''

// 主题色：只改本页 CSS 变量做即时反馈（全局换肤要动 App.vue 的 page 变量，不在本次改动范围）
const themes = [
  { key: 'cream', name: '奶油', color: '#FFC9B0' },
  { key: 'mint', name: '薄荷', color: '#BFE3D0' },
  { key: 'sky', name: '天空', color: '#BCD8F0' },
  { key: 'peach', name: '蜜桃', color: '#F6C6D4' },
]
const themeStyle = computed(() => {
  const t = themes.find((x) => x.key === form.theme)
  return t ? { '--peach': t.color } : {}
})
const themeName = computed(() => themes.find((x) => x.key === form.theme)?.name || '奶油')

const today = formatDate(new Date())

function refreshFromState(): void {
  oldAvatar = appState.user?.avatar || ''
  form.avatarUrl = appState.user?.avatar || ''
  form.nickname = appState.user?.nickname || ''
  const r = appState.relation
  if (r) {
    form.nicknameMine = r.nicknameMine
    form.anniversary = r.anniversary || ''
    form.theme = r.theme || 'cream'
    form.remindEnabled = r.remindEnabled
  }
}

async function loadRelation(): Promise<void> {
  if (appState.relation) {
    refreshFromState()
    return
  }
  try {
    const info = await get<CoupleInfo>('/api/couple/info')
    applyCoupleInfo(info)
    refreshFromState()
  } catch {
    // 1002 → 请求层已 reLaunch 配对页
  }
}

onShow(() => {
  void loadRelation()
})

// —— 头像 ——
function syncUser(patch: { nickname?: string; avatar?: string }): void {
  if (!appState.user) return
  const updated = { ...appState.user, ...patch }
  appState.user = updated
  setToken(getToken(), updated) // 落盘与内存态保持同源
}

function handleChooseAvatar(e: { detail: { avatarUrl: string } }): void {
  if (avatarUploading.value) return // 上传中锁住，禁止连点
  const tmp = onChooseAvatar(e) // http://tmp/... 仅本次会话有效，不能直接存
  form.avatarUrl = tmp // 先本地预览，不等接口（体感即时）
  avatarUploading.value = true
  avatarSaveFailed.value = false
  upload<AvatarUploadResp>('/api/user/avatar', tmp)
    .then((resp) => {
      uploadedAvatarPath.value = resp.avatar // 留住已上传路径
      form.avatarUrl = resp.avatar
      return post<ProfileResp>('/api/user/profile', { avatar: resp.avatar })
    })
    .then((resp) => {
      uploadedAvatarPath.value = ''
      avatarSaveFailed.value = false
      syncUser({ avatar: resp.avatar })
      oldAvatar = resp.avatar
    })
    .catch(() => {
      if (uploadedAvatarPath.value) {
        // 上传成功但保存失败：留住路径，重试不重传
        avatarSaveFailed.value = true
      } else {
        // 上传失败：回退旧头像 + toast，不出现破图（§5 验收）
        form.avatarUrl = oldAvatar
        toast('头像没传上去，换个网络再试试')
      }
    })
    .finally(() => {
      avatarUploading.value = false
    })
}

function retrySaveAvatar(): void {
  const path = uploadedAvatarPath.value
  if (!path || avatarUploading.value) return
  avatarUploading.value = true
  post<ProfileResp>('/api/user/profile', { avatar: path })
    .then((resp) => {
      uploadedAvatarPath.value = ''
      avatarSaveFailed.value = false
      syncUser({ avatar: resp.avatar })
      oldAvatar = resp.avatar
      toast('头像已换好啦')
    })
    .catch(() => toast('保存失败，请重试'))
    .finally(() => {
      avatarUploading.value = false
    })
}

// —— 昵称（type="nickname" 的值在 blur 才可靠，见基础设施 §2 易错点 2）——
function handleNicknameBlur(e: UniDetailEvent): void {
  const v = eventValue(e)
  form.nickname = v
  if (v.length > 64) {
    nicknameError.value = '昵称不能超过 64 个字'
    return
  }
  nicknameError.value = ''
  if (!v) return
  post<ProfileResp>('/api/user/profile', { nickname: v })
    .then((resp) => syncUser({ nickname: resp.nickname }))
    .catch(() => {
      // 请求层已 toast
    })
}

// —— 专属称呼（2–12 字，走 couple/settings.nicknameMine）——
function saveCall(): void {
  const v = form.nicknameMine.trim()
  if (!v) {
    callError.value = ''
    void saveSettings({ nicknameMine: v })
    return
  }
  if (v.length < 2 || v.length > 12) {
    callError.value = '起个 2–12 字的称呼吧'
    return
  }
  callError.value = ''
  void saveSettings({ nicknameMine: v }, '称呼已记下啦 💞')
}

// —— 纪念日（驱动「在一起 N 天」）——
function onAnniversaryChange(e: UniDetailEvent): void {
  form.anniversary = eventValue(e)
  void saveSettings({ anniversary: form.anniversary }, '纪念日已记下 💝')
}

// —— 主题色 / 提醒开关 ——
function selectTheme(key: string): void {
  form.theme = key
  void saveSettings({ theme: key })
}

function onRemindChange(e: UniDetailEvent): void {
  form.remindEnabled = eventBool(e)
  void saveSettings({ remindEnabled: form.remindEnabled })
}

/**
 * 微信消息提醒：本地开关（storage wx_remind_on），**与上面的 remindEnabled 无关**——
 * 后者是关系级的「每日带饭提醒」，存 couple 表、由后端定时任务读；
 * 这个开关只表示「我愿意收微信订阅消息」，打开时立刻申请一次授权（微信侧记账，后端下发时才用得上）。
 */
const wxRemindOn = ref(isWxRemindOn())

async function onWxRemindChange(e: UniDetailEvent): Promise<void> {
  const on = eventBool(e)
  setWxRemindOn(on)
  wxRemindOn.value = on
  if (!on) return // 关掉后不再主动请求授权
  const res = await requestSubscribeConsent()
  if (res !== 'rejected') return
  // 用户拒绝：开关回落关闭并提示一次，不反复弹窗（spec「用户开启开关即申请授权」）
  setWxRemindOn(false)
  wxRemindOn.value = false
  toast('没开成微信提醒，想收提醒时再来开一次')
}

/** 关系级设置：成功后重取 couple/info 同步全局态（铁律：服务端状态不本地臆造） */
async function saveSettings(data: CoupleSettingsReq, okText?: string): Promise<void> {
  try {
    await post('/api/couple/settings', data)
    const info = await get<CoupleInfo>('/api/couple/info')
    applyCoupleInfo(info)
    if (okText) toast(okText)
  } catch {
    // 错误码 → toast 已由请求层处理
  }
}

// —— 退出登录（PRD §12.9：轻提示即可，无需 L3 弹层）——
function onLogout(): void {
  uni.showModal({
    title: '退出登录',
    content: '退出后需要重新登录才能看到你们的约定。',
    confirmColor: '#FF9B6A',
    success: (res) => {
      if (!res.confirm) return
      clearToken()
      logout()
      toast('已退出登录')
      uni.reLaunch({ url: '/pages/login/index' })
    },
  })
}

// —— 解绑（L3 二次确认，必须明说历史保留）——
const unbindVisible = ref(false)
const unbinding = ref(false)

function confirmUnbind(): void {
  if (unbinding.value) return
  unbinding.value = true
  post('/api/couple/unbind')
    .then(() => {
      setRelation(null) // §4：解绑必须清缓存，否则换绑后旧关系仍在内存里
      clearDrafts()
      unbindVisible.value = false
      uni.reLaunch({ url: '/pages/pair/index' }) // 1002 也会兜底
    })
    .catch(() => {
      unbindVisible.value = false
    })
    .finally(() => {
      unbinding.value = false
    })
}

// —— 注销（L3 二次确认 + 勾选知晓项）——
const deleteVisible = ref(false)
const deleteAck = ref(false)
const deleting = ref(false)

function openDelete(): void {
  deleteAck.value = false
  deleteVisible.value = true
}

function confirmDelete(): void {
  if (!deleteAck.value) {
    toast('请先勾选「我已知晓」')
    return
  }
  if (deleting.value) return
  deleting.value = true
  post('/api/user/delete')
    .then(() => {
      // 注销必须全清（§4）：token / user / relation / 所有 draft:* 一并清空
      uni.clearStorageSync()
      logout()
      deleteVisible.value = false
      toast('账号已注销')
      uni.reLaunch({ url: '/pages/login/index' })
    })
    .catch(() => {
      deleteVisible.value = false
    })
    .finally(() => {
      deleting.value = false
    })
}

/** 清 storage 里所有 draft:* 键（登录态除外，由上层各自处理） */
function clearDrafts(): void {
  uni.getStorageInfoSync().keys.forEach((k) => {
    if (k.startsWith('draft:')) uni.removeStorageSync(k)
  })
}
</script>

<template>
  <view class="settings" :style="themeStyle">
    <!-- 头像 + 昵称 -->
    <view class="section">
      <!-- #ifdef MP-WEIXIN -->
      <button
        class="avatar-btn"
        open-type="chooseAvatar"
        :disabled="avatarUploading"
        @chooseavatar="handleChooseAvatar"
      >
        <image
          v-if="resolveAsset(form.avatarUrl)"
          class="avatar"
          :src="resolveAsset(form.avatarUrl)"
          mode="aspectFill"
        />
        <view v-else class="avatar placeholder">👤</view>
        <view v-if="avatarUploading" class="avatar-ring"></view>
      </button>
      <!-- #endif -->
      <!-- #ifndef MP-WEIXIN -->
      <view class="avatar-btn">
        <image
          v-if="resolveAsset(form.avatarUrl)"
          class="avatar"
          :src="resolveAsset(form.avatarUrl)"
          mode="aspectFill"
        />
        <view v-else class="avatar placeholder">👤</view>
      </view>
      <!-- #endif -->

      <view v-if="avatarSaveFailed" class="row-tip" @tap="retrySaveAvatar">头像已上传，保存失败，点此重试</view>
      <view v-else-if="avatarUploading" class="row-tip">头像上传中…</view>

      <!-- #ifdef MP-WEIXIN -->
      <input
        class="nickname-input"
        type="nickname"
        :value="form.nickname"
        placeholder="点击填写昵称"
        @blur="handleNicknameBlur"
      />
      <!-- #endif -->
      <!-- #ifndef MP-WEIXIN -->
      <input
        class="nickname-input"
        :value="form.nickname"
        placeholder="昵称（仅微信端可快捷填写）"
        @blur="handleNicknameBlur"
      />
      <!-- #endif -->
      <view v-if="nicknameError" class="row-tip err">{{ nicknameError }}</view>
    </view>

    <!-- 关系设置 -->
    <view class="section">
      <view class="field">
        <text class="label">💝 专属称呼</text>
        <input
          class="field-input"
          :value="form.nicknameMine"
          placeholder="2–12 字，如「猪猪」"
          :maxlength="12"
          @input="(e: UniDetailEvent) => (form.nicknameMine = eventValue(e))"
          @blur="saveCall"
        />
      </view>
      <view v-if="callError" class="row-tip err left">{{ callError }}</view>

      <view class="field">
        <text class="label">🎉 在一起纪念日</text>
        <picker mode="date" :value="form.anniversary" :end="today" @change="onAnniversaryChange">
          <view class="field-value">{{ form.anniversary || '未设置' }}</view>
        </picker>
      </view>

      <view class="field col">
        <text class="label">🎨 情侣主题色</text>
        <view class="theme-row">
          <view
            v-for="t in themes"
            :key="t.key"
            class="theme-item"
            :class="{ on: form.theme === t.key }"
            @tap="selectTheme(t.key)"
          >
            <view class="theme-dot" :style="{ background: t.color }"></view>
            <text class="theme-name">{{ t.name }}</text>
          </view>
        </view>
      </view>

      <view class="field">
        <view class="label-col">
          <text class="label">🔔 每日带饭提醒</text>
          <text class="desc">工作日 11:00 提醒午饭、17:00 提醒晚饭</text>
        </view>
        <switch :checked="form.remindEnabled" color="#FF9B6A" @change="onRemindChange" />
      </view>

      <view class="field">
        <view class="label-col">
          <text class="label">💬 微信消息提醒</text>
          <text class="desc">认领、送达、待打卡时微信通知你</text>
        </view>
        <switch :checked="wxRemindOn" color="#FF9B6A" @change="onWxRemindChange" />
      </view>
    </view>

    <!-- 危险操作 -->
    <view class="section">
      <view class="danger-item" @tap="unbindVisible = true">💔 解绑关系</view>
      <view class="danger-item red" @tap="openDelete">🗑 注销账号</view>
    </view>

    <view class="logout-wrap">
      <button class="logout-btn" @tap="onLogout">退出登录</button>
    </view>
    <view class="foot">v1.0.0 · 仅收集 openid / 昵称 / 头像</view>

    <!-- 解绑 L3 确认 -->
    <BottomSheet :visible="unbindVisible" title="确定要解绑吗？" @close="unbindVisible = false">
      <view class="confirm-text">
        解绑后你们不再是情侣关系，但<text class="strong">历史记录（约定、菜单、打卡）都会保留</text>，
        仍可在「历史」里回看。之后可以重新和 TA 配对。
      </view>
      <template #footer>
        <view class="btn-row">
          <button class="btn-cancel" @tap="unbindVisible = false">再想想</button>
          <button class="btn-danger" :disabled="unbinding" @tap="confirmUnbind">
            {{ unbinding ? '解绑中…' : '确认解绑' }}
          </button>
        </view>
      </template>
    </BottomSheet>

    <!-- 注销 L3 确认 + 勾选知晓项 -->
    <BottomSheet :visible="deleteVisible" title="注销账号" :mask-closable="false" @close="deleteVisible = false">
      <view class="confirm-text">
        注销后将<text class="strong">真实删除你的个人身份数据，且不可恢复</text>。
        你们一起吃饭的记录会留下来，只是不再属于你。
      </view>
      <view class="ack" @tap="deleteAck = !deleteAck">
        <view class="checkbox" :class="{ on: deleteAck }"></view>
        <text class="ack-text">我已知晓：注销后无法恢复，我上传的打卡照片会被删除</text>
      </view>
      <template #footer>
        <view class="btn-row">
          <button class="btn-cancel" @tap="deleteVisible = false">再想想</button>
          <button class="btn-danger" :disabled="!deleteAck || deleting" @tap="confirmDelete">
            {{ deleting ? '注销中…' : '确认注销' }}
          </button>
        </view>
      </template>
    </BottomSheet>
  </view>
</template>

<style scoped lang="scss">
.settings {
  min-height: 100vh;
  padding: 32rpx 32rpx 60rpx;
  box-sizing: border-box;
  background: var(--cream-bg);
}
.section {
  background: #fff;
  border-radius: $radius;
  padding: 16rpx 32rpx;
  margin-bottom: 24rpx;
  box-shadow: var(--shadow-sm);
}
.avatar-btn {
  display: block;
  width: 140rpx;
  height: 140rpx;
  margin: 24rpx auto 8rpx;
  padding: 0;
  border-radius: 50%;
  position: relative;
  background: transparent;
  &::after {
    border: none;
  }
}
.avatar {
  width: 140rpx;
  height: 140rpx;
  border-radius: 50%;
  border: 4rpx solid var(--peach);
  background: var(--line);
}
.avatar.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 60rpx;
  color: var(--ink-2);
}
/* 上传中：头像角标转圈（§3 头像交互） */
.avatar-ring {
  position: absolute;
  right: -4rpx;
  bottom: -4rpx;
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  background: #fff;
  box-sizing: border-box;
  border: 4rpx solid rgba(255, 155, 106, 0.25);
  border-top-color: var(--orange);
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
.nickname-input {
  text-align: center;
  height: 72rpx;
  font-size: 30rpx;
  color: var(--ink);
}
.row-tip {
  text-align: center;
  font-size: 24rpx;
  color: var(--orange);
  padding-bottom: 16rpx;
  &.err {
    color: #e85c5c;
  }
  &.left {
    text-align: left;
  }
}
.field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 0;
  border-bottom: 2rpx solid var(--line);
  &.col {
    flex-direction: column;
    align-items: flex-start;
    gap: 16rpx;
  }
  &:last-child {
    border-bottom: none;
  }
}
.label-col {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}
.label {
  font-size: 30rpx;
  color: var(--ink);
}
.desc {
  font-size: 22rpx;
  color: var(--ink-2);
}
.field-input {
  flex: 1;
  text-align: right;
  font-size: 28rpx;
  color: var(--ink-2);
}
.field-value {
  font-size: 28rpx;
  color: var(--ink-2);
}
.theme-row {
  display: flex;
  gap: 28rpx;
}
.theme-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}
.theme-dot {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  border: 4rpx solid transparent;
}
.theme-name {
  font-size: 22rpx;
  color: var(--ink-2);
}
.theme-item.on {
  .theme-dot {
    border-color: var(--orange);
  }
  .theme-name {
    color: var(--orange);
    font-weight: 600;
  }
}
.danger-item {
  padding: 30rpx 0;
  font-size: 30rpx;
  color: var(--ink);
  border-bottom: 2rpx solid var(--line);
  &.red {
    color: #e85c5c;
    border-bottom: none;
  }
}
.logout-wrap {
  padding: 0 8rpx;
}
.logout-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: $radius;
  background: #fff;
  color: var(--ink-2);
  font-size: 30rpx;
  border: 2rpx solid var(--line);
  &::after {
    border: none;
  }
}
.foot {
  margin-top: 24rpx;
  text-align: center;
  font-size: 22rpx;
  color: var(--ink-2);
}
.confirm-text {
  font-size: 28rpx;
  color: var(--ink);
  line-height: 1.6;
}
.strong {
  color: var(--orange);
  font-weight: 600;
}
.ack {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 24rpx;
}
.checkbox {
  width: 32rpx;
  height: 32rpx;
  border-radius: 6rpx;
  border: 2rpx solid var(--ink-2);
  box-sizing: border-box;
  flex-shrink: 0;
  &.on {
    background: var(--orange);
    border-color: var(--orange);
    position: relative;
  }
  &.on::after {
    content: '';
    position: absolute;
    left: 10rpx;
    top: 5rpx;
    width: 8rpx;
    height: 14rpx;
    border: solid #fff;
    border-width: 0 3rpx 3rpx 0;
    transform: rotate(45deg);
  }
}
.ack-text {
  font-size: 24rpx;
  color: var(--ink-2);
}
.btn-row {
  display: flex;
  gap: 24rpx;
}
.btn-cancel,
.btn-danger {
  flex: 1;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: $radius;
  font-size: 30rpx;
  border: none;
  &::after {
    border: none;
  }
}
.btn-cancel {
  background: var(--line);
  color: var(--ink);
}
.btn-danger {
  background: #e85c5c;
  color: #fff;
  &[disabled] {
    opacity: 0.5;
  }
}
</style>
