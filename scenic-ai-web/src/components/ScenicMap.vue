<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import type { Attraction, RouteStop } from '../types'

const props = defineProps<{
  attractions: Attraction[]
  routeStops: RouteStop[]
  scenicName: string
}>()

const hostRef = ref<HTMLDivElement | null>(null)

const DEFAULT_CENTER: L.LatLngExpression = [31.4316, 120.0918]
const DEFAULT_ZOOM = 16

let map: L.Map | null = null
let tileLayer: L.TileLayer | null = null
let markerLayer: L.LayerGroup | null = null
let routeLayer: L.LayerGroup | null = null
let resizeObserver: ResizeObserver | null = null
let renderFrame = 0
let lastBoundsKey = ''

const mappedAttractions = computed(() =>
  props.attractions.filter(
    (item) => typeof item.latitude === 'number' && typeof item.longitude === 'number',
  ),
)

const routeAttractions = computed(() =>
  props.routeStops
    .map((stop) => mappedAttractions.value.find((item) => item.name === stop.attractionName))
    .filter((item): item is Attraction => Boolean(item)),
)

const hasRoute = computed(() => routeAttractions.value.length > 0)

function buildPopup(item: Attraction, order: number | null) {
  const headline = order ? `第 ${order} 站` : '景点位置'

  return `
    <div class="map-popup">
      <strong>${headline} · ${item.name}</strong>
      <p>${item.area}</p>
      <p>${item.highlight}</p>
    </div>
  `
}

function renderMap() {
  if (!map || !markerLayer || !routeLayer) {
    return
  }

  markerLayer.clearLayers()
  routeLayer.clearLayers()

  if (mappedAttractions.value.length === 0) {
    return
  }

  const allBounds = L.latLngBounds([])
  const routeBounds = L.latLngBounds([])

  mappedAttractions.value.forEach((item) => {
    const point = L.latLng(item.latitude!, item.longitude!)
    const order = routeAttractions.value.findIndex((stop) => stop.id === item.id)
    const isRouteStop = order >= 0

    allBounds.extend(point)

    if (isRouteStop) {
      routeBounds.extend(point)
      const marker = L.marker(point, {
        icon: L.divIcon({
          className: 'route-map-pin',
          html: `<span>${order + 1}</span>`,
          iconSize: [34, 34],
          iconAnchor: [17, 34],
          popupAnchor: [0, -28],
        }),
      })
      marker.bindPopup(buildPopup(item, order + 1))
      marker.addTo(markerLayer!)
      return
    }

    const marker = L.circleMarker(point, {
      radius: 8,
      color: '#f6fbf5',
      weight: 2,
      fillColor: '#58735f',
      fillOpacity: 0.88,
    })
    marker.bindPopup(buildPopup(item, null))
    marker.addTo(markerLayer!)
  })

  if (routeAttractions.value.length > 1) {
    const line = L.polyline(
      routeAttractions.value.map((item) => [item.latitude!, item.longitude!] as [number, number]),
      {
        color: '#c57958',
        weight: 4,
        opacity: 0.9,
        dashArray: '10 8',
      },
    )
    line.addTo(routeLayer!)
  }

  if (routeAttractions.value.length > 0 && routeBounds.isValid()) {
    fitBoundsOnce(routeBounds.pad(0.3))
    return
  }

  if (allBounds.isValid()) {
    fitBoundsOnce(allBounds.pad(0.18))
  }
}

function fitBoundsOnce(bounds: L.LatLngBounds) {
  if (!map) {
    return
  }
  const key = bounds.toBBoxString()
  if (key === lastBoundsKey) {
    return
  }
  lastBoundsKey = key
  map.fitBounds(bounds, {
    animate: false,
    paddingTopLeft: [16, 16],
    paddingBottomRight: [16, 16],
  })
}

function scheduleRender() {
  if (renderFrame) {
    window.cancelAnimationFrame(renderFrame)
  }
  renderFrame = window.requestAnimationFrame(() => {
    renderFrame = 0
    renderMap()
  })
}

