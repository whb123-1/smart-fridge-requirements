<template>
  <div class="page">
    <el-card>
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="搜索食材名称" clearable style="width: 200px"
          @keyup.enter="search" />
        <el-select v-model="query.itemType" placeholder="食物类型" clearable style="width: 130px">
          <el-option label="食材" value="食材" />
          <el-option label="零食" value="零食" />
          <el-option label="饮料" value="饮料" />
          <el-option label="调味品" value="调味品" />
        </el-select>
        <el-select v-model="query.zoneId" placeholder="存放分区" clearable style="width: 150px">
          <el-option v-for="z in zones" :key="z.id" :label="z.name" :value="z.id" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px">
          <el-option label="在库" value="in_stock" />
          <el-option label="已食用" value="consumed" />
          <el-option label="已过期" value="expired" />
          <el-option label="已丢弃" value="discarded" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <div style="flex: 1"></div>
        <el-button @click="$router.push('/fridge')">3D 视图</el-button>
        <el-button @click="showEstimates">重量参考</el-button>
        <el-button type="primary" @click="openAdd">添加食材</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="name" label="名称" min-width="120" />
        <el-table-column prop="categoryName" label="分类" width="90" />
        <el-table-column prop="zoneName" label="存放分区" width="100" />
        <el-table-column label="数量" width="110">
          <template #default="{ row }">{{ row.quantity }} {{ row.unit }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ row.statusText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="建议食用期限" min-width="110">
          <template #default="{ row }">{{ row.suggestedExpiryDate || '-' }}</template>
        </el-table-column>
        <el-table-column label="剩余" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.daysToExpiry != null" size="small"
              :type="row.daysToExpiry < 0 ? 'danger' : row.daysToExpiry <= 3 ? 'warning' : 'success'">
              {{ row.daysToExpiry < 0 ? '已过期' : row.daysToExpiry + ' 天' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="expiryBasis" label="依据" width="110" />
        <el-table-column label="低库存" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.isLowStock === 1" type="warning" size="small">是</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="success" @click="onConsume(row)">消耗</el-button>
            <el-button link type="warning" @click="onExpire(row)">过期</el-button>
            <el-button link type="danger" @click="onDiscard(row)">丢弃</el-button>
            <el-button link type="info" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination style="margin-top: 12px; justify-content: flex-end" background
        layout="total, prev, pager, next" :total="total" :page-size="query.size"
        v-model:current-page="query.page" @current-change="load" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑食材' : '添加食材'" width="680px">
      <el-form :model="form" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="名称" required>
              <el-input v-model="form.name" placeholder="如：鸡蛋" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类">
              <el-select v-model="form.categoryId" placeholder="选择分类" clearable filterable
                style="width: 100%" @change="onCategoryChange">
                <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="数量" required>
              <el-input-number v-model="form.quantity" :min="0.1" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计量单位">
              <el-select v-model="form.unit" allow-create filterable style="width: 100%"
                @change="onUnitChange">
                <el-option v-for="u in units" :key="u" :label="u" :value="u" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="存放分区">
              <el-select v-model="form.zoneId" placeholder="选择分区" clearable style="width: 100%">
                <el-option v-for="z in zones" :key="z.id" :label="z.name" :value="z.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="入库日期">
              <el-date-picker v-model="form.entryDate" type="date" value-format="YYYY-MM-DD"
                style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="开封日期">
              <el-date-picker v-model="form.openedDate" type="date" value-format="YYYY-MM-DD"
                style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="包装保质期">
              <el-date-picker v-model="form.packageExpiryDate" type="date" value-format="YYYY-MM-DD"
                style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="低库存阈值">
              <el-input-number v-model="form.lowStockThreshold" :min="0" :precision="2"
                style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.note" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="estimatesVisible" title="常见食物重量参考" width="560px">
      <el-table :data="estimates" border size="small" max-height="400">
        <el-table-column prop="name" label="食物" />
        <el-table-column prop="unit" label="单位" width="80" />
        <el-table-column prop="weightGrams" label="参考重量（克）" width="140" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addFood, consumeFood, deleteFood, discardFood, expireFood, listCategories,
  listEstimates, listFoods, updateFood,
} from '../api/food'
import { listZones } from '../api/zone'

const loading = ref(false)
const saving = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const categories = ref<any[]>([])
const zones = ref<any[]>([])
const estimates = ref<any[]>([])
const dialogVisible = ref(false)
const estimatesVisible = ref(false)
const editing = ref<any>(null)
const query = reactive({
  page: 1, size: 10, keyword: '', itemType: '', zoneId: undefined as number | undefined, status: '',
})
const form = reactive({
  name: '', categoryId: undefined as number | undefined, zoneId: undefined as number | undefined,
  quantity: 1, unit: '个', unitType: 'count', entryDate: '', openedDate: '',
  packageExpiryDate: '', lowStockThreshold: undefined as number | undefined, note: '',
})
const units = ['个', '克', '千克', '毫升', '瓶', '包', '盒', '袋', '根', '勺', '份']

const load = async () => {
  loading.value = true
  try {
    const data = await listFoods({ ...query })
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const search = () => {
  query.page = 1
  load()
}

const reset = () => {
  query.keyword = ''
  query.itemType = ''
  query.zoneId = undefined
  query.status = ''
  search()
}

const resetForm = () => {
  editing.value = null
  Object.assign(form, {
    name: '', categoryId: undefined, zoneId: undefined, quantity: 1, unit: '个',
    unitType: 'count', entryDate: '', openedDate: '', packageExpiryDate: '',
    lowStockThreshold: undefined, note: '',
  })
}

const openAdd = () => {
  resetForm()
  dialogVisible.value = true
}

const openEdit = (row: any) => {
  editing.value = row
  Object.assign(form, {
    name: row.name, categoryId: row.categoryId, zoneId: row.zoneId, quantity: Number(row.quantity),
    unit: row.unit, unitType: row.unitType, entryDate: row.entryDate, openedDate: row.openedDate,
    packageExpiryDate: row.packageExpiryDate, lowStockThreshold: row.lowStockThreshold, note: row.note,
  })
  dialogVisible.value = true
}

const onCategoryChange = (id: number) => {
  const cat = categories.value.find((c) => c.id === id)
  if (cat && cat.defaultUnit) {
    form.unit = cat.defaultUnit
    form.unitType = cat.unitType
  }
}

const onUnitChange = (unit: string) => {
  if (['克', '千克'].includes(unit)) {
    form.unitType = 'weight'
  } else if (unit === '毫升') {
    form.unitType = 'volume'
  } else {
    form.unitType = 'count'
  }
}

const submit = async () => {
  if (!form.name || !form.quantity) {
    ElMessage.warning('请填写名称和数量')
    return
  }
  saving.value = true
  try {
    const payload = { ...form, entryDate: form.entryDate || undefined }
    if (editing.value) {
      await updateFood(editing.value.id, payload)
      ElMessage.success('修改成功')
    } else {
      await addFood(payload)
      ElMessage.success('添加成功')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

const onConsume = async (row: any) => {
  const { value } = await ElMessageBox.prompt(
    `当前库存 ${row.quantity} ${row.unit}，请输入消耗数量`, '消耗食材',
    { inputValue: '1', inputPattern: /^\d+(\.\d+)?$/, inputErrorMessage: '请输入数字' },
  )
  await consumeFood(row.id, { quantity: Number(value), remark: '手动消耗' })
  ElMessage.success('已记录消耗')
  load()
}

const onExpire = async (row: any) => {
  await ElMessageBox.confirm(`确认将「${row.name}」标记为过期？`, '提示', { type: 'warning' })
  await expireFood(row.id)
  ElMessage.success('已标记过期')
  load()
}

const onDiscard = async (row: any) => {
  await ElMessageBox.confirm(`确认丢弃「${row.name}」？`, '提示', { type: 'warning' })
  await discardFood(row.id, '手动标记丢弃')
  ElMessage.success('已标记丢弃')
  load()
}

const onDelete = async (row: any) => {
  await ElMessageBox.confirm(`确认删除「${row.name}」？删除后不可恢复。`, '提示', { type: 'warning' })
  await deleteFood(row.id)
  ElMessage.success('已删除')
  load()
}

const showEstimates = async () => {
  estimates.value = await listEstimates()
  estimatesVisible.value = true
}

const statusTag = (status: string) => {
  const map: Record<string, string> = {
    in_stock: 'success', consumed: 'info', expired: 'danger', discarded: 'warning',
  }
  return map[status] || 'info'
}

onMounted(async () => {
  load()
  categories.value = await listCategories()
  zones.value = await listZones()
})
</script>
