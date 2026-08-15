<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'
import { RoundedBoxGeometry } from 'three/addons/geometries/RoundedBoxGeometry.js'
import { getFridgeSpec } from './fridgeLayouts'

const props = defineProps({
  zones: { type: Array, required: true },
  foods: { type: Array, required: true },
})

const emit = defineEmits(['zone-navigate'])

const canvas = ref(null)
const activeZone = ref(null)

let renderer
let scene
let camera
let fridge
let raycaster
let pointer
let frameId
let resizeObserver
let ground
let cameraTarget = new THREE.Vector3(0, 0.5, 0)
let dragStart
let hoveredDoor = null
let dragDistance = 0
let rotationTarget = -0.1
let rotationCurrent = -0.1
let elevationTarget = -0.025
let elevationCurrent = -0.025
let cameraBaseDistance = 15.15
let cameraZoom = 1
let minimumCameraZoom = 0.96
const maximumCameraZoom = 1.08
let doors = []
let clickable = []
let currentSpec = getFridgeSpec(props.zones.length)
const zoneLayout = computed(() => getFridgeSpec(props.zones.length).layouts)
const iconByKind = Object.freeze({ chill: '●', fresh: '◆', variable: '▲', freeze: '✦' })
const iconForKind = kind => iconByKind[kind] || '●'

const palette = {
  ink: '#203a5a',
  shell: '#c6dcf5',
  shellSide: '#9fbdde',
  cream: '#eef5fc',
  gasket: '#355679',
  chill: '#82b5d3',
  fresh: '#93bfd8',
  variable: '#779fc9',
  freeze: '#6d88b5',
}

const color = value => new THREE.Color(value)

function material(value, options = {}) {
  return new THREE.MeshStandardMaterial({ color: color(value), roughness: 0.62, metalness: 0.02, ...options })
}

function physicalMaterial(value, options = {}) {
  return new THREE.MeshPhysicalMaterial({ color: color(value), roughness: 0.5, metalness: 0, clearcoat: 0.16, clearcoatRoughness: 0.68, ...options })
}

function box(width, height, depth, meshMaterial, position, parent, options = {}) {
  const radius = Math.min(options.radius ?? 0.045, width / 2, height / 2, depth / 2)
  const geometry = radius > 0
    ? new RoundedBoxGeometry(width, height, depth, options.segments ?? 3, radius)
    : new THREE.BoxGeometry(width, height, depth)
  const mesh = new THREE.Mesh(geometry, meshMaterial)
  mesh.position.set(...position)
  mesh.castShadow = options.castShadow !== false
  mesh.receiveShadow = options.receiveShadow !== false
  parent.add(mesh)
  return mesh
}

function cylinder(radius, height, meshMaterial, position, parent, options = {}) {
  const mesh = new THREE.Mesh(new THREE.CylinderGeometry(radius, radius, height, options.segments ?? 16), meshMaterial)
  mesh.position.set(...position)
  if (options.rotation) mesh.rotation.set(...options.rotation)
  mesh.castShadow = options.castShadow !== false
  mesh.receiveShadow = options.receiveShadow !== false
  parent.add(mesh)
  return mesh
}

function zoneAccent(kind, state = 'normal') {
  if (kind === 'variable' && state === 'warning') return '#c37a68'
  return palette[kind]
}

function drawZoneIcon(context, kind, x, y, size, accent) {
  context.save()
  context.translate(x, y)
  context.strokeStyle = accent
  context.fillStyle = accent
  context.lineWidth = 8
  context.lineCap = 'round'
  context.lineJoin = 'round'

  if (kind === 'chill') {
    context.beginPath()
    context.arc(0, 0, size * 0.27, 0, Math.PI * 2)
    context.stroke()
    context.beginPath()
    context.moveTo(-size * 0.38, size * 0.27)
    context.quadraticCurveTo(0, size * 0.5, size * 0.38, size * 0.27)
    context.stroke()
  } else if (kind === 'fresh') {
    context.beginPath()
    context.moveTo(-size * 0.32, size * 0.28)
    context.quadraticCurveTo(-size * 0.35, -size * 0.28, size * 0.33, -size * 0.34)
    context.quadraticCurveTo(size * 0.35, size * 0.3, -size * 0.32, size * 0.28)
    context.fill()
    context.strokeStyle = '#fffdf5'
    context.lineWidth = 5
    context.beginPath()
    context.moveTo(-size * 0.19, size * 0.2)
    context.quadraticCurveTo(0, 0, size * 0.22, -size * 0.2)
    context.stroke()
  } else if (kind === 'variable') {
    context.beginPath()
    context.arc(0, 0, size * 0.34, Math.PI * 0.2, Math.PI * 1.65)
    context.stroke()
    context.beginPath()
    context.moveTo(size * 0.19, -size * 0.3)
    context.lineTo(size * 0.37, -size * 0.31)
    context.lineTo(size * 0.32, -size * 0.13)
    context.fill()
    context.beginPath()
    context.moveTo(-size * 0.17, 0)
    context.lineTo(size * 0.18, 0)
    context.moveTo(0, -size * 0.18)
    context.lineTo(0, size * 0.18)
    context.stroke()
  } else {
    for (let index = 0; index < 3; index += 1) {
      context.save()
      context.rotate(index * Math.PI / 3)
      context.beginPath()
      context.moveTo(-size * 0.38, 0)
      context.lineTo(size * 0.38, 0)
      context.moveTo(-size * 0.28, -size * 0.12)
      context.lineTo(-size * 0.17, 0)
      context.lineTo(-size * 0.28, size * 0.12)
      context.moveTo(size * 0.28, -size * 0.12)
      context.lineTo(size * 0.17, 0)
      context.lineTo(size * 0.28, size * 0.12)
      context.stroke()
      context.restore()
    }
  }
  context.restore()
}

