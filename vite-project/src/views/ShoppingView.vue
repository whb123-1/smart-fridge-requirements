<template>
  <div class="page">
    <el-card>
      <div class="toolbar">
        <el-select v-model="currentListId" placeholder="选择购物清单" style="width: 260px"
          @change="loadItems">
          <el-option v-for="l in lists" :key="l.id" :label="`${l.name}（${statusText(l.status)}）`"
            :value="l.id" />
        </el-select>
        <el-button @click="onCreate">新建清单</el-button>
        <el-button type="primary" :loading="autoLoading" @click="onAuto">自动生成</el-button>
        <div style="flex: 1"></div>
        <el-button v-if="currentList" type="success" @click="addVisible = true">添加物品</el-button>
        <el-button v-if="currentList" type="danger" @click="onDeleteList">删除清单</el-button>
      </div>

      <el-empty v-if="lists.length === 0" description="还没有购物清单，可点击自动生成" />
      <template v-else>
        <el-progress v-if="currentList && currentList.items.length"
          :percentage="purchasedPercent" :stroke-width="10" style="margin-bottom: 12px"
          :format="() => `已购 ${purchasedPercent}%`" />
        <el-table v-loading="loading" :data="currentList?.items || []" border>
          <el-table-column label="已购" width="70">
            <template #default="{ row }">
              <el-checkbox :model-value="row.purchased === 1" @change="(v: any) => toggleItem(row, v)" />
            </template>
          </el-table-column>
          <el-table-column prop="foodName" label="物品" min-width="140" />
          <el-table-column label="数量" width="110">
            <template #default="{ row }">{{ row.quantity }} {{ row.unit }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="说明" min-width="180" />
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button link type="danger" @click="onRemoveItem(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-card>

    <el-dialog v-model="addVisible" title="添加购物物品" width="440px">
      <el-form :model="itemForm" label-width="80px">
        <el-form-item label="物品" required>
          <el-input v-model="itemForm.foodName" placeholder="如：鸡蛋" />
        </el-form-item>
        <el-form-item label="数量">
          <el-input-number v-model="itemForm.quantity" :min="0.1" :precision="2" />
        </el-form-item>
        <el-form-item label="单位">
          <el-select v-model="itemForm.unit" allow-create filterable style="width: 100%">
            <el-option v-for="u in units" :key="u" :label="u" :value="u" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="itemForm.remark" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" @click="onAddItem">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addShoppingItem, autoShoppingList, createShoppingList, deleteShoppingList,
  listShoppingLists, removeShoppingItem, updateShoppingItem,
} from '../api/shopping'

const lists = ref<any[]>([])
const currentListId = ref<number | null>(null)
const loading = ref(false)
const autoLoading = ref(false)
const addVisible = ref(false)
const units = ['个', '克', '千克', '毫升', '瓶', '包', '盒', '袋', '根', '勺', '份']
const itemForm = reactive({ foodName: '', quantity: 1, unit: '个', remark: '' })

const currentList = computed(() =>
  lists.value.find((l) => l.id === currentListId.value) || null,
)

const purchasedPercent = computed(() => {
  const items = currentList.value?.items || []
  if (items.length === 0) return 0
  return Math.round((items.filter((i: any) => i.purchased === 1).length / items.length) * 100)
})

const statusText = (status: string) => {
  const map: Record<string, string> = {
    pending: '进行中', partial: '部分已购', done: '已完成',
  }
  return map[status] || status
}

const load = async () => {
  lists.value = await listShoppingLists()
  if (lists.value.length && (currentListId.value == null
    || !lists.value.some((l) => l.id === currentListId.value))) {
    currentListId.value = lists.value[0].id
  }
}

const loadItems = () => {
  loading.value = true
  setTimeout(() => { loading.value = false }, 100)
}

const onCreate = async () => {
  const { value } = await ElMessageBox.prompt('请输入清单名称', '新建购物清单', {
    inputValue: `购物清单 ${new Date().toISOString().slice(0, 10)}`,
  })
  const created = await createShoppingList(value)
  await load()
  currentListId.value = created.id
  ElMessage.success('创建成功')
}

const onAuto = async () => {
  autoLoading.value = true
  try {
    const created = await autoShoppingList()
    await load()
    currentListId.value = created.id
    ElMessage.success('已根据库存自动生成购物清单')
  } finally {
    autoLoading.value = false
  }
}

const onAddItem = async () => {
  if (!itemForm.foodName) {
    ElMessage.warning('请填写物品名称')
    return
  }
  await addShoppingItem(currentListId.value!, { ...itemForm })
  ElMessage.success('已添加')
  addVisible.value = false
  itemForm.foodName = ''
  load()
}

const toggleItem = async (row: any, checked: boolean) => {
  await updateShoppingItem(row.id, { purchased: checked ? 1 : 0 })
  load()
}

const onRemoveItem = async (row: any) => {
  await ElMessageBox.confirm(`确认删除「${row.foodName}」？`, '提示', { type: 'warning' })
  await removeShoppingItem(row.id)
  ElMessage.success('已删除')
  load()
}

const onDeleteList = async () => {
  await ElMessageBox.confirm('确认删除整个清单？', '提示', { type: 'warning' })
  await deleteShoppingList(currentListId.value!)
  currentListId.value = null
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
