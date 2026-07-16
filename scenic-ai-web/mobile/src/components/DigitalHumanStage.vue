<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'

type SkinKey = 'caramel' | 'mint' | 'berry' | 'cocoa'

const props = defineProps<{
  skin: SkinKey
  speaking: boolean
}>()

const hostRef = ref<HTMLDivElement | null>(null)
const ready = ref(false)
const dragActive = ref(false)

const palettes: Record<
  SkinKey,
  {
    hair: string
    skin: string
    robe: string
    sash: string
    gold: string
    boot: string
    gem: string
  }
> = {
  caramel: {
    hair: '#6f4938',
    skin: '#f4d4c6',
    robe: '#f7f1ea',
    sash: '#d96d51',
    gold: '#d6b06f',
    boot: '#9d4f3d',
    gem: '#f2c54b',
  },
  mint: {
    hair: '#54766e',
    skin: '#f3d9ce',
    robe: '#eef8f5',
    sash: '#69b49e',
    gold: '#d6c385',
    boot: '#6f817a',
    gem: '#8fd9c1',
  },
  berry: {
    hair: '#6e4255',
    skin: '#f4d4cb',
    robe: '#fff1f5',
    sash: '#d76a82',
    gold: '#dfbf74',
    boot: '#9f4d61',
    gem: '#f093b2',
  },
  cocoa: {
    hair: '#594238',
    skin: '#ebc6b4',
    robe: '#f4ebe5',
    sash: '#a36748',
    gold: '#d4b074',
    boot: '#785041',
    gem: '#c78d62',
  },
}

let renderer: THREE.WebGLRenderer | null = null
let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let avatarRoot: THREE.Group | null = null
let frameId = 0
let startTime = 0
let resizeObserver: ResizeObserver | null = null

const modelTintParts: Array<{ mesh: THREE.Mesh; role: 'hair' | 'skin' | 'robe' | 'sash' | 'gold' | 'boot' | 'gem' }> = []
const swayingParts: THREE.Object3D[] = []
const baseAvatarY = -0.62
const dragOffset = {
  x: 0,
  y: 0,
}
const dragState = {
  pointerId: -1,
  startX: 0,
  startY: 0,
  startOffsetX: 0,
  startOffsetY: 0,
}

const loader = new GLTFLoader()

function safeNameIncludes(name: string | undefined, keywords: string[]) {
  const value = name?.toLowerCase() ?? ''
  return keywords.some((keyword) => value.includes(keyword))
}

function registerModelMesh(mesh: THREE.Mesh) {
  const lower = mesh.name.toLowerCase()
  let role: 'hair' | 'skin' | 'robe' | 'sash' | 'gold' | 'boot' | 'gem' = 'robe'

  if (safeNameIncludes(lower, ['hair', 'bang', 'curl', 'bun'])) {
    role = 'hair'
  } else if (safeNameIncludes(lower, ['face', 'skin', 'head', 'hand', 'leg'])) {
    role = 'skin'
  } else if (safeNameIncludes(lower, ['sash', 'belt', 'ribbon'])) {
    role = 'sash'
  } else if (safeNameIncludes(lower, ['gold', 'trim', 'ornament', 'metal'])) {
    role = 'gold'
  } else if (safeNameIncludes(lower, ['boot', 'shoe'])) {
    role = 'boot'
  } else if (safeNameIncludes(lower, ['gem', 'crystal', 'jewel'])) {
    role = 'gem'
  }

  modelTintParts.push({ mesh, role })
}

function applyPalette() {
  const palette = palettes[props.skin]

  modelTintParts.forEach(({ mesh, role }) => {
    const material = Array.isArray(mesh.material) ? mesh.material[0] : mesh.material
    if (!(material instanceof THREE.MeshStandardMaterial) && !(material instanceof THREE.MeshPhysicalMaterial)) {
      return
    }
    if (role === 'hair') material.color.set(palette.hair)
    if (role === 'skin') material.color.set(palette.skin)
    if (role === 'robe') material.color.set(palette.robe)
    if (role === 'sash') material.color.set(palette.sash)
    if (role === 'gold') material.color.set(palette.gold)
    if (role === 'boot') material.color.set(palette.boot)
    if (role === 'gem') material.color.set(palette.gem)
  })
}

