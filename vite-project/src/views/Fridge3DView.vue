<template>
  <div class="fridge-page">
    <div class="fridge-center">
      <div class="fridge-wrap">
        <Fridge3D ref="fridgeRef" :zones="zones" :selected-type="selectedType" :alerts="alerts"
          @select="onSelect" />

        <div class="floating-tools">
          <el-button size="small" @click="resetView">重置视角</el-button>
        </div>

        <div class="legend">
          <span><i class="dot active"></i>已配置</span>
          <span><i class="dot inactive"></i>未配置</span>
          <span><i class="dot selected"></i>选中</span>
        </div>

        <div class="center-hint">点击冰箱上的分区类型，在右侧管理分区与库存</div>
      </div>
    </div>

    <FridgePanel
      v-if="selectedType"
      :type="selectedType || ''"
      :zones="currentGroup ? currentGroup.zones : []"
      :selected-zone-id="selectedZoneId"
      :inventory="inventory"
      :loading="loading"
      :alerts="alerts"
      @select-zone="selectZone"
      @refresh="refreshAll"
      @close="closePanel"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import Fridge3D from '../components/Fridge3D.vue'
import FridgePanel from '../components/FridgePanel.vue'
import { listFoods } from '../api/food'
import { listZones, zoneAlerts } from '../api/zone'

const zones = ref<any[]>([])
const selectedType = ref<string | null>(null)
const selectedZoneId = ref<number | null>(null)
const inventory = ref<any[]>([])
const alerts = ref<any[]>([])
const loading = ref(false)
const fridgeRef = ref<InstanceType<typeof Fridge3D> | null>(null)
let alertTimer: number | undefined

const zoneOrder = ['冷藏区', '保鲜区', '变温区', '冷冻区', '常温区', '自定义']
const zoneGroups = computed(() => {
  const map = new Map<string, any[]>()
  for (const z of zones.value) {
    const arr = map.get(z.zoneType) ?? []
    arr.push(z)
    map.set(z.zoneType, arr)
  }
  return [...map.entries()]
    .map(([type, list]) => ({ type, zones: list }))
    .sort((a, b) =>
      zoneOrder.indexOf(a.type) - zoneOrder.indexOf(b.type) || a.zones[0].id - b.zones[0].id,
    )
})
const currentGroup = computed(() =>
  zoneGroups.value.find((g) => g.type === selectedType.value) || null,
)

const loadZones = async () => {
  zones.value = await listZones()
  if (selectedZoneId.value != null && !zones.value.some((z) => z.id === selectedZoneId.value)) {
    selectedZoneId.value = null
    inventory.value = []
  }
}

const loadInventory = async () => {
  if (selectedZoneId.value == null) {
    inventory.value = []
    return
  }
  loading.value = true
  try {
    const [stock, expired] = await Promise.all([
      listFoods({ zoneId: selectedZoneId.value, status: 'in_stock', page: 1, size: 100 }),
      listFoods({ zoneId: selectedZoneId.value, status: 'expired', page: 1, size: 100 }),
    ])
    // 在库在前，已过期排后；过期食材仍需显示，便于丢弃/删除处理
    inventory.value = [...stock.list, ...expired.list]
  } finally {
    loading.value = false
  }
}

const loadAlerts = async () => {
  try {
    alerts.value = await zoneAlerts()
  } catch {
    alerts.value = []
  }
}

const onSelect = async (type: string) => {
  selectedType.value = type
  const group = zoneGroups.value.find((g) => g.type === type)
  if (!group) {
    selectedZoneId.value = null
    inventory.value = []
    return
  }
  if (selectedZoneId.value == null || !group.zones.some((z) => z.id === selectedZoneId.value)) {
    selectedZoneId.value = group.zones[0].id
  }
  await loadInventory()
}

const selectZone = (id: number) => {
  selectedZoneId.value = id
  loadInventory()
}

const refreshAll = async () => {
  await loadZones()
  await loadAlerts()
  const group = zoneGroups.value.find((g) => g.type === selectedType.value)
  if (!group) {
    selectedZoneId.value = null
    inventory.value = []
    return
  }
  if (selectedZoneId.value == null || !group.zones.some((z) => z.id === selectedZoneId.value)) {
    selectedZoneId.value = group.zones[0].id
  }
  await loadInventory()
}

const closePanel = () => {
  selectedType.value = null
  selectedZoneId.value = null
  inventory.value = []
}

const resetView = () => {
  fridgeRef.value?.resetView()
}

onMounted(() => {
  loadZones()
  loadAlerts()
  alertTimer = window.setInterval(loadAlerts, 30000)
})

onBeforeUnmount(() => {
  if (alertTimer) {
    window.clearInterval(alertTimer)
  }
})
</script>

<style scoped>
.fridge-page {
  position: relative;
  height: 100vh;
  overflow: hidden;
  background: linear-gradient(180deg, #eef4fa 0%, #dde8f2 100%);
}
.fridge-center {
  position: absolute;
  inset: 0;
  display: flex;
  justify-content: center;
  align-items: center;
}
.fridge-wrap {
  width: 100%;
  height: 100%;
  position: relative;
}
.floating-tools {
  position: absolute;
  top: 16px;
  left: 16px;
  z-index: 10;
}
.legend {
  position: absolute;
  bottom: 16px;
  left: 16px;
  display: flex;
  gap: 16px;
  color: #4a5a6a;
  font-size: 13px;
  background: rgba(255, 255, 255, 0.75);
  padding: 6px 14px;
  border-radius: 14px;
}
.dot {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 3px;
  margin-right: 5px;
  vertical-align: -1px;
}
.dot.active {
  background: #9cc8f2;
}
.dot.inactive {
  background: #cfd6dd;
}
.dot.selected {
  background: #ffb74d;
}
.center-hint {
  position: absolute;
  left: 50%;
  bottom: 78px;
  transform: translateX(-50%);
  background: rgba(30, 40, 55, 0.72);
  color: #fff;
  font-size: 13px;
  padding: 6px 16px;
  border-radius: 16px;
  white-space: nowrap;
  pointer-events: none;
  z-index: 10;
}
</style>
