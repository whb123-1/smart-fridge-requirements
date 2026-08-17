<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../services/api'
import { completeOnboarding } from '../session'
import { MAX_ZONES, MIN_ZONES, ZONE_KINDS } from '../components/fridgeLayouts'
import { clampSensorCount, validateOnboardingDraft } from '../services/onboarding'

const router = useRouter()
const step = ref(1)
const busy = ref(false)
const loading = ref(true)
const error = ref('')
const zoneCount = ref(4)
const fridgeName = ref('我的冰箱')
const defaults = ref([])
const zones = reactive([])
const suggestedNames = Object.freeze(['冷藏区', '保鲜区', '变温区', '冷冻区', '扩展冷藏区', '扩展保鲜区'])

const normalizedNames = computed(() => zones.map(zone => zone.name.trim().toLocaleLowerCase()))
const namesAreUnique = computed(() => normalizedNames.value.every((name, index, all) => name && all.indexOf(name) === index))
const canContinue = computed(() => validateOnboardingDraft(fridgeName.value, zones).valid)
const currentLabel = computed(() => ['配置分区', '配置传感器', '确认冰箱'][step.value - 1])

function kindDefault(kind) { return defaults.value.find(item => item.kind === kind) || {} }
function adjustSensor(zone, field, change) { zone[field] = clampSensorCount(zone[field] + change) }

function syncZones(count) {
  const target = Math.max(MIN_ZONES, Math.min(MAX_ZONES, Number(count) || 4))
  while (zones.length < target) {
    const index = zones.length
    zones.push({
      kind: ZONE_KINDS[index].toUpperCase(),
      name: suggestedNames[index],
      temperatureSensorCount: 1,
      humiditySensorCount: index === 3 ? 0 : 1,
    })
  }
  if (zones.length > target) zones.splice(target)
}

function next() {
  error.value = ''
  if (step.value === 1 && !canContinue.value) {
    error.value = validateOnboardingDraft(fridgeName.value, zones).message
    return
  }
  step.value = Math.min(3, step.value + 1)
}

async function initialize() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  const storageKey = 'xianzhi.onboarding.idempotency-key'
  let idempotencyKey = sessionStorage.getItem(storageKey)
  if (!idempotencyKey) {
    idempotencyKey = crypto.randomUUID()
    sessionStorage.setItem(storageKey, idempotencyKey)
  }
  try {
    const fridge = await api.initializeOnboarding({ fridgeName: fridgeName.value.trim(), zones: zones.map(zone => ({ ...zone, name: zone.name.trim() })) }, idempotencyKey)
    sessionStorage.removeItem(storageKey)
    completeOnboarding(fridge)
    await router.replace('/app/home')
  } catch (exception) {
    error.value = exception.code === 'ONBOARDING_ALREADY_COMPLETED'
      ? '这台冰箱已经完成初始化，正在返回首页'
      : exception.message || '初始化未完成，请检查后重试'
    if (exception.code === 'ONBOARDING_ALREADY_COMPLETED') {
      const status = await api.getOnboarding().catch(() => null)
      if (status?.fridge) { completeOnboarding(status.fridge); await router.replace('/app/home') }
    }
  } finally {
    busy.value = false
  }
}

watch(zoneCount, syncZones, { immediate: true })

onMounted(async () => {
  try {
    const status = await api.getOnboarding()
    defaults.value = status.zoneDefaults || []
    if (status.completed && status.fridge) {
      completeOnboarding(status.fridge)
      await router.replace('/app/home')
    }
  } catch (exception) {
    error.value = exception.message || '无法读取初始化配置'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <main class="onboarding-view">
    <header class="onboarding-header">
      <div><p>鲜知 · 首次设置</p><h1>{{ currentLabel }}</h1></div>
      <ol aria-label="初始化进度">
        <li v-for="index in 3" :key="index" :class="{ active: index === step, done: index < step }"><span>{{ index }}</span><b>{{ ['分区', '传感器', '确认'][index - 1] }}</b></li>
      </ol>
    </header>

    <section v-if="loading" class="onboarding-loading">正在读取冰箱配置</section>
    <section v-else class="onboarding-workspace">
      <template v-if="step === 1">
        <div class="onboarding-intro"><p>冰箱结构</p><h2>你的冰箱如何分区？</h2></div>
        <label class="fridge-name-field">冰箱名称<input v-model.trim="fridgeName" maxlength="80" /></label>
        <div class="zone-count-control">
          <span>分区数量</span><input v-model.number="zoneCount" type="range" :min="MIN_ZONES" :max="MAX_ZONES" step="1" /><strong>{{ zoneCount }}</strong>
        </div>
        <div class="onboarding-zone-list">
          <label v-for="(zone, index) in zones" :key="index"><span>{{ index + 1 }}</span><small>{{ zone.kind }}</small><input v-model.trim="zone.name" maxlength="48" :aria-label="`第 ${index + 1} 个分区名称`" /></label>
        </div>
      </template>

      <template v-else-if="step === 2">
        <div class="onboarding-intro"><p>传感器槽位</p><h2>每个分区需要几枚探头？</h2><small>数量可以为 0，未接入时使用目标值估算。</small></div>
        <div class="sensor-config-list">
          <article v-for="zone in zones" :key="zone.name">
            <header><span>{{ zone.name }}</span><small>{{ zone.kind }}</small></header>
            <div><span>温度</span><button type="button" aria-label="减少温度传感器" @click="adjustSensor(zone, 'temperatureSensorCount', -1)">−</button><strong>{{ zone.temperatureSensorCount }}</strong><button type="button" aria-label="增加温度传感器" @click="adjustSensor(zone, 'temperatureSensorCount', 1)">+</button></div>
            <div><span>湿度</span><button type="button" aria-label="减少湿度传感器" @click="adjustSensor(zone, 'humiditySensorCount', -1)">−</button><strong>{{ zone.humiditySensorCount }}</strong><button type="button" aria-label="增加湿度传感器" @click="adjustSensor(zone, 'humiditySensorCount', 1)">+</button></div>
          </article>
        </div>
      </template>

      <template v-else>
        <div class="onboarding-intro"><p>{{ fridgeName }}</p><h2>确认后进入冰箱首页</h2></div>
        <div class="onboarding-summary">
          <article v-for="zone in zones" :key="zone.name">
            <div><span>{{ zone.name }}</span><small>{{ zone.kind }}</small></div>
            <dl><div><dt>目标温度</dt><dd>{{ kindDefault(zone.kind).targetTemperatureC ?? '—' }} °C</dd></div><div><dt>目标湿度</dt><dd>{{ kindDefault(zone.kind).targetHumidityPct ?? '—' }}%</dd></div><div><dt>传感器</dt><dd>{{ zone.temperatureSensorCount }} 温 / {{ zone.humiditySensorCount }} 湿</dd></div></dl>
          </article>
        </div>
      </template>

      <p v-if="error" class="form-error onboarding-error" role="alert">{{ error }}</p>
      <footer class="onboarding-actions">
        <button v-if="step > 1" type="button" class="secondary-btn" :disabled="busy" @click="step--">上一步</button>
        <span></span>
        <button v-if="step < 3" type="button" class="primary-btn" @click="next">继续</button>
        <button v-else type="button" class="primary-btn" :disabled="busy" @click="initialize">{{ busy ? '正在创建冰箱' : '确认并进入首页' }}</button>
      </footer>
    </section>
  </main>
</template>