function labelTexture(zone, kind, compact = false) {
  const labelCanvas = document.createElement('canvas')
  labelCanvas.width = 960
  labelCanvas.height = 420
  const context = labelCanvas.getContext('2d')
  const accent = zoneAccent(kind, zone.state)
  const paperByKind = {
    chill: '#f2fbff',
    fresh: '#edf7fd',
    variable: '#e8f2fc',
    freeze: '#e4eefb',
  }
  context.clearRect(0, 0, 960, 420)

  context.save()
  context.shadowColor = 'rgba(49, 87, 126, 0.22)'
  context.shadowBlur = 24
  context.shadowOffsetY = 14
  context.fillStyle = paperByKind[kind]
  context.beginPath()
  context.moveTo(58, 45)
  context.quadraticCurveTo(235, 26, 468, 42)
  context.quadraticCurveTo(720, 24, 902, 54)
  context.quadraticCurveTo(925, 196, 900, 364)
  context.quadraticCurveTo(696, 389, 474, 373)
  context.quadraticCurveTo(244, 391, 55, 359)
  context.quadraticCurveTo(33, 203, 58, 45)
  context.closePath()
  context.fill()
  context.restore()

  context.strokeStyle = accent
  context.globalAlpha = 0.72
  context.lineWidth = 9
  context.setLineDash([26, 13])
  context.beginPath()
  context.moveTo(75, 63)
  context.quadraticCurveTo(245, 48, 470, 60)
  context.quadraticCurveTo(715, 45, 885, 70)
  context.quadraticCurveTo(902, 202, 882, 346)
  context.quadraticCurveTo(675, 367, 475, 353)
  context.quadraticCurveTo(250, 369, 74, 342)
  context.quadraticCurveTo(55, 204, 75, 63)
  context.stroke()
  context.setLineDash([])
  context.globalAlpha = 1

  context.fillStyle = `${accent}22`
  context.beginPath()
  context.arc(167, 192, 82, 0, Math.PI * 2)
  context.fill()
  drawZoneIcon(context, kind, 167, 190, 96, accent)

  context.textAlign = 'left'
  context.textBaseline = 'middle'
  context.lineJoin = 'round'
  context.strokeStyle = '#f8fbff'
  context.lineWidth = 11
  const nameWidth = compact ? 580 : 615
  let nameSize = compact ? 104 : 114
  do {
    context.font = `900 ${nameSize}px "YouYuan", "幼圆", "Noto Sans SC", sans-serif`
    if (context.measureText(zone.name).width <= nameWidth || nameSize <= 64) break
    nameSize -= 4
  } while (nameSize > 60)
  context.strokeText(zone.name, 274, 104)
  context.fillStyle = palette.ink
  context.fillText(zone.name, 274, 104)

  context.fillStyle = accent
  const temperatureSize = compact ? 124 : 136
  context.font = `800 ${temperatureSize}px "Nunito Sans", "Noto Sans SC", sans-serif`
  context.fillText(`${zone.temp.toFixed(1)}°`, 272, 222)
  const tempWidth = context.measureText(`${zone.temp.toFixed(1)}°`).width
  context.font = '800 50px "Nunito Sans", sans-serif'
  context.fillText('C', 284 + tempWidth, 244)

  context.fillStyle = `${accent}2c`
  context.beginPath()
  context.roundRect(266, 264, 574, 104, 32)
  context.fill()
  context.fillStyle = '#294967'
  context.font = `800 ${compact ? 46 : 50}px "YouYuan", "幼圆", "Noto Sans SC", sans-serif`
  context.fillText('湿度', 314, 318)
  const humidityLabelWidth = context.measureText('湿度').width
  context.font = `900 ${compact ? 74 : 82}px "Nunito Sans", "Noto Sans SC", sans-serif`
  context.fillText(`${zone.humidity}%`, 340 + humidityLabelWidth, 318)

  context.globalAlpha = 0.12
  context.fillStyle = accent
  for (const [x, y, radius] of [[104, 105, 5], [835, 102, 7], [790, 330, 4], [214, 342, 3], [676, 84, 3]]) {
    context.beginPath()
    context.arc(x, y, radius, 0, Math.PI * 2)
    context.fill()
  }
  context.globalAlpha = 1

  const texture = new THREE.CanvasTexture(labelCanvas)
  texture.colorSpace = THREE.SRGBColorSpace
  texture.anisotropy = renderer?.capabilities.getMaxAnisotropy?.() || 1
  texture.minFilter = THREE.LinearMipmapLinearFilter
  texture.magFilter = THREE.LinearFilter
  return texture
}

function addLeaf(parent, position, size = 1, leafColor = '#4f9c62') {
  const leaf = new THREE.Mesh(
    new THREE.SphereGeometry(0.11 * size, 12, 8),
    material(leafColor, { roughness: 0.82 }),
  )
  leaf.scale.set(0.62, 1.5, 0.32)
  leaf.rotation.z = -0.42
  leaf.position.set(...position)
  leaf.castShadow = true
  parent.add(leaf)
}

