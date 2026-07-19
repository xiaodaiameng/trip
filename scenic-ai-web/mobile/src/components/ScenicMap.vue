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

const mapBadgeLabel = computed(() =>
  routeAttractions.value.length > 0
    ? `景点 ${mappedAttractions.value.length} · 路线 ${routeAttractions.value.length} 站`
    : `景点分布 ${mappedAttractions.value.length} 处`,
)

function buildPopup(item: Attraction, order: number | null) {
  const title = order ? `第 ${order} 站 · ${item.name}` : item.name

  return `
    <div class="map-popup">
      <strong>${title}</strong>
      <p>${item.area} · ${item.theme}</p>
      <p>${item.highlight}</p>
    </div>
  `
}

function fitMapBounds() {
  if (!map || mappedAttractions.value.length === 0) {
    return
  }

  const targetPoints = routeAttractions.value.length > 0 ? routeAttractions.value : mappedAttractions.value
  const bounds = L.latLngBounds(
    targetPoints.map((item) => [item.latitude!, item.longitude!] as [number, number]),
  )

  if (!bounds.isValid()) {
    return
  }

  const paddedBounds = bounds.pad(0.24)
  const key = paddedBounds.toBBoxString()
  if (key === lastBoundsKey) {
    return
  }
  lastBoundsKey = key

  map.fitBounds(paddedBounds, {
    animate: false,
    paddingTopLeft: [18, 18],
    paddingBottomRight: [132, 156],
  })
}

function renderMap() {
  if (!map || !markerLayer || !routeLayer) {
    return
  }

  const activeMarkerLayer = markerLayer
  const activeRouteLayer = routeLayer

  activeMarkerLayer.clearLayers()
  activeRouteLayer.clearLayers()

  if (mappedAttractions.value.length === 0) {
    return
  }

  mappedAttractions.value.forEach((item) => {
    const point = L.latLng(item.latitude!, item.longitude!)
    const order = routeAttractions.value.findIndex((stop) => stop.id === item.id)
    const isRouteStop = order >= 0

    if (isRouteStop) {
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
      marker.addTo(activeMarkerLayer)
      return
    }

    const marker = L.circleMarker(point, {
      radius: 10,
      color: '#f6fbf5',
      weight: 2,
      fillColor: '#5d8f6c',
      fillOpacity: 0.96,
    })
    marker.bindPopup(buildPopup(item, null))
    marker.addTo(activeMarkerLayer)
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
    line.addTo(activeRouteLayer)
  }

  fitMapBounds()
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
    scheduleRender()
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
      <div class="map-badge">{{ mapBadgeLabel }}</div>
    </div>

    <div class="scenic-map-legend">
      <strong>{{ scenicName }} 实景底图</strong>
      <span>绿色点位表示全部景点，编号点位表示当前推荐路线。</span>
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
  min-height: 300px;
  border: 1px solid rgba(220, 232, 210, 0.52);
  border-radius: 8px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(241, 247, 237, 0.92)),
    #eef4e8;
  box-shadow:
    0 20px 54px rgba(3, 17, 12, 0.16),
    inset 0 1px 0 rgba(255, 255, 255, 0.78);
}

.scenic-map-canvas {
  min-height: 300px;
}

.map-badge {
  position: absolute;
  top: 14px;
  right: 14px;
  z-index: 450;
  padding: 8px 12px;
  border: 1px solid rgba(232, 195, 112, 0.5);
  border-radius: 8px;
  background: rgba(247, 251, 242, 0.94);
  color: #13251f;
  font-size: 13px;
  font-weight: 800;
  backdrop-filter: blur(8px);
  box-shadow: 0 10px 24px rgba(61, 74, 59, 0.14);
}

.scenic-map-legend {
  display: grid;
  gap: 4px;
  padding: 14px 16px;
  border: 1px solid rgba(220, 232, 210, 0.52);
  border-radius: 8px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(241, 247, 237, 0.92)),
    rgba(247, 251, 241, 0.9);
  color: #13251f;
  box-shadow:
    0 20px 54px rgba(3, 17, 12, 0.12),
    inset 0 1px 0 rgba(255, 255, 255, 0.78);
}

.scenic-map-legend span {
  color: #667568;
  font-size: 13px;
  line-height: 1.5;
}

:deep(.leaflet-control-zoom) {
  border: none;
  box-shadow: 0 14px 24px rgba(61, 74, 59, 0.16);
}

:deep(.leaflet-control-zoom a) {
  color: #3d5645;
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
  border: 2px solid #fff6ef;
  border-radius: 8px;
  background: linear-gradient(180deg, #d18a69 0%, #b76849 100%);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  box-shadow: 0 12px 20px rgba(183, 104, 73, 0.24);
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
    min-height: 260px;
  }
}
</style>
