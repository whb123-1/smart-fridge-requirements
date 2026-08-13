<template>
  <div class="fridge3d">
    <div ref="containerRef" class="fridge3d-canvas"></div>
    <div class="fridge3d-tip">{{ hoverTip }}</div>
    <div v-if="!modelLoaded && !loadFailed" class="fridge3d-loading">冰箱模型加载中...</div>
    <div v-if="loadFailed" class="fridge3d-loading error">模型加载失败，仍可使用分区功能</div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/addons/controls/OrbitControls.js'
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js'
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js'

export interface ZoneInfo {
  id: number
  name: string
  zoneType: string
}

export interface ZoneAlert {
  zoneId: number
  zoneType: string
  zoneName: string
  expireSoon: number
  expired: number
  lowStock: number
  abnormal: boolean
}

const props = defineProps<{
  zones: ZoneInfo[]
  selectedType: string | null
  alerts: ZoneAlert[]
}>()

const emit = defineEmits<{
  (e: 'select', zoneType: string): void
}>()

const containerRef = ref<HTMLDivElement | null>(null)
const hoverTip = ref('拖拽旋转视角，点击分区查看库存')
const modelLoaded = ref(false)
const loadFailed = ref(false)

interface HitDef {
  key: string
  x: number
  y: number
  z: number
  w: number
  h: number
  d: number
}

interface Compartment {
  zoneType: string
  zoneId: number | null
  label: string
  mesh: THREE.Mesh
  material: THREE.MeshBasicMaterial
  edge: THREE.LineSegments
  alertLevel: string | null
}

let renderer: THREE.WebGLRenderer | null = null
let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let controls: OrbitControls | null = null
let zoneGroup: THREE.Group | null = null
let floorGroup: THREE.Group | null = null
let modelBox: THREE.Box3 | null = null
let raycaster = new THREE.Raycaster()
let pointer = new THREE.Vector2()
let hoveredMesh: THREE.Object3D | null = null
let frameId = 0
let resizeObserver: ResizeObserver | null = null
let disposed = false
let downX = 0
let downY = 0
const compartments: Compartment[] = []

const zonesByType = (type: string) => props.zones.filter((z) => z.zoneType === type)

watch(() => props.selectedType, () => updateAllVisuals())
watch(() => props.zones, () => {
  if (modelBox) {
    rebuildZones()
  }
}, { deep: true })
watch(() => props.alerts, () => {
  if (modelBox) {
    rebuildZones()
  }
}, { deep: true })

// ---------- GLB 模型加载与摆放 ----------
function setupModel(gltfScene: THREE.Group) {
  const modelGroup = new THREE.Group()
  modelGroup.add(gltfScene)

  // 先按几何中心居中，再旋转使最高维度朝 Y
  const b0 = new THREE.Box3().setFromObject(modelGroup)
  const c0 = new THREE.Vector3()
  b0.getCenter(c0)
  modelGroup.position.set(-c0.x, -c0.y, -c0.z)
  const size0 = new THREE.Vector3()
  b0.getSize(size0)
  if (size0.z > size0.y && size0.z >= size0.x) {
    modelGroup.rotation.x = -Math.PI / 2
  } else if (size0.x > size0.y) {
    modelGroup.rotation.z = Math.PI / 2
  }
  // 模型正面朝 -z，翻转到面向默认视角 (+z)
  modelGroup.rotation.y = Math.PI

  // 等比缩放到统一高度 180
  const b1 = new THREE.Box3().setFromObject(modelGroup)
  const size1 = new THREE.Vector3()
  b1.getSize(size1)
  modelGroup.scale.setScalar(180 / Math.max(size1.y, 0.001))

  // 落到地面并居中
  const b2 = new THREE.Box3().setFromObject(modelGroup)
  const min2 = b2.min
  const size2 = new THREE.Vector3()
  b2.getSize(size2)
  modelGroup.position.x = -(min2.x + size2.x / 2)
  modelGroup.position.y = -min2.y
  modelGroup.position.z = -(min2.z + size2.z / 2)

  // 修正材质：降低金属度、提高粗糙度、强制白色机身，避免模型发黑
  gltfScene.traverse((obj) => {
    const mesh = obj as THREE.Mesh
    if (!mesh.isMesh || !mesh.material) {
      return
    }
    const mats = Array.isArray(mesh.material) ? mesh.material : [mesh.material]
    for (const m of mats) {
      const mat = m as THREE.MeshStandardMaterial
      if (mat.isMeshStandardMaterial) {
        mat.metalness = Math.min(mat.metalness, 0.15)
        mat.roughness = Math.max(mat.roughness, 0.55)
        mat.side = THREE.DoubleSide
        // 没有贴图时强制为亮白色，保证冰箱轮廓清晰
        if (!mat.map && !mat.vertexColors) {
          mat.color.setHex(0xf5f7f9)
        }
        mat.needsUpdate = true
      } else {
        (m as THREE.Material).side = THREE.DoubleSide
      }
    }
    mesh.castShadow = true
    mesh.receiveShadow = true
  })

  scene?.add(modelGroup)
  modelBox = new THREE.Box3().setFromObject(modelGroup)
  modelLoaded.value = true
  buildZones(modelBox)
}

