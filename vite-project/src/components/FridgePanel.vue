<template>
  <el-card class="fridge-panel">
    <template #header>
      <div class="panel-header">
        <span class="panel-title">
          {{ type }}{{ zones.length > 1 ? `（${zones.length} 个格子）` : '' }}
        </span>
        <el-button text type="primary" size="small" @click="$emit('close')">关闭</el-button>
      </div>
    </template>

    <div v-if="zones.length > 1" class="zone-switch">
      <span class="switch-label">切换格子：</span>
      <el-tag v-for="z in zones" :key="z.id" class="zone-chip"
        :type="selectedZoneId === z.id ? 'warning' : 'info'" effect="plain"
        @click="$emit('select-zone', z.id)">
        {{ z.name }}
        <span v-if="zoneLevel(z.id)" class="zone-alert" :class="'lv-' + zoneLevel(z.id)">
          {{ zoneText(z.id) }}
        </span>
      </el-tag>
    </div>

    <el-tabs v-model="tab">
      <!-- 打开的冰箱内部 -->
      <el-tab-pane label="冰箱内部" name="inner">
        <template v-if="selectedZone">
          <div class="inner-info">
            <el-tag :type="statusTag(selectedZone.status)" size="small">
              {{ selectedZone.statusText }}
            </el-tag>
            <span v-if="selectedZone.latestTempC != null">
              温度 {{ selectedZone.latestTempC }} ℃
            </span>
            <span v-if="selectedZone.latestHumidity != null">
              湿度 {{ selectedZone.latestHumidity }} %
            </span>
          </div>

          <div v-loading="loading" class="fridge-interior">
            <div class="interior-body">
              <div v-for="(shelf, si) in shelves" :key="si" class="shelf">
                <div class="shelf-label">第 {{ si + 1 }} 层</div>
                <div class="shelf-bar"></div>
                <div class="food-tiles">
                  <el-dropdown v-for="food in shelf" :key="food.id" trigger="click"
                    @command="(cmd: string) => onFoodCommand(cmd, food)">
                    <div class="food-tile" :class="tileClass(food)">
                      <span v-if="food.daysToExpiry != null && food.daysToExpiry < 0"
                        class="tile-badge expired">!!!</span>
                      <span v-else-if="food.daysToExpiry != null && food.daysToExpiry <= 3"
                        class="tile-badge soon">?!</span>
                      <img v-if="foodImg(food.name)" :src="foodImg(food.name)" class="food-img" alt="">
                      <div class="food-name">{{ food.name }}</div>
                      <div class="food-qty">{{ food.quantity }} {{ food.unit }}</div>
                      <div class="food-expiry">
                        <el-tag v-if="food.daysToExpiry != null" size="small"
                          :type="daysTagType(food.daysToExpiry)">
                          {{ daysText(food.daysToExpiry) }}
                        </el-tag>
                        <el-tag v-else size="small" type="info">无期限</el-tag>
                      </div>
                      <div v-if="food.daysToExpiry != null && food.daysToExpiry < 0"
                        class="food-handle">点击处理</div>
                    </div>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="consume">消耗</el-dropdown-item>
                        <el-dropdown-item command="expire">标记过期</el-dropdown-item>
                        <el-dropdown-item command="discard">丢弃</el-dropdown-item>
                        <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </div>
              <el-empty v-if="!loading && inventory.length === 0"
                description="这个格子还是空的，点下方添加食材" :image-size="50" />
            </div>
            <div class="inner-actions">
              <el-button size="small" type="primary" @click="openAddFood">添加食材</el-button>
            </div>
          </div>
        </template>
        <el-empty v-else description="请先选择分区格子" :image-size="50" />
      </el-tab-pane>

      <!-- 分区管理 -->
      <el-tab-pane label="分区管理" name="zone">
        <template v-if="selectedZone">
          <el-descriptions :column="1" border size="small" class="zone-desc">
            <el-descriptions-item label="状态">
              <el-tag :type="statusTag(selectedZone.status)" size="small">
                {{ selectedZone.statusText }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="温度">
              {{ selectedZone.latestTempC != null ? `${selectedZone.latestTempC} ℃` : '暂无记录' }}
            </el-descriptions-item>
            <el-descriptions-item label="湿度">
              {{ selectedZone.latestHumidity != null ? `${selectedZone.latestHumidity} %` : '暂无记录' }}
            </el-descriptions-item>
            <el-descriptions-item label="建议范围">
              {{ selectedZone.minTemp }} ~ {{ selectedZone.maxTemp }}
              {{ selectedZone.tempUnit === 'F' ? '℉' : '℃' }}
            </el-descriptions-item>
            <el-descriptions-item label="提醒">
              <template v-if="zoneAlert(selectedZone.id)">
                <el-tag v-if="zoneAlert(selectedZone.id).expireSoon > 0" size="small" type="warning"
                  style="margin-right: 4px">临期 {{ zoneAlert(selectedZone.id).expireSoon }}</el-tag>
                <el-tag v-if="zoneAlert(selectedZone.id).expired > 0" size="small" type="danger"
                  style="margin-right: 4px">过期 {{ zoneAlert(selectedZone.id).expired }}</el-tag>
                <el-tag v-if="zoneAlert(selectedZone.id).lowStock > 0" size="small" type="warning"
                  style="margin-right: 4px">低库存 {{ zoneAlert(selectedZone.id).lowStock }}</el-tag>
                <el-tag v-if="zoneAlert(selectedZone.id).abnormal" size="small" type="danger">
                  温湿度异常
                </el-tag>
              </template>
              <span v-else>无</span>
            </el-descriptions-item>
          </el-descriptions>
          <div class="btn-row">
            <el-button size="small" type="primary" @click="openRecord(selectedZone)">记录温湿度</el-button>
            <el-button size="small" @click="openEditZone(selectedZone)">编辑</el-button>
            <el-button size="small" type="danger" @click="removeZone(selectedZone)">删除</el-button>
          </div>
        </template>
        <el-empty v-else description="该类型暂无分区" :image-size="50" />

        <el-button style="margin-top: 12px" type="success" size="small"
          @click="openAddZone">新增{{ type }}分区</el-button>
      </el-tab-pane>
    </el-tabs>

    <!-- 分区新增/编辑 -->
    <el-dialog v-model="zoneDialogVisible" :title="editingZone ? '编辑分区' : '新增分区'"
      width="480px" class="pixel-dialog">
      <el-form :model="zoneForm" label-width="100px">
        <el-form-item label="分区名称" required>
          <el-input v-model="zoneForm.name" placeholder="如：冷藏区" />
        </el-form-item>
        <el-form-item label="分区类型">
          <el-select v-model="zoneForm.zoneType" style="width: 100%" @change="onZoneTypeChange">
            <el-option v-for="t in zoneTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="温度单位">
          <el-radio-group v-model="zoneForm.tempUnit">
            <el-radio value="C">摄氏度 ℃</el-radio>
            <el-radio value="F">华氏度 ℉</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="目标温度">
          <el-input-number v-model="zoneForm.targetTemp" :precision="1" :step="0.5" style="width: 100%" />
        </el-form-item>
        <el-form-item label="建议温度范围">
          <el-input-number v-model="zoneForm.minTemp" :precision="1" style="width: 42%" />
          <span style="margin: 0 8px">~</span>
          <el-input-number v-model="zoneForm.maxTemp" :precision="1" style="width: 42%" />
        </el-form-item>
        <el-form-item label="目标湿度 %">
          <el-input-number v-model="zoneForm.targetHumidity" :min="0" :max="100" :precision="1"
            style="width: 100%" />
        </el-form-item>
        <el-form-item label="湿度建议范围">
          <el-input-number v-model="zoneForm.minHumidity" :min="0" :max="100" style="width: 42%" />
          <span style="margin: 0 8px">~</span>
          <el-input-number v-model="zoneForm.maxHumidity" :min="0" :max="100" style="width: 42%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="zoneDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveZone">保存</el-button>
      </template>
    </el-dialog>

    <!-- 温湿度记录 -->
    <el-dialog v-model="recordVisible" title="记录温湿度" width="380px" class="pixel-dialog">
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
        <el-button type="primary" @click="saveRecord">保存</el-button>
      </template>
    </el-dialog>

    <!-- 添加食材 -->
    <el-dialog v-model="foodDialogVisible" title="添加食材" width="460px" class="pixel-dialog">
      <el-form :model="foodForm" label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="foodForm.name" placeholder="如：鸡蛋" />
        </el-form-item>
        <el-form-item label="图片预览">
          <div class="food-preview">
            <img v-if="previewImg" :src="previewImg" alt="">
            <span v-else>暂无匹配图片，将以文字显示</span>
          </div>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="foodForm.categoryId" placeholder="选择分类" clearable filterable
            style="width: 100%" @change="onCategoryChange">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="数量" required>
          <el-input-number v-model="foodForm.quantity" :min="0.1" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="单位">
          <el-select v-model="foodForm.unit" allow-create filterable style="width: 100%">
            <el-option v-for="u in units" :key="u" :label="u" :value="u" />
          </el-select>
        </el-form-item>
        <el-form-item label="开封日期">
          <el-date-picker v-model="foodForm.openedDate" type="date" value-format="YYYY-MM-DD"
            style="width: 100%" />
        </el-form-item>
        <el-form-item label="包装保质期">
          <el-date-picker v-model="foodForm.packageExpiryDate" type="date" value-format="YYYY-MM-DD"
            style="width: 100%" />
        </el-form-item>
        <el-form-item label="低库存阈值">
          <el-input-number v-model="foodForm.lowStockThreshold" :min="0" :precision="2"
            style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="foodDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveFood">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addFood, consumeFood, deleteFood, discardFood, expireFood, listCategories,
} from '../api/food'
import { createZone, deleteZone, recordZone, updateZone } from '../api/zone'
import { findFoodImage } from '../utils/foodImages'

