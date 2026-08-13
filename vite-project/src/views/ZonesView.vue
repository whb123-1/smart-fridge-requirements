<template>
  <div class="page">
    <div class="toolbar">
      <el-button type="primary" @click="openAdd">新增分区</el-button>
      <el-alert style="flex: 1" type="info" :closable="false"
        title="温度单位支持摄氏（℃）与华氏（℉），系统统一换算后计算保质期。" />
    </div>
    <el-row :gutter="16">
      <el-col v-for="z in zones" :key="z.id" :span="8" style="margin-bottom: 16px">
        <el-card>
          <template #header>
            <div class="zone-header">
              <span>{{ z.name }}</span>
              <el-tag :type="statusTag(z.status)" size="small">{{ z.statusText }}</el-tag>
            </div>
          </template>
          <div class="zone-info">
            <div>类型：{{ z.zoneType }}</div>
            <div>建议范围：{{ z.minTemp }} ~ {{ z.maxTemp }} {{ tempUnitText(z.tempUnit) }}</div>
            <div v-if="z.latestTempC != null">最近温度：{{ z.latestTempC }} ℃</div>
            <div v-if="z.latestHumidity != null">最近湿度：{{ z.latestHumidity }} %</div>
            <div v-if="z.abnormalSeconds > 0" class="abnormal">
              异常持续：{{ formatSeconds(z.abnormalSeconds) }}
            </div>
            <div class="last-time">最近记录：{{ z.lastRecordAt || '暂无' }}</div>
          </div>
          <div class="zone-actions">
            <el-button size="small" type="primary" @click="openRecord(z)">记录温湿度</el-button>
            <el-button size="small" @click="openRecords(z)">历史记录</el-button>
            <el-button size="small" @click="openEdit(z)">编辑</el-button>
            <el-button size="small" type="danger" @click="onDelete(z)">删除</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-empty v-if="zones.length === 0" description="还没有冰箱分区，点击右上角新增" />

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑分区' : '新增分区'" width="560px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="分区名称" required>
          <el-input v-model="form.name" placeholder="如：冷藏区" />
        </el-form-item>
        <el-form-item label="分区类型">
          <el-select v-model="form.zoneType" style="width: 100%" @change="onTypeChange">
            <el-option v-for="t in zoneTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="温度单位">
          <el-radio-group v-model="form.tempUnit">
            <el-radio value="C">摄氏度 ℃</el-radio>
            <el-radio value="F">华氏度 ℉</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="目标温度">
          <el-input-number v-model="form.targetTemp" :precision="1" :step="0.5" style="width: 100%" />
        </el-form-item>
        <el-form-item label="建议温度下限">
          <el-input-number v-model="form.minTemp" :precision="1" :step="0.5" style="width: 100%" />
        </el-form-item>
        <el-form-item label="建议温度上限">
          <el-input-number v-model="form.maxTemp" :precision="1" :step="0.5" style="width: 100%" />
        </el-form-item>
        <el-form-item label="目标湿度 %">
          <el-input-number v-model="form.targetHumidity" :min="0" :max="100" :precision="1"
            style="width: 100%" />
        </el-form-item>
        <el-form-item label="湿度建议范围 %">
          <el-input-number v-model="form.minHumidity" :min="0" :max="100" style="width: 45%" />
          <span style="margin: 0 8px">~</span>
          <el-input-number v-model="form.maxHumidity" :min="0" :max="100" style="width: 45%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="recordVisible" title="记录温湿度" width="420px">
      <el-form :model="recordForm" label-width="90px">
        <el-form-item label="温度" required>
          <el-input-number v-model="recordForm.temp" :precision="1" :step="0.5" style="width: 100%" />
        </el-form-item>
        <el-form-item label="湿度 %">
          <el-input-number v-model="recordForm.humidity" :min="0" :max="100" :precision="1"
            style="width: 100%" />
        </el-form-item>
        <el-form-item label="数据来源">
          <el-radio-group v-model="recordForm.source">
            <el-radio value="manual">手动</el-radio>
            <el-radio value="sensor">传感器</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="recordVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRecord">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="recordsVisible" :title="recordsTitle" size="520px">
      <el-table :data="records" border size="small" max-height="600">
        <el-table-column prop="recordTime" label="时间" width="160" />
        <el-table-column prop="tempC" label="温度(℃)" width="90" />
        <el-table-column prop="humidity" label="湿度(%)" width="90" />
        <el-table-column label="来源" width="70">
          <template #default="{ row }">{{ row.source === 'sensor' ? '传感器' : '手动' }}</template>
        </el-table-column>
        <el-table-column prop="abnormalSeconds" label="异常持续(秒)" />
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createZone, deleteZone, listZones, recordZone, updateZone, zoneRecords,
} from '../api/zone'

