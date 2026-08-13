<template>
  <div class="page">
    <el-card style="max-width: 720px">
      <template #header>饮食偏好与忌口设置</template>
      <el-form :model="form" label-width="120px" v-loading="loading">
        <el-form-item label="口味偏好">
          <el-checkbox-group v-model="form.tasteList">
            <el-checkbox v-for="t in tastes" :key="t" :value="t" :label="t" />
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="饮食目标">
          <el-select v-model="form.dietGoal" style="width: 100%">
            <el-option label="均衡" value="均衡" />
            <el-option label="减脂" value="减脂" />
            <el-option label="增肌" value="增肌" />
            <el-option label="控制热量" value="控制热量" />
          </el-select>
        </el-form-item>
        <el-form-item label="每日目标热量">
          <el-input-number v-model="form.targetCalories" :min="800" :max="6000" :step="50"
            style="width: 100%" />
        </el-form-item>
        <el-form-item label="过敏食材">
          <el-input v-model="form.allergy" placeholder="多个食材用逗号分隔，如：花生,海鲜" />
        </el-form-item>
        <el-form-item label="忌口食材">
          <el-input v-model="form.avoidFoods" placeholder="多个食材用逗号分隔，如：香菜,苦瓜" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="save">保存设置</el-button>
          <el-text type="info" size="small">菜谱推荐时会自动避开过敏与忌口食材</el-text>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getPreference, updatePreference } from '../api/preference'

const tastes = ['清淡', '微辣', '少油', '低盐']
const loading = ref(false)
const saving = ref(false)
const form = reactive({
  tasteList: [] as string[],
  dietGoal: '均衡',
  targetCalories: 2000,
  allergy: '',
  avoidFoods: '',
})

const split = (text: string) =>
  (text || '').split(/[,，、;；]/).map((s) => s.trim()).filter(Boolean)

const load = async () => {
  loading.value = true
  try {
    const p = await getPreference()
    form.tasteList = split(p.taste)
    form.dietGoal = p.dietGoal || '均衡'
    form.targetCalories = p.targetCalories || 2000
    form.allergy = p.allergy || ''
    form.avoidFoods = p.avoidFoods || ''
  } finally {
    loading.value = false
  }
}

const save = async () => {
  saving.value = true
  try {
    await updatePreference({
      taste: form.tasteList.join(','),
      dietGoal: form.dietGoal,
      targetCalories: form.targetCalories,
      allergy: form.allergy,
      avoidFoods: form.avoidFoods,
    })
    ElMessage.success('保存成功')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>
