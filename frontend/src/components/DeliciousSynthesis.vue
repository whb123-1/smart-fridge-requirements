<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { matchRecipeCombination } from '../services/deliciousSynthesis'

const props = defineProps({
  foods: { type: Array, required: true },
  preferences: { type: Object, required: true },
})

const emit = defineEmits(['start-cooking'])

const selectedIngredients = ref([])
const matchResult = ref(null)
const isMatching = ref(false)
const matchError = ref('')
const inlineNotice = ref('')
const isDragOver = ref(false)
const isBouncing = ref(false)
let dragDepth = 0
let dragStarted = false
let requestVersion = 0
let requestController = null
let bounceTimer = null
let noticeTimer = null

const availableFoods = computed(() => props.foods.filter(food => Number(food.amount) > 0 && food.batchId))
const selectedTypeCount = computed(() => selectedIngredients.value.length)

function selectedEntry(food) {
  return selectedIngredients.value.find(item => String(item.id) === String(food.id))
}

function setNotice(message) {
  inlineNotice.value = message
  clearTimeout(noticeTimer)
  noticeTimer = setTimeout(() => {
    if (inlineNotice.value === message) inlineNotice.value = ''
  }, 2400)
}

function triggerBounce() {
  isBouncing.value = false
  clearTimeout(bounceTimer)
  requestAnimationFrame(() => {
    isBouncing.value = true
    bounceTimer = setTimeout(() => { isBouncing.value = false }, 230)
  })
}

function buildRequest() {
  return {
    ingredients: selectedIngredients.value.map(item => ({
      batchId: item.batchId,
      name: item.name,
      quantity: item.quantity,
      unit: item.apiUnit,
    })),
  }
}

async function runMatch() {
  requestController?.abort()
  const version = ++requestVersion
  matchError.value = ''

  if (!selectedIngredients.value.length) {
    matchResult.value = null
    isMatching.value = false
    return
  }

  requestController = new AbortController()
  isMatching.value = true
  matchResult.value = null
  try {
    const result = await matchRecipeCombination(buildRequest(), { signal: requestController.signal })
    if (version === requestVersion) matchResult.value = result
  } catch (error) {
    if (error?.name !== 'AbortError' && version === requestVersion) matchError.value = '菜谱匹配失败，请重新匹配'
  } finally {
    if (version === requestVersion) isMatching.value = false
  }
}

function addIngredient(food, { bounce = false } = {}) {
  const existing = selectedEntry(food)
  if (existing) {
    setNotice(`${food.name} 已在合成区`)
    return
  }
  if (selectedIngredients.value.length >= 4) {
    setNotice('合成区最多放入 4 种食材')
    return
  }

  selectedIngredients.value.push({
    id: food.id,
    batchId: food.batchId,
    name: food.name,
    icon: food.icon,
    category: food.category,
    // Matching uses the real usable quantity from the selected batch. Clicking
    // an item must not silently turn one gram/piece into an arbitrary serving.
    quantity: Number(food.amount),
    unit: food.unit,
    apiUnit: food.apiUnit,
  })
  inlineNotice.value = ''
  if (bounce) triggerBounce()
  runMatch()
}

function removeIngredient(item) {
  selectedIngredients.value = selectedIngredients.value.filter(candidate => String(candidate.id) !== String(item.id))
  runMatch()
}

function clearSynthesis() {
  requestController?.abort()
  requestController = null
  requestVersion += 1
  selectedIngredients.value = []
  matchResult.value = null
  matchError.value = ''
  inlineNotice.value = ''
  isMatching.value = false
}

function onIngredientClick(food) {
  if (dragStarted) return
  const existing = selectedEntry(food)
  if (existing) removeIngredient(existing)
  else addIngredient(food)
}

function onDragStart(event, food) {
  dragStarted = true
  event.dataTransfer.effectAllowed = 'copy'
  event.dataTransfer.setData('text/plain', String(food.id))
}

