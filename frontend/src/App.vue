<template>
  <router-view v-slot="{ Component, route }">
    <transition name="fade-slide" mode="out-in">
      <component :is="Component" :key="route.path" />
    </transition>
  </router-view>
</template>

<script setup>
/**
 * 根组件。
 * 只负责承载路由出口与页面切换动画，具体布局在 layouts/MainLayout.vue。
 */
</script>
