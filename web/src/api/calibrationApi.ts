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
  /** M121 — true once a completed calibration session has sealed the row. */
  calibrationLocked?: boolean
}

/** M121 — one row of the board distribution (band → actual/target/delta). */
export interface BoardCell {
  actualCount: number
  actualPercent: number
  targetPercent?: number | null
  delta?: number | null
}

export interface CalibrationBoardResponse {
  cycleId: string
  cycleName: string
  totalReviews: number
  ratingDistribution: Record<string, number>
  /** M121 — band → cell. Same keys as targetDistribution + any actual-only bands. */
  boardCells: Record<string, BoardCell>
  /** M121 — raw band → target % map. */
  targetDistribution: Record<string, number>
  entries: CalibrationBoardEntry[]
}

/** M121 — one edit-log row captured during a calibration session. */
export interface CalibrationEditLog {
  id: string
  sessionId: string
  reviewId: string
  editedBy: string
  editedAt: string
  beforeJson?: Record<string, unknown> | null
  afterJson?: Record<string, unknown> | null
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

  // ── M121 ─────────────────────────────────────────────────────────────────

  /** GET /api/performance/calibration-sessions/{sessionId}/edit-log */
  editLog: (sessionId: string) =>
    api
      .get<CalibrationEditLog[]>(
        `/performance/calibration-sessions/${sessionId}/edit-log`,
      )
      .then((r) => r.data),

  /** POST /api/performance/reviews/{reviewId}/unlock-calibration — HR_ADMIN only */
  unlockReview: (reviewId: string) =>
    api
      .post<void>(`/performance/reviews/${reviewId}/unlock-calibration`)
      .then((r) => r.data),

  /** GET /api/performance/cycles/{cycleId}/calibration-targets */
  getTargets: (cycleId: string) =>
    api
      .get<Record<string, number | null>>(
        `/performance/cycles/${cycleId}/calibration-targets`,
      )
      .then((r) => r.data),

  /** PUT /api/performance/cycles/{cycleId}/calibration-targets — HR_ADMIN only */
  saveTargets: (cycleId: string, targets: Record<string, number | null>) =>
    api
      .put<Record<string, number | null>>(
        `/performance/cycles/${cycleId}/calibration-targets`,
        targets,
      )
      .then((r) => r.data),
}