function addBottle(parent, x, y, z, bodyColor = '#f6fbfd', accent = '#61aacf') {
  const bottle = new THREE.Group()
  cylinder(0.16, 0.5, physicalMaterial(bodyColor, { transparent: true, opacity: 0.9, transmission: 0.04 }), [0, 0.28, 0], bottle)
  cylinder(0.105, 0.12, material(bodyColor), [0, 0.59, 0], bottle)
  cylinder(0.115, 0.085, material(accent), [0, 0.69, 0], bottle)
  const band = cylinder(0.164, 0.14, material(accent, { roughness: 0.78 }), [0, 0.27, 0], bottle)
  band.castShadow = false
  bottle.position.set(x, y, z)
  parent.add(bottle)
}

function addJar(parent, x, y, z, bodyColor = '#f1e5b5', lidColor = '#ef856e') {
  const jar = new THREE.Group()
  cylinder(0.22, 0.38, physicalMaterial(bodyColor, { clearcoat: 0.45 }), [0, 0.19, 0], jar)
  cylinder(0.23, 0.085, material(lidColor), [0, 0.425, 0], jar)
  jar.position.set(x, y, z)
  parent.add(jar)
}

function addProduce(parent, x, y, z, produceColor = '#69b765', type = 'round') {
  const item = new THREE.Group()
  const geometry = type === 'long'
    ? new THREE.CapsuleGeometry(0.13, 0.33, 5, 10)
    : new THREE.SphereGeometry(0.22, 16, 12)
  const body = new THREE.Mesh(geometry, material(produceColor, { roughness: 0.78 }))
  if (type === 'long') body.rotation.z = Math.PI / 2
  body.castShadow = true
  item.add(body)
  addLeaf(item, [type === 'long' ? -0.27 : 0, type === 'long' ? 0.06 : 0.2, 0], 0.75)
  item.position.set(x, y, z)
  parent.add(item)
}

function addFoodTray(parent, x, y, z, meatColor = '#e99b89') {
  const tray = new THREE.Group()
  box(0.7, 0.1, 0.46, material('#d7e5e7', { roughness: 0.34, metalness: 0.08 }), [0, 0, 0], tray, { radius: 0.06 })
  for (const offset of [-0.21, 0, 0.21]) {
    const food = box(0.25, 0.11, 0.28, material(meatColor, { roughness: 0.75 }), [offset, 0.1, 0], tray, { radius: 0.06 })
    food.rotation.y = 0.08
  }
  const wrap = box(0.72, 0.035, 0.48, physicalMaterial('#dff7fb', { transparent: true, opacity: 0.28, transmission: 0.4 }), [0, 0.18, 0], tray, { radius: 0.035, castShadow: false })
  wrap.renderOrder = 2
  tray.position.set(x, y, z)
  parent.add(tray)
}

function addEggBox(parent, x, y, z) {
  const carton = new THREE.Group()
  box(0.82, 0.12, 0.48, material('#caa981', { roughness: 0.9 }), [0, 0, 0], carton, { radius: 0.06 })
  for (const row of [-0.12, 0.12]) {
    for (const column of [-0.27, 0, 0.27]) {
      const egg = new THREE.Mesh(new THREE.SphereGeometry(0.115, 12, 9), material('#fff0d8', { roughness: 0.82 }))
      egg.scale.y = 1.28
      egg.position.set(column, 0.14, row)
      egg.castShadow = true
      carton.add(egg)
    }
  }
  carton.position.set(x, y, z)
  parent.add(carton)
}

function addFrozenPack(parent, x, y, z, packColor = '#75b9dd') {
  const pack = box(0.64, 0.32, 0.14, material(packColor, { roughness: 0.52, metalness: 0.04 }), [x, y, z], parent, { radius: 0.07 })
  pack.rotation.z = -0.08
  const stripe = box(0.46, 0.055, 0.018, material('#fff9e8'), [x, y + 0.035, z + 0.078], parent, { radius: 0.018 })
  stripe.rotation.z = -0.08
}