const props = defineProps<{
  type: string
  zones: any[]
  selectedZoneId: number | null
  inventory: any[]
  loading: boolean
  alerts: any[]
}>()

const emit = defineEmits<{
  (e: 'select-zone', id: number): void
  (e: 'refresh'): void
  (e: 'close'): void
}>()

const tab = ref('inner')
const zoneTypes = ['冷藏区', '冷冻区', '保鲜区', '变温区', '常温区', '自定义']
const units = ['个', '克', '千克', '毫升', '瓶', '包', '盒', '袋', '根', '勺', '份']
const categories = ref<any[]>([])
const zoneDefaults: Record<string, [number, number]> = {
  冷藏区: [0, 8], 冷冻区: [-18, -12], 保鲜区: [0, 4], 变温区: [-3, 4], 常温区: [10, 25], 自定义: [0, 10],
}

const selectedZone = computed(() =>
  props.zones.find((z) => z.id === props.selectedZoneId) || null,
)

const foodImg = (name: string) => findFoodImage(name) ?? undefined
const previewImg = computed(() => findFoodImage(foodForm.name) ?? undefined)

// 冰箱内部：每层放 3 个食材
const shelves = computed(() => {
  const rows: any[][] = []
  for (let i = 0; i < props.inventory.length; i += 3) {
    rows.push(props.inventory.slice(i, i + 3))
  }
  return rows
})