onMounted(() => {
  if (!hostRef.value) {
    return
  }

  map = L.map(hostRef.value, {
    zoomControl: true,
    attributionControl: false,
    preferCanvas: true,
    fadeAnimation: false,
    zoomAnimation: true,
  }).setView(DEFAULT_CENTER, DEFAULT_ZOOM)

  tileLayer = L.tileLayer(
    'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}',
    {
      subdomains: ['1', '2', '3', '4'],
      maxZoom: 19,
      maxNativeZoom: 18,
      updateWhenIdle: true,
      updateWhenZooming: false,
      keepBuffer: 4,
    },
  )
  tileLayer.addTo(map)

  markerLayer = L.layerGroup().addTo(map)
  routeLayer = L.layerGroup().addTo(map)

  scheduleRender()

  resizeObserver = new ResizeObserver(() => {
    map?.invalidateSize()
  })
  resizeObserver.observe(hostRef.value)
})

watch(
  () => [props.attractions, props.routeStops],
  () => {
    scheduleRender()
  },
  { deep: true },
)

onBeforeUnmount(() => {
  if (renderFrame) {
    window.cancelAnimationFrame(renderFrame)
    renderFrame = 0
  }
  resizeObserver?.disconnect()
  resizeObserver = null

  routeLayer?.clearLayers()
  markerLayer?.clearLayers()
  tileLayer = null
  routeLayer = null
  markerLayer = null

  map?.remove()
  map = null
})
</script>

<template>
  <section class="scenic-map-panel">
    <div class="scenic-map-frame">
      <div ref="hostRef" class="scenic-map-canvas"></div>
      <div class="map-badge">{{ hasRoute ? '路线已高亮' : '已展示景点分布' }}</div>
    </div>

    <div class="scenic-map-legend">
      <strong>{{ scenicName }} 实景底图</strong>
      <span>绿色点位表示景点位置，编号点位表示当前推荐路线。</span>
    </div>
  </section>
</template>

<style scoped>
.scenic-map-panel {
  display: grid;
  gap: 12px;
}

.scenic-map-frame {
  position: relative;
  overflow: hidden;
  min-height: 420px;
  border: 1px solid rgba(232, 195, 112, 0.24);
  border-radius: 8px;
  background:
    radial-gradient(circle at 18% 18%, rgba(255, 255, 255, 0.2), transparent 32%),
    linear-gradient(180deg, rgba(17, 46, 36, 0.18), rgba(255, 255, 255, 0));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.56),
    0 18px 34px rgba(8, 18, 15, 0.14);
}

.scenic-map-canvas {
  min-height: 420px;
}

.map-badge {
  position: absolute;
  top: 12px;
  right: 12px;
  padding: 8px 12px;
  border: 1px solid rgba(232, 195, 112, 0.38);
  border-radius: 8px;
  background: rgba(247, 251, 242, 0.84);
  color: #294538;
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0;
  backdrop-filter: blur(10px);
  box-shadow: 0 10px 24px rgba(8, 18, 15, 0.14);
}

.scenic-map-legend {
  display: grid;
  gap: 6px;
  padding: 14px 16px;
  border: 1px solid rgba(36, 70, 56, 0.12);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.66);
  color: #44594b;
}

.scenic-map-legend span {
  font-size: 13px;
  line-height: 1.5;
}

:deep(.leaflet-control-zoom) {
  border: none;
  box-shadow: 0 14px 24px rgba(8, 18, 15, 0.18);
}

:deep(.leaflet-control-zoom a) {
  color: #2b473a;
  background: rgba(255, 255, 255, 0.92);
}

:deep(.route-map-pin) {
  display: grid;
  place-items: center;
}

:deep(.route-map-pin span) {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 2px solid rgba(255, 248, 235, 0.96);
  border-radius: 8px;
  background: linear-gradient(180deg, #d9ad58 0%, #bb6f53 100%);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  box-shadow: 0 12px 20px rgba(183, 104, 73, 0.28);
}

:deep(.leaflet-popup-content-wrapper) {
  border-radius: 8px;
  background: rgba(247, 251, 242, 0.96);
  box-shadow: 0 16px 28px rgba(8, 18, 15, 0.18);
}

:deep(.leaflet-popup-tip) {
  background: rgba(247, 251, 242, 0.96);
}

:deep(.map-popup) {
  display: grid;
  gap: 4px;
  min-width: 180px;
  color: #415347;
}

:deep(.map-popup p) {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 768px) {
  .scenic-map-frame,
  .scenic-map-canvas {
    min-height: 320px;
  }
}
</style>