function addInterior(zone, layout, parent) {
  const compartment = new THREE.Group()
  const centerX = layout.centerX
  const chamberWidth = layout.width - 0.12
  const chamberHeight = layout.height - 0.16
  const shelfWidth = chamberWidth - 0.16
  const wallOffset = chamberWidth / 2 - 0.04
  const interiorMaterial = material('#f7fbff', { roughness: 0.68 })
  const wallMaterial = material('#e4f0fb', { roughness: 0.7 })
  const shelfMaterial = physicalMaterial('#d4e8f8', { transparent: true, opacity: 0.78, transmission: 0.08, roughness: 0.22 })
  const accent = zoneAccent(layout.kind, zone.state)

  box(chamberWidth, chamberHeight, 0.18, wallMaterial, [centerX, layout.y, -1.19], compartment, { radius: 0.08 })
  box(0.09, chamberHeight - 0.08, 1.64, interiorMaterial, [centerX - wallOffset, layout.y, -0.42], compartment, { radius: 0.035 })
  box(0.09, chamberHeight - 0.08, 1.64, interiorMaterial, [centerX + wallOffset, layout.y, -0.42], compartment, { radius: 0.035 })
  box(chamberWidth, 0.09, 1.64, interiorMaterial, [centerX, layout.y + chamberHeight / 2 - 0.045, -0.42], compartment, { radius: 0.035 })
  box(chamberWidth, 0.09, 1.64, interiorMaterial, [centerX, layout.y - chamberHeight / 2 + 0.045, -0.42], compartment, { radius: 0.035 })

  const shelfLevels = currentSpec.interiorProfile === 'legacy'
    ? [layout.y + 0.42, layout.y - 0.42]
    : currentSpec.interiorProfile === 'wide'
      ? [layout.y + chamberHeight * 0.2, layout.y - chamberHeight * 0.2]
      : [layout.y + chamberHeight * 0.16]
  shelfLevels.forEach(level => {
    box(shelfWidth, 0.07, 1.46, shelfMaterial, [centerX, level, -0.34], compartment, { radius: 0.025 })
    box(shelfWidth, 0.07, 0.07, material(accent, { roughness: 0.55 }), [centerX, level, 0.38], compartment, { radius: 0.025 })
  })

  const light = box(0.78, 0.12, 0.055, new THREE.MeshBasicMaterial({ color: '#e6f4ff' }), [centerX, layout.y + chamberHeight / 2 - 0.16, -1.075], compartment, { radius: 0.045, castShadow: false })
  light.material.toneMapped = false
  const drawerWidth = chamberWidth - 0.24
  const drawerHeight = currentSpec.interiorProfile === 'legacy' ? 0.54 : Math.min(0.42, chamberHeight * 0.24)
  const drawerY = currentSpec.interiorProfile === 'legacy' ? layout.y - 0.91 : layout.y - chamberHeight * 0.32
  const drawer = box(drawerWidth, drawerHeight, 1.34, physicalMaterial('#cfe3f5', { transparent: true, opacity: 0.5, transmission: 0.12 }), [centerX, drawerY, -0.35], compartment, { radius: 0.1 })
  drawer.material.depthWrite = false
  box(Math.min(1.2, drawerWidth * 0.44), 0.09, 0.08, material(accent), [centerX, drawerY + drawerHeight * 0.28, 0.36], compartment, { radius: 0.04 })

  const z = 0.12
  const xScale = Math.min(1.55, chamberWidth / 2.12)
  const yScale = Math.min(1, chamberHeight / 2.46)
  const px = offset => centerX + offset * xScale
  const py = offset => layout.y + offset * yScale
  if (layout.kind === 'chill') {
    addBottle(compartment, px(-0.57), py(0.54), z, '#f6fbfd', '#69b4d2')
    addBottle(compartment, px(-0.13), py(0.54), z, '#fff6dc', '#ef8c73')
    addJar(compartment, px(0.52), py(0.45), z, '#f5e7b9', '#77b99b')
    addFoodTray(compartment, px(0.38), py(-0.21), z, '#eb9a8b')
    if (currentSpec.interiorProfile !== 'compact') addEggBox(compartment, px(-0.38), py(-0.99), z)
  }
  if (layout.kind === 'fresh') {
    addProduce(compartment, px(-0.57), py(0.61), z, '#68b760')
    addProduce(compartment, px(-0.08), py(0.59), z, '#e86457')
    addProduce(compartment, px(0.47), py(0.56), z, '#f0c950', 'long')
    addProduce(compartment, px(-0.45), py(-0.2), z, '#80bd4d', 'long')
    addProduce(compartment, px(0.34), py(-0.18), z, '#e88445')
    if (currentSpec.interiorProfile !== 'compact') addProduce(compartment, centerX, py(-1), z, '#63aa72')
  }
  if (layout.kind === 'variable') {
    addFoodTray(compartment, px(-0.42), py(0.55), z, '#e9a08c')
    addFoodTray(compartment, px(0.42), py(-0.2), z, '#df8f86')
    addJar(compartment, px(-0.46), py(-0.17), z, '#f3d68d', '#efa35f')
    if (currentSpec.interiorProfile !== 'compact') addProduce(compartment, centerX, py(-1), z, '#d6b64f')
  }
  if (layout.kind === 'freeze') {
    addFrozenPack(compartment, px(-0.46), py(0.57), z, '#72b9dc')
    addFrozenPack(compartment, px(0.36), py(0.54), z, '#789ad0')
    addFrozenPack(compartment, px(-0.18), py(-0.2), z, '#9cd2e7')
    if (currentSpec.interiorProfile !== 'compact') addFoodTray(compartment, px(0.36), py(-0.99), z, '#ef9e88')
  }

  compartment.traverse(object => {
    if (!object.isMesh) return
    object.userData.zoneId = zone.id
    object.userData.interactive = true
    clickable.push(object)
  })
  parent.add(compartment)
}

function zoneItemCount(zone) {
  return props.foods.filter(food => Number(food.zoneId) === Number(zone.id)).length
}

function zoneSnapshot(zone) {
  return { ...zone, items: zoneItemCount(zone) }
}

function addDoorBins(door, panelCenterX, layout, accent) {
  const inner = material('#edf5fc', { roughness: 0.7 })
  box(layout.width - 0.25, layout.height - 0.26, 0.16, inner, [panelCenterX, 0, -0.12], door, { radius: 0.12 })
  const binLevels = layout.height < 2.1 ? [-layout.height * 0.22, layout.height * 0.22] : [-0.6, 0.36]
  const sideOffset = Math.min(0.68, layout.width * 0.3)
  for (const y of binLevels) {
    box(layout.width - 0.53, 0.08, 0.42, material('#d4e6f5'), [panelCenterX, y, -0.35], door, { radius: 0.035 })
    box(layout.width - 0.53, 0.12, 0.06, material(accent), [panelCenterX, y + 0.16, -0.55], door, { radius: 0.025 })
    for (const offset of [-sideOffset, sideOffset]) {
      box(0.06, 0.38, 0.42, material('#d4e6f5'), [panelCenterX + offset, y + 0.08, -0.35], door, { radius: 0.025 })
    }
  }
  if (layout.kind === 'chill' || layout.kind === 'fresh') {
    addBottle(door, panelCenterX - 0.38, 0.48, -0.48, '#f8fbf3', accent)
    addJar(door, panelCenterX + 0.38, -0.51, -0.48, '#f0dfaa', accent)
  } else {
    addFrozenPack(door, panelCenterX - 0.35, 0.45, -0.48, accent)
    addFrozenPack(door, panelCenterX + 0.34, -0.52, -0.48, '#9bbbd7')
  }
}

