<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

type Point = {
  x: number
  y: number
}

type Ribbon = {
  color: string
  thickness: number
  spring: number
  friction: number
  velocityX: number
  velocityY: number
  offsetX: number
  offsetY: number
  points: Point[]
}

const props = withDefaults(
  defineProps<{
    colors?: string[]
    pointCount?: number
    baseThickness?: number
    speedMultiplier?: number
  }>(),
  {
    colors: () => ['#f5eefe', '#c084fc', '#8b5cf6'],
    pointCount: 26,
    baseThickness: 12,
    speedMultiplier: 0.2,
  },
)

const hostRef = ref<HTMLDivElement | null>(null)
const canvasRef = ref<HTMLCanvasElement | null>(null)

let context: CanvasRenderingContext2D | null = null
let frameId = 0
let resizeObserver: ResizeObserver | null = null
let ribbons: Ribbon[] = []

const pointer = {
  x: 0,
  y: 0,
  active: false,
}

function makeRibbon(index: number, width: number, height: number): Ribbon {
  const center = (props.colors.length - 1) / 2
  return {
    color: props.colors[index],
    thickness: Math.max(2, props.baseThickness - index * 2),
    spring: 0.022 + index * 0.004,
    friction: 0.84 - index * 0.03,
    velocityX: 0,
    velocityY: 0,
    offsetX: (index - center) * 10,
    offsetY: (index % 2 === 0 ? -1 : 1) * 6,
    points: Array.from({ length: props.pointCount }, () => ({
      x: width / 2,
      y: height / 2,
    })),
  }
}

function hexToRgba(hex: string, alpha: number) {
  const raw = hex.replace('#', '')
  const normalized = raw.length === 3
    ? raw.split('').map((char) => `${char}${char}`).join('')
    : raw
  const red = Number.parseInt(normalized.slice(0, 2), 16)
  const green = Number.parseInt(normalized.slice(2, 4), 16)
  const blue = Number.parseInt(normalized.slice(4, 6), 16)
  return `rgba(${red}, ${green}, ${blue}, ${alpha})`
}

function rebuild() {
  if (!hostRef.value || !canvasRef.value) {
    return
  }

  const dpr = window.devicePixelRatio || 1
  const width = window.innerWidth
  const height = window.innerHeight

  canvasRef.value.width = Math.max(1, Math.floor(width * dpr))
  canvasRef.value.height = Math.max(1, Math.floor(height * dpr))
  canvasRef.value.style.width = `${width}px`
  canvasRef.value.style.height = `${height}px`

  context = canvasRef.value.getContext('2d')
  context?.setTransform(dpr, 0, 0, dpr, 0, 0)
  ribbons = props.colors.map((_, index) => makeRibbon(index, width, height))
}

function handlePointerMove(event: PointerEvent) {
  if (event.pointerType === 'touch') {
    return
  }
  pointer.x = event.clientX
  pointer.y = event.clientY
  pointer.active = true
}

function handlePointerLeave() {
  pointer.active = false
}

function animate() {
  frameId = window.requestAnimationFrame(animate)
  const drawingContext = context
  if (!drawingContext) {
    return
  }

  const width = window.innerWidth
  const height = window.innerHeight
  drawingContext.clearRect(0, 0, width, height)

  const targetX = pointer.active ? pointer.x : width * 0.5
  const targetY = pointer.active ? pointer.y : height * 0.5

  ribbons.forEach((ribbon) => {
    const head = ribbon.points[0]
    ribbon.velocityX += (targetX + ribbon.offsetX - head.x) * ribbon.spring
    ribbon.velocityY += (targetY + ribbon.offsetY - head.y) * ribbon.spring
    ribbon.velocityX *= ribbon.friction
    ribbon.velocityY *= ribbon.friction
    head.x += ribbon.velocityX
    head.y += ribbon.velocityY

    for (let index = 1; index < ribbon.points.length; index += 1) {
      const previous = ribbon.points[index - 1]
      const point = ribbon.points[index]
      point.x += (previous.x - point.x) * (0.18 + props.speedMultiplier)
      point.y += (previous.y - point.y) * (0.18 + props.speedMultiplier)
    }

    drawingContext.lineCap = 'round'
    drawingContext.lineJoin = 'round'

    for (let index = 0; index < ribbon.points.length - 1; index += 1) {
      const current = ribbon.points[index]
      const next = ribbon.points[index + 1]
      const alpha = 0.78 * (1 - index / ribbon.points.length)

      drawingContext.beginPath()
      drawingContext.strokeStyle = hexToRgba(ribbon.color, Math.max(0.04, alpha))
      drawingContext.lineWidth = Math.max(1, ribbon.thickness * (1 - index / ribbon.points.length * 0.72))
      drawingContext.moveTo(current.x, current.y)
      drawingContext.quadraticCurveTo(
        (current.x + next.x) / 2,
        (current.y + next.y) / 2,
        next.x,
        next.y,
      )
      drawingContext.stroke()
    }
  })
}

onMounted(() => {
  rebuild()
  animate()

  window.addEventListener('pointermove', handlePointerMove, { passive: true })
  window.addEventListener('pointerleave', handlePointerLeave)

  resizeObserver = new ResizeObserver(() => {
    rebuild()
  })

  if (hostRef.value) {
    resizeObserver.observe(hostRef.value)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('pointermove', handlePointerMove)
  window.removeEventListener('pointerleave', handlePointerLeave)
  resizeObserver?.disconnect()
  window.cancelAnimationFrame(frameId)
})
</script>

<template>
  <div ref="hostRef" class="ribbons-cursor">
    <canvas ref="canvasRef" class="ribbons-cursor-canvas" aria-hidden="true"></canvas>
  </div>
</template>

<style scoped>
.ribbons-cursor {
  position: fixed;
  inset: 0;
  z-index: 9998;
  pointer-events: none;
  overflow: hidden;
}

.ribbons-cursor-canvas {
  display: block;
  width: 100%;
  height: 100%;
}

@media (prefers-reduced-motion: reduce), (pointer: coarse) {
  .ribbons-cursor {
    display: none;
  }
}
</style>
