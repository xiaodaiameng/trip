import type {
  ApiResponse,
  Attraction,
  ChatResponse,
  ConversationRecord,
  DashboardResponse,
  LoginResponse,
  Overview,
  PageResponse,
  RoutePlanResponse,
  TtsVoiceResponse,
} from './types'

function resolveApiBase() {
  const configuredApiBase = (import.meta.env.VITE_API_BASE ?? '').trim().replace(/\/$/, '')
  if (configuredApiBase) {
    return configuredApiBase
  }
  if (typeof window === 'undefined') {
    return 'http://localhost:8080'
  }
  const { protocol, hostname } = window.location
  const apiProtocol = protocol === 'https:' ? 'https:' : 'http:'
  return `${apiProtocol}//${hostname}:8080`
}

const API_BASE = resolveApiBase()

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const token = localStorage.getItem('scenic-admin-token')
  let response: Response
  try {
    response = await fetch(`${API_BASE}${path}`, {
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(init?.headers ?? {}),
      },
      ...init,
    })
  } catch {
    throw new Error(`连接后端失败，请确认 ${API_BASE} 已启动，并且手机和电脑在同一网络。`)
  }

  const contentType = response.headers.get('content-type') ?? ''
  const result = contentType.includes('application/json')
    ? (await response.json()) as ApiResponse<T>
    : null

  if (!response.ok || !result?.success) {
    if (response.status === 401 || response.status === 403) {
      throw new Error('运营数据仅管理员可查看，请登录后台后再打开。')
    }
    throw new Error(result?.message || `Request failed: ${response.status} ${response.statusText}`)
  }

  return result.data
}

function pageContent<T>(page: PageResponse<T> | T[]): T[] {
  return Array.isArray(page) ? page : page.content
}

export function getApiBase() {
  return API_BASE
}

export function fetchOverview() {
  return request<Overview>('/api/public/overview')
}

export function fetchAttractions() {
  return request<PageResponse<Attraction> | Attraction[]>('/api/public/attractions?size=100').then(pageContent)
}

export function askQuestion(payload: { question: string; sessionId?: string }) {
  return request<ChatResponse>('/api/public/chat', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function recommendRoute(payload: {
  interests: string[]
  durationHours: number
  pace: string
  companionType: string
}) {
  return request<RoutePlanResponse>('/api/public/routes/recommend', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export async function adminLogin(payload: { username: string; password: string }) {
  const profile = await request<LoginResponse>('/api/admin/login', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
  localStorage.setItem('scenic-admin-token', profile.token)
  return profile
}

export function submitFeedback(payload: {
  recordId: number
  helpful: boolean
  comment: string
}) {
  return request<string>('/api/public/feedback', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function fetchDashboard() {
  return request<DashboardResponse>('/api/admin/dashboard')
}

export function fetchRecords() {
  return request<PageResponse<ConversationRecord> | ConversationRecord[]>('/api/admin/records?size=100').then(pageContent)
}

export function fetchTtsVoices() {
  return request<TtsVoiceResponse[]>('/api/public/tts/voices')
}

export async function synthesizeSpeech(payload: {
  text: string
  voiceName?: string
}) {
  const response = await fetch(`${API_BASE}/api/public/tts`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  })

  if (!response.ok) {
    let message = 'Server TTS generation failed'
    try {
      const result = (await response.json()) as ApiResponse<null>
      message = result.message || message
    } catch {
      // ignore non-json response
    }
    throw new Error(message)
  }

  return await response.blob()
}
