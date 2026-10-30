<template>
  <div class="stat-card" :class="[`tone-${tone}`, { clickable: !!to }]" @click="onClick">
    <div class="sc-body">
      <div class="sc-label">{{ label }}</div>
      <div class="sc-value">
        <span class="num">{{ displayValue }}</span>
        <span v-if="unit" class="sc-unit">{{ unit }}</span>
      </div>
      <div v-if="sub" class="sc-sub">{{ sub }}</div>
    </div>
    <div v-if="icon" class="sc-icon">
      <el-icon><component :is="icon" /></el-icon>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'

/** 统计卡（融合自第一版）：顶部色条 + 大数字 + 图标底 */
const props = defineProps({
  label: { type: String, required: true },
  value: { type: [Number, String], default: 0 },
  unit: { type: String, default: '' },
  sub: { type: String, default: '' },
  icon: { type: String, default: '' },
  /** default / primary / success / warning / danger / info */
  tone: { type: String, default: 'default' },
  to: { type: String, default: '' },
})

const router = useRouter()

const displayValue = computed(() => {
  if (typeof props.value === 'string') return props.value
  return Number(props.value).toLocaleString('zh-CN')
})

function onClick() {
  if (props.to) router.push(props.to)
}
</script>

<style scoped>
.stat-card {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 18px;
  background: #fff;
  border: 1px solid var(--wms-border);
  border-radius: 10px;
  box-shadow: var(--wms-shadow-card);
  overflow: hidden;
  transition: transform 0.16s, box-shadow 0.16s;
}

.stat-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--tone-color, #cbd5e1);
}

.stat-card.clickable {
  cursor: pointer;
}

.stat-card.clickable:hover {
  transform: translateY(-2px);
  box-shadow: var(--wms-shadow-hover);
}

.tone-default { --tone-color: #94a3b8; }
.tone-primary { --tone-color: #2563eb; }
.tone-success { --tone-color: #16a34a; }
.tone-warning { --tone-color: #f59e0b; }
.tone-danger  { --tone-color: #dc2626; }
.tone-info    { --tone-color: #64748b; }

.sc-body {
  min-width: 0;
}

.sc-label {
  font-size: 12.5px;
  color: var(--wms-text-secondary);
  margin-bottom: 7px;
  white-space: nowrap;
}

.sc-value {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.sc-value .num {
  font-size: 25px;
  font-weight: 700;
  line-height: 1.1;
  color: var(--wms-text-primary);
  letter-spacing: -0.4px;
}

.sc-unit {
  font-size: 12px;
  color: var(--wms-text-placeholder);
}

.sc-sub {
  margin-top: 6px;
  font-size: 11.5px;
  color: var(--wms-text-placeholder);
}

.sc-icon {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: var(--tone-color, #94a3b8);
  background: color-mix(in srgb, var(--tone-color, #94a3b8) 12%, #fff);
}
</style>
