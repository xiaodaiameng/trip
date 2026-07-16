<script setup lang="ts">
import { Color, Mesh, Program, Renderer, RenderTarget, Triangle } from 'ogl'
import { onBeforeUnmount, onMounted, ref } from 'vue'

const MAX_STRANDS = 12
const MAX_COLORS = 8

interface StrandsProps {
  colors?: string[]
  count?: number
  speed?: number
  amplitude?: number
  waviness?: number
  thickness?: number
  glow?: number
  taper?: number
  spread?: number
  hueShift?: number
  intensity?: number
  saturation?: number
  opacity?: number
  scale?: number
  glass?: boolean
  refraction?: number
  dispersion?: number
  glassSize?: number
}

const props = withDefaults(defineProps<StrandsProps>(), {
  colors: () => ['#FF4242', '#7C3AED', '#06B6D4', '#EAB308'],
  count: 3,
  speed: 0.5,
  amplitude: 1,
  waviness: 1,
  thickness: 0.7,
  glow: 2.6,
  taper: 3,
  spread: 1,
  hueShift: 0,
  intensity: 0.6,
  saturation: 1,
  opacity: 1,
  scale: 1,
  glass: false,
  refraction: 1,
  dispersion: 1,
  glassSize: 1,
})

const containerRef = ref<HTMLDivElement | null>(null)

let renderer: Renderer | null = null
let program: Program | null = null
let glassProgram: Program | null = null
let mesh: Mesh | null = null
let glassMesh: Mesh | null = null
let renderTarget: RenderTarget | null = null
let frameId = 0
let resizeObserver: ResizeObserver | null = null

const vertexShader = `#version 300 es
in vec2 position;
void main() {
  gl_Position = vec4(position, 0.0, 1.0);
}
`

const fragmentShader = `#version 300 es
precision highp float;

uniform float uTime;
uniform vec2 uResolution;
uniform vec3 uColors[${MAX_COLORS}];
uniform int uColorCount;
uniform int uStrandCount;
uniform float uSpeed;
uniform float uAmplitude;
uniform float uWaviness;
uniform float uThickness;
uniform float uGlow;
uniform float uTaper;
uniform float uSpread;
uniform float uHueShift;
uniform float uIntensity;
uniform float uOpacity;
uniform float uScale;
uniform float uSaturation;

out vec4 fragColor;

const float PI = 3.14159265;

vec3 spectrum(float t) {
  return 0.5 + 0.5 * cos(2.0 * PI * (t + vec3(0.00, 0.33, 0.67)));
}

vec3 samplePalette(float t) {
  t = fract(t);
  float scaled = t * float(uColorCount);
  int idx = int(floor(scaled));
  float blend = fract(scaled);
  int nextIdx = idx + 1;
  if (nextIdx >= uColorCount) nextIdx = 0;
  return mix(uColors[idx], uColors[nextIdx], blend);
}

vec3 strandColor(float t) {
  if (uColorCount > 0) return samplePalette(t);
  return spectrum(t);
}

void main() {
  vec2 uv = (gl_FragCoord.xy - 0.5 * uResolution) / uResolution.y;
  uv /= max(uScale, 0.0001);

  float e = 0.06 + uIntensity * 0.94;
  float env = pow(max(cos(uv.x * PI * 1.3), 0.0), uTaper);

  vec3 col = vec3(0.0);

  for (int i = 0; i < ${MAX_STRANDS}; i++) {
    if (i >= uStrandCount) break;

    float fi = float(i);
    float ph = fi * 1.7 * uSpread;
    float freq = (2.0 + fi * 0.35) * uWaviness;
    float spd = 1.4 + fi * 1.2;

    float tt = uTime * uSpeed;
    float w = sin(uv.x * freq + tt * spd + ph) * 0.60
            + sin(uv.x * freq * 1.1 - tt * spd * 0.7 + ph * 1.7) * 0.40;

    float amp = (0.1 + 0.02 * e) * env * uAmplitude;
    float y = w * amp;

    float d = abs(uv.y - y);
    float thick = (0.001 + 0.05 * e) * (0.35 + env) * uThickness;
    float g = thick / (d + thick * 0.45);
    g = g * g;

    float h = fi / float(uStrandCount) + uv.x * 0.30 + uTime * 0.04 + uHueShift;
    col += strandColor(h) * g * env;
  }

  col *= 0.45 + 0.7 * e;
  col = 1.0 - exp(-col * uGlow);

  float gray = dot(col, vec3(0.2126, 0.7152, 0.0722));
  col = max(mix(vec3(gray), col, uSaturation), 0.0);

  float lum = max(max(col.r, col.g), col.b);
  float alpha = clamp(lum, 0.0, 1.0) * uOpacity;

  fragColor = vec4(col * uOpacity, alpha);
}
`

