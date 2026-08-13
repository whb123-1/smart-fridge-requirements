<template>
  <div class="page">
    <el-row :gutter="16">
      <el-col :span="6">
        <el-card class="stat-card"><div class="label">在库食材</div>
          <div class="num">{{ summary.inStock ?? 0 }}</div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card"><div class="label">低库存</div>
          <div class="num warn">{{ summary.lowStock ?? 0 }}</div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card"><div class="label">过期食材</div>
          <div class="num danger">{{ summary.expiredThisMonth ?? 0 }}</div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card"><div class="label">异常分区</div>
          <div class="num warn">{{ summary.abnormalZones ?? 0 }}</div></el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top: 16px">
      <div class="toolbar">
        <el-radio-group v-model="period" @change="load">
          <el-radio-button value="week">本周</el-radio-button>
          <el-radio-button value="month">本月</el-radio-button>
        </el-radio-group>
        <div class="stat-total">
          消耗总量：<b>{{ stat.totalConsume ?? 0 }}</b>，
          浪费数量：<b class="danger-text">{{ stat.totalWasteQty ?? 0 }}</b>，
          浪费次数：<b class="danger-text">{{ stat.wasteCount ?? 0 }}</b>
        </div>
      </div>
      <el-table v-loading="loading" :data="stat.foods || []" border>
        <el-table-column prop="name" label="食材" min-width="140" />
        <el-table-column label="消耗量" width="120">
          <template #default="{ row }">{{ row.consumedQty }}</template>
        </el-table-column>
        <el-table-column prop="consumeCount" label="消耗次数" width="100" />
        <el-table-column label="浪费量" width="110">
          <template #default="{ row }">{{ row.wasteQty }}</template>
        </el-table-column>
        <el-table-column prop="wasteCount" label="浪费次数" width="100" />
      </el-table>
      <el-empty v-if="!loading && (stat.foods || []).length === 0"
        description="该时间段暂无消耗数据" />
      <el-alert style="margin-top: 12px" type="info" :closable="false"
        title="统计帮助优化采购计划，减少重复购买和食物浪费。" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { consumptionStats, summaryStats } from '../api/stats'

const period = ref('month')
const stat = ref<Record<string, any>>({})
const summary = ref<Record<string, any>>({})
const loading = ref(false)

const load = async () => {
  loading.value = true
  try {
    stat.value = await consumptionStats(period.value)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  summary.value = await summaryStats()
  load()
})
</script>

<style scoped>
.stat-card .label {
  color: #909399;
  font-size: 13px;
}
.stat-card .num {
  font-size: 26px;
  font-weight: 600;
  color: #409eff;
}
.stat-card .num.warn {
  color: #e6a23c;
}
.stat-card .num.danger {
  color: #f56c6c;
}
.stat-total {
  color: #606266;
}
.danger-text {
  color: #f56c6c;
}
</style>