const statusTag = (status: string) => {
  const map: Record<string, string> = {
    normal: 'success', abnormal: 'danger', stale: 'warning',
  }
  return map[status] || 'info'
}

const zoneAlert = (zoneId: number) => props.alerts.find((a) => a.zoneId === zoneId) || null

const zoneLevel = (zoneId: number) => {
  const a = zoneAlert(zoneId)
  if (!a) return null
  if (a.expired > 0) return 'expired'
  if (a.expireSoon > 0) return 'soon'
  if (a.lowStock > 0) return 'low'
  if (a.abnormal) return 'abnormal'
  return null
}

const zoneText = (zoneId: number) => {
  const lv = zoneLevel(zoneId)
  if (lv === 'expired') return '!!!'
  if (lv === 'soon') return '?!'
  if (lv === 'low') return '↓'
  return '△'
}

const daysTagType = (days: number) => (days < 0 ? 'danger' : days <= 3 ? 'warning' : 'success')
const daysText = (days: number) => (days < 0 ? '已过期' : `${days} 天`)

const tileClass = (food: any) => {
  if (food.daysToExpiry != null && food.daysToExpiry < 0) return 'danger'
  if (food.daysToExpiry != null && food.daysToExpiry <= 3) return 'warn'
  if (food.isLowStock === 1) return 'low'
  return ''
}