const glassFragmentShader = `#version 300 es
precision highp float;

uniform sampler2D uScene;
uniform vec2 uResolution;
uniform float uRadius;
uniform float uRefraction;
uniform float uDispersion;

out vec4 fragColor;

vec2 toUv(vec2 p) {
  return p * (uResolution.y / uResolution) + 0.5;
}

void main() {
  vec2 p = (gl_FragCoord.xy - 0.5 * uResolution) / uResolution.y;
  float d = length(p);
  float r = uRadius;

  float edge = fwidth(d) * 1.5;
  float mask = 1.0 - smoothstep(r - edge, r + edge, d);
  if (mask <= 0.0) {
    fragColor = vec4(0.0);
    return;
  }

  float z = sqrt(max(r * r - d * d, 0.0)) / r;
  float nd = d / r;

  vec2 dir = d > 0.0 ? p / d : vec2(0.0);
  float lens = smoothstep(0.85, 1.0, nd) * pow(nd, 6.0);
  vec2 offset = -dir * lens * uRefraction * 0.15;
  vec2 disp = -dir * lens * uDispersion * 0.012;

  vec3 light;
  light.r = texture(uScene, toUv(p + offset - disp)).r;
  light.g = texture(uScene, toUv(p + offset)).g;
  light.b = texture(uScene, toUv(p + offset + disp)).b;

  float fres = pow(1.0 - z, 3.0);
  vec3 rim = vec3(1.0) * fres * 0.18;

  vec2 lightDir = normalize(vec2(-0.55, 0.6));
  float spec = pow(max(dot(p / max(r, 1e-4), lightDir), 0.0), 6.0);
  spec *= smoothstep(r, r * 0.55, d);

  vec3 emissive = light + rim + vec3(spec) * 0.4;
  float emissiveA = clamp(max(max(emissive.r, emissive.g), emissive.b), 0.0, 1.0);

  float bodyA = 0.05 + fres * 0.05;

  float outA = emissiveA + bodyA * (1.0 - emissiveA);
  vec3 outRGB = emissive;

  outRGB *= mask;
  outA *= mask;

  fragColor = vec4(outRGB, outA);
}
`

function buildPalette(colors: readonly string[]) {
  const filled = colors.length > 0 ? colors : ['#ffffff']
  const padded: number[][] = []

  for (let index = 0; index < MAX_COLORS; index += 1) {
    const color = new Color(filled[index] ?? filled[filled.length - 1])
    padded.push([color.r, color.g, color.b])
  }

  return padded
}

function getColorCount(colors: readonly string[]) {
  return Math.min(colors.length, MAX_COLORS)
}

function getStrandCount(count: number) {
  return Math.min(Math.max(Math.round(count), 1), MAX_STRANDS)
}

function getContainerSize() {
  const container = containerRef.value
  return {
    width: Math.max(container?.offsetWidth ?? 1, 1),
    height: Math.max(container?.offsetHeight ?? 1, 1),
  }
}

function resizeRenderer() {
  if (!renderer || !program || !glassProgram || !renderTarget) {
    return
  }

  const { width, height } = getContainerSize()
  renderer.setSize(width, height)
  program.uniforms.uResolution.value = [width, height]
  renderTarget.setSize(width, height)
  glassProgram.uniforms.uResolution.value = [width, height]
}

function updateUniforms(time: number) {
  if (!program) {
    return
  }

  program.uniforms.uTime.value = time * 0.001
  program.uniforms.uColors.value = buildPalette(props.colors)
  program.uniforms.uColorCount.value = getColorCount(props.colors)
  program.uniforms.uStrandCount.value = getStrandCount(props.count)
  program.uniforms.uSpeed.value = props.speed
  program.uniforms.uAmplitude.value = props.amplitude
  program.uniforms.uWaviness.value = props.waviness
  program.uniforms.uThickness.value = props.thickness
  program.uniforms.uGlow.value = props.glow
  program.uniforms.uTaper.value = props.taper
  program.uniforms.uSpread.value = props.spread
  program.uniforms.uHueShift.value = props.hueShift
  program.uniforms.uIntensity.value = props.intensity
  program.uniforms.uOpacity.value = props.opacity
  program.uniforms.uScale.value = props.scale
  program.uniforms.uSaturation.value = props.saturation
}

