<script setup lang="ts">
// M0 占位启动页（奶油风）。业务页面（login / today / menu / message / me / pair / ...）在 M1+ 落地。
// 完整 pages.json（含 tabBar 与 13 个页面）见 前端-00-基础设施.md §3，于对应里程碑补上。
</script>

<template>
  <view class="launch">
    <view class="logo">🍱</view>
    <view class="tip">情侣带饭 · 加载中</view>
  </view>
</template>

<style scoped lang="scss">
.launch {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $gap;
  background: var(--cream-bg);
}
.logo {
  font-size: 96rpx;
  line-height: 1;
}
.tip {
  color: var(--ink-2);
  font-size: 28rpx;
}
</style>