function onDragEnd() {
  dragDepth = 0
  isDragOver.value = false
  setTimeout(() => { dragStarted = false }, 0)
}

function onDragEnter(event) {
  event.preventDefault()
  dragDepth += 1
  isDragOver.value = true
}

function onDragOver(event) {
  event.preventDefault()
  event.dataTransfer.dropEffect = 'copy'
}

function onDragLeave(event) {
  event.preventDefault()
  dragDepth = Math.max(0, dragDepth - 1)
  if (dragDepth === 0) isDragOver.value = false
}

function onDrop(event) {
  event.preventDefault()
  dragDepth = 0
  isDragOver.value = false
  const foodId = event.dataTransfer.getData('text/plain')
  const food = availableFoods.value.find(item => String(item.id) === String(foodId))
  if (food) addIngredient(food, { bounce: true })
}

function resultAvailability(item) {
  if (item.state === 'missing') return '库存缺少'
  if (item.state === 'unknown') return '库存未记录'
  if (item.state === 'insufficient') return `还差 ${item.shortage}${item.unit}`
  return '库存充足'
}

function startCooking() {
  if (matchResult.value?.recipe) emit('start-cooking', { ...matchResult.value.recipe })
}

onBeforeUnmount(() => {
  requestController?.abort()
  clearTimeout(bounceTimer)
  clearTimeout(noticeTimer)
})
</script>

