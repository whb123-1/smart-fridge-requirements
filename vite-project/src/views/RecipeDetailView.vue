<template>
  <div class="page" v-loading="loading">
    <el-card v-if="detail">
      <div class="detail-header">
        <h2>{{ detail.name }}</h2>
        <div>
          <el-tag :type="matchTag(detail.matchType)" style="margin-right: 8px">{{ detail.matchText }}</el-tag>
          <el-button :type="detail.favorite ? 'warning' : 'default'"
            @click="toggleFavorite">{{ detail.favorite ? '已收藏' : '收藏' }}</el-button>
        </div>
      </div>
      <div class="meta">
        <el-tag v-if="detail.cuisine" size="small">{{ detail.cuisine }}</el-tag>
        <span v-if="detail.taste">口味：{{ detail.taste }}</span>
        <span v-if="detail.cookTimeMin">烹饪时间：约 {{ detail.cookTimeMin }} 分钟</span>
        <span>难度：{{ detail.difficulty }}</span>
        <span>份量：{{ detail.servings }} 份</span>
        <span v-if="detail.perServingCalorie">单份热量：{{ detail.perServingCalorie }} 千卡</span>
      </div>
      <el-alert v-if="detail.missingNames.length" type="warning" :closable="false"
        style="margin: 12px 0"
        :title="`缺少食材：${detail.missingNames.join('、')}`" />

      <el-row :gutter="32">
        <el-col :span="12">
          <h3>食材清单</h3>
          <el-table :data="detail.ingredients" border size="small">
            <el-table-column label="食材" min-width="100">
              <template #default="{ row }">
                {{ row.name }}
                <el-tag v-if="row.isStaple === 1" size="small" type="primary" style="margin-left: 4px">主料</el-tag>
                <el-tag v-if="row.isCondiment === 1" size="small" type="info" style="margin-left: 4px">调味</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="用量" width="100">
              <template #default="{ row }">{{ row.quantity }} {{ row.unit }}</template>
            </el-table-column>
            <el-table-column label="库存" width="90">
              <template #default="{ row }">
                <el-tag :type="row.available ? 'success' : 'danger'" size="small">
                  {{ row.available ? (row.stockQty + ' ' + row.unit) : '缺' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="替代" min-width="90">
              <template #default="{ row }">{{ row.alternative || '-' }}</template>
            </el-table-column>
          </el-table>
          <div style="margin-top: 12px">
            <el-button type="primary" @click="openScale">用量调整</el-button>
            <el-button type="success" @click="openCook">完成制作（扣减库存）</el-button>
          </div>
        </el-col>
        <el-col :span="12">
          <h3>制作步骤</h3>
          <el-timeline>
            <el-timeline-item v-for="s in detail.steps" :key="s.stepNo"
              :timestamp="s.cookMin ? `约 ${s.cookMin} 分钟` : ''">
              {{ s.content }}
            </el-timeline-item>
          </el-timeline>
        </el-col>
      </el-row>
    </el-card>

    <el-dialog v-model="scaleVisible" title="用量动态调整" width="520px">
      <el-form :model="scaleForm" label-width="100px">
        <el-form-item label="主料名称" required>
          <el-select v-model="scaleForm.mainName" allow-create filterable style="width: 100%">
            <el-option v-for="i in detail?.ingredients.filter((x: any) => x.isCondiment === 0)"
              :key="i.name" :label="i.name" :value="i.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="实际用量" required>
          <el-input-number v-model="scaleForm.actualQty" :min="0.1" :precision="2" style="width: 100%" />
        </el-form-item>
      </el-form>
      <div v-if="scaleResult" class="scale-result">
        <el-divider />
        <div>缩放比例：{{ scaleResult.ratio }}，调整后单份热量约 {{ scaleResult.newPerServingCalorie }} 千卡</div>
        <el-table :data="scaleResult.ingredients" border size="small" style="margin-top: 8px">
          <el-table-column prop="name" label="食材" />
          <el-table-column label="建议用量" width="120">
            <template #default="{ row }">{{ row.quantity }} {{ row.unit }}</template>
          </el-table-column>
          <el-table-column label="类型" width="80">
            <template #default="{ row }">{{ row.isCondiment ? '调味品' : '主配菜' }}</template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="scaleVisible = false">关闭</el-button>
        <el-button type="primary" @click="doScale">计算</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="cookVisible" title="完成制作" width="440px">
      <el-form label-width="100px">
        <el-form-item label="制作份数">
          <el-input-number v-model="cookServings" :min="1" :max="20" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cookVisible = false">取消</el-button>
        <el-button type="success" :loading="cooking" @click="doCook">确认并扣减库存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  cookRecipe, favoriteRecipe, recipeDetail, scaleRecipe, unfavoriteRecipe,
} from '../api/recipe'

const route = useRoute()
const detail = ref<any>(null)
const loading = ref(false)
const scaleVisible = ref(false)
const scaleResult = ref<any>(null)
const cookVisible = ref(false)
const cooking = ref(false)
const scaleForm = ref({ mainName: '', actualQty: 100 })
const cookServings = ref(1)

const load = async () => {
  loading.value = true
  try {
    detail.value = await recipeDetail(Number(route.params.id))
    scaleForm.value.mainName = detail.value.ingredients.find((i: any) => i.isStaple === 1)?.name
      || detail.value.ingredients[0]?.name || ''
  } finally {
    loading.value = false
  }
}

const matchTag = (type: string) => {
  const map: Record<string, string> = {
    can_make: 'success', alternative: 'warning', missing_few: 'info', other: 'danger',
  }
  return map[type] || 'info'
}

const toggleFavorite = async () => {
  if (detail.value.favorite) {
    await unfavoriteRecipe(detail.value.id)
    detail.value.favorite = false
    ElMessage.success('已取消收藏')
  } else {
    await favoriteRecipe(detail.value.id)
    detail.value.favorite = true
    ElMessage.success('已收藏')
  }
}

const openScale = () => {
  scaleResult.value = null
  scaleVisible.value = true
}

const doScale = async () => {
  if (!scaleForm.value.mainName || !scaleForm.value.actualQty) {
    ElMessage.warning('请填写主料和实际用量')
    return
  }
  scaleResult.value = await scaleRecipe(detail.value.id, { ...scaleForm.value })
}

const openCook = () => {
  cookServings.value = 1
  cookVisible.value = true
}

const doCook = async () => {
  cooking.value = true
  try {
    const consumed = await cookRecipe(detail.value.id, { servings: cookServings.value })
    cookVisible.value = false
    const names = consumed.map((c: any) => `${c.name} ${c.consumedQty}${c.unit}`).join('、')
    ElMessage.success({ message: `制作完成，已扣减库存：${names || '无'}`, duration: 4000 })
    load()
  } finally {
    cooking.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.detail-header h2 {
  margin: 0;
}
.meta {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  color: #909399;
  margin-top: 12px;
  font-size: 13px;
}
.scale-result {
  color: #606266;
}
</style>
