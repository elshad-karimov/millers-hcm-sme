import { api } from './client'
import type { PerformanceReview } from './performance'

// ── Session status ────────────────────────────────────────────────────────────

export type CalibrationSessionStatus = 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED'

// ── Interfaces ────────────────────────────────────────────────────────────────

export interface CalibrationSession {
  id: string
  cycleId: string
  name: string
  scheduledAt?: string | null
  status: CalibrationSessionStatus
  facilitator?: string | null
  notes?: string | null
  createdAt: string
}

export interface CalibrationSessionRequest {
  name: string
  scheduledAt?: string | null
  facilitator?: string | null
  notes?: string | null
}

export interface CalibrationBoardEntry {
  reviewId: string
  employeeId: string
  employeeName: string
  department?: string | null
  managerId?: string | null
  selfRating?: number | null
  managerRating?: number | null
  finalRating?: number | null
  finalBand?: string | null
  recommendation?: string | null
  bonusPercent?: number | null
  calibrationNotes?: string | null
  /** M93 — potential rating used by the 9-box succession grid (M92). */
  potentialRating?: number | null
  /** M93 — rationale for the potential rating. */
  potentialNotes?: string | null
}

export interface CalibrationBoardResponse {
  cycleId: string
  cycleName: string
  totalReviews: number
  ratingDistribution: Record<string, number>
  entries: CalibrationBoardEntry[]
}

export interface CalibrationRequest {
  finalRating?: number | null
  finalBand?: string | null
  recommendation?: string | null
  bonusPercent?: number | null
  calibrationNotes?: string | null
}

// ── API client ────────────────────────────────────────────────────────────────

export const calibrationApi = {
  /** POST /api/performance/cycles/{cycleId}/calibration-sessions — create a new session */
  createSession: (cycleId: string, payload: CalibrationSessionRequest) =>
    api
      .post<CalibrationSession>(
        `/performance/cycles/${cycleId}/calibration-sessions`,
        payload,
      )
      .then((r) => r.data),

  /** GET /api/performance/cycles/{cycleId}/calibration-sessions — list sessions */
  listSessions: (cycleId: string) =>
    api
      .get<CalibrationSession[]>(`/performance/cycles/${cycleId}/calibration-sessions`)
      .then((r) => r.data),

  /** GET /api/performance/cycles/{cycleId}/calibration-board — board view */
  getBoard: (cycleId: string) =>
    api
      .get<CalibrationBoardResponse>(`/performance/cycles/${cycleId}/calibration-board`)
      .then((r) => r.data),

  /** POST /api/performance/calibration-sessions/{sessionId}/start */
  startSession: (sessionId: string) =>
    api
      .post<CalibrationSession>(`/performance/calibration-sessions/${sessionId}/start`)
      .then((r) => r.data),

  /** POST /api/performance/calibration-sessions/{sessionId}/reviews/{reviewId}/calibrate */
  calibrateReview: (sessionId: string, reviewId: string, payload: CalibrationRequest) =>
    api
      .post<PerformanceReview>(
        `/performance/calibration-sessions/${sessionId}/reviews/${reviewId}/calibrate`,
        payload,
      )
      .then((r) => r.data),

  /** POST /api/performance/calibration-sessions/{sessionId}/complete */
  completeSession: (sessionId: string) =>
    api
      .post<CalibrationSession>(`/performance/calibration-sessions/${sessionId}/complete`)
      .then((r) => r.data),
}
