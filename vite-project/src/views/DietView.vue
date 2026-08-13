<template>
  <div class="page">
    <el-tabs v-model="tab">
      <el-tab-pane label="饮食记录" name="diet">
        <el-card>
          <div class="toolbar">
            <el-date-picker v-model="date" type="date" value-format="YYYY-MM-DD" @change="load" />
            <div style="flex: 1"></div>
            <el-button type="primary" @click="openAdd">添加饮食记录</el-button>
          </div>
          <el-row :gutter="16">
            <el-col :span="6">
              <el-card class="stat-card"><div class="label">总热量（千卡）</div>
                <div class="num">{{ summary.totalCalorie ?? 0 }}</div></el-card>
            </el-col>
            <el-col :span="6">
              <el-card class="stat-card"><div class="label">蛋白质（克）</div>
                <div class="num green">{{ summary.totalProtein ?? 0 }}</div></el-card>
            </el-col>
            <el-col :span="6">
              <el-card class="stat-card"><div class="label">脂肪（克）</div>
                <div class="num orange">{{ summary.totalFat ?? 0 }}</div></el-card>
            </el-col>
            <el-col :span="6">
              <el-card class="stat-card"><div class="label">碳水（克）</div>
                <div class="num">{{ summary.totalCarb ?? 0 }}</div></el-card>
            </el-col>
          </el-row>
          <el-alert style="margin-top: 16px" type="info" :closable="false" show-icon
            :title="summary.advice || '暂无建议'" />
          <el-table v-loading="loading" :data="records" border stripe style="margin-top: 16px">
            <el-table-column prop="mealType" label="餐次" width="80" />
            <el-table-column prop="customName" label="食物/菜谱" min-width="150" />
            <el-table-column label="份量" width="100">
              <template #default="{ row }">{{ row.quantity }} {{ row.unit }}</template>
            </el-table-column>
            <el-table-column prop="calorie" label="热量（千卡）" width="110" />
            <el-table-column prop="protein" label="蛋白质" width="90" />
            <el-table-column prop="fat" label="脂肪" width="90" />
            <el-table-column prop="carb" label="碳水" width="90" />
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button link type="danger" @click="onDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="数据统计" name="stats">
        <el-row :gutter="16">
          <el-col :span="6">
            <el-card class="stat-card"><div class="label">在库食材</div>
              <div class="num">{{ statsSummary.inStock ?? 0 }}</div></el-card>
          </el-col>
          <el-col :span="6">
            <el-card class="stat-card"><div class="label">低库存</div>
              <div class="num warn">{{ statsSummary.lowStock ?? 0 }}</div></el-card>
          </el-col>
          <el-col :span="6">
            <el-card class="stat-card"><div class="label">过期食材</div>
              <div class="num danger">{{ statsSummary.expiredThisMonth ?? 0 }}</div></el-card>
          </el-col>
          <el-col :span="6">
            <el-card class="stat-card"><div class="label">异常分区</div>
              <div class="num warn">{{ statsSummary.abnormalZones ?? 0 }}</div></el-card>
          </el-col>
        </el-row>

        <el-card style="margin-top: 16px">
          <div class="toolbar">
            <el-radio-group v-model="period" @change="loadStats">
              <el-radio-button value="week">本周</el-radio-button>
              <el-radio-button value="month">本月</el-radio-button>
            </el-radio-group>
            <div class="stat-total">
              消耗总量：<b>{{ stat.totalConsume ?? 0 }}</b>，
              浪费数量：<b class="danger-text">{{ stat.totalWasteQty ?? 0 }}</b>，
              浪费次数：<b class="danger-text">{{ stat.wasteCount ?? 0 }}</b>
            </div>
          </div>
          <el-table v-loading="statsLoading" :data="stat.foods || []" border>
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
          <el-empty v-if="!statsLoading && (stat.foods || []).length === 0"
            description="该时间段暂无消耗数据" />
          <el-alert style="margin-top: 12px" type="info" :closable="false"
            title="统计帮助优化采购计划，减少重复购买和食物浪费。" />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="dialogVisible" title="添加饮食记录" width="560px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="记录方式">
          <el-radio-group v-model="form.mode">
            <el-radio value="recipe">选择菜谱（可多选）</el-radio>
            <el-radio value="custom">手动填写</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="form.mode === 'recipe'">
          <el-form-item label="菜谱">
            <el-select v-model="form.recipeIds" multiple filterable collapse-tags
              placeholder="可多选菜谱" style="width: 100%">
              <el-option v-for="r in recipes" :key="r.id" :label="r.name" :value="r.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="份数">
            <el-input-number v-model="form.quantity" :min="0.5" :step="0.5" />
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="食物名称" required>
            <el-input v-model="form.customName" placeholder="如：苹果" />
          </el-form-item>
          <el-form-item label="份量">
            <el-input-number v-model="form.quantity" :min="0.1" :precision="2" />
          </el-form-item>
          <el-form-item label="热量（千卡）">
            <el-input-number v-model="form.calorie" :min="0" :precision="1" />
          </el-form-item>
          <el-form-item label="蛋白质/脂肪/碳水">
            <el-input-number v-model="form.protein" :min="0" :precision="1" style="width: 30%" />
            <el-input-number v-model="form.fat" :min="0" :precision="1" style="width: 30%" />
            <el-input-number v-model="form.carb" :min="0" :precision="1" style="width: 30%" />
          </el-form-item>
        </template>
        <el-form-item label="餐次">
          <el-select v-model="form.mealType" style="width: 100%">
            <el-option v-for="m in meals" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">记录并检查食材</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addDietRecord, deleteDietRecord, dietSummary, listDietRecords,
} from '../api/diet'
import { checkSelectedRecipes, recommendRecipes } from '../api/recipe'
import { consumptionStats, summaryStats } from '../api/stats'
import { useMissingShoppingStore } from '../stores/missingShopping'

