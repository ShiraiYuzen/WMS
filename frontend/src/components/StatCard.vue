<template>
  <div class="stat-card" :class="[`tone-${tone}`, { clickable: !!to }]" @click="onClick">
    <div class="sc-body">
      <div class="sc-label">
        {{ label }}
        <el-tooltip v-if="tip" :content="tip" placement="top">
          <el-icon class="sc-tip"><QuestionFilled /></el-icon>
        </el-tooltip>
      </div>
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

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { formatNumber } from '@/utils'

const props = defineProps({
  label: { type: String, required: true },
  value: { type: [Number, String], default: 0 },
  unit: { type: String, default: '' },
  sub: { type: String, default: '' },
  tip: { type: String, default: '' },
  icon: { type: String, default: '' },
  /** 色调：default / primary / success / warning / danger */
  tone: { type: String, default: 'default' },
  /** 数字是否千分位 */
  thousand: { type: Boolean, default: true },
  /** 点击跳转路径 */
  to: { type: String, default: '' }
})

const router = useRouter()

const displayValue = computed(() => {
  if (typeof props.value === 'string') return props.value
  return props.thousand ? formatNumber(props.value) : String(props.value)
})

function onClick() {
  if (props.to) router.push(props.to)
}
</script>

<style scoped lang="scss">
.stat-card {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 18px;
  background: #fff;
  border: 1px solid $border-base;
  border-radius: $radius-card;
  box-shadow: $shadow-card;
  overflow: hidden;
  transition: transform 0.16s, box-shadow 0.16s;

  // 顶部色条
  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 3px;
    background: var(--tone-color, #cbd5e1);
  }

  &.clickable {
    cursor: pointer;

    &:hover {
      transform: translateY(-2px);
      box-shadow: $shadow-hover;
    }
  }
}

.tone-default {
  --tone-color: #94a3b8;
}

.tone-primary {
  --tone-color: #2563eb;
}

.tone-success {
  --tone-color: #16a34a;
}

.tone-warning {
  --tone-color: #f59e0b;
}

.tone-danger {
  --tone-color: #dc2626;
}

.sc-body {
  min-width: 0;
}

.sc-label {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12.5px;
  color: $text-secondary;
  margin-bottom: 7px;
}

.sc-tip {
  font-size: 12px;
  color: #cbd5e1;
  cursor: help;
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
  color: $text-primary;
  letter-spacing: -0.4px;
}

.sc-unit {
  font-size: 12px;
  color: $text-placeholder;
}

.sc-sub {
  margin-top: 6px;
  font-size: 11.5px;
  color: $text-placeholder;
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