function addDoor(zone, layout, parent) {
  const door = new THREE.Group()
  const left = layout.hingeSide !== 'right'
  const hingeX = left ? -2.39 : 2.39
  const panelCenterX = layout.panelCenterX ?? (layout.centerX - hingeX)
  const accent = zoneAccent(layout.kind, zone.state)
  door.position.set(hingeX, layout.y, 1.19)

  const gasketMaterial = material(palette.gasket, { roughness: 0.88 })
  const doorMaterial = physicalMaterial(palette.cream, { roughness: 0.48, clearcoat: 0.2 })
  const edgeMaterial = material('#8faed3', { roughness: 0.58 })
  const accentMaterial = material(accent, { roughness: 0.55 })
  box(layout.width + 0.055, layout.height + 0.055, 0.12, gasketMaterial, [panelCenterX, 0, -0.045], door, { radius: 0.13 })
  box(layout.width + 0.018, layout.height + 0.018, 0.17, edgeMaterial, [panelCenterX, 0, 0], door, { radius: 0.14 })
  const panel = box(layout.width - 0.075, layout.height - 0.075, 0.22, doorMaterial, [panelCenterX, 0, 0.08], door, { radius: 0.14, segments: 5 })
  panel.userData.zoneId = zone.id
  panel.userData.door = true
  clickable.push(panel)

  box(Math.min(1.2, layout.width * 0.32), 0.095, 0.075, accentMaterial, [panelCenterX, layout.height * 0.33, 0.225], door, { radius: 0.047 })
  const labelCenterX = panelCenterX + (layout.side === 'center' ? 0 : left ? -0.095 : 0.095)
  const labelY = layout.height * 0.075
  const compactLabel = layout.width < 3
  const labelWidth = Math.min(layout.width - 0.28, layout.width * (compactLabel ? 0.86 : 0.8))
  const labelHeight = labelWidth / (960 / 420)
  const label = new THREE.Mesh(
    new THREE.PlaneGeometry(labelWidth, labelHeight),
    new THREE.MeshBasicMaterial({ map: labelTexture(zone, layout.kind, compactLabel), transparent: true, depthWrite: false }),
  )
  const stickerAngles = { chill: -0.022, fresh: 0.018, variable: 0.025, freeze: -0.015 }
  label.position.set(labelCenterX, labelY, 0.201)
  label.rotation.z = stickerAngles[layout.kind]
  label.userData.zoneId = zone.id
  label.userData.door = true
  door.add(label)
  clickable.push(label)

  const tape = box(Math.min(0.72, labelWidth * 0.31), 0.115, 0.022, material(accent, { transparent: true, opacity: 0.38, roughness: 0.92 }), [labelCenterX, labelY + labelHeight * 0.48, 0.214], door, { radius: 0.018, castShadow: false })
  tape.rotation.z = stickerAngles[layout.kind] * -1.7

  const handleOffset = layout.width / 2 - 0.22
  const handleX = left ? panelCenterX + handleOffset : panelCenterX - handleOffset
  const handleHeight = Math.min(0.82, layout.height * 0.42)
  const handle = box(0.17, handleHeight, 0.19, accentMaterial, [handleX, -0.12, 0.25], door, { radius: 0.085, segments: 4 })
  handle.userData.zoneId = zone.id
  handle.userData.door = true
  clickable.push(handle)
  box(0.27, 0.16, 0.16, accentMaterial, [handleX, handleHeight * 0.28, 0.18], door, { radius: 0.065 })
  box(0.27, 0.16, 0.16, accentMaterial, [handleX, -handleHeight * 0.5, 0.18], door, { radius: 0.065 })

  addDoorBins(door, panelCenterX, layout, accent)

  const hingeMaterial = material('#6686ab', { roughness: 0.52, metalness: 0.12 })
  for (const y of [-layout.height * 0.35, layout.height * 0.35]) {
    cylinder(0.075, 0.26, hingeMaterial, [left ? 0.01 : -0.01, y, -0.03], door, { segments: 14 })
  }

  parent.add(door)
  doors.push({ zone, layout, pivot: door, open: false, target: 0, hover: 0 })
}

function addSmartDisplay(parent, displayY) {
  const displayBody = box(1.2, 0.48, 0.1, material('#365b81', { roughness: 0.44 }), [0, displayY, 1.36], parent, { radius: 0.16, segments: 5 })
  displayBody.material.emissive = color('#213f60')
  displayBody.material.emissiveIntensity = 0.25
  const faceMaterial = new THREE.MeshBasicMaterial({ color: '#d9e9fb' })
  for (const x of [-0.23, 0.23]) cylinder(0.045, 0.025, faceMaterial, [x, displayY + 0.05, 1.42], parent, { rotation: [Math.PI / 2, 0, 0], castShadow: false })
  const smile = new THREE.Mesh(new THREE.TorusGeometry(0.17, 0.025, 8, 22, Math.PI), faceMaterial)
  smile.rotation.z = Math.PI
  smile.position.set(0, displayY - 0.02, 1.42)
  parent.add(smile)
}

