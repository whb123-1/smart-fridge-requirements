<template>
  <div class="page">
    <el-row :gutter="16">
      <el-col :span="4">
        <el-card class="stat-card"><div class="label">在库食材</div>
          <div class="num">{{ summary.inStock ?? 0 }}</div></el-card>
      </el-col>
      <el-col :span="4">
        <el-card class="stat-card"><div class="label">低库存</div>
          <div class="num warn">{{ summary.lowStock ?? 0 }}</div></el-card>
      </el-col>
      <el-col :span="4">
        <el-card class="stat-card"><div class="label">本月过期</div>
          <div class="num danger">{{ summary.expiredThisMonth ?? 0 }}</div></el-card>
      </el-col>
      <el-col :span="4">
        <el-card class="stat-card"><div class="label">异常分区</div>
          <div class="num warn">{{ summary.abnormalZones ?? 0 }}</div></el-card>
      </el-col>
      <el-col :span="4">
        <el-card class="stat-card"><div class="label">未读提醒</div>
          <div class="num">{{ summary.unreadReminders ?? 0 }}</div></el-card>
      </el-col>
      <el-col :span="4">
        <el-card class="stat-card"><div class="label">当前日期</div>
          <div class="date">{{ summary.date }}</div></el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="12">
        <el-card header="最新提醒">
          <el-empty v-if="reminders.length === 0" description="暂无提醒" :image-size="60" />
          <ul v-else class="reminder-list">
            <li v-for="r in reminders" :key="r.id">
              <el-tag size="small" :type="tagType(r.type)">{{ typeText(r.type) }}</el-tag>
              <span class="reminder-title">{{ r.title }}</span>
              <span class="reminder-time">{{ r.createdAt }}</span>
            </li>
          </ul>
          <template #footer>
            <el-button text type="primary" @click="$router.push('/reminders')">查看全部提醒</el-button>
          </template>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card header="快捷入口">
          <div class="quick-grid">
            <el-button size="large" @click="$router.push('/inventory')">库存管理</el-button>
            <el-button size="large" type="primary" @click="$router.push('/recipes')">智能菜谱</el-button>
            <el-button size="large" @click="$router.push('/zones')">冰箱分区</el-button>
            <el-button size="large" @click="$router.push('/diet')">饮食记录</el-button>
          </div>
          <el-alert style="margin-top: 16px" type="info" :closable="false"
            title="系统会根据冰箱库存自动推荐可制作的菜谱，并提醒临期食材和低库存。" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listReminders } from '../api/reminder'
import { summaryStats } from '../api/stats'

const summary = ref<Record<string, any>>({})
const reminders = ref<any[]>([])

const tagType = (type: string) => {
  if (type === 'expiry') return 'danger'
  if (type === 'low_stock') return 'warning'
  if (type === 'zone_abnormal') return 'danger'
  return 'info'
}

const typeText = (type: string) => {
  const map: Record<string, string> = {
    expiry: '临期', low_stock: '低库存', zone_abnormal: '冰箱异常', custom: '自定义',
  }
  return map[type] || type
}

onMounted(async () => {
  summary.value = await summaryStats()
  const list = await listReminders('active')
  reminders.value = list.slice(0, 5)
})
</script>

<style scoped>
.label {
  color: #909399;
  font-size: 13px;
}
.num {
  font-size: 28px;
  font-weight: 600;
  color: #409eff;
}
.num.warn {
  color: #e6a23c;
}
.num.danger {
  color: #f56c6c;
}
.date {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.reminder-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.reminder-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}
.reminder-title {
  flex: 1;
}
.reminder-time {
  color: #909399;
  font-size: 12px;
}
.quick-grid {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
</style>
