<template>
  <router-view v-slot="{ Component, route }">
    <transition name="fade-slide" mode="out-in">
      <component :is="Component" :key="route.path" />
    </transition>
  </router-view>
</template>

<script setup lang="ts">
/** 根组件：只承载路由出口与页面切换动画，布局在 views/Layout.vue */
</script>
