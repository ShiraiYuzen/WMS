<template>
  <div class="page-header">
    <div class="ph-left">
      <div class="ph-title-row">
        <h2 class="ph-title">{{ title }}</h2>
        <el-tag v-if="count !== null && count !== undefined" size="small" effect="plain" class="ph-count">
          共 {{ count }} 条
        </el-tag>
      </div>
      <p v-if="description" class="ph-desc">{{ description }}</p>
    </div>
    <div class="ph-right">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup>
/**
 * 页面标题栏。统一各页面的标题、描述与操作按钮区。
 */
defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
  count: { type: [Number, String], default: null }
})
</script>

<style scoped lang="scss">
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.ph-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ph-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: $text-primary;
  letter-spacing: 0.2px;
}

.ph-count {
  font-size: 12px;
  color: $text-secondary;
}

.ph-desc {
  margin: 5px 0 0;
  font-size: 12.5px;
  color: $text-secondary;
  line-height: 1.6;
}

.ph-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  flex-wrap: wrap;
}
</style>