function animate(time: number) {
  frameId = requestAnimationFrame(animate)

  if (!renderer || !program || !mesh || !glassProgram || !glassMesh || !renderTarget) {
    return
  }

  updateUniforms(time)

  if (props.glass) {
    renderer.render({ scene: mesh, target: renderTarget })
    glassProgram.uniforms.uScene.value = renderTarget.texture
    glassProgram.uniforms.uRefraction.value = props.refraction
    glassProgram.uniforms.uDispersion.value = props.dispersion
    glassProgram.uniforms.uRadius.value = 0.46 * props.glassSize
    renderer.render({ scene: glassMesh })
    return
  }

  renderer.render({ scene: mesh })
}

function initStrands() {
  const container = containerRef.value
  if (!container) {
    return
  }

  renderer = new Renderer({
    alpha: true,
    premultipliedAlpha: true,
    antialias: true,
    depth: false,
  })

  if (!renderer.isWebgl2) {
    renderer.gl.getExtension('WEBGL_lose_context')?.loseContext()
    renderer = null
    return
  }

  const gl = renderer.gl
  const { width, height } = getContainerSize()

  gl.clearColor(0, 0, 0, 0)
  gl.enable(gl.BLEND)
  gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA)
  gl.canvas.style.backgroundColor = 'transparent'

  const geometry = new Triangle(gl)
  if (geometry.attributes.uv) {
    delete geometry.attributes.uv
  }

  program = new Program(gl, {
    vertex: vertexShader,
    fragment: fragmentShader,
    uniforms: {
      uTime: { value: 0 },
      uResolution: { value: [width, height] },
      uColors: { value: buildPalette(props.colors) },
      uColorCount: { value: getColorCount(props.colors) },
      uStrandCount: { value: getStrandCount(props.count) },
      uSpeed: { value: props.speed },
      uAmplitude: { value: props.amplitude },
      uWaviness: { value: props.waviness },
      uThickness: { value: props.thickness },
      uGlow: { value: props.glow },
      uTaper: { value: props.taper },
      uSpread: { value: props.spread },
      uHueShift: { value: props.hueShift },
      uIntensity: { value: props.intensity },
      uOpacity: { value: props.opacity },
      uScale: { value: props.scale },
      uSaturation: { value: props.saturation },
    },
  })

  mesh = new Mesh(gl, { geometry, program })
  renderTarget = new RenderTarget(gl, { width, height })

  glassProgram = new Program(gl, {
    vertex: vertexShader,
    fragment: glassFragmentShader,
    uniforms: {
      uScene: { value: renderTarget.texture },
      uResolution: { value: [width, height] },
      uRadius: { value: 0.46 * props.glassSize },
      uRefraction: { value: props.refraction },
      uDispersion: { value: props.dispersion },
    },
  })
  glassMesh = new Mesh(gl, { geometry, program: glassProgram })

  container.appendChild(gl.canvas)
  resizeObserver = new ResizeObserver(resizeRenderer)
  resizeObserver.observe(container)
  resizeRenderer()
  frameId = requestAnimationFrame(animate)
}

function disposeStrands() {
  if (frameId) {
    cancelAnimationFrame(frameId)
  }

  resizeObserver?.disconnect()
  resizeObserver = null

  const canvas = renderer?.gl.canvas
  if (canvas?.parentNode) {
    canvas.parentNode.removeChild(canvas)
  }

  renderer?.gl.getExtension('WEBGL_lose_context')?.loseContext()
  renderer = null
  program = null
  glassProgram = null
  mesh = null
  glassMesh = null
  renderTarget = null
}

onMounted(() => {
  initStrands()
})

onBeforeUnmount(() => {
  disposeStrands()
})
</script>

<template>
  <div ref="containerRef" class="strands-container" aria-hidden="true"></div>
</template>

<style scoped>
.strands-container {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 1px;
  overflow: hidden;
  pointer-events: none;
  background: transparent;
}

.strands-container :deep(canvas) {
  display: block;
  width: 100%;
  height: 100%;
  background: transparent;
}
</style>
