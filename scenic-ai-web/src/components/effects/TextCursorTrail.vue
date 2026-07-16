<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'

type TrailPoint = {
  id: number
  x: number
  y: number
  angle: number
  driftX: number
  driftY: number
}

const props = withDefaults(
  defineProps<{
    text?: string
    spacing?: number
    maxPoints?: number
    removalInterval?: number
  }>(),
  {
    text: '问',
    spacing: 78,
    maxPoints: 8,
    removalInterval: 34,
  },
)

const containerRef = ref<HTMLDivElement | null>(null)
const trail = ref<TrailPoint[]>([])

let idSeed = 0
let lastPoint: TrailPoint | null = null
let removalTimer = 0

function removeOnePoint() {
  trail.value = trail.value.slice(1)
  if (trail.value.length > 0) {
    removalTimer = window.setTimeout(removeOnePoint, props.removalInterval)
    return
  }
  lastPoint = null
  removalTimer = 0
}

function scheduleRemoval() {
  if (removalTimer) {
    window.clearTimeout(removalTimer)
  }
  removalTimer = window.setTimeout(removeOnePoint, 180)
}

function handlePointerMove(event: PointerEvent) {
  if (!containerRef.value || event.pointerType === 'touch') {
    return
  }

  const rect = containerRef.value.getBoundingClientRect()
  const x = event.clientX - rect.left
  const y = event.clientY - rect.top

  if (lastPoint) {
    const distance = Math.hypot(x - lastPoint.x, y - lastPoint.y)
    if (distance < props.spacing) {
      scheduleRemoval()
      return
    }
  }

  const angle = lastPoint ? (Math.atan2(y - lastPoint.y, x - lastPoint.x) * 180) / Math.PI : 0
  const nextPoint = {
    id: idSeed,
    x,
    y,
    angle,
    driftX: (idSeed % 2 === 0 ? 1 : -1) * (4 + (idSeed % 3) * 2),
    driftY: idSeed % 2 === 0 ? -5 : 5,
  }
  idSeed += 1
  lastPoint = nextPoint
  trail.value = [...trail.value, nextPoint].slice(-props.maxPoints)
  scheduleRemoval()
}

onBeforeUnmount(() => {
  if (removalTimer) {
    window.clearTimeout(removalTimer)
  }
})
</script>

<template>
  <div ref="containerRef" class="text-cursor-container" @pointermove="handlePointerMove">
    <div class="text-cursor-layer" aria-hidden="true">
      <span
        v-for="item in trail"
        :key="item.id"
        class="text-cursor-item"
        :style="{
          left: `${item.x}px`,
          top: `${item.y}px`,
          '--cursor-angle': `${item.angle}deg`,
          '--cursor-drift-x': `${item.driftX}px`,
          '--cursor-drift-y': `${item.driftY}px`,
        }"
      >
        {{ text }}
      </span>
    </div>
    <div class="text-cursor-content">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.text-cursor-container {
  position: relative;
}

.text-cursor-layer {
  position: absolute;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
}

.text-cursor-content {
  position: relative;
  z-index: 1;
}

.text-cursor-item {
  position: absolute;
  color: rgba(192, 132, 252, 0.58);
  font-size: 26px;
  font-weight: 800;
  line-height: 1;
  user-select: none;
  white-space: nowrap;
  transform: translate(-50%, -50%) rotate(var(--cursor-angle));
  animation: text-cursor-float 0.7s ease forwards;
  text-shadow: 0 0 18px rgba(139, 92, 246, 0.46);
}

@keyframes text-cursor-float {
  0% {
    opacity: 0;
    transform: translate(-50%, -50%) rotate(var(--cursor-angle)) scale(0.88);
  }

  24% {
    opacity: 1;
  }

  100% {
    opacity: 0;
    transform:
      translate(calc(-50% + var(--cursor-drift-x)), calc(-50% + var(--cursor-drift-y)))
      rotate(var(--cursor-angle))
      scale(0.76);
  }
}

@media (prefers-reduced-motion: reduce) {
  .text-cursor-item {
    display: none;
  }
}
</style>