function loadModel() {
  const loader = new GLTFLoader()
  loader.load(
    '/haier_refrigerator.glb',
    (gltf) => setupModel(gltf.scene),
    undefined,
    () => {
      loadFailed.value = true
      hoverTip.value = '冰箱模型加载失败，请检查 public/haier_refrigerator.glb'
      // 用默认尺寸兜底，分区功能仍可用
      modelBox = new THREE.Box3(
        new THREE.Vector3(-45, 0, -30),
        new THREE.Vector3(45, 180, 30),
      )
      buildZones(modelBox)
    },
  )
}

function loadFloor() {
  const loader = new GLTFLoader()
  loader.load(
    '/Wood%20Floor%20by%20Mark%20Steelman%20-%204qpvnIQNcl5.glb',
    (gltf) => setupFloor(gltf.scene),
    undefined,
    () => {
      // 地板加载失败时忽略，不影响主功能
    },
  )
}

function setupFloor(gltfScene: THREE.Group) {
  const group = new THREE.Group()
  group.add(gltfScene)
  // 模型单位为米，与冰箱统一放大 100 倍
  group.scale.setScalar(100)
  const box = new THREE.Box3().setFromObject(group)
  const size = new THREE.Vector3()
  box.getSize(size)
  const center = new THREE.Vector3()
  box.getCenter(center)
  // 地板顶面略低于 y=0，避免与冰箱底面重叠闪烁
  group.position.set(-center.x, -box.max.y - 0.5, -center.z)
  gltfScene.traverse((obj) => {
    const mesh = obj as THREE.Mesh
    if (mesh.isMesh && mesh.material) {
      const mat = mesh.material as THREE.MeshStandardMaterial
      mat.side = THREE.DoubleSide
      // 地板颜色适度加深，保留木纹质感
      mat.color.multiplyScalar(0.82)
      mat.needsUpdate = true
      mesh.receiveShadow = true
      mesh.castShadow = false
    }
  })
  scene?.add(group)
  floorGroup = group
}

// ---------- 分区感应区 ----------
function buildZones(box: THREE.Box3) {
  clearZones()
  zoneGroup = new THREE.Group()
  scene?.add(zoneGroup)

  const size = new THREE.Vector3()
  box.getSize(size)
  const min = box.min
  const w = size.x
  const h = size.y
  const d = size.z
  const cx = min.x + w / 2
  const cz = min.z + d / 2

  // 十字均分为四个象限（正面视角）：左上/右上/左下/右下
  const quadrants: Array<{ key: string; fx: number; fy: number }> = [
    { key: '常温区', fx: 0.25, fy: 0.75 },
    { key: '保鲜区', fx: 0.75, fy: 0.75 },
    { key: '冷藏区', fx: 0.25, fy: 0.25 },
    { key: '冷冻区', fx: 0.75, fy: 0.25 },
  ]
  for (const q of quadrants) {
    const group = zonesByType(q.key)
    const def: HitDef = {
      key: q.key,
      x: min.x + w * q.fx,
      y: min.y + h * q.fy,
      z: cz,
      w: w * 0.47,
      h: h * 0.47,
      d: d * 0.98,
    }
    addHitbox(def, group[0] ?? null, group.length, q.key)
  }

  // 其余类型（变温区/自定义）如已配置，放在右侧外挂柜
  const extras = ['变温区', '自定义']
  let extraIndex = 0
  for (const type of extras) {
    const group = zonesByType(type)
    if (!group.length) {
      continue
    }
    addHitbox(
      {
        key: type,
        x: cx + w / 2 + w * 0.28,
        y: min.y + h * (0.78 - extraIndex * 0.3),
        z: cz,
        w: w * 0.5,
        h: h * 0.26,
        d: d * 0.55,
      },
      group[0],
      group.length,
      group[0].name,
    )
    extraIndex++
  }

  updateAllVisuals()
}

