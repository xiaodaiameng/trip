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
const dragActive = ref(false)

const palettes: Record<
  SkinKey,
  {
    hair: string
    hairDeep: string
    skin: string
    robe: string
    robeShadow: string
    sash: string
    gold: string
    boot: string
    gem: string
  }
> = {
  caramel: {
    hair: '#6f4938',
    hairDeep: '#4b2d23',
    skin: '#f4d4c6',
    robe: '#f7f1ea',
    robeShadow: '#dfd5c9',
    sash: '#d96d51',
    gold: '#d6b06f',
    boot: '#9d4f3d',
    gem: '#f2c54b',
  },
  mint: {
    hair: '#54766e',
    hairDeep: '#355049',
    skin: '#f3d9ce',
    robe: '#eef8f5',
    robeShadow: '#d3e4de',
    sash: '#69b49e',
    gold: '#d6c385',
    boot: '#6f817a',
    gem: '#8fd9c1',
  },
  berry: {
    hair: '#6e4255',
    hairDeep: '#4e2938',
    skin: '#f4d4cb',
    robe: '#fff1f5',
    robeShadow: '#ecd4dd',
    sash: '#d76a82',
    gold: '#dfbf74',
    boot: '#9f4d61',
    gem: '#f093b2',
  },
  cocoa: {
    hair: '#594238',
    hairDeep: '#3b2a23',
    skin: '#ebc6b4',
    robe: '#f4ebe5',
    robeShadow: '#dac9bd',
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

let fallbackHeadGroup: THREE.Group | null = null
let fallbackMouth: THREE.Mesh | null = null
let modelHeadGroup: THREE.Group | null = null
let modelBodyRoot: THREE.Group | null = null

const hairParts: THREE.Mesh[] = []
const robeParts: THREE.Mesh[] = []
const robeShadowParts: THREE.Mesh[] = []
const sashParts: THREE.Mesh[] = []
const goldParts: THREE.Mesh[] = []
const bootParts: THREE.Mesh[] = []
const gemParts: THREE.Mesh[] = []
const skinParts: THREE.Mesh[] = []
const modelTintParts: Array<{ mesh: THREE.Mesh; role: 'hair' | 'skin' | 'robe' | 'sash' | 'gold' | 'boot' | 'gem' }> = []
const swayingParts: THREE.Object3D[] = []
const baseAvatarY = -0.18
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

function makeMaterial(color: string) {
  return new THREE.MeshStandardMaterial({
    color,
    roughness: 0.72,
    metalness: 0.08,
  })
}

function pushMesh(
  geometry: THREE.BufferGeometry,
  material: THREE.Material,
  bucket?: THREE.Mesh[],
) {
  const mesh = new THREE.Mesh(geometry, material)
  mesh.castShadow = true
  mesh.receiveShadow = true
  if (bucket) {
    bucket.push(mesh)
  }
  return mesh
}

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

function addCurl(group: THREE.Group, x: number, y: number, z: number, rotationZ: number) {
  const curl = pushMesh(new THREE.TorusGeometry(0.2, 0.06, 18, 42, Math.PI * 1.55), makeMaterial('#000000'))
  curl.position.set(x, y, z)
  curl.rotation.z = rotationZ
  curl.rotation.x = 0.6
  curl.scale.set(1, 1.15, 1)
  hairParts.push(curl)
  swayingParts.push(curl)
  group.add(curl)
}

function buildFallbackBody() {
  const bodyGroup = new THREE.Group()

  const neck = pushMesh(new THREE.CylinderGeometry(0.14, 0.18, 0.28, 24), makeMaterial('#000000'), skinParts)
  neck.position.set(0, 1.1, 0)
  bodyGroup.add(neck)

  const robe = pushMesh(new THREE.CylinderGeometry(0.58, 1.02, 1.92, 8, 1), makeMaterial('#000000'), robeParts)
  robe.position.set(0, 0.18, 0)
  robe.scale.set(1.12, 1, 0.8)
  bodyGroup.add(robe)

  const robeFrontLeft = pushMesh(new THREE.BoxGeometry(0.64, 1.72, 0.16), makeMaterial('#000000'), robeParts)
  robeFrontLeft.position.set(-0.34, 0.08, 0.54)
  robeFrontLeft.rotation.z = 0.2
  robeFrontLeft.rotation.y = 0.14
  bodyGroup.add(robeFrontLeft)

  const robeFrontRight = pushMesh(new THREE.BoxGeometry(0.64, 1.72, 0.16), makeMaterial('#000000'), robeParts)
  robeFrontRight.position.set(0.34, 0.08, 0.54)
  robeFrontRight.rotation.z = -0.2
  robeFrontRight.rotation.y = -0.14
  bodyGroup.add(robeFrontRight)

  const innerLayer = pushMesh(new THREE.CylinderGeometry(0.36, 0.72, 1.52, 8, 1), makeMaterial('#000000'), robeShadowParts)
  innerLayer.position.set(0, 0.08, 0.1)
  innerLayer.scale.set(1, 1, 0.76)
  bodyGroup.add(innerLayer)

  const sashMain = pushMesh(new THREE.BoxGeometry(1.1, 0.18, 0.28), makeMaterial('#000000'), sashParts)
  sashMain.position.set(0, 0.66, 0.48)
  sashMain.rotation.z = -0.12
  bodyGroup.add(sashMain)

  const sashKnot = pushMesh(new THREE.SphereGeometry(0.13, 24, 24), makeMaterial('#000000'), sashParts)
  sashKnot.position.set(0.12, 0.58, 0.58)
  bodyGroup.add(sashKnot)

  const sashTailLeft = pushMesh(new THREE.BoxGeometry(0.14, 0.86, 0.12), makeMaterial('#000000'), sashParts)
  sashTailLeft.position.set(-0.12, 0.05, 0.58)
  sashTailLeft.rotation.z = -0.16
  swayingParts.push(sashTailLeft)
  bodyGroup.add(sashTailLeft)

  const sashTailRight = pushMesh(new THREE.BoxGeometry(0.14, 1.08, 0.12), makeMaterial('#000000'), sashParts)
  sashTailRight.position.set(0.52, 0.18, 0.54)
  sashTailRight.rotation.z = -0.78
  swayingParts.push(sashTailRight)
  bodyGroup.add(sashTailRight)

  const sleeveLeft = pushMesh(new THREE.CylinderGeometry(0.22, 0.38, 0.92, 18, 1), makeMaterial('#000000'), robeParts)
  sleeveLeft.position.set(-0.96, 0.72, 0.1)
  sleeveLeft.rotation.z = 0.74
  sleeveLeft.rotation.x = 0.16
  bodyGroup.add(sleeveLeft)

  const sleeveRight = pushMesh(new THREE.CylinderGeometry(0.22, 0.38, 0.92, 18, 1), makeMaterial('#000000'), robeParts)
  sleeveRight.position.set(0.96, 0.72, 0.1)
  sleeveRight.rotation.z = -0.74
  sleeveRight.rotation.x = -0.16
  bodyGroup.add(sleeveRight)

  const handLeft = pushMesh(new THREE.SphereGeometry(0.16, 20, 20), makeMaterial('#000000'), skinParts)
  handLeft.position.set(-1.42, 0.34, 0.18)
  handLeft.scale.set(1.2, 0.72, 0.8)
  bodyGroup.add(handLeft)

  const handRight = pushMesh(new THREE.SphereGeometry(0.16, 20, 20), makeMaterial('#000000'), skinParts)
  handRight.position.set(1.42, 0.34, 0.18)
  handRight.scale.set(1.2, 0.72, 0.8)
  bodyGroup.add(handRight)

  const trimLeft = pushMesh(new THREE.TorusGeometry(0.28, 0.045, 12, 32), makeMaterial('#000000'), goldParts)
  trimLeft.position.set(-1.16, 0.42, 0.14)
  trimLeft.rotation.y = 1.3
  trimLeft.rotation.x = 0.5
  bodyGroup.add(trimLeft)

  const trimRight = pushMesh(new THREE.TorusGeometry(0.28, 0.045, 12, 32), makeMaterial('#000000'), goldParts)
  trimRight.position.set(1.16, 0.42, 0.14)
  trimRight.rotation.y = -1.3
  trimRight.rotation.x = 0.5
  bodyGroup.add(trimRight)

  const skirtGoldLeft = pushMesh(new THREE.TorusGeometry(0.45, 0.04, 12, 38, Math.PI * 0.66), makeMaterial('#000000'), goldParts)
  skirtGoldLeft.position.set(-0.34, -0.32, 0.64)
  skirtGoldLeft.rotation.z = 1.72
  skirtGoldLeft.rotation.y = 0.42
  bodyGroup.add(skirtGoldLeft)

  const skirtGoldRight = pushMesh(new THREE.TorusGeometry(0.45, 0.04, 12, 38, Math.PI * 0.66), makeMaterial('#000000'), goldParts)
  skirtGoldRight.position.set(0.34, -0.32, 0.64)
  skirtGoldRight.rotation.z = 1.42
  skirtGoldRight.rotation.y = -0.42
  bodyGroup.add(skirtGoldRight)

  const legLeft = pushMesh(new THREE.CylinderGeometry(0.16, 0.18, 0.82, 18, 1), makeMaterial('#d8b3a5'), skinParts)
  legLeft.position.set(-0.28, -1.02, 0.08)
  bodyGroup.add(legLeft)

  const legRight = pushMesh(new THREE.CylinderGeometry(0.16, 0.18, 0.82, 18, 1), makeMaterial('#d8b3a5'), skinParts)
  legRight.position.set(0.28, -1.02, 0.08)
  bodyGroup.add(legRight)

  const bootLeft = pushMesh(new THREE.CylinderGeometry(0.18, 0.22, 0.82, 18, 1), makeMaterial('#000000'), bootParts)
  bootLeft.position.set(-0.28, -1.18, 0.14)
  bodyGroup.add(bootLeft)

  const bootRight = pushMesh(new THREE.CylinderGeometry(0.18, 0.22, 0.82, 18, 1), makeMaterial('#000000'), bootParts)
  bootRight.position.set(0.28, -1.18, 0.14)
  bodyGroup.add(bootRight)

  const shoeLeft = pushMesh(new THREE.SphereGeometry(0.2, 20, 20), makeMaterial('#000000'), bootParts)
  shoeLeft.position.set(-0.28, -1.62, 0.24)
  shoeLeft.scale.set(1.1, 0.7, 1.5)
  bodyGroup.add(shoeLeft)

  const shoeRight = pushMesh(new THREE.SphereGeometry(0.2, 20, 20), makeMaterial('#000000'), bootParts)
  shoeRight.position.set(0.28, -1.62, 0.24)
  shoeRight.scale.set(1.1, 0.7, 1.5)
  bodyGroup.add(shoeRight)

  const earringLeft = pushMesh(new THREE.CylinderGeometry(0.018, 0.018, 0.36, 10, 1), makeMaterial('#000000'), goldParts)
  earringLeft.position.set(-0.72, 1.76, 0.38)
  earringLeft.rotation.z = -0.18
  bodyGroup.add(earringLeft)

  const earringLeftDrop = pushMesh(new THREE.SphereGeometry(0.06, 16, 16), makeMaterial('#000000'), gemParts)
  earringLeftDrop.position.set(-0.75, 1.54, 0.42)
  bodyGroup.add(earringLeftDrop)

  const earringRight = pushMesh(new THREE.CylinderGeometry(0.018, 0.018, 0.36, 10, 1), makeMaterial('#000000'), goldParts)
  earringRight.position.set(0.72, 1.76, 0.38)
  earringRight.rotation.z = 0.18
  bodyGroup.add(earringRight)

  const earringRightDrop = pushMesh(new THREE.SphereGeometry(0.06, 16, 16), makeMaterial('#000000'), gemParts)
  earringRightDrop.position.set(0.75, 1.54, 0.42)
  bodyGroup.add(earringRightDrop)

  return bodyGroup
}

function buildFallbackHead() {
  const headGroup = new THREE.Group()

  const head = pushMesh(new THREE.SphereGeometry(0.84, 48, 48), makeMaterial('#000000'), skinParts)
  head.position.set(0, 1.92, 0.08)
  head.scale.set(1, 1.02, 0.94)
  headGroup.add(head)

  const crownHair = pushMesh(new THREE.SphereGeometry(1.02, 42, 42), makeMaterial('#000000'), hairParts)
  crownHair.position.set(0, 2.14, -0.08)
  crownHair.scale.set(1.08, 0.9, 1.02)
  headGroup.add(crownHair)

  const fringe = pushMesh(new THREE.SphereGeometry(0.88, 40, 40), makeMaterial('#000000'), hairParts)
  fringe.position.set(0, 2.34, 0.26)
  fringe.scale.set(1.02, 0.42, 0.72)
  headGroup.add(fringe)

  const frontCap = pushMesh(new THREE.SphereGeometry(0.56, 36, 36), makeMaterial('#000000'), hairParts)
  frontCap.position.set(0, 2.2, 0.56)
  frontCap.scale.set(0.96, 0.22, 0.28)
  frontCap.rotation.x = -0.18
  headGroup.add(frontCap)

  const bangLeft = pushMesh(new THREE.SphereGeometry(0.3, 24, 24), makeMaterial('#000000'), hairParts)
  bangLeft.position.set(-0.42, 2.02, 0.54)
  bangLeft.scale.set(0.52, 1.02, 0.34)
  bangLeft.rotation.z = 0.22
  headGroup.add(bangLeft)

  const bangRight = pushMesh(new THREE.SphereGeometry(0.3, 24, 24), makeMaterial('#000000'), hairParts)
  bangRight.position.set(0.42, 2.02, 0.54)
  bangRight.scale.set(0.52, 1.02, 0.34)
  bangRight.rotation.z = -0.22
  headGroup.add(bangRight)

  const backHair = pushMesh(new THREE.SphereGeometry(1.08, 40, 40), makeMaterial('#000000'), hairParts)
  backHair.position.set(0, 1.9, -0.38)
  backHair.scale.set(1.08, 0.98, 0.94)
  headGroup.add(backHair)

  const bunLeft = pushMesh(new THREE.SphereGeometry(0.28, 32, 32), makeMaterial('#000000'), hairParts)
  bunLeft.position.set(-0.72, 2.5, -0.08)
  headGroup.add(bunLeft)

  const bunRight = pushMesh(new THREE.SphereGeometry(0.28, 32, 32), makeMaterial('#000000'), hairParts)
  bunRight.position.set(0.72, 2.5, -0.08)
  headGroup.add(bunRight)

  const band = pushMesh(new THREE.TorusGeometry(0.84, 0.03, 12, 60, Math.PI), makeMaterial('#000000'), sashParts)
  band.position.set(0, 2.3, 0.28)
  band.rotation.x = 0.15
  headGroup.add(band)

  addCurl(headGroup, -0.98, 1.78, 0.12, 1.3)
  addCurl(headGroup, -1.07, 1.28, 0.16, 1.45)
  addCurl(headGroup, 0.98, 1.78, 0.12, -1.3)
  addCurl(headGroup, 1.07, 1.28, 0.16, -1.45)

  const leftEye = pushMesh(new THREE.SphereGeometry(0.16, 26, 26), makeMaterial('#2f211b'))
  leftEye.position.set(-0.26, 1.98, 0.74)
  leftEye.scale.set(1, 1.18, 0.5)
  headGroup.add(leftEye)

  const rightEye = pushMesh(new THREE.SphereGeometry(0.16, 26, 26), makeMaterial('#2f211b'))
  rightEye.position.set(0.26, 1.98, 0.74)
  rightEye.scale.set(1, 1.18, 0.5)
  headGroup.add(rightEye)

  const leftEyeSpark = pushMesh(new THREE.SphereGeometry(0.045, 16, 16), makeMaterial('#fff8f4'))
  leftEyeSpark.position.set(-0.22, 2.04, 0.82)
  headGroup.add(leftEyeSpark)

  const rightEyeSpark = pushMesh(new THREE.SphereGeometry(0.045, 16, 16), makeMaterial('#fff8f4'))
  rightEyeSpark.position.set(0.3, 2.04, 0.82)
  headGroup.add(rightEyeSpark)

  fallbackMouth = pushMesh(new THREE.TorusGeometry(0.12, 0.018, 12, 28, Math.PI), makeMaterial('#c56f6a'))
  fallbackMouth.position.set(0, 1.6, 0.78)
  fallbackMouth.rotation.x = Math.PI
  headGroup.add(fallbackMouth)

  return headGroup
}

function buildFallbackAvatar() {
  const root = new THREE.Group()
  root.add(buildFallbackBody())
  fallbackHeadGroup = buildFallbackHead()
  root.add(fallbackHeadGroup)
  root.position.y = baseAvatarY
  root.rotation.y = -0.18
  return root
}

function applyPalette() {
  const palette = palettes[props.skin]

  hairParts.forEach((mesh, index) => {
    const material = mesh.material as THREE.MeshStandardMaterial
    material.color.set(index % 2 === 0 ? palette.hair : palette.hairDeep)
  })
  robeParts.forEach((mesh) => {
    ;(mesh.material as THREE.MeshStandardMaterial).color.set(palette.robe)
  })
  robeShadowParts.forEach((mesh) => {
    ;(mesh.material as THREE.MeshStandardMaterial).color.set(palette.robeShadow)
  })
  sashParts.forEach((mesh) => {
    ;(mesh.material as THREE.MeshStandardMaterial).color.set(palette.sash)
  })
  goldParts.forEach((mesh) => {
    ;(mesh.material as THREE.MeshStandardMaterial).color.set(palette.gold)
  })
  bootParts.forEach((mesh) => {
    ;(mesh.material as THREE.MeshStandardMaterial).color.set(palette.boot)
  })
  gemParts.forEach((mesh) => {
    ;(mesh.material as THREE.MeshStandardMaterial).color.set(palette.gem)
  })
  skinParts.forEach((mesh) => {
    ;(mesh.material as THREE.MeshStandardMaterial).color.set(palette.skin)
  })

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

  const nextX = dragState.startOffsetX + ((clientX - dragState.startX) / rect.width) * 3.4
  const nextY = dragState.startOffsetY - ((clientY - dragState.startY) / rect.height) * 2.1

  dragOffset.x = clamp(nextX, -0.95, 0.95)
  dragOffset.y = clamp(nextY, -0.42, 0.46)
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

  avatarRoot.rotation.y = -0.18 + dragOffset.x * 0.18 + Math.sin(elapsed * 0.9) * 0.12
  avatarRoot.position.x = dragOffset.x
  avatarRoot.position.y = baseAvatarY + dragOffset.y + Math.sin(elapsed * 1.35) * (props.speaking ? 0.07 : 0.04)
  avatarRoot.rotation.z = Math.sin(elapsed * 0.7) * 0.02

  if (fallbackHeadGroup) {
    fallbackHeadGroup.rotation.y = Math.sin(elapsed * 1.1) * 0.05
    fallbackHeadGroup.rotation.x = Math.sin(elapsed * 0.9) * 0.02
  }

  if (modelHeadGroup) {
    modelHeadGroup.rotation.y = Math.sin(elapsed * 1.1) * 0.04
    modelHeadGroup.rotation.x = Math.sin(elapsed * 0.9) * 0.02
  }

  swayingParts.forEach((part, index) => {
    part.rotation.z += Math.sin(elapsed * 1.6 + index * 0.7) * 0.0015
  })

  if (fallbackMouth) {
    const targetScaleY = props.speaking ? 0.84 + Math.sin(elapsed * 12) * 0.14 : 0.84
    fallbackMouth.scale.y = targetScaleY
    fallbackMouth.scale.x = props.speaking ? 1.05 : 1
  }

  renderer.render(scene, camera)
  frameId = requestAnimationFrame(animate)
}

async function fileExists(url: string) {
  try {
    const response = await fetch(url, { method: 'HEAD' })
    return response.ok
  } catch {
    return false
  }
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
    }
  })
  group.scale.setScalar(scale)
  group.position.copy(position)
}