function addInternalFrames(spec, parent, frameMaterial) {
  const frameDepth = 0.22
  const frontZ = 1.08

  if (spec.count === 4) {
    box(0.12, spec.bodyHeight - 0.23, frameDepth, frameMaterial, [0, spec.bodyCenterY - 0.02, frontZ], parent, { radius: 0.05 })
    box(spec.bodyWidth - 0.23, 0.12, frameDepth, frameMaterial, [0, spec.bodyCenterY + 0.03, frontZ], parent, { radius: 0.05 })
    return
  }

  const rows = [...new Set(spec.layouts.map(layout => layout.y))].sort((a, b) => b - a)
  rows.slice(1).forEach((rowY, index) => {
    const seamY = (rows[index] + rowY) / 2
    box(spec.bodyWidth - 0.23, 0.05, frameDepth, frameMaterial, [0, seamY, frontZ], parent, { radius: 0.025 })
  })

  const hasCenterDoor = spec.layouts.some(layout => layout.side === 'center')
  if (!hasCenterDoor) {
    box(0.12, spec.bodyHeight - 0.23, frameDepth, frameMaterial, [0, spec.bodyCenterY - 0.02, frontZ], parent, { radius: 0.05 })
    return
  }

  const pairedDoors = spec.layouts.filter(layout => layout.side === 'left' || layout.side === 'right')
  if (!pairedDoors.length) return
  const upperEdge = Math.max(...pairedDoors.map(layout => layout.y + layout.height / 2 + 0.0275))
  const lowerEdge = Math.min(...pairedDoors.map(layout => layout.y - layout.height / 2 - 0.0275))
  box(0.12, upperEdge - lowerEdge, frameDepth, frameMaterial, [0, (upperEdge + lowerEdge) / 2, frontZ], parent, { radius: 0.05 })
}

function buildFridge() {
  const spec = currentSpec
  fridge = new THREE.Group()
  fridge.position.y = 0.18
  scene.add(fridge)

  const shellMaterial = physicalMaterial(palette.shell, { roughness: 0.56, clearcoat: 0.12 })
  const sideMaterial = material(palette.shellSide, { roughness: 0.63 })
  const innerEdgeMaterial = material('#5f7fa4', { roughness: 0.62 })

  box(spec.bodyWidth, spec.bodyHeight, 0.24, shellMaterial, [0, spec.bodyCenterY, -1.33], fridge, { radius: 0.11, segments: 4 })
  box(0.22, spec.bodyHeight - 0.06, 2.56, sideMaterial, [-2.4, spec.bodyCenterY, -0.12], fridge, { radius: 0.1, segments: 4 })
  box(0.22, spec.bodyHeight - 0.06, 2.56, sideMaterial, [2.4, spec.bodyCenterY, -0.12], fridge, { radius: 0.1, segments: 4 })
  addInternalFrames(spec, fridge, innerEdgeMaterial)

  for (const layout of spec.layouts) {
    const zone = props.zones.find(item => item.id === layout.id)
    if (!zone) continue
    const resolvedLayout = { ...layout, kind: zone.kind || layout.kind }
    const displayZone = zoneSnapshot(zone)
    addInterior(displayZone, resolvedLayout, fridge)
    addDoor(displayZone, resolvedLayout, fridge)
  }

  box(5.2, 0.34, 2.67, sideMaterial, [0, spec.shellBottomY, -0.1], fridge, { radius: 0.14, segments: 4 })
  box(5.12, 0.27, 2.65, shellMaterial, [0, spec.shellTopY, -0.1], fridge, { radius: 0.14, segments: 4 })
  addSmartDisplay(fridge, spec.displayY)

  const footMaterial = material('#355679', { roughness: 0.68 })
  for (const footX of [-1.78, 1.78]) box(0.72, 0.24, 1.62, footMaterial, [footX, spec.feetY, -0.1], fridge, { radius: 0.1 })

  const badge = new THREE.Mesh(
    new THREE.CircleGeometry(0.16, 28),
    new THREE.MeshBasicMaterial({ color: '#f5bf5c' }),
  )
  badge.position.set(-2.03, spec.displayY - 0.03, 1.42)
  fridge.add(badge)
  if (ground) ground.position.y = spec.groundY
}

function disposeObject(object) {
  if (!object) return
  const geometries = new Set()
  const materials = new Set()
  object.traverse(item => {
    if (item.geometry) geometries.add(item.geometry)
    const itemMaterials = Array.isArray(item.material) ? item.material : [item.material]
    itemMaterials.filter(Boolean).forEach(materialItem => materials.add(materialItem))
  })
  geometries.forEach(geometry => geometry.dispose())
  materials.forEach(materialItem => {
    Object.values(materialItem).forEach(value => {
      if (value?.isTexture) value.dispose()
    })
    materialItem.dispose()
  })
}

function disposeFridge() {
  if (!fridge) return
  scene?.remove(fridge)
  disposeObject(fridge)
  fridge = null
  doors = []
  clickable = []
  hoveredDoor = null
}