function resizeRenderer() {
  if (!hostRef.value || !renderer || !camera) {
    return
  }
  const width = hostRef.value.clientWidth
  const height = hostRef.value.clientHeight
  renderer.setSize(width, height, false)
  camera.aspect = width / Math.max(height, 1)
  camera.updateProjectionMatrix()
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max)
}

function updateDragOffset(clientX: number, clientY: number) {
  if (!hostRef.value) {
    return
  }

  const rect = hostRef.value.getBoundingClientRect()
  if (!rect.width || !rect.height) {
    return
  }

  const nextX = dragState.startOffsetX + ((clientX - dragState.startX) / rect.width) * 4.2
  const nextY = dragState.startOffsetY - ((clientY - dragState.startY) / rect.height) * 2.8

  dragOffset.x = clamp(nextX, -1.15, 1.15)
  dragOffset.y = clamp(nextY, -0.55, 0.72)
}

function handlePointerDown(event: PointerEvent) {
  if (!hostRef.value) {
    return
  }

  dragActive.value = true
  dragState.pointerId = event.pointerId
  dragState.startX = event.clientX
  dragState.startY = event.clientY
  dragState.startOffsetX = dragOffset.x
  dragState.startOffsetY = dragOffset.y
  hostRef.value.setPointerCapture(event.pointerId)
}

function handlePointerMove(event: PointerEvent) {
  if (!dragActive.value || dragState.pointerId !== event.pointerId) {
    return
  }

  updateDragOffset(event.clientX, event.clientY)
}

function handlePointerUp(event: PointerEvent) {
  if (!hostRef.value || dragState.pointerId !== event.pointerId) {
    return
  }

  dragActive.value = false
  hostRef.value.releasePointerCapture(event.pointerId)
  dragState.pointerId = -1
}

function animate(time: number) {
  if (!scene || !camera || !renderer || !avatarRoot) {
    return
  }
  if (!startTime) {
    startTime = time
  }

  const elapsed = (time - startTime) / 1000
  avatarRoot.rotation.y = -0.14 + dragOffset.x * 0.18 + Math.sin(elapsed * 0.8) * 0.08
  avatarRoot.position.x = dragOffset.x
  avatarRoot.position.y = baseAvatarY + dragOffset.y + Math.sin(elapsed * 1.2) * (props.speaking ? 0.08 : 0.04)

  swayingParts.forEach((part, index) => {
    part.rotation.z += Math.sin(elapsed * 1.4 + index * 0.7) * 0.0012
  })

  renderer.render(scene, camera)
  frameId = requestAnimationFrame(animate)
}

async function loadGlb(url: string) {
  return await loader.loadAsync(url)
}

function prepareImportedGroup(group: THREE.Group, scale: number, position: THREE.Vector3) {
  group.traverse((child: THREE.Object3D) => {
    if (child instanceof THREE.Mesh) {
      child.castShadow = true
      child.receiveShadow = true
      registerModelMesh(child)

      if (safeNameIncludes(child.name, ['ribbon', 'sash', 'skirt', 'sleeve', 'ornament'])) {
        swayingParts.push(child)
      }
    }
  })
  group.scale.setScalar(scale)
  group.position.copy(position)
}

async function attachModelAvatar() {
  if (!avatarRoot) {
    return
  }

  const avatarCandidates = ['/models/avatar.glb', '/models/head.glb']
  let modelLoaded = false

  for (const url of avatarCandidates) {
    try {
      const gltf = await loadGlb(url)
      const imported = gltf.scene
      const isHeadOnly = url.endsWith('/head.glb')
      prepareImportedGroup(
        imported,
        isHeadOnly ? 1.62 : 2.02,
        isHeadOnly ? new THREE.Vector3(0, 0.12, 0.1) : new THREE.Vector3(0, baseAvatarY, 0),
      )
      avatarRoot.clear()
      avatarRoot.add(imported)
      applyPalette()
      ready.value = true
      modelLoaded = true
      break
    } catch {
      continue
    }
  }

  if (!modelLoaded) {
    console.warn('数字人模型未找到，移动端不会再回退到旧的占位数字人。')
  }
}

