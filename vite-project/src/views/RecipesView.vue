<template>
  <div class="page">
    <el-tabs v-model="recipeTab">
      <el-tab-pane label="智能菜谱" name="list">
        <el-card>
          <div class="toolbar">
            <el-input v-model="query.keyword" placeholder="搜索菜谱" clearable style="width: 180px"
              @keyup.enter="load" />
            <el-select v-model="query.taste" placeholder="口味" clearable style="width: 120px">
              <el-option v-for="t in tastes" :key="t" :label="t" :value="t" />
            </el-select>
            <el-input-number v-model="query.cookTimeMax" placeholder="最大烹饪时间" :min="1"
              style="width: 160px" />
            <el-select v-model="query.dietGoal" placeholder="饮食目标" clearable style="width: 140px">
              <el-option v-for="g in dietGoals" :key="g" :label="g" :value="g" />
            </el-select>
            <el-button type="primary" @click="load">推荐</el-button>
            <div style="flex: 1"></div>
            <el-button type="success" @click="genVisible = true">智能生成菜谱</el-button>
            <el-button :type="showFavorites ? 'primary' : 'default'" @click="toggleFavorites">
              我的收藏
            </el-button>
            <el-button @click="openHistory">制作历史</el-button>
          </div>

          <el-card v-if="missingStore.items.length" class="missing-card" shadow="never">
            <div class="missing-header" @click="missingOpen = !missingOpen">
              <el-icon :size="18" color="#e6a23c"><WarningFilled /></el-icon>
              <span class="missing-title">缺料购物清单（{{ missingStore.items.length }} 项）</span>
              <el-icon class="missing-arrow" :class="{ open: missingOpen }"><ArrowDown /></el-icon>
            </div>
            <el-collapse-transition>
              <div v-show="missingOpen">
                <el-table :data="missingStore.items" size="small" border>
                  <el-table-column prop="name" label="食材" min-width="100" />
                  <el-table-column label="需要" width="90">
                    <template #default="{ row }">{{ row.needed }} {{ row.unit }}</template>
                  </el-table-column>
                  <el-table-column label="已有" width="90">
                    <template #default="{ row }">{{ row.have }} {{ row.unit }}</template>
                  </el-table-column>
                  <el-table-column label="缺少" width="100">
                    <template #default="{ row }">
                      <span class="missing-qty">{{ row.missing }} {{ row.unit }}</span>
                    </template>
                  </el-table-column>
                </el-table>
                <div class="missing-actions">
                  <el-button size="small" type="danger" plain @click="missingStore.clear()">
                    清空清单
                  </el-button>
                </div>
              </div>
            </el-collapse-transition>
          </el-card>

          <div v-loading="loading" class="recipe-grid">
            <el-card v-for="r in list" :key="r.id" class="recipe-card" shadow="hover"
              @click="$router.push(`/recipes/${r.id}`)">
              <div class="recipe-title">
                <span>{{ r.name }}</span>
                <el-tag size="small" :type="matchTag(r.matchType)">{{ r.matchText }}</el-tag>
              </div>
              <div class="recipe-meta">
                <span>{{ r.cuisine || '家常菜' }}</span>
                <span v-if="r.taste">口味：{{ r.taste }}</span>
                <span v-if="r.cookTimeMin">约 {{ r.cookTimeMin }} 分钟</span>
                <span v-if="r.perServingCalorie">{{ r.perServingCalorie }} 千卡/份</span>
              </div>
              <el-progress :percentage="Math.round(r.coverage * 100)" :stroke-width="8"
                :format="() => `食材覆盖 ${Math.round(r.coverage * 100)}%`" style="margin: 10px 0" />
              <div v-if="r.missingNames.length" class="missing">
                缺少：{{ r.missingNames.join('、') }}
              </div>
            </el-card>
          </div>
          <el-empty v-if="!loading && list.length === 0" description="没有匹配的菜谱" />
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="偏好设置" name="pref">
        <el-card style="max-width: 720px">
          <el-form :model="prefForm" label-width="120px" v-loading="prefLoading">
            <el-form-item label="口味偏好">
              <el-checkbox-group v-model="prefForm.tasteList">
                <el-checkbox v-for="t in tastes" :key="t" :value="t" :label="t" />
              </el-checkbox-group>
            </el-form-item>
            <el-form-item label="饮食目标">
              <el-select v-model="prefForm.dietGoal" style="width: 100%">
                <el-option v-for="g in dietGoals" :key="g" :label="g" :value="g" />
              </el-select>
            </el-form-item>
            <el-form-item label="每日目标热量">
              <el-input-number v-model="prefForm.targetCalories" :min="800" :max="6000"
                :step="50" style="width: 100%" />
            </el-form-item>
            <el-form-item label="过敏食材">
              <el-input v-model="prefForm.allergy" placeholder="多个食材用逗号分隔，如：花生,海鲜" />
            </el-form-item>
            <el-form-item label="忌口食材">
              <el-input v-model="prefForm.avoidFoods" placeholder="多个食材用逗号分隔，如：香菜,苦瓜" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="prefSaving" @click="savePreference">保存设置</el-button>
              <el-text type="info" size="small">菜谱推荐时会自动避开过敏与忌口食材</el-text>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="genVisible" title="根据冰箱库存生成菜谱" width="460px">
      <el-form :model="genForm" label-width="100px">
        <el-form-item label="菜名" required>
          <el-input v-model="genForm.name" placeholder="如：番茄牛腩" />
        </el-form-item>
        <el-form-item label="预计时间">
          <el-input-number v-model="genForm.cookTimeMin" :min="1" :max="300" />
        </el-form-item>
        <el-form-item label="份数">
          <el-input-number v-model="genForm.servings" :min="1" :max="20" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="genVisible = false">取消</el-button>
        <el-button type="primary" :loading="generating" @click="onGenerate">生成</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showHistory" title="菜谱历史" width="640px">
      <el-table :data="history" border size="small" max-height="480">
        <el-table-column prop="recipeName" label="菜谱" />
        <el-table-column prop="actionText" label="动作" width="100" />
        <el-table-column prop="servings" label="份数" width="80" />
        <el-table-column prop="createdAt" label="时间" width="170" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowDown, WarningFilled } from '@element-plus/icons-vue'