const zones = ref<any[]>([])
const dialogVisible = ref(false)
const recordVisible = ref(false)
const recordsVisible = ref(false)
const recordsTitle = ref('')
const editing = ref<any>(null)
const currentZone = ref<any>(null)
const records = ref<any[]>([])
const zoneTypes = ['冷藏区', '冷冻区', '保鲜区', '变温区', '常温区', '自定义']
const form = reactive({
  name: '', zoneType: '冷藏区', tempUnit: 'C', targetTemp: 4, minTemp: 0, maxTemp: 8,
  targetHumidity: 60, minHumidity: 40, maxHumidity: 80,
})
const recordForm = reactive({ temp: 4, humidity: 60, source: 'manual' })

const defaults: Record<string, [number, number]> = {
  冷藏区: [0, 8], 冷冻区: [-18, -12], 保鲜区: [0, 4], 变温区: [-3, 4], 常温区: [10, 25],
}

const load = async () => {
  zones.value = await listZones()
}

const statusTag = (status: string) => {
  const map: Record<string, string> = {
    normal: 'success', abnormal: 'danger', stale: 'warning',
  }
  return map[status] || 'info'
}

const tempUnitText = (unit: string) => (unit === 'F' ? '℉' : '℃')

const formatSeconds = (seconds: number) => {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  if (h > 0) return `${h} 小时 ${m} 分`
  return `${m} 分钟`
}

const onTypeChange = (type: string) => {
  const range = defaults[type]
  if (range) {
    form.minTemp = range[0]
    form.maxTemp = range[1]
  }
}

const openAdd = () => {
  editing.value = null
  Object.assign(form, {
    name: '', zoneType: '冷藏区', tempUnit: 'C', targetTemp: 4, minTemp: 0, maxTemp: 8,
    targetHumidity: 60, minHumidity: 40, maxHumidity: 80,
  })
  dialogVisible.value = true
}

const openEdit = (zone: any) => {
  editing.value = zone
  Object.assign(form, {
    name: zone.name, zoneType: zone.zoneType, tempUnit: zone.tempUnit,
    targetTemp: zone.targetTemp, minTemp: zone.minTemp, maxTemp: zone.maxTemp,
    targetHumidity: zone.targetHumidity, minHumidity: zone.minHumidity, maxHumidity: zone.maxHumidity,
  })
  dialogVisible.value = true
}

const submit = async () => {
  if (!form.name) {
    ElMessage.warning('请填写分区名称')
    return
  }
  if (editing.value) {
    await updateZone(editing.value.id, { ...form })
    ElMessage.success('修改成功')
  } else {
    await createZone({ ...form })
    ElMessage.success('新增成功')
  }
  dialogVisible.value = false
  load()
}

const openRecord = (zone: any) => {
  currentZone.value = zone
  recordForm.temp = Number(zone.latestTempC ?? zone.targetTemp ?? 4)
  recordForm.humidity = Number(zone.latestHumidity ?? zone.targetHumidity ?? 60)
  recordForm.source = 'manual'
  recordVisible.value = true
}

const submitRecord = async () => {
  await recordZone(currentZone.value.id, { ...recordForm })
  ElMessage.success('记录成功')
  recordVisible.value = false
  load()
}

const openRecords = async (zone: any) => {
  currentZone.value = zone
  recordsTitle.value = `「${zone.name}」温湿度记录`
  records.value = await zoneRecords(zone.id)
  recordsVisible.value = true
}

const onDelete = async (zone: any) => {
  await ElMessageBox.confirm(`确认删除分区「${zone.name}」？`, '提示', { type: 'warning' })
  await deleteZone(zone.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.zone-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.zone-info {
  line-height: 1.9;
  color: #606266;
  font-size: 14px;
}
.zone-info .abnormal {
  color: #f56c6c;
}
.zone-info .last-time {
  color: #909399;
  font-size: 12px;
}
.zone-actions {
  margin-top: 12px;
}
</style>