function rebuildFridge() {
  if (!scene) return
  const activeZoneId = activeZone.value?.id
  const nextSpec = getFridgeSpec(props.zones.length)
  if (nextSpec.count !== currentSpec.count) cameraZoom = 1
  currentSpec = nextSpec
  disposeFridge()
  buildFridge()
  if (activeZoneId && props.zones.some(zone => zone.id === activeZoneId)) handleZone(activeZoneId)
  else activeZone.value = null
  resize()
}

function handleZone(zoneId) {
  const door = doors.find(item => item.zone.id === zoneId)
  if (!door) return
  if (door.open) {
    closeDoors()
    return
  }
  doors.forEach(item => {
    item.open = item.zone.id === zoneId
    item.target = item.open ? (item.layout.hingeSide === 'right' ? 1.72 : -1.72) : 0
  })
  activeZone.value = door.zone
}

function closeDoors() {
  doors.forEach(item => {
    item.open = false
    item.target = 0
  })
  activeZone.value = null
}

function openZoneDetails() {
  if (activeZone.value) emit('zone-navigate', activeZone.value)
}

function hitTest(event) {
  const bounds = canvas.value.getBoundingClientRect()
  pointer.x = ((event.clientX - bounds.left) / bounds.width) * 2 - 1
  pointer.y = -((event.clientY - bounds.top) / bounds.height) * 2 + 1
  raycaster.setFromCamera(pointer, camera)
  return raycaster.intersectObjects(clickable, false)[0]
}

function findZone(event) {
  const intersection = hitTest(event)
  if (intersection?.object?.userData?.zoneId) handleZone(intersection.object.userData.zoneId)
}

function onPointerDown(event) {
  dragStart = { x: event.clientX, y: event.clientY, rotation: rotationTarget, elevation: elevationTarget }
  dragDistance = 0
  canvas.value.setPointerCapture?.(event.pointerId)
}

function onPointerMove(event) {
  if (!dragStart) {
    const intersection = hitTest(event)
    hoveredDoor = intersection?.object?.userData?.zoneId || null
    canvas.value.classList.toggle('is-over-door', Boolean(hoveredDoor))
    return
  }
  const deltaX = event.clientX - dragStart.x
  const deltaY = event.clientY - dragStart.y
  dragDistance = Math.max(dragDistance, Math.abs(deltaX), Math.abs(deltaY))
  if (dragDistance < 4) return
  rotationTarget = THREE.MathUtils.clamp(dragStart.rotation + deltaX * 0.007, -0.58, 0.38)
  elevationTarget = THREE.MathUtils.clamp(dragStart.elevation + deltaY * 0.0035, -0.13, 0.12)
}

function onPointerUp(event) {
  if (dragDistance < 4) findZone(event)
  dragStart = null
}

function onPointerLeave() {
  dragStart = null
  hoveredDoor = null
  canvas.value?.classList.remove('is-over-door')
}

function onWheel(event) {
  event.preventDefault()
  cameraZoom = THREE.MathUtils.clamp(cameraZoom + event.deltaY * 0.0002, minimumCameraZoom, maximumCameraZoom)
  updateCamera()
}

function onKeyDown(event) {
  if (['INPUT', 'SELECT', 'TEXTAREA'].includes(event.target?.tagName) || event.target?.isContentEditable) return
  if (event.key === 'Escape' && activeZone.value) closeDoors()
  const number = Number(event.key)
  if (number >= 1 && number <= props.zones.length) handleZone(number)
}

function updateCamera() {
  if (!camera) return
  const top = currentSpec.displayY + 0.28
  const bottom = currentSpec.feetY - 0.15
  const modelHeight = top - bottom
  const modelWidth = 5.3
  const verticalFov = THREE.MathUtils.degToRad(camera.fov)
  const horizontalFov = 2 * Math.atan(Math.tan(verticalFov / 2) * camera.aspect)
  const verticalDistance = modelHeight / (2 * Math.tan(verticalFov / 2)) * 1.26
  const horizontalDistance = modelWidth / (2 * Math.tan(horizontalFov / 2)) * 1.18

  cameraTarget.set(0, (top + bottom) / 2 + 0.18, 0)
  cameraBaseDistance = Math.max(verticalDistance, horizontalDistance)
  minimumCameraZoom = camera.aspect < 0.74 ? 0.96 : 0.94
  cameraZoom = THREE.MathUtils.clamp(cameraZoom, minimumCameraZoom, maximumCameraZoom)
  camera.position.set(0, cameraTarget.y + 0.03, cameraBaseDistance * cameraZoom)
  camera.lookAt(cameraTarget)
}

function resize() {
  if (!canvas.value || !renderer) return
  const { width, height } = canvas.value.getBoundingClientRect()
  if (!width || !height) return
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5))
  renderer.setSize(width, height, false)
  camera.aspect = width / height
  updateCamera()
  camera.updateProjectionMatrix()
}

function animate() {
  frameId = requestAnimationFrame(animate)
  rotationCurrent = THREE.MathUtils.lerp(rotationCurrent, rotationTarget, 0.075)
  elevationCurrent = THREE.MathUtils.lerp(elevationCurrent, elevationTarget, 0.075)
  if (fridge) {
    fridge.rotation.y = rotationCurrent
    fridge.rotation.x = elevationCurrent
  }
  doors.forEach(door => {
    door.pivot.rotation.y = THREE.MathUtils.lerp(door.pivot.rotation.y, door.target, 0.105)
    const targetLift = hoveredDoor === door.zone.id && !door.open ? 0.035 : 0
    door.hover = THREE.MathUtils.lerp(door.hover, targetLift, 0.14)
    door.pivot.position.z = 1.19 + door.hover
  })
  renderer.render(scene, camera)
}

