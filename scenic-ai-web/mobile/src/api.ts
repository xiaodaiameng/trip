import type {
  ApiResponse,
  Attraction,
  ChatResponse,
  ConversationRecord,
  DashboardResponse,
  Overview,
  RoutePlanResponse,
  TtsVoiceResponse,
} from './types'

const API_BASE = (import.meta.env.VITE_API_BASE ?? 'http://localhost:8080').replace(/\/$/, '')

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {}),
    },
    ...init,
  })

  const result = (await response.json()) as ApiResponse<T>

  if (!response.ok || !result.success) {
    throw new Error(result.message || '请求失败，请稍后重试')
  }

  return result.data
}

export function getApiBase() {
  return API_BASE
}

export function fetchOverview() {
  return request<Overview>('/api/public/overview')
}

export function fetchAttractions() {
  return request<Attraction[]>('/api/public/attractions')
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
  return request<ConversationRecord[]>('/api/admin/records')
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
    let message = '服务端语音生成失败'
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