async function attachModelAvatar() {
  if (!scene || !avatarRoot) {
    return
  }

  const avatarUrl = '/models/avatar.glb'
  const headUrl = '/models/head.glb'

  const hasAvatar = await fileExists(avatarUrl)
  if (hasAvatar) {
    const avatarGltf = await loadGlb(avatarUrl)
    modelBodyRoot = avatarGltf.scene
    prepareImportedGroup(modelBodyRoot, 1.9, new THREE.Vector3(0, -1.78, 0))
    avatarRoot.clear()
    avatarRoot.add(modelBodyRoot)
    applyPalette()
    return
  }

  const hasHead = await fileExists(headUrl)
  if (hasHead) {
    const headGltf = await loadGlb(headUrl)
    modelHeadGroup = headGltf.scene
    prepareImportedGroup(modelHeadGroup, 1.16, new THREE.Vector3(0, -0.08, 0.1))
    avatarRoot.clear()
    avatarRoot.add(modelHeadGroup)
    applyPalette()
  }
}

async function initScene() {
  if (!hostRef.value) {
    return
  }

  scene = new THREE.Scene()
  camera = new THREE.PerspectiveCamera(28, 1, 0.1, 100)
  camera.position.set(0, 0.62, 8.2)

  renderer = new THREE.WebGLRenderer({
    antialias: true,
    alpha: true,
  })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFSoftShadowMap
  hostRef.value.appendChild(renderer.domElement)

  const ambient = new THREE.AmbientLight('#fff7ef', 2.4)
  scene.add(ambient)

  const key = new THREE.DirectionalLight('#fff7f0', 2.2)
  key.position.set(2.8, 4.6, 5.8)
  key.castShadow = true
  scene.add(key)

  const fill = new THREE.DirectionalLight('#f4c7de', 1.1)
  fill.position.set(-3.8, 2.5, 3.2)
  scene.add(fill)

  const rim = new THREE.PointLight('#ffe1b8', 1.6, 20)
  rim.position.set(0, 2.4, -2.8)
  scene.add(rim)

  const floor = pushMesh(
    new THREE.CircleGeometry(2.35, 48),
    new THREE.MeshStandardMaterial({
      color: '#f6e7db',
      transparent: true,
      opacity: 0.92,
      roughness: 0.92,
    }),
  )
  floor.position.set(0, -1.86, -0.2)
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

  try {
    await attachModelAvatar()
  } catch (error) {
    avatarRoot?.clear()
    avatarRoot?.add(buildFallbackAvatar())
    console.warn('3D 模型加载失败，已回退到内置数字人。', error)
  }
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
  fallbackHeadGroup = null
  fallbackMouth = null
  modelHeadGroup = null
  modelBodyRoot = null
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
  <div ref="hostRef" class="digital-human-canvas" :class="{ dragging: dragActive }" aria-hidden="true"></div>
</template>

<style scoped>
.digital-human-canvas {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 420px;
  background:
    radial-gradient(circle at 50% 24%, rgba(255, 255, 255, 0.78), rgba(255, 255, 255, 0) 42%),
    radial-gradient(circle at 50% 86%, rgba(236, 192, 146, 0.22), rgba(236, 192, 146, 0) 34%);
  overflow: visible;
  cursor: grab;
  touch-action: none;
}

.digital-human-canvas.dragging {
  cursor: grabbing;
}

.digital-human-canvas :deep(canvas) {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
