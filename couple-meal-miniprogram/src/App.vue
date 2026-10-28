<script setup lang="ts">
import { onLaunch, onShow } from '@dcloudio/uni-app'
import { setupGuard } from '@/utils/guard'
import { restoreLogin } from '@/stores/app'
import { bootstrapApp } from '@/utils/bootstrap'
import { initPrivacy } from '@/utils/privacy'

// 冷启动：恢复登录态 → 注册全局路由门控 → 静默恢复并门控跳转（10-关系绑定/前端.md §2）
onLaunch(() => {
  initPrivacy()
  restoreLogin()
  setupGuard()
  bootstrapApp()
})

// 切前台：同步未读数（接口 5.3 在 M4 落地，这里只搭路径）
onShow(() => {
  // if (getToken()) {
  //   request<number>({ url: '/api/msg/unread', silent: true })
  //     .then(setUnread)
  //     .catch(() => {})
  // }
})
</script>

<template></template>

<style lang="scss">
/* 前端-00-基础设施.md §6 —— 运行时颜色变量，支持换肤（改 page 上的 class 即可换主题）
 * 颜色必须用 CSS 变量而非 SCSS 变量，才能运行时切换。 */
page {
  --cream-bg: #fff9f2;
  --peach: #ffc9b0;
  --orange: #ff9b6a;
  --ink: #4a3f35;
  --ink-2: #8c7b6b;
  --line: #f0e4d8;
  --shadow: 0 12rpx 40rpx rgba(255, 155, 106, 0.14);
  --shadow-sm: 0 6rpx 20rpx rgba(255, 155, 106, 0.1);

  background: var(--cream-bg);
  color: var(--ink);
  font-size: 28rpx;
  line-height: 1.5;
}

/* 全局卡片外观：白底 + 主圆角 + 柔和阴影（$radius / soft-shadow 来自 uni.scss）。
 * 各页面卡片直接 class="card"，不要自己再写一遍圆角和阴影。 */
.card {
  background: #fff;
  border-radius: $radius;
  @include soft-shadow(2);
  overflow: hidden;
}
</style>
