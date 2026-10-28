<script setup lang="ts">
// 9 个弹层的统一容器（前端-00-基础设施.md §7）：圆角、遮罩、滑入动画只写一次，内容走默认 slot。
// M1 用于：隐私协议弹窗、解绑/注销 L3 二次确认。
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    visible: boolean
    title?: string
    maskClosable?: boolean
  }>(),
  { title: '', maskClosable: true },
)

const emit = defineEmits<{ (e: 'close'): void }>()

const show = computed(() => props.visible)

function onMask(): void {
  if (props.maskClosable) emit('close')
}
</script>

<template>
  <view v-if="show" class="bs-mask" @tap="onMask">
    <view class="bs-sheet" @tap.stop>
      <view v-if="title" class="bs-title">{{ title }}</view>
      <view class="bs-body">
        <slot />
      </view>
      <view v-if="$slots.footer" class="bs-footer">
        <slot name="footer" />
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.bs-mask {
  position: fixed;
  inset: 0;
  background: rgba(74, 63, 53, 0.45);
  display: flex;
  align-items: flex-end;
  z-index: 999;
}
.bs-sheet {
  width: 100%;
  background: var(--cream-bg);
  border-radius: 32rpx 32rpx 0 0;
  padding: 40rpx 32rpx calc(40rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
  max-height: 85vh;
  display: flex;
  flex-direction: column;
  animation: bs-up 0.24s ease-out;
}
@keyframes bs-up {
  from {
    transform: translateY(100%);
  }
  to {
    transform: translateY(0);
  }
}
.bs-title {
  font-size: 34rpx;
  font-weight: 600;
  color: var(--ink);
  text-align: center;
  margin-bottom: 24rpx;
}
.bs-body {
  font-size: 28rpx;
  color: var(--ink-2);
  line-height: 1.6;
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}
.bs-footer {
  margin-top: 32rpx;
}
</style>