const onFoodCommand = async (cmd: string, food: any) => {
  if (cmd === 'consume') {
    await consume(food)
  } else if (cmd === 'expire') {
    await expire(food)
  } else if (cmd === 'discard') {
    await discard(food)
  } else if (cmd === 'delete') {
    await removeFood(food)
  }
}

// ---------- 分区 ----------
const zoneDialogVisible = ref(false)
const editingZone = ref<any>(null)
const zoneForm = reactive({
  name: '', zoneType: '自定义', tempUnit: 'C', targetTemp: 4, minTemp: 0, maxTemp: 8,
  targetHumidity: 60, minHumidity: 40, maxHumidity: 80,
})

const resetZoneForm = () => {
  editingZone.value = null
  Object.assign(zoneForm, {
    name: '', zoneType: props.type, tempUnit: 'C', targetTemp: 4, minTemp: 0, maxTemp: 8,
    targetHumidity: 60, minHumidity: 40, maxHumidity: 80,
  })
  onZoneTypeChange(zoneForm.zoneType)
}

const openAddZone = () => {
  resetZoneForm()
  zoneDialogVisible.value = true
}

const openEditZone = (zone: any) => {
  editingZone.value = zone
  Object.assign(zoneForm, {
    name: zone.name, zoneType: zone.zoneType, tempUnit: zone.tempUnit,
    targetTemp: zone.targetTemp, minTemp: zone.minTemp, maxTemp: zone.maxTemp,
    targetHumidity: zone.targetHumidity, minHumidity: zone.minHumidity, maxHumidity: zone.maxHumidity,
  })
  zoneDialogVisible.value = true
}

const onZoneTypeChange = (type: string) => {
  const range = zoneDefaults[type]
  if (range) {
    zoneForm.minTemp = range[0]
    zoneForm.maxTemp = range[1]
  }
}

const saveZone = async () => {
  if (!zoneForm.name) {
    ElMessage.warning('请填写分区名称')
    return
  }
  if (editingZone.value) {
    await updateZone(editingZone.value.id, { ...zoneForm })
    ElMessage.success('修改成功')
  } else {
    await createZone({ ...zoneForm })
    ElMessage.success('新增成功')
  }
  zoneDialogVisible.value = false
  emit('refresh')
}

const removeZone = async (zone: any) => {
  await ElMessageBox.confirm(`确认删除分区「${zone.name}」？`, '提示', { type: 'warning' })
  await deleteZone(zone.id)
  ElMessage.success('已删除')
  emit('refresh')
}

// ---------- 温湿度记录 ----------
const recordVisible = ref(false)
const recordForm = reactive({ temp: 4, humidity: 60, source: 'manual' })