onMounted(async () => {
  await nextTick()
  await document.fonts?.ready
  scene = new THREE.Scene()
  camera = new THREE.PerspectiveCamera(29, 1, 0.1, 100)
  camera.position.set(0, 0.45, 15.15)
  camera.lookAt(0, 0.5, 0)
  renderer = new THREE.WebGLRenderer({ canvas: canvas.value, alpha: true, antialias: true, powerPreference: 'high-performance', preserveDrawingBuffer: true })
  renderer.outputColorSpace = THREE.SRGBColorSpace
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.05
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFSoftShadowMap
  renderer.setClearColor(0x000000, 0)
  raycaster = new THREE.Raycaster()
  pointer = new THREE.Vector2()

  scene.add(new THREE.HemisphereLight('#fffefe', '#afcbea', 2.15))
  const keyLight = new THREE.DirectionalLight('#f8fbff', 3.35)
  keyLight.position.set(5.8, 8.5, 9)
  keyLight.castShadow = true
  keyLight.shadow.mapSize.set(1536, 1536)
  keyLight.shadow.camera.left = -7
  keyLight.shadow.camera.right = 7
  keyLight.shadow.camera.top = 8
  keyLight.shadow.camera.bottom = -6
  scene.add(keyLight)
  const fillLight = new THREE.DirectionalLight('#c5d7f2', 1.35)
  fillLight.position.set(-6, 2, 5)
  scene.add(fillLight)
  const rimLight = new THREE.DirectionalLight('#87afd5', 1.6)
  rimLight.position.set(-5, 5, -6)
  scene.add(rimLight)
  const interiorLight = new THREE.PointLight('#d9ecff', 2.1, 12, 1.8)
  interiorLight.position.set(0, 1.2, 4.3)
  scene.add(interiorLight)

  ground = new THREE.Mesh(new THREE.PlaneGeometry(26, 26), new THREE.ShadowMaterial({ color: '#31577e', opacity: 0.15 }))
  ground.rotation.x = -Math.PI / 2
  ground.position.y = -2.76
  ground.receiveShadow = true
  scene.add(ground)

  buildFridge()
  resize()
  resizeObserver = new ResizeObserver(resize)
  resizeObserver.observe(canvas.value)
  canvas.value.addEventListener('pointerdown', onPointerDown)
  canvas.value.addEventListener('pointermove', onPointerMove)
  canvas.value.addEventListener('pointerup', onPointerUp)
  canvas.value.addEventListener('pointerleave', onPointerLeave)
  canvas.value.addEventListener('wheel', onWheel, { passive: false })
  window.addEventListener('keydown', onKeyDown)
  animate()
})

watch(() => [props.zones, props.foods], rebuildFridge, { deep: true })

onBeforeUnmount(() => {
  cancelAnimationFrame(frameId)
  resizeObserver?.disconnect()
  canvas.value?.removeEventListener('pointerdown', onPointerDown)
  canvas.value?.removeEventListener('pointermove', onPointerMove)
  canvas.value?.removeEventListener('pointerup', onPointerUp)
  canvas.value?.removeEventListener('pointerleave', onPointerLeave)
  canvas.value?.removeEventListener('wheel', onWheel)
  window.removeEventListener('keydown', onKeyDown)
  disposeFridge()
  scene?.traverse(object => {
    object.geometry?.dispose?.()
    const materials = Array.isArray(object.material) ? object.material : [object.material]
    materials.filter(Boolean).forEach(item => {
      item.map?.dispose?.()
      item.dispose?.()
    })
  })
  renderer?.dispose()
})
</script>

<template>
  <section class="fridge-model" :class="`model-count-${zoneLayout.length}`" :aria-label="`可交互的 ${zoneLayout.length} 分区智能冰箱`">
    <div class="fridge-model-dock">
      <div class="fridge-zone-controls" :class="`zone-count-${zoneLayout.length}`" aria-label="冰箱门控制">
        <button
          v-for="layout in zoneLayout"
          :key="layout.id"
          type="button"
          :class="[`zone-${layout.kind}`, { active: activeZone?.id === layout.id }]"
          :aria-pressed="activeZone?.id === layout.id"
          @click="handleZone(layout.id)"
        >
          <i aria-hidden="true">{{ iconForKind(layout.kind) }}</i>
          <span>{{ props.zones.find(zone => zone.id === layout.id)?.name }}</span>
        </button>
      </div>
      <div class="fridge-status-slot" aria-live="polite">
        <div v-if="activeZone" class="fridge-model-status" :class="{ warning: activeZone.state === 'warning' }">
          <span></span><b>{{ activeZone.name }}</b><small>{{ zoneItemCount(activeZone) }} 件 · {{ activeZone.temp.toFixed(1) }}°C</small>
          <button type="button" class="fridge-zone-detail" @click="openZoneDetails">查看分区</button>
          <button type="button" class="fridge-door-close" aria-label="关闭冰箱门" @click="closeDoors">×</button>
        </div>
      </div>
    </div>
    <div class="fridge-model-stage">
      <canvas ref="canvas" class="fridge-model-canvas" tabindex="0" :aria-label="`可旋转、可开关门的 ${zoneLayout.length} 分区冰箱模型，按数字 1 到 ${zoneLayout.length} 可打开对应分区`" />
    </div>
  </section>
</template>
