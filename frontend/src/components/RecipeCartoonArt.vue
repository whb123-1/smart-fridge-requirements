<script setup>
import { computed } from 'vue'

const props = defineProps({
  name: { type: String, required: true },
  art: { type: String, default: '🍲' },
  ingredients: { type: Array, default: () => [] },
  color: { type: String, default: '#dfe9df' },
  imageUrl: { type: String, default: '' },
})

const ingredientIcons = [
  [/番茄|西红柿/, '🍅'], [/鸡蛋|蛋/, '🥚'], [/鸡|鸭/, '🍗'], [/牛|羊|猪|肉/, '🥩'],
  [/鱼|虾|蟹|海鲜/, '🐟'], [/面|粉/, '🍜'], [/米|饭|粥/, '🍚'], [/豆腐|豆/, '🫘'],
  [/西兰花|青菜|生菜|蔬菜|菠菜/, '🥦'], [/土豆|薯/, '🥔'], [/胡萝卜/, '🥕'], [/蘑菇|菌/, '🍄'],
]

const garnish = computed(() => {
  const text = [props.name, ...props.ingredients.map(item => item.name)].join(' ')
  const result = ingredientIcons.filter(([pattern]) => pattern.test(text)).map(([, icon]) => icon)
  return [...new Set(result)].slice(0, 3).concat(['🌿', '✨']).slice(0, 3)
})
</script>

<template>
  <div class="recipe-cartoon" :style="{ '--cartoon-tint': color }" role="img" :aria-label="`${name}的卡通插画`">
    <img v-if="imageUrl" class="ai-cartoon-image" :src="imageUrl" :alt="`${name}的 AI 卡通插画`" />
    <template v-else>
    <span class="cartoon-sun"></span>
    <span class="cartoon-spark spark-one">✦</span>
    <span class="cartoon-spark spark-two">✦</span>
    <span class="cartoon-garnish garnish-one">{{ garnish[0] }}</span>
    <span class="cartoon-garnish garnish-two">{{ garnish[1] }}</span>
    <span class="cartoon-plate-shadow"></span>
    <span class="cartoon-plate"><i>{{ art }}</i></span>
    <span class="cartoon-garnish garnish-three">{{ garnish[2] }}</span>
    </template>
    <small>{{ name }}</small>
  </div>
</template>

<style scoped>
.recipe-cartoon{position:absolute;inset:0;overflow:hidden;background:linear-gradient(145deg,color-mix(in srgb,var(--cartoon-tint) 72%,#fff),color-mix(in srgb,var(--cartoon-tint) 86%,#b7dbe3));isolation:isolate}
.recipe-cartoon:before{content:'';position:absolute;inset:10px;border:1px dashed rgba(35,83,94,.16);border-radius:20px 8px 20px 8px}
.ai-cartoon-image{position:absolute;inset:0;width:100%;height:100%;object-fit:cover}
.cartoon-sun{position:absolute;left:50%;top:49%;width:126px;height:126px;transform:translate(-50%,-50%);border-radius:50%;background:rgba(255,255,255,.48);box-shadow:0 0 0 14px rgba(255,255,255,.18)}
.cartoon-plate-shadow{position:absolute;left:50%;top:70%;width:116px;height:24px;transform:translate(-50%,-50%);border-radius:50%;background:rgba(29,70,79,.16);filter:blur(5px)}
.cartoon-plate{position:absolute;left:50%;top:52%;display:grid;place-items:center;width:116px;height:82px;transform:translate(-50%,-50%) rotate(-2deg);border:6px solid #fff;border-radius:50%;background:#f7fbf9;box-shadow:inset 0 0 0 3px rgba(87,145,150,.18),0 7px 0 rgba(75,116,123,.22),0 13px 20px rgba(42,82,90,.14)}
.cartoon-plate i{font-size:57px;font-style:normal;line-height:1;filter:saturate(.92) drop-shadow(0 3px 1px rgba(31,67,72,.15));transform:translateY(-2px)}
.cartoon-garnish,.cartoon-spark{position:absolute;z-index:2;filter:drop-shadow(0 3px 2px rgba(29,70,79,.15))}
.garnish-one{left:13%;top:21%;font-size:24px;transform:rotate(-12deg)}
.garnish-two{right:12%;top:25%;font-size:23px;transform:rotate(12deg)}
.garnish-three{right:16%;bottom:16%;font-size:19px;transform:rotate(-8deg)}
.cartoon-spark{color:rgba(255,255,255,.92);font-size:16px}.spark-one{left:26%;top:16%}.spark-two{right:27%;top:13%;font-size:10px}
.recipe-cartoon small{position:absolute;z-index:3;left:12px;bottom:10px;max-width:62%;overflow:hidden;color:#315963;font:700 10px 'Noto Sans SC',sans-serif;letter-spacing:.08em;text-overflow:ellipsis;white-space:nowrap}
@media (prefers-reduced-motion:no-preference){.cartoon-plate i{animation:cartoon-bob 4s ease-in-out infinite}.garnish-one{animation:cartoon-float 4.8s ease-in-out infinite}.garnish-two{animation:cartoon-float 4.8s .8s ease-in-out infinite}}
@keyframes cartoon-bob{50%{transform:translateY(-5px) rotate(2deg)}}
@keyframes cartoon-float{50%{translate:0 -5px}}
</style>