const router = useRouter()
const missingStore = useMissingShoppingStore()
const tab = ref('diet')
const loading = ref(false)
const saving = ref(false)
const date = ref(new Date().toISOString().slice(0, 10))
const records = ref<any[]>([])
const summary = ref<Record<string, any>>({})
const recipes = ref<any[]>([])
const dialogVisible = ref(false)
const meals = ['早餐', '午餐', '晚餐', '加餐']
const form = reactive({
  mode: 'recipe', mealType: '午餐', recipeIds: [] as number[],
  customName: '', quantity: 1, unit: '份', calorie: 0, protein: 0, fat: 0, carb: 0,
})

// 数据统计
const period = ref('month')
const statsLoading = ref(false)
const stat = ref<Record<string, any>>({})
const statsSummary = ref<Record<string, any>>({})

const loadStats = async () => {
  statsLoading.value = true
  try {
    stat.value = await consumptionStats(period.value)
  } finally {
    statsLoading.value = false
  }
}

// 饮食记录
const load = async () => {
  loading.value = true
  try {
    records.value = await listDietRecords({ date: date.value })
    summary.value = await dietSummary({ date: date.value })
  } finally {
    loading.value = false
  }
}

const openAdd = async () => {
  Object.assign(form, {
    mode: 'recipe', mealType: '午餐', recipeIds: [], customName: '',
    quantity: 1, calorie: 0, protein: 0, fat: 0, carb: 0,
  })
  recipes.value = await recommendRecipes({})
  dialogVisible.value = true
}

const recordRecipes = async () => {
  for (const id of form.recipeIds) {
    await addDietRecord({
      recordDate: date.value,
      mealType: form.mealType,
      recipeId: id,
      quantity: form.quantity,
      unit: '份',
    })
  }
}

const submit = async () => {
  if (form.mode === 'recipe' && form.recipeIds.length === 0) {
    ElMessage.warning('请至少选择一道菜谱')
    return
  }
  if (form.mode === 'custom' && !form.customName) {
    ElMessage.warning('请填写食物名称')
    return
  }
  saving.value = true
  try {
    if (form.mode === 'recipe') {
      const check = await checkSelectedRecipes({
        recipeIds: form.recipeIds, servings: form.quantity,
      })
      if (!check.ok) {
        missingStore.set(check.items)
        const names = check.items.map((i: any) => `${i.name}（缺 ${i.missing} ${i.unit}）`).join('、')
        try {
          await ElMessageBox.confirm(
            `食材不足：${names}。已生成缺料购物清单，可在「智能菜谱」中展开查看。`,
            '食材不足',
            {
              confirmButtonText: '去智能菜谱查看',
              cancelButtonText: '仍然记录',
              type: 'warning',
            },
          )
          dialogVisible.value = false
          router.push('/recipes')
          return
        } catch {
          // 用户选择"仍然记录"
        }
      }
      await recordRecipes()
    } else {
      await addDietRecord({
        recordDate: date.value,
        mealType: form.mealType,
        customName: form.customName,
        quantity: form.quantity,
        unit: '克',
        calorie: form.calorie,
        protein: form.protein,
        fat: form.fat,
        carb: form.carb,
      })
    }
    ElMessage.success('记录成功')
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

const onDelete = async (row: any) => {
  await ElMessageBox.confirm('确认删除这条饮食记录？', '提示', { type: 'warning' })
  await deleteDietRecord(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(() => {
  load()
  loadStats()
  summaryStats().then((data) => {
    statsSummary.value = data
  })
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
.stat-card .num.green {
  color: #67c23a;
}
.stat-card .num.orange {
  color: #e6a23c;
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