function addHitbox(def: HitDef, zone: ZoneInfo | null, count: number, labelBase: string) {
  if (!zoneGroup) {
    return
  }
  const active = zone != null
  const material = new THREE.MeshBasicMaterial({
    color: active ? 0x59a7f0 : 0x9aa7b5,
    transparent: true,
    opacity: 0,
    depthWrite: false,
    side: THREE.DoubleSide,
  })
  const geometry = new THREE.BoxGeometry(def.w, def.h, def.d)
  const mesh = new THREE.Mesh(geometry, material)
  mesh.position.set(def.x, def.y, def.z)
  mesh.userData = { zoneType: def.key, zoneId: active ? zone.id : null }
  zoneGroup.add(mesh)

  const edge = new THREE.LineSegments(
    new THREE.EdgesGeometry(geometry),
    new THREE.LineBasicMaterial({
      color: active ? 0x2f7fd6 : 0xb8c2cc,
      transparent: true,
      opacity: active ? 0.85 : 0.2,
    }),
  )
  edge.position.copy(mesh.position)
  zoneGroup.add(edge)

  const labelText = active
    ? (count > 1 ? `${labelBase} ×${count}` : labelBase)
    : `${def.key}（未配置）`
  const label = makeLabel(labelText, active)
  label.position.set(def.x, def.y, def.z + def.d / 2 + 8)
  zoneGroup.add(label)

  const alert = alertFor(def.key)
  if (alert) {
    const [bg, fg] = alertStyle(alert.level)
    const marker = makeAlertSprite(alertText(alert.level), bg, fg)
    marker.position.set(def.x + def.w * 0.3, def.y + def.h * 0.3, def.z + def.d / 2 + 16)
    zoneGroup.add(marker)
  }

  compartments.push({
    zoneType: def.key,
    zoneId: active ? zone.id : null,
    label: labelText,
    mesh,
    material,
    edge,
    alertLevel: alert ? alert.level : null,
  })
}

function alertFor(zoneType: string) {
  const typeZones = props.zones.filter((z) => z.zoneType === zoneType)
  const list = props.alerts.filter((a) => typeZones.some((z) => z.id === a.zoneId))
  if (!list.length) {
    return null
  }
  let expired = 0
  let soon = 0
  let low = 0
  let abnormal = false
  for (const a of list) {
    expired += a.expired
    soon += a.expireSoon
    low += a.lowStock
    abnormal = abnormal || a.abnormal
  }
  const level = expired > 0 ? 'expired' : soon > 0 ? 'soon' : low > 0 ? 'low'
    : abnormal ? 'abnormal' : null
  return level ? { level, expired, soon, low } : null
}

function alertStyle(level: string): [string, string] {
  if (level === 'expired') return ['#e5484d', '#ffffff']
  if (level === 'soon') return ['#f7c948', '#5a3d00']
  if (level === 'low') return ['#ff8f1f', '#ffffff']
  return ['#9b6dff', '#ffffff']
}

function alertText(level: string) {
  if (level === 'expired') return '!!!'
  if (level === 'soon') return '?!'
  if (level === 'low') return '↓'
  return '△'
}

function makeAlertSprite(text: string, bg: string, fg: string) {
  const canvas = document.createElement('canvas')
  canvas.width = 256
  canvas.height = 128
  const ctx = canvas.getContext('2d')
  if (ctx) {
    ctx.fillStyle = bg
    ctx.fillRect(8, 8, 240, 112)
    ctx.fillStyle = fg
    ctx.font = 'bold 72px "Microsoft YaHei", "PingFang SC", sans-serif'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(text, 128, 68)
  }
  const texture = new THREE.CanvasTexture(canvas)
  const sprite = new THREE.Sprite(new THREE.SpriteMaterial({
    map: texture, transparent: true, depthTest: false,
  }))
  sprite.scale.set(16, 8, 1)
  return sprite
}

function makeLabel(text: string, active: boolean) {
  const canvas = document.createElement('canvas')
  canvas.width = 512
  canvas.height = 96
  const ctx = canvas.getContext('2d')
  if (ctx) {
    ctx.fillStyle = active ? 'rgba(255,255,255,0.92)' : 'rgba(240,244,248,0.78)'
    ctx.fillRect(0, 0, 512, 96)
    ctx.strokeStyle = active ? '#7fa8cf' : '#b9c4cf'
    ctx.lineWidth = 6
    ctx.strokeRect(3, 3, 506, 90)
    ctx.fillStyle = active ? '#24537f' : '#8a97a5'
    ctx.font = 'bold 52px "Microsoft YaHei", "PingFang SC", sans-serif'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(text, 256, 52)
  }
  const texture = new THREE.CanvasTexture(canvas)
  const sprite = new THREE.Sprite(new THREE.SpriteMaterial({
    map: texture, transparent: true, depthTest: false,
  }))
  sprite.scale.set(36, 6.8, 1)
  return sprite
}

