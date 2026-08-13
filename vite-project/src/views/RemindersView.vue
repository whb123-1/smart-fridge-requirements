<template>
  <div class="page">
    <el-card>
      <div class="toolbar">
        <el-radio-group v-model="status" @change="load">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="active">进行中</el-radio-button>
          <el-radio-button value="dismissed">已忽略</el-radio-button>
        </el-radio-group>
        <div style="flex: 1"></div>
        <el-button @click="load">刷新</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag :type="tagType(row.type)" size="small">{{ typeText(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" width="150" />
        <el-table-column prop="content" label="内容" min-width="260" />
        <el-table-column prop="createdAt" label="时间" width="170" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.isRead === 1 ? 'info' : 'danger'">
              {{ row.isRead === 1 ? '已读' : '未读' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button v-if="row.isRead === 0" link type="primary" @click="onRead(row)">标记已读</el-button>
            <el-button v-if="row.status === 'active'" link type="info" @click="onDismiss(row)">忽略</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { dismissReminder, listReminders, markReminderRead } from '../api/reminder'

const rows = ref<any[]>([])
const status = ref('')
const loading = ref(false)

const load = async () => {
  loading.value = true
  try {
    rows.value = await listReminders(status.value || undefined)
  } finally {
    loading.value = false
  }
}

const tagType = (type: string) => {
  const map: Record<string, string> = {
    expiry: 'danger', low_stock: 'warning', zone_abnormal: 'danger', custom: 'info',
  }
  return map[type] || 'info'
}

const typeText = (type: string) => {
  const map: Record<string, string> = {
    expiry: '临期', low_stock: '低库存', zone_abnormal: '冰箱异常', custom: '自定义',
  }
  return map[type] || type
}

const onRead = async (row: any) => {
  await markReminderRead(row.id)
  row.isRead = 1
}

const onDismiss = async (row: any) => {
  await dismissReminder(row.id)
  load()
}

onMounted(load)
</script>
