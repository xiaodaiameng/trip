import type {
  ApiResponse,
  Attraction,
  ChatResponse,
  ConversationRecord,
  DashboardResponse,
  KnowledgeEntry,
  LoginResponse,
  Overview,
  RoutePlanResponse,
} from './types'

const configuredApiBase = (import.meta.env.VITE_API_BASE ?? '').trim().replace(/\/$/, '')
const API_BASE = configuredApiBase

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    credentials: 'same-origin',
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {}),
    },
    ...init,
  })

  const contentType = response.headers.get('content-type') ?? ''
  const isJson = contentType.includes('application/json')
  const result = isJson
    ? (await response.json()) as ApiResponse<T>
    : null

  if (!response.ok || !result?.success) {
    if (!isJson) {
      throw new Error(`后端接口异常：${response.status} ${response.statusText}`)
    }
    throw new Error(result?.message || '请求失败，请稍后重试')
  }

  return result.data
}

export function getApiBase() {
  return API_BASE || '同源 /api'
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

export function adminLogin(payload: { username: string; password: string }) {
  return request<LoginResponse>('/api/admin/login', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function fetchDashboard() {
  return request<DashboardResponse>('/api/admin/dashboard')
}

export function fetchKnowledge() {
  return request<KnowledgeEntry[]>('/api/admin/knowledge')
}

export function createKnowledge(payload: {
  title: string
  category: string
  keywords: string[]
  content: string
  source: string
  published: boolean
}) {
  return request<KnowledgeEntry>('/api/admin/knowledge', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateKnowledge(
  id: number,
  payload: {
    title: string
    category: string
    keywords: string[]
    content: string
    source: string
    published: boolean
  },
) {
  return request<KnowledgeEntry>(`/api/admin/knowledge/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteKnowledge(id: number) {
  return request<null>(`/api/admin/knowledge/${id}`, {
    method: 'DELETE',
  })
}

export function fetchRecords() {
  return request<ConversationRecord[]>('/api/admin/records')
}