function updateAllVisuals() {
  for (const c of compartments) {
    const selected = c.zoneId !== null && c.zoneType === props.selectedType
    const hovered = hoveredMesh === c.mesh
    c.material.opacity = selected ? 0.32 : hovered ? 0.2 : 0
    c.material.color.setHex(selected ? 0xffb74d : hovered ? 0x7fc0ff : c.zoneId != null ? 0x59a7f0 : 0x9aa7b5)
    const edgeMat = c.edge.material as THREE.LineBasicMaterial
    let edgeColor = c.zoneId != null ? 0x2f7fd6 : 0xb8c2cc
    let edgeOpacity = c.zoneId != null ? 0.85 : 0.2
    if (c.alertLevel) {
      edgeColor = c.alertLevel === 'expired' ? 0xe5484d
        : c.alertLevel === 'soon' ? 0xf0c33c
          : c.alertLevel === 'low' ? 0xff8f1f : 0x9b6dff
      edgeOpacity = 1
    }
    if (selected) {
      edgeColor = 0xff9f2e
      edgeOpacity = 1
    }
    edgeMat.color.setHex(edgeColor)
    edgeMat.opacity = edgeOpacity
    c.material.needsUpdate = true
  }
}

function rebuildZones() {
  if (modelBox) {
    buildZones(modelBox)
  }
}

function clearZones() {
  compartments.length = 0
  hoveredMesh = null
  if (zoneGroup && scene) {
    scene.remove(zoneGroup)
    zoneGroup.traverse((obj) => {
      const anyObj = obj as any
      if (obj instanceof THREE.Mesh || obj instanceof THREE.LineSegments) {
        obj.geometry.dispose()
        const mat = obj.material as THREE.Material
        if ((mat as any).map) {
          ;(mat as any).map.dispose()
        }
        mat.dispose()
      } else if (obj instanceof THREE.Sprite) {
        const sm = obj.material as THREE.SpriteMaterial
        if (sm.map) {
          sm.map.dispose()
        }
        sm.dispose()
      }
      delete anyObj.geometry
      delete anyObj.material
    })
    zoneGroup = null
  }
}

function computePointer(e: PointerEvent) {
  if (!renderer || !camera) {
    return
  }
  const rect = renderer.domElement.getBoundingClientRect()
  pointer.x = ((e.clientX - rect.left) / rect.width) * 2 - 1
  pointer.y = -((e.clientY - rect.top) / rect.height) * 2 + 1
  raycaster.setFromCamera(pointer, camera)
}

function onPointerDown(e: PointerEvent) {
  downX = e.clientX
  downY = e.clientY
}

function onPointerMove(e: PointerEvent) {
  if (!renderer || !camera) {
    return
  }
  computePointer(e)
  const hits = raycaster.intersectObjects(compartments.map((c) => c.mesh))
  const target = hits.length ? hits[0].object : null
  if (target !== hoveredMesh) {
    hoveredMesh = target
    updateAllVisuals()
    if (target) {
      const c = compartments.find((x) => x.mesh === target)
      hoverTip.value = c && c.zoneId != null
        ? `点击「${c.label}」查看库存`
        : '该分区尚未配置，点击可在面板中添加'
      renderer.domElement.style.cursor = 'pointer'
    } else {
      hoverTip.value = '拖拽旋转视角，点击分区查看库存'
      renderer.domElement.style.cursor = 'grab'
    }
  }
}

function onPointerClick(e: PointerEvent) {
  if (Math.abs(e.clientX - downX) > 6 || Math.abs(e.clientY - downY) > 6) {
    return
  }
  if (!renderer || !camera) {
    return
  }
  computePointer(e)
  const hits = raycaster.intersectObjects(compartments.map((c) => c.mesh))
  if (hits.length) {
    const c = compartments.find((x) => x.mesh === hits[0].object)
    if (c) {
      emit('select', c.zoneType)
    }
  }
}