<template>
  <section class="page-intro synthesis-intro">
    <div>
      <p class="eyebrow">AI 调度 · 后端库存校验 · 可追踪合成会话</p>
      <h1>美味合成</h1>
      <p>从冰箱中挑选想用的食材；后端会校验真实批次与数量、创建合成会话，并在完成制作时原子扣减库存和写入饮食记录。</p>
    </div>
    <div class="synthesis-count" aria-live="polite">
      <strong>{{ selectedTypeCount }}</strong>
      <span>种食材入锅</span>
    </div>
  </section>

  <section class="synthesis-logic" aria-label="合成匹配规则">
    <article><strong>01</strong><div><b>按真实库存选材</b><small>提交所选批次的实际可用数量，不按点击次数虚增份量。</small></div></article>
    <article><strong>02</strong><div><b>优先使用更多所选食材</b><small>后端先排除过敏与忌口，再按未使用食材、缺料数量和用时排序。</small></div></article>
    <article><strong>03</strong><div><b>制作时原子扣减</b><small>确认做菜后才扣减对应批次，并写入饮食记录；匹配阶段不会改库存。</small></div></article>
  </section>

  <section class="synthesis-layout">
    <aside class="ingredient-library" aria-label="食材库">
      <header>
        <div>
          <p class="eyebrow">冰箱现有库存</p>
          <h2>食材库</h2>
        </div>
        <span>{{ availableFoods.length }} 项可用</span>
      </header>

      <div class="ingredient-list">
        <button
          v-for="food in availableFoods"
          :key="food.id"
          class="ingredient-option"
          :class="{ selected: selectedEntry(food) }"
          draggable="true"
          :aria-label="`${selectedEntry(food) ? '取消选择' : '加入'}${food.name}，当前库存${food.amount}${food.unit}`"
          @click="onIngredientClick(food)"
          @dragstart="onDragStart($event, food)"
          @dragend="onDragEnd"
        >
          <i>{{ food.icon }}</i>
          <span>
            <b>{{ food.name }}</b>
            <small>{{ food.category }} · {{ food.zone }}</small>
          </span>
          <em>{{ food.amount === '' ? '未记录' : `${food.amount}${food.unit}` }}</em>
          <strong v-if="selectedEntry(food)">已选</strong>
        </button>
      </div>
    </aside>

    <div class="synthesis-workbench">
      <header class="synthesis-toolbar">
        <div>
          <p class="eyebrow">{{ selectedTypeCount }} / 4 种 · 使用所选批次库存</p>
          <h2>合成区</h2>
        </div>
        <button class="clear-synthesis" :disabled="!selectedTypeCount" @click="clearSynthesis">清空合成区</button>
      </header>

      <div
        class="pot-stage"
        :class="{ 'is-drag-over': isDragOver, 'is-bouncing': isBouncing }"
        role="region"
        aria-label="菜谱合成区"
        :aria-busy="isMatching"
        @dragenter="onDragEnter"
        @dragover="onDragOver"
        @dragleave="onDragLeave"
        @drop="onDrop"
      >
        <div class="pot-steam" aria-hidden="true"><i></i><i></i><i></i></div>
        <div class="pot-assembly" aria-hidden="true">
          <span class="pot-handle pot-handle-left"></span>
          <span class="pot-handle pot-handle-right"></span>
          <div class="pot-rim"></div>
          <div class="pot-body"></div>
          <div class="pot-heat"><i></i><i></i><i></i></div>
        </div>

        <div class="pot-content">
          <div v-if="selectedIngredients.length" class="pot-ingredients">
            <article
              v-for="item in selectedIngredients"
              :key="item.id"
              class="pot-ingredient"
            >
              <span>{{ item.icon }}</span>
              <div><b>{{ item.name }}</b><small>可用 {{ item.quantity }}{{ item.unit }}</small></div>
              <button :aria-label="`移除${item.name}`" title="移除食材" @click="removeIngredient(item)">×</button>
            </article>
          </div>
          <div v-else class="pot-empty">
            <span>＋</span>
            <b>等待食材</b>
          </div>
        </div>

        <p v-if="inlineNotice" class="synthesis-notice" role="status">{{ inlineNotice }}</p>
      </div>

      <section class="synthesis-result-slot" aria-live="polite">
        <div v-if="isMatching" class="synthesis-loading">
          <span class="matching-spinner" aria-hidden="true"></span>
          <div><b>正在匹配菜谱</b><small>从菜谱库中寻找最合适的组合</small></div>
        </div>

        <div v-else-if="matchError" class="synthesis-error" role="alert">
          <span>!</span>
          <div><b>{{ matchError }}</b><small>当前食材组合已经保留</small></div>
          <button @click="runMatch">重新匹配</button>
        </div>

        <article v-else-if="matchResult?.status === 'matched'" class="synthesis-result-card">
          <header>
            <span class="result-art">{{ matchResult.recipe.art }}</span>
            <div>
              <p class="eyebrow">组合推荐 · 优先使用所选食材</p>
              <h2>{{ matchResult.recipe.name }}</h2>
              <p>{{ matchResult.recipe.desc }}</p>
            </div>
            <div class="result-time"><strong>{{ matchResult.recipe.time }}</strong><span>分钟</span></div>
          </header>

          <div class="result-detail-grid">
            <section class="result-ingredients">
              <h3>需要的食材</h3>
              <div v-for="item in matchResult.recipe.ingredients" :key="item.name" :class="item.state">
                <span>{{ item.name }}</span>
                <b>{{ item.amount }}{{ item.unit }}</b>
                <small>{{ resultAvailability(item) }}</small>
              </div>
            </section>
            <section class="result-method">
              <h3>详细制作过程</h3>
              <ol>
                <li v-for="(step, index) in matchResult.recipe.detailedSteps" :key="step.number || index"><span>{{ index + 1 }}</span><div><b>{{ step.title }}</b>{{ step.instruction }}<small>{{ step.heat }} · {{ step.duration }} · {{ step.checkpoint }}</small></div></li>
              </ol>
              <h3 class="result-utensil-title">使用厨具</h3>
              <div class="result-utensils"><span v-for="utensil in matchResult.recipe.utensils" :key="utensil">{{ utensil }}</span></div>
            </section>
          </div>

          <footer>
            <span :class="{ warning: matchResult.recipe.missing.length }">
              {{ matchResult.recipe.missing.length ? `还缺 ${matchResult.recipe.missing.join('、')}` : matchResult.unmatched?.length ? `本菜谱未使用：${matchResult.unmatched.join('、')}` : `当前库存可以直接制作 · AI 估算每份 ${matchResult.recipe.kcal} 千卡` }}
            </span>
            <button class="start-cooking" :disabled="matchResult.recipe.missing.length" @click="startCooking">开始做菜</button>
          </footer>
        </article>

        <article v-else-if="matchResult?.status === 'unmatched'" class="synthesis-unmatched">
          <span class="unmatched-mark">?</span>
          <div>
            <p class="eyebrow">组合待补全</p>
            <h2>暂未找到该组合的菜谱</h2>
            <p v-if="matchResult.suggestion">
              再加 <strong>{{ matchResult.suggestion.ingredientName }}</strong> 食材即可成菜
              <span>推荐尝试：{{ matchResult.suggestion.targetRecipeName }}</span>
            </p>
            <small v-if="matchResult.suggestion">{{ matchResult.suggestion.reason }}</small>
          </div>
        </article>

        <div v-else class="synthesis-result-empty">
          <span>锅</span>
          <div><b>合成结果</b><small>食材入锅后，推荐会显示在这里</small></div>
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.synthesis-intro,.synthesis-layout{width:min(100%,1320px);margin-inline:auto}
.synthesis-intro{align-items:flex-end}
.synthesis-count{display:flex;align-items:baseline;gap:8px;padding:12px 16px;border-left:3px solid #e79b55;color:#315964}
.synthesis-count strong{font:800 32px/1 'Nunito Sans','Noto Sans SC',sans-serif}
.synthesis-count span{font-size:13px;font-weight:700}
.synthesis-logic{width:min(100%,1320px);margin:0 auto 20px;display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}
.synthesis-logic article{display:flex;gap:10px;align-items:flex-start;padding:13px 14px;border:1px solid #d8e6ea;border-radius:8px;background:#f8fcfd;color:#315964}
.synthesis-logic article>strong{display:grid;place-items:center;flex:0 0 28px;height:28px;border-radius:50%;background:#dceff2;color:#286e83;font:800 11px 'Nunito Sans',sans-serif}
.synthesis-logic b,.synthesis-logic small{display:block}.synthesis-logic b{font-size:13px}.synthesis-logic small{margin-top:4px;color:#728696;font-size:11px;line-height:1.5}
.synthesis-layout{display:grid;grid-template-columns:320px minmax(0,1fr);align-items:start;gap:28px}
.ingredient-library{overflow:hidden;border:1px solid #d5dcec;border-radius:8px;background:#fff;box-shadow:0 5px 18px rgba(33,7,31,.045)}
.ingredient-library>header,.synthesis-toolbar{display:flex;align-items:center;justify-content:space-between}
.ingredient-library>header{padding:21px 20px 18px;border-bottom:1px solid #e4e8f1;background:#f7fbff}
.ingredient-library h2,.synthesis-toolbar h2,.synthesis-result-card h2,.synthesis-unmatched h2{margin:3px 0 0;color:#21071f;font-family:'Noto Sans SC','Microsoft YaHei',sans-serif;letter-spacing:0}
.ingredient-library h2,.synthesis-toolbar h2{font-size:20px}
.ingredient-library>header>span{display:grid;place-items:center;min-width:48px;height:32px;border-radius:999px;background:#e2f3f8;color:#286e87;font:700 12px 'Nunito Sans','Noto Sans SC',sans-serif}
.ingredient-list{max-height:650px;overflow-y:auto;padding:10px}
.ingredient-option{position:relative;width:100%;min-height:76px;display:grid;grid-template-columns:44px minmax(0,1fr) auto;grid-template-rows:auto auto;align-items:center;gap:2px 11px;padding:11px 10px;border:1px solid transparent;border-bottom-color:#e9edf4;background:#fff;color:#263f58;text-align:left;transition:background .16s,border-color .16s,transform .16s}
.ingredient-option:last-child{border-bottom-color:transparent}.ingredient-option:hover,.ingredient-option:focus-visible{z-index:1;border-color:#9ecada;border-radius:7px;background:#f1fbfe;transform:translateX(2px)}
.ingredient-option.selected{border-color:#acd7e5;border-radius:7px;background:#edfaff}.ingredient-option.insufficient{border-color:#e3a5aa;background:#fff6f6}
.ingredient-option>i{grid-row:1/3;display:grid;place-items:center;width:44px;height:44px;border-radius:8px;background:#eef4fb;font-size:23px;font-style:normal}
.ingredient-option>span{min-width:0}.ingredient-option b,.ingredient-option small{display:block}.ingredient-option b{overflow:hidden;color:#203a5a;font-size:14px;text-overflow:ellipsis;white-space:nowrap}.ingredient-option small{margin-top:3px;color:#74869a;font-size:12px;line-height:1.35}
.ingredient-option>em{align-self:start;color:#536d82;font-size:12px;font-style:normal;font-weight:700;white-space:nowrap}.ingredient-option>strong{position:absolute;top:42px;right:10px;color:#247591;font:800 12px 'Nunito Sans',sans-serif}
.ingredient-option>.ingredient-stock-warning{grid-column:2/4;margin-top:5px;color:#b43f4d;font-size:12px;font-weight:700}
.synthesis-workbench{min-width:0}
.synthesis-toolbar{min-height:68px;margin-bottom:12px;padding:0 4px}
.clear-synthesis{min-height:44px;padding:0 16px;border:1px solid #bac6d6;border-radius:999px;background:#fff;color:#4d5d71;font-size:14px;font-weight:700;transition:border-color .16s,background .16s,color .16s}
.clear-synthesis:hover:not(:disabled){border-color:#315964;background:#edf7fa;color:#244d58}.clear-synthesis:disabled{cursor:not-allowed;opacity:.4}
.pot-stage{--pot-edge:#315964;position:relative;min-height:408px;display:grid;place-items:center;overflow:hidden;border:1px solid #c9dce8;border-radius:8px;background:linear-gradient(180deg,#f7fcff 0%,#e7f5f8 68%,#d7e8ed 100%);isolation:isolate;transition:border-color .16s,box-shadow .16s,background .16s}
.pot-stage:before{content:'';position:absolute;z-index:-1;left:50%;bottom:43px;width:min(72%,540px);height:28px;transform:translateX(-50%);border-radius:50%;background:rgba(38,75,87,.2);filter:blur(12px)}
.pot-stage.is-drag-over{border-color:#2e8ba3;background:linear-gradient(180deg,#effcff 0%,#d8f4f3 66%,#cbe6e3 100%);box-shadow:inset 0 0 0 3px rgba(56,151,164,.17),0 12px 26px rgba(39,111,129,.12)}
.pot-assembly{position:absolute;left:50%;top:106px;width:min(78%,610px);height:250px;transform:translateX(-50%);transform-origin:50% 78%;pointer-events:none}
.pot-rim{position:absolute;z-index:3;left:50%;top:25px;width:78%;height:92px;transform:translateX(-50%);border:10px solid var(--pot-edge);border-radius:50%;background:#c6e5ec;box-shadow:inset 0 9px 0 rgba(255,255,255,.72),0 8px 0 #9dbbc3}
.pot-rim:after{content:'';position:absolute;inset:17px 28px;border-radius:50%;background:#e9f8f7;box-shadow:inset 0 9px 16px rgba(38,91,102,.13)}
.pot-body{position:absolute;z-index:2;left:50%;top:70px;width:68%;height:148px;transform:translateX(-50%);border:8px solid var(--pot-edge);border-top:0;border-radius:0 0 45% 45% / 0 0 38% 38%;background:linear-gradient(100deg,#7bb1be 0%,#d8eef2 20%,#9bc9d2 58%,#6e9fae 100%);box-shadow:inset 13px 0 0 rgba(255,255,255,.22),inset -14px -8px 0 rgba(29,79,91,.12),0 10px 0 #254d58}
.pot-body:after{content:'';position:absolute;left:50%;bottom:17px;width:47%;height:8px;transform:translateX(-50%);border-radius:999px;background:rgba(49,89,100,.28)}
.pot-handle{position:absolute;z-index:1;top:82px;width:21%;height:49px;border:8px solid var(--pot-edge);background:#7faab4}.pot-handle-left{left:2%;border-radius:22px 0 0 22px}.pot-handle-right{right:2%;border-radius:0 22px 22px 0}
.pot-heat{position:absolute;left:50%;bottom:0;display:flex;gap:13px;transform:translateX(-50%)}.pot-heat i{display:block;width:14px;height:26px;border-radius:70% 30% 68% 32%;background:#ef9a52;transform:rotate(10deg)}.pot-heat i:nth-child(2){height:34px;background:#d96f54;transform:translateY(-6px) rotate(-6deg)}
.pot-steam{position:absolute;z-index:1;left:50%;top:36px;width:150px;display:flex;justify-content:center;gap:38px;transform:translateX(-50%)}.pot-steam i{display:block;width:13px;height:55px;border-left:4px solid rgba(71,139,155,.35);border-radius:50%;transform:rotate(12deg)}.pot-steam i:nth-child(2){height:67px;transform:translateY(-8px) rotate(-9deg)}
.pot-content{position:relative;z-index:5;width:min(52%,390px);min-height:132px;margin-top:-86px;display:grid;place-items:center}
.pot-ingredients{width:100%;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}
.pot-ingredient{position:relative;min-width:0;min-height:58px;display:grid;grid-template-columns:30px minmax(0,1fr) 28px;align-items:center;gap:7px;padding:8px;border:1px solid rgba(49,89,100,.18);border-radius:7px;background:rgba(255,255,255,.9);box-shadow:0 5px 10px rgba(42,94,105,.1);color:#254d58}.pot-ingredient.insufficient{border-color:#d9828c;background:#fff1f2}.pot-ingredient.unknown{border-color:#d5b47a;background:#fffaf0}
.pot-ingredient>span{font-size:20px}.pot-ingredient>div{min-width:0}.pot-ingredient b,.pot-ingredient small{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.pot-ingredient b{font-size:13px}.pot-ingredient small{margin-top:2px;color:#63808a;font-size:12px}.pot-ingredient>button{width:28px;height:28px;border-radius:50%;background:#e1eff2;color:#315964;font-size:18px;line-height:1}.pot-ingredient>button:hover{background:#c8e3e8}.pot-ingredient>em{grid-column:1/4;color:#b43f4d;font-size:12px;font-style:normal;font-weight:700;text-align:center}
.pot-empty{display:grid;place-items:center;color:#47717d}.pot-empty>span{display:grid;place-items:center;width:48px;height:48px;border:1px dashed #6aa5b3;border-radius:50%;background:rgba(255,255,255,.55);font-size:27px}.pot-empty b{margin-top:8px;font-size:14px}
.synthesis-notice{position:absolute;z-index:7;left:50%;bottom:17px;margin:0;padding:8px 12px;border-radius:999px;background:#315964;color:#fff;font-size:12px;font-weight:700;transform:translateX(-50%);white-space:nowrap}
.pot-stage.is-bouncing .pot-assembly,.pot-stage.is-bouncing .pot-content{animation:pot-drop-bounce .23s ease-out}
@keyframes pot-drop-bounce{0%{scale:1 1}42%{scale:1.025 .965;translate:0 7px}75%{scale:.99 1.018;translate:0 -3px}100%{scale:1 1;translate:0 0}}
.synthesis-result-slot{min-height:306px;margin-top:18px}
.synthesis-loading,.synthesis-error,.synthesis-result-empty,.synthesis-unmatched{min-height:190px;display:flex;align-items:center;justify-content:center;gap:16px;padding:24px;border:1px solid #d7e0ea;border-radius:8px;background:#fff;color:#445b6e;text-align:left}
.matching-spinner{width:38px;height:38px;border:4px solid #d6e8ee;border-top-color:#348ba2;border-radius:50%;animation:matching-spin .8s linear infinite}.synthesis-loading b,.synthesis-loading small,.synthesis-error b,.synthesis-error small,.synthesis-result-empty b,.synthesis-result-empty small{display:block}.synthesis-loading b,.synthesis-error b,.synthesis-result-empty b{font-size:15px}.synthesis-loading small,.synthesis-error small,.synthesis-result-empty small{margin-top:4px;color:#768798;font-size:12px}
@keyframes matching-spin{to{transform:rotate(360deg)}}
.synthesis-error>span,.unmatched-mark,.synthesis-result-empty>span{display:grid;place-items:center;flex:0 0 auto;width:46px;height:46px;border-radius:50%;font:800 22px 'Nunito Sans',sans-serif}.synthesis-error>span{background:#fff0f1;color:#b43f4d}.synthesis-error button{min-height:40px;margin-left:auto;padding:0 14px;border:1px solid #b8c6d4;border-radius:999px;font-size:13px;font-weight:700}
.synthesis-result-empty>span{background:#e7f4f7;color:#32778c;font-family:'Noto Sans SC',sans-serif;font-size:15px}
.synthesis-result-card{overflow:hidden;border:1px solid #cad9e5;border-radius:8px;background:#fff;box-shadow:0 8px 22px rgba(44,75,102,.07)}
.synthesis-result-card>header{display:grid;grid-template-columns:64px minmax(0,1fr) auto;align-items:center;gap:16px;padding:21px 23px;border-bottom:1px solid #dce5ed;background:#f4faff}.result-art{display:grid;place-items:center;width:64px;height:64px;border-radius:8px;background:#fff;font-size:34px;box-shadow:inset 0 0 0 1px #dbe6ed}.synthesis-result-card h2{margin:3px 0 5px;font-size:22px}.synthesis-result-card header p:not(.eyebrow){margin:0;color:#607183;font-size:13px;line-height:1.55}.result-time{min-width:70px;padding-left:16px;border-left:1px solid #cbd8e3;text-align:center}.result-time strong,.result-time span{display:block}.result-time strong{color:#315964;font:800 27px 'Nunito Sans',sans-serif}.result-time span{color:#728191;font-size:12px}
.result-detail-grid{display:grid;grid-template-columns:minmax(230px,.8fr) minmax(280px,1.2fr);gap:24px;padding:22px 23px}.result-detail-grid h3{margin:0 0 13px;color:#263f58;font-size:14px}.result-ingredients>div{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:2px 10px;padding:8px 0;border-bottom:1px solid #edf0f4;font-size:13px}.result-ingredients>div span{font-weight:700}.result-ingredients>div b{font-size:13px}.result-ingredients>div small{grid-column:1/3;color:#48806c;font-size:12px}.result-ingredients>div.missing small,.result-ingredients>div.insufficient small{color:#b43f4d}.result-ingredients>div.unknown small{color:#9b6b1c}
.result-method ol{display:grid;gap:10px;margin:0;padding:0;list-style:none}.result-method li{display:grid;grid-template-columns:24px minmax(0,1fr);align-items:start;gap:9px;color:#556779;font-size:13px;line-height:1.65}.result-method li span{display:grid;place-items:center;width:24px;height:24px;border-radius:50%;background:#e4f1f4;color:#286e83;font:800 12px 'Nunito Sans',sans-serif}
.result-method li div b,.result-method li div small{display:block}.result-method li div b{color:#263f58;font-size:13px}.result-method li div small{margin-top:3px;color:#74869a;font-size:11px}.result-utensil-title{margin-top:18px!important}.result-utensils{display:flex;flex-wrap:wrap;gap:6px}.result-utensils span{padding:5px 9px;border-radius:999px;background:#edf6fb;color:#315964;font-size:11px;font-weight:700}
.synthesis-result-card>footer{min-height:68px;display:flex;align-items:center;justify-content:space-between;gap:18px;padding:12px 23px;border-top:1px solid #e3e8ef}.synthesis-result-card>footer>span{color:#39755f;font-size:13px;font-weight:700}.synthesis-result-card>footer>span.warning{color:#b4535d}.start-cooking{min-height:44px;padding:0 22px;border-radius:999px;background:#21071f;color:#fff;font-size:14px;font-weight:700}.start-cooking:hover:not(:disabled){background:#46203e}.start-cooking:disabled{cursor:not-allowed;opacity:.45}
.synthesis-unmatched{justify-content:flex-start;min-height:210px;background:#fff}.unmatched-mark{background:#fff1df;color:#bd6d2e}.synthesis-unmatched h2{margin:4px 0 10px;font-size:20px}.synthesis-unmatched p:not(.eyebrow){margin:0;color:#526779;font-size:14px;line-height:1.7}.synthesis-unmatched p strong{color:#b45534;font-size:16px}.synthesis-unmatched p span{display:block;margin-top:3px;color:#315964;font-weight:700}.synthesis-unmatched small{display:block;margin-top:9px;color:#798897;font-size:12px;line-height:1.55}
@media (max-width:1100px){.synthesis-logic{grid-template-columns:1fr}.synthesis-layout{grid-template-columns:280px minmax(0,1fr);gap:20px}.pot-stage{min-height:390px}.pot-content{width:min(61%,360px)}.result-detail-grid{grid-template-columns:1fr}.ingredient-option{grid-template-columns:40px minmax(0,1fr)}.ingredient-option>em{grid-column:2}.ingredient-option>strong{top:14px}.ingredient-option>.ingredient-stock-warning{grid-column:2}}
@media (max-width:760px){.synthesis-intro{align-items:flex-start}.synthesis-count{align-self:stretch;justify-content:flex-start;padding:9px 13px}.synthesis-layout{grid-template-columns:1fr;gap:22px}.ingredient-list{max-height:none;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}.ingredient-option{min-height:88px;border:1px solid #e0e7ef;border-radius:7px}.ingredient-option:hover,.ingredient-option:focus-visible{transform:none}.synthesis-toolbar{min-height:58px}.pot-stage{min-height:390px}.pot-assembly{top:108px;width:100%;height:245px}.pot-rim{width:78%;height:86px}.pot-body{width:68%;height:142px}.pot-content{width:62%;margin-top:-80px}.pot-ingredients{grid-template-columns:1fr;gap:6px}.pot-ingredient{min-height:51px;padding:6px 8px}.synthesis-result-slot{min-height:260px}.synthesis-result-card>header{grid-template-columns:54px minmax(0,1fr);padding:18px}.result-art{width:54px;height:54px}.result-time{grid-column:2;display:flex;align-items:baseline;gap:5px;padding:0;border:0;text-align:left}.result-time strong,.result-time span{display:inline}.result-detail-grid{padding:18px}.synthesis-result-card>footer{align-items:flex-start;flex-direction:column;padding:15px 18px}.start-cooking{width:100%}.synthesis-loading,.synthesis-error,.synthesis-result-empty,.synthesis-unmatched{padding:20px}.synthesis-error{align-items:flex-start;flex-wrap:wrap}.synthesis-error button{width:100%;margin:0}.synthesis-unmatched{align-items:flex-start}}
@media (max-width:420px){.ingredient-list{grid-template-columns:1fr}.pot-stage{min-height:380px}.pot-content{width:66%}.pot-assembly{top:104px}.pot-rim{width:84%}.pot-body{width:73%}.pot-handle{width:18%}.synthesis-notice{max-width:calc(100% - 24px);white-space:normal;text-align:center}}
@media (prefers-reduced-motion:reduce){.pot-stage,.ingredient-option,.clear-synthesis{transition:none}.pot-stage.is-bouncing .pot-assembly,.pot-stage.is-bouncing .pot-content,.matching-spinner{animation:none}}
</style>