async function initScene() {
  if (!hostRef.value) {
    return
  }

  scene = new THREE.Scene()
  camera = new THREE.PerspectiveCamera(24, 1, 0.1, 100)
  camera.position.set(0, 1.02, 6.7)

  renderer = new THREE.WebGLRenderer({
    antialias: true,
    alpha: true,
  })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFSoftShadowMap
  hostRef.value.appendChild(renderer.domElement)

  const ambient = new THREE.AmbientLight('#fff9f2', 2.6)
  scene.add(ambient)

  const key = new THREE.DirectionalLight('#fff8f3', 2.4)
  key.position.set(2.6, 4.4, 5.2)
  key.castShadow = true
  scene.add(key)

  const fill = new THREE.DirectionalLight('#f3c8df', 1.2)
  fill.position.set(-3.2, 2.2, 3.1)
  scene.add(fill)

  const rim = new THREE.PointLight('#ffe2b7', 1.8, 24)
  rim.position.set(0, 2.8, -2.6)
  scene.add(rim)

  const floor = new THREE.Mesh(
    new THREE.CircleGeometry(2.7, 48),
    new THREE.MeshStandardMaterial({
      color: '#f6e7db',
      transparent: true,
      opacity: 0.18,
      roughness: 0.92,
    }),
  )
  floor.position.set(0, -1.72, -0.4)
  floor.rotation.x = -Math.PI / 2
  floor.receiveShadow = true
  scene.add(floor)

  avatarRoot = new THREE.Group()
  scene.add(avatarRoot)

  resizeRenderer()

  resizeObserver = new ResizeObserver(() => {
    resizeRenderer()
  })
  resizeObserver.observe(hostRef.value)

  hostRef.value.addEventListener('pointerdown', handlePointerDown)
  hostRef.value.addEventListener('pointermove', handlePointerMove)
  hostRef.value.addEventListener('pointerup', handlePointerUp)
  hostRef.value.addEventListener('pointercancel', handlePointerUp)

  frameId = requestAnimationFrame(animate)
  await attachModelAvatar()
}

function disposeScene() {
  if (frameId) {
    cancelAnimationFrame(frameId)
  }
  resizeObserver?.disconnect()
  resizeObserver = null

  if (scene) {
    scene.traverse((child: THREE.Object3D) => {
      if (child instanceof THREE.Mesh) {
        child.geometry.dispose()
        if (Array.isArray(child.material)) {
          child.material.forEach((item: THREE.Material) => item.dispose())
        } else {
          child.material.dispose()
        }
      }
    })
  }

  renderer?.dispose()
  if (renderer?.domElement.parentNode) {
    renderer.domElement.parentNode.removeChild(renderer.domElement)
  }
  if (hostRef.value) {
    hostRef.value.removeEventListener('pointerdown', handlePointerDown)
    hostRef.value.removeEventListener('pointermove', handlePointerMove)
    hostRef.value.removeEventListener('pointerup', handlePointerUp)
    hostRef.value.removeEventListener('pointercancel', handlePointerUp)
  }

  renderer = null
  scene = null
  camera = null
  avatarRoot = null
  modelTintParts.length = 0
  swayingParts.length = 0
  ready.value = false
}

watch(
  () => props.skin,
  () => {
    applyPalette()
  },
)

onMounted(() => {
  initScene()
})

onBeforeUnmount(() => {
  disposeScene()
})
</script>

<template>
  <div ref="hostRef" class="digital-human-canvas" :class="{ ready, dragging: dragActive }" aria-hidden="true"></div>
</template>

<style scoped>
.digital-human-canvas {
  position: relative;
  width: 100%;
  height: 100%;
  background:
    radial-gradient(circle at 50% 20%, rgba(255, 255, 255, 0.12), rgba(255, 255, 255, 0) 28%),
    radial-gradient(circle at 50% 84%, rgba(236, 192, 146, 0.18), rgba(236, 192, 146, 0) 34%);
  opacity: 0.01;
  transition: opacity 220ms ease;
  cursor: grab;
  touch-action: none;
}

.digital-human-canvas.ready {
  opacity: 1;
}

.digital-human-canvas.ready:active,
.digital-human-canvas.ready.dragging {
  cursor: grabbing;
}

.digital-human-canvas :deep(canvas) {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