function resize() {
  const el = containerRef.value
  if (!el || !renderer || !camera) {
    return
  }
  const w = el.clientWidth
  const h = el.clientHeight
  if (w === 0 || h === 0) {
    return
  }
  renderer.setSize(w, h)
  camera.aspect = w / h
  camera.updateProjectionMatrix()
}

function setupResize() {
  const el = containerRef.value
  if (!el) {
    return
  }
  resizeObserver = new ResizeObserver(resize)
  resizeObserver.observe(el)
}

function animate() {
  if (disposed) {
    return
  }
  frameId = requestAnimationFrame(animate)
  controls?.update()
  if (renderer && scene && camera) {
    renderer.render(scene, camera)
  }
}

function resetView() {
  if (!controls || !camera) {
    return
  }
  controls.target.set(0, 85, 0)
  camera.position.set(160, 135, 210)
  controls.update()
}

defineExpose({ resetView })

onMounted(() => {
  const el = containerRef.value
  if (!el) {
    return
  }
  const w = el.clientWidth || 800
  const h = el.clientHeight || 520
  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.setSize(w, h)
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFShadowMap
  el.appendChild(renderer.domElement)

  scene = new THREE.Scene()
  camera = new THREE.PerspectiveCamera(45, w / h, 1, 3000)
  camera.position.set(160, 135, 210)
  camera.lookAt(0, 85, 0)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.target.set(0, 85, 0)
  controls.enableDamping = true
  controls.dampingFactor = 0.08
  controls.minDistance = 90
  controls.maxDistance = 550
  controls.maxPolarAngle = Math.PI * 0.55
  controls.update()

  // 环境反射，让白色机身有自然明暗
  const pmrem = new THREE.PMREMGenerator(renderer)
  scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.04).texture
  pmrem.dispose()

  scene.add(new THREE.AmbientLight(0xffffff, 0.85))
  scene.add(new THREE.HemisphereLight(0xffffff, 0xbcc8d4, 0.9))
  const dir1 = new THREE.DirectionalLight(0xffffff, 2.0)
  dir1.position.set(140, 260, 180)
  dir1.castShadow = true
  dir1.shadow.mapSize.set(2048, 2048)
  dir1.shadow.camera.left = -220
  dir1.shadow.camera.right = 220
  dir1.shadow.camera.top = 220
  dir1.shadow.camera.bottom = -220
  dir1.shadow.camera.near = 50
  dir1.shadow.camera.far = 700
  scene.add(dir1)
  const dir2 = new THREE.DirectionalLight(0xdfeaff, 0.7)
  dir2.position.set(-160, 120, -120)
  scene.add(dir2)

  loadModel()
  loadFloor()

  renderer.domElement.addEventListener('pointerdown', onPointerDown)
  renderer.domElement.addEventListener('pointermove', onPointerMove)
  renderer.domElement.addEventListener('click', onPointerClick)
  setupResize()
  animate()
})

onBeforeUnmount(() => {
  disposed = true
  cancelAnimationFrame(frameId)
  resizeObserver?.disconnect()
  controls?.dispose()
  if (renderer) {
    renderer.domElement.removeEventListener('pointerdown', onPointerDown)
    renderer.domElement.removeEventListener('pointermove', onPointerMove)
    renderer.domElement.removeEventListener('click', onPointerClick)
    renderer.domElement.parentElement?.removeChild(renderer.domElement)
    renderer.dispose()
    renderer = null
  }
  clearZones()
  if (floorGroup && scene) {
    scene.remove(floorGroup)
    floorGroup.traverse((obj) => {
      const anyObj = obj as any
      if (obj instanceof THREE.Mesh) {
        obj.geometry.dispose()
        const mat = obj.material as THREE.Material
        if ((mat as any).map) {
          ;(mat as any).map.dispose()
        }
        mat.dispose()
      }
      delete anyObj.geometry
      delete anyObj.material
    })
    floorGroup = null
  }
})
</script>

<style scoped>
.fridge3d {
  position: relative;
  width: 100%;
  height: 100%;
}
.fridge3d-canvas {
  width: 100%;
  height: 100%;
  cursor: grab;
}
.fridge3d-tip {
  position: absolute;
  left: 50%;
  bottom: 12px;
  transform: translateX(-50%);
  background: rgba(30, 40, 55, 0.78);
  color: #fff;
  font-size: 13px;
  padding: 6px 14px;
  border-radius: 16px;
  white-space: nowrap;
  pointer-events: none;
  z-index: 5;
}
.fridge3d-loading {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #5a7a99;
  font-size: 15px;
  pointer-events: none;
}
.fridge3d-loading.error {
  color: #c05656;
}
</style>