const openRecord = (zone: any) => {
  recordForm.temp = Number(zone.latestTempC ?? zone.targetTemp ?? 4)
  recordForm.humidity = Number(zone.latestHumidity ?? zone.targetHumidity ?? 60)
  recordForm.source = 'manual'
  recordVisible.value = true
}

const saveRecord = async () => {
  if (!selectedZone.value) {
    return
  }
  await recordZone(selectedZone.value.id, { ...recordForm })
  ElMessage.success('记录成功')
  recordVisible.value = false
  emit('refresh')
}

// ---------- 库存 ----------
const foodDialogVisible = ref(false)
const foodForm = reactive({
  name: '', categoryId: undefined as number | undefined, quantity: 1, unit: '个',
  openedDate: '', packageExpiryDate: '', lowStockThreshold: undefined as number | undefined,
})

const openAddFood = () => {
  Object.assign(foodForm, {
    name: '', categoryId: undefined, quantity: 1, unit: '个',
    openedDate: '', packageExpiryDate: '', lowStockThreshold: undefined,
  })
  foodDialogVisible.value = true
}

const onCategoryChange = (id: number) => {
  const cat = categories.value.find((c) => c.id === id)
  if (cat && cat.defaultUnit) {
    foodForm.unit = cat.defaultUnit
  }
}

const saveFood = async () => {
  if (!foodForm.name || !selectedZone.value) {
    ElMessage.warning('请填写食材名称')
    return
  }
  await addFood({
    ...foodForm,
    zoneId: selectedZone.value.id,
    entryDate: new Date().toISOString().slice(0, 10),
  })
  ElMessage.success('添加成功')
  foodDialogVisible.value = false
  emit('refresh')
}

const consume = async (row: any) => {
  const { value } = await ElMessageBox.prompt(
    `当前库存 ${row.quantity} ${row.unit}，请输入消耗数量`, '消耗食材',
    { inputValue: '1', inputPattern: /^\d+(\.\d+)?$/, inputErrorMessage: '请输入数字' },
  )
  await consumeFood(row.id, { quantity: Number(value), remark: '冰箱面板消耗' })
  ElMessage.success('已记录消耗')
  emit('refresh')
}

const expire = async (row: any) => {
  await ElMessageBox.confirm(`确认将「${row.name}」标记为过期？`, '提示', { type: 'warning' })
  await expireFood(row.id)
  ElMessage.success('已标记过期')
  emit('refresh')
}

const discard = async (row: any) => {
  await ElMessageBox.confirm(`确认丢弃「${row.name}」？`, '提示', { type: 'warning' })
  await discardFood(row.id, '面板标记丢弃')
  ElMessage.success('已标记丢弃')
  emit('refresh')
}

const removeFood = async (row: any) => {
  await ElMessageBox.confirm(`确认删除「${row.name}」？`, '提示', { type: 'warning' })
  await deleteFood(row.id)
  ElMessage.success('已删除')
  emit('refresh')
}

onMounted(async () => {
  categories.value = await listCategories()
})
</script>