import { getPreference, updatePreference } from '../api/preference'
import {
  favoriteList, generateRecipe, historyList, recommendRecipes,
} from '../api/recipe'
import { useMissingShoppingStore } from '../stores/missingShopping'

const router = useRouter()
const missingStore = useMissingShoppingStore()
const recipeTab = ref('list')
const missingOpen = ref(true)
const loading = ref(false)
const generating = ref(false)
const list = ref<any[]>([])
const history = ref<any[]>([])
const genVisible = ref(false)
const showHistory = ref(false)
const showFavorites = ref(false)
const tastes = ['清淡', '微辣', '少油', '低盐', '咸鲜', '甜口']
const dietGoals = ['均衡', '减脂', '增肌', '控制热量']
const query = reactive({
  keyword: '', taste: '', cookTimeMax: undefined as number | undefined,
  dietGoal: '',
})
const genForm = reactive({ name: '', cookTimeMin: 20, servings: 2 })

// 偏好设置
const prefLoading = ref(false)
const prefSaving = ref(false)
const prefForm = reactive({
  tasteList: [] as string[], dietGoal: '均衡', targetCalories: 2000,
  allergy: '', avoidFoods: '',
})

const split = (text: string) =>
  (text || '').split(/[,，、;；]/).map((s) => s.trim()).filter(Boolean)

const loadPreference = async () => {
  prefLoading.value = true
  try {
    const p = await getPreference()
    prefForm.tasteList = split(p.taste)
    prefForm.dietGoal = p.dietGoal || '均衡'
    prefForm.targetCalories = p.targetCalories || 2000
    prefForm.allergy = p.allergy || ''
    prefForm.avoidFoods = p.avoidFoods || ''
    query.dietGoal = p.dietGoal || ''
  } finally {
    prefLoading.value = false
  }
}

const savePreference = async () => {
  prefSaving.value = true
  try {
    await updatePreference({
      taste: prefForm.tasteList.join(','),
      dietGoal: prefForm.dietGoal,
      targetCalories: prefForm.targetCalories,
      allergy: prefForm.allergy,
      avoidFoods: prefForm.avoidFoods,
    })
    ElMessage.success('保存成功')
  } finally {
    prefSaving.value = false
  }
}

// 菜谱推荐
const load = async () => {
  loading.value = true
  try {
    if (showFavorites.value) {
      list.value = await favoriteList()
      return
    }
    const params: Record<string, any> = {
      keyword: query.keyword || undefined,
      taste: query.taste || undefined,
      cookTimeMax: query.cookTimeMax || undefined,
      dietGoal: query.dietGoal || undefined,
    }
    list.value = await recommendRecipes(params)
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

const onGenerate = async () => {
  if (!genForm.name) {
    ElMessage.warning('请输入菜名')
    return
  }
  generating.value = true
  try {
    const detail = await generateRecipe({ ...genForm })
    genVisible.value = false
    router.push(`/recipes/${detail.id}`)
  } finally {
    generating.value = false
  }
}

const openHistory = async () => {
  history.value = await historyList()
  showHistory.value = true
}

const toggleFavorites = async () => {
  showFavorites.value = !showFavorites.value
  if (showFavorites.value) {
    query.keyword = ''
    loading.value = true
    try {
      list.value = await favoriteList()
    } finally {
      loading.value = false
    }
  } else {
    load()
  }
}

onMounted(() => {
  loadPreference()
  load()
})
</script>

<style scoped>
.recipe-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}
.recipe-card {
  cursor: pointer;
}
.recipe-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 16px;
  font-weight: 600;
}
.recipe-meta {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  color: #909399;
  font-size: 13px;
  margin-top: 8px;
}
.missing {
  color: #e6a23c;
  font-size: 13px;
}
.missing-card {
  margin-bottom: 16px;
  border-color: #f3d19e;
}
.missing-header {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}
.missing-title {
  flex: 1;
  font-weight: 600;
  color: #b88230;
}
.missing-arrow {
  transition: transform 0.2s;
  color: #909399;
}
.missing-arrow.open {
  transform: rotate(180deg);
}
.missing-qty {
  color: #e6a23c;
  font-weight: 600;
}
.missing-actions {
  margin-top: 10px;
  text-align: right;
}
</style>
