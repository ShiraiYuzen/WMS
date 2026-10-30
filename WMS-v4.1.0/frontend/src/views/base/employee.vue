<template>
  <div class="page-container">
    <PageHeader
      title="人员管理"
      description="仓库作业人员档案。角色决定菜单可见范围与待办内容，班组用于效率复盘中按组对比。"
      :count="total"
    >
      <template #actions>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <!-- 角色分布 -->
    <div class="role-row mb-12">
      <div v-for="r in roleStats" :key="r.code" class="role-card">
        <div class="rc-icon" :style="{ background: r.soft, color: r.color }">
          <el-icon><component :is="r.icon" /></el-icon>
        </div>
        <div class="rc-body">
          <div class="rc-name">{{ r.desc }}</div>
          <div class="rc-count"><span class="num">{{ r.count }}</span> 人</div>
        </div>
      </div>
    </div>

    <div class="card card-pad mb-12">
      <el-form :model="query" inline class="filter-form">
        <el-form-item label="角色">
          <el-select v-model="query.role" placeholder="全部" clearable style="width: 160px">
            <el-option v-for="o in dict.options('employeeRole')" :key="o.code" :label="o.desc" :value="o.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="班组">
          <el-select v-model="query.team" placeholder="全部" clearable style="width: 160px">
            <el-option v-for="t in teams" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button :icon="RefreshLeft" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="card">
      <el-table v-loading="loading" :data="rows" stripe height="calc(100vh - 452px)">
        <el-table-column label="工号" width="110" fixed>
          <template #default="{ row }"><span class="mono">{{ row.employeeNo }}</span></template>
        </el-table-column>
        <el-table-column label="姓名" width="110">
          <template #default="{ row }">
            <div class="name-cell">
              <span class="avatar">{{ row.employeeName.slice(0, 1) }}</span>
              <span>{{ row.employeeName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="130">
          <template #default="{ row }">
            <el-tag :type="roleTagType(row.role)" size="small" effect="plain">
              {{ dict.text('employeeRole', row.role) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="班组" width="150">
          <template #default="{ row }"><span class="text-secondary">{{ row.team }}</span></template>
        </el-table-column>
        <el-table-column label="联系电话" width="150">
          <template #default="{ row }"><span class="mono">{{ row.phone }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" effect="plain">
              {{ row.status === 1 ? '在岗' : '离岗' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="菜单权限范围" min-width="260">
          <template #default="{ row }">
            <div class="perm-tags">
              <el-tag v-for="p in permissions(row.role)" :key="p" size="small" effect="plain" class="perm-tag">
                {{ p }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="入职时间" width="150">
          <template #default="{ row }">
            <span class="text-secondary">{{ formatTime(row.createTime, 'YYYY-MM-DD') }}</span>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @size-change="load"
        @current-change="load"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh, Search, RefreshLeft } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { baseApi } from '@/api'
import { useDictStore } from '@/stores/dict'
import { formatTime } from '@/utils'

const dict = useDictStore()

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const teams = ref([])
const allEmployees = ref([])

const query = ref({ pageNum: 1, pageSize: 50, role: '', team: '' })

const ROLE_META = {
  WAVE_OPERATOR: { icon: 'Grid', color: '#2563eb', soft: 'rgba(37,99,235,0.1)' },
  PICKER: { icon: 'Van', color: '#16a34a', soft: 'rgba(22,163,74,0.1)' },
  REVIEWER: { icon: 'DocumentChecked', color: '#f59e0b', soft: 'rgba(245,158,11,0.12)' },
  ADMIN: { icon: 'UserFilled', color: '#8b5cf6', soft: 'rgba(139,92,246,0.1)' }
}

const PERMISSIONS = {
  WAVE_OPERATOR: ['作业看板', '订单池', '波次管理', '缺货处理', '效率复盘'],
  PICKER: ['作业看板', '拣货任务', '拣货作业'],
  REVIEWER: ['作业看板', '复核打包', '出库交接'],
  ADMIN: ['全部菜单', '基础数据维护']
}

const roleStats = computed(() =>
  dict.options('employeeRole').map((o) => ({
    code: o.code,
    desc: o.desc,
    count: allEmployees.value.filter((e) => e.role === o.code).length,
    icon: ROLE_META[o.code]?.icon || 'User',
    color: ROLE_META[o.code]?.color || '#64748b',
    soft: ROLE_META[o.code]?.soft || 'rgba(100,116,139,0.1)'
  }))
)

function roleTagType(role) {
  return { WAVE_OPERATOR: 'primary', PICKER: 'success', REVIEWER: 'warning', ADMIN: 'danger' }[role] || 'info'
}

function permissions(role) {
  return PERMISSIONS[role] || []
}

async function load() {
  loading.value = true
  try {
    const res = await baseApi.employeePage(query.value)
    rows.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function search() {
  query.value.pageNum = 1
  load()
}

function reset() {
  query.value = { pageNum: 1, pageSize: query.value.pageSize, role: '', team: '' }
  load()
}

onMounted(async () => {
  const [teamList, all] = await Promise.all([
    baseApi.teams(),
    baseApi.employeePage({ pageNum: 1, pageSize: 500 })
  ])
  teams.value = teamList
  allEmployees.value = all.records
  await load()
})
</script>

<style scoped lang="scss">
.filter-form {
  :deep(.el-form-item) {
    margin-bottom: 6px;
    margin-right: 16px;
  }
}

.role-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
}

.role-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: #fff;
  border: 1px solid $border-base;
  border-radius: $radius-card;
  box-shadow: $shadow-card;
}

.rc-icon {
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

.rc-name {
  font-size: 12.5px;
  color: $text-secondary;
  margin-bottom: 4px;
}

.rc-count {
  font-size: 11.5px;
  color: $text-placeholder;

  .num {
    font-size: 19px;
    font-weight: 700;
    color: $text-primary;
  }
}

.name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.avatar {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: linear-gradient(135deg, #dbeafe, #bfdbfe);
  color: $color-primary-dark;
  font-size: 11.5px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.perm-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.perm-tag {
  font-size: 11px;
}
</style>