<style scoped>
.fridge-panel {
  position: absolute;
  top: 16px;
  right: 16px;
  bottom: 16px;
  width: 460px;
  display: flex;
  flex-direction: column;
  z-index: 20;
  background: #f4f9ff;
  border: 4px solid #6f9fd0;
  border-radius: 0;
  box-shadow: 6px 6px 0 rgba(120, 160, 210, 0.4), 0 0 0 2px #ffffff;
  animation: panelIn 0.25s ease;
}
@keyframes panelIn {
  from {
    opacity: 0;
    transform: translateX(24px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}
.fridge-panel :deep(.el-card__body) {
  flex: 1;
  overflow: auto;
  background: #f4f9ff;
}
.fridge-panel :deep(.el-card__header) {
  background: #dcebfa;
  border-bottom: 3px solid #6f9fd0;
}
.fridge-panel :deep(.el-button) {
  border-radius: 0;
  border: 2px solid #6f9fd0;
  color: #3a6ea5;
  font-family: 'Courier New', monospace;
  box-shadow: 2px 2px 0 rgba(120, 160, 210, 0.45);
}
.fridge-panel :deep(.el-tabs__item) {
  font-family: 'Courier New', monospace;
  color: #5a7ba0;
  border-radius: 0;
}
.fridge-panel :deep(.el-tabs__item.is-active) {
  color: #fff;
  background: #4c8fe0;
  box-shadow: 2px 2px 0 #b9d2ec;
}
.fridge-panel :deep(.el-tabs__nav-wrap::after) {
  background: #6f9fd0;
  height: 3px;
}
.fridge-panel :deep(.el-tabs__active-bar) {
  background: #4c8fe0;
  height: 4px;
}
.fridge-panel :deep(.el-empty__description p) {
  color: #7b9cc4;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.panel-title {
  font-weight: 600;
  font-family: 'Courier New', monospace;
  letter-spacing: 1px;
  color: #2c6bb5;
  text-shadow: 1px 1px 0 #ffffff;
}
.zone-switch {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 6px;
}
.switch-label {
  color: #6b86a3;
  font-size: 13px;
  font-family: 'Courier New', monospace;
}
.zone-chip {
  cursor: pointer;
  border-radius: 0 !important;
  border: 2px solid #6f9fd0 !important;
  background: #ffffff !important;
  color: #3a6ea5 !important;
  font-family: 'Courier New', monospace !important;
  box-shadow: 2px 2px 0 rgba(120, 160, 210, 0.4);
}
.zone-chip.is-active {
  background: #4c8fe0 !important;
  color: #ffffff !important;
}
.zone-alert {
  font-weight: 700;
  font-size: 12px;
  margin-left: 4px;
  padding: 0 4px;
  border-radius: 4px;
}
.zone-alert.lv-expired {
  color: #fff;
  background: #e5484d;
}
.zone-alert.lv-soon {
  color: #5a3d00;
  background: #f7c948;
}
.zone-alert.lv-low {
  color: #fff;
  background: #ff8f1f;
}
.zone-alert.lv-abnormal {
  color: #fff;
  background: #9b6dff;
}
.inner-info {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
  color: #3a6ea5;
  font-size: 13px;
  font-family: 'Courier New', monospace;
}
.fridge-interior {
  border: 3px solid #6f9fd0;
  border-radius: 0;
  background: linear-gradient(180deg, #eaf3fc 0%, #d7e8f8 100%);
  box-shadow: inset 0 0 0 3px #ffffff;
  padding: 10px;
  min-height: 260px;
  position: relative;
}
.interior-body {
  min-height: 220px;
}
.shelf {
  margin-bottom: 12px;
}
.shelf-label {
  font-size: 12px;
  color: #7b9cc4;
  font-family: 'Courier New', monospace;
  letter-spacing: 1px;
  margin-bottom: 4px;
}
.shelf-bar {
  height: 8px;
  background: #6f9fd0;
  box-shadow: 0 3px 0 #ffffff;
  margin-bottom: 6px;
}
.food-tiles {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.food-tile {
  position: relative;
  width: 118px;
  background: #ffffff;
  border: 3px solid #6f9fd0;
  border-radius: 0;
  padding: 8px;
  cursor: pointer;
  text-align: center;
  transition: all 0.2s;
  box-shadow: 3px 3px 0 rgba(120, 160, 210, 0.4);
  font-family: 'Courier New', monospace;
}
.tile-badge {
  position: absolute;
  top: -9px;
  right: -9px;
  font-weight: 700;
  font-size: 12px;
  line-height: 1;
  padding: 4px 5px;
  border: 2px solid #6f9fd0;
  border-radius: 0;
  box-shadow: 2px 2px 0 rgba(120, 160, 210, 0.4);
  z-index: 2;
}
.tile-badge.expired {
  background: #e5484d;
  color: #fff;
}
.tile-badge.soon {
  background: #f7c948;
  color: #5a3d00;
}
.food-img {
  width: 52px;
  height: 52px;
  object-fit: contain;
  margin: 0 auto 4px;
  display: block;
  image-rendering: pixelated;
  border: 2px solid #6f9fd0;
  background: #fff;
}
.food-tile:hover {
  box-shadow: 5px 5px 0 rgba(120, 160, 210, 0.45);
  transform: translateY(-2px);
}
.food-tile.warn {
  background: #fff3c4;
  border-color: #a08000;
}
.food-tile.danger {
  background: #ffe0e0;
  border-color: #8a1f22;
}
.food-tile.low {
  background: #ffe9d6;
  border-color: #9a4d00;
}
.food-name {
  font-weight: 600;
  font-size: 13px;
  color: #2c5f8a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.food-qty {
  color: #6b86a3;
  font-size: 12px;
  margin: 4px 0;
}
.food-handle {
  color: #8a1f22;
  font-size: 11px;
  margin-top: 2px;
}
.inner-actions {
  margin-top: 10px;
  text-align: center;
}
.inner-actions :deep(.el-button--primary) {
  background: #4c8fe0;
  border-color: #6f9fd0;
  color: #fff;
}
.food-preview {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 48px;
  color: #909399;
  font-size: 13px;
}
.food-preview img {
  width: 48px;
  height: 48px;
  object-fit: contain;
  image-rendering: pixelated;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fff;
  padding: 4px;
}
.zone-desc {
  margin-bottom: 12px;
}
.zone-desc :deep(.el-descriptions__label) {
  background: #dcebfa;
  color: #2c6bb5;
  font-family: 'Courier New', monospace;
}
.zone-desc :deep(.el-descriptions__content) {
  background: #ffffff;
  color: #3a556f;
  font-family: 'Courier New', monospace;
}
.btn-row {
  display: flex;
  gap: 8px;
}
</style>

<style>
/* 像素风弹窗（el-dialog 挂载到 body，需要全局样式） */
.pixel-dialog .el-dialog {
  background: #f4f9ff;
  border: 3px solid #6f9fd0;
  border-radius: 0;
  box-shadow: 6px 6px 0 rgba(120, 160, 210, 0.45);
}
.pixel-dialog .el-dialog__title {
  color: #2c6bb5;
  font-family: 'Courier New', monospace;
  letter-spacing: 1px;
}
.pixel-dialog .el-dialog__body {
  background: #ffffff;
  color: #3a556f;
  font-family: 'Courier New', monospace;
}
.pixel-dialog .el-dialog__header {
  border-bottom: 3px solid #6f9fd0;
  background: #dcebfa;
}
.pixel-dialog .el-form-item__label {
  color: #5a7ba0;
  font-family: 'Courier New', monospace;
}
.pixel-dialog .el-input__wrapper,
.pixel-dialog .el-select__wrapper,
.pixel-dialog .el-input-number,
.pixel-dialog .el-date-editor.el-input__wrapper {
  border-radius: 0;
  background: #f4f9ff;
  box-shadow: 0 0 0 1px #b9d2ec inset;
}
.pixel-dialog .el-input__inner {
  color: #3a556f;
  font-family: 'Courier New', monospace;
}
.pixel-dialog .el-button {
  border-radius: 0;
  border: 2px solid #6f9fd0;
  background: #ffffff;
  color: #3a6ea5;
  font-family: 'Courier New', monospace;
  box-shadow: 2px 2px 0 rgba(120, 160, 210, 0.45);
}
.pixel-dialog .el-button--primary {
  background: #4c8fe0;
  color: #fff;
}
.pixel-dialog .el-radio__label,
.pixel-dialog .el-checkbox__label {
  color: #3a556f;
  font-family: 'Courier New', monospace;
}
</style>
