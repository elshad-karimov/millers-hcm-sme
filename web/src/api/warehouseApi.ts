import { api } from './client';

export interface SyncResult {
  success: boolean;
  message: string;
  employeesLoaded: number;
  attendanceLoaded: number;
  payrollLoaded: number;
  leaveLoaded: number;
}

export interface WarehouseStatus {
  available: boolean;
  engine: string;
}

export interface HeadcountPoint {
  department_name: string;
  headcount: string;
}

export interface AttendanceTrendPoint {
  event_date: string;
  check_ins: string;
  check_outs: string;
}

export interface PayrollTrendPoint {
  period_year: string;
  period_month: string;
  total_gross: string;
  total_net: string;
  employee_count: string;
}

export interface LeaveSummaryPoint {
  leave_type_name: string;
  days_total: string;
  approved_count: string;
  pending_count: string;
}

const BASE = '/api/analytics/warehouse';

export const warehouseApi = {
  status: () =>
    api.get<WarehouseStatus>(`${BASE}/status`).then(r => r.data),

  sync: () =>
    api.post<SyncResult>(`${BASE}/sync`).then(r => r.data),

  headcountByDept: () =>
    api.get<HeadcountPoint[]>(`${BASE}/headcount-by-dept`).then(r => r.data),

  attendanceTrend: () =>
    api.get<AttendanceTrendPoint[]>(`${BASE}/attendance-trend`).then(r => r.data),

  payrollTrend: () =>
    api.get<PayrollTrendPoint[]>(`${BASE}/payroll-trend`).then(r => r.data),

  leaveSummary: () =>
    api.get<LeaveSummaryPoint[]>(`${BASE}/leave-summary`).then(r => r.data),
};
