import { useEffect, useState } from 'react';
import {
  Alert, Button, Card, Col, message, Row, Space, Statistic, Table, Tag, Typography,
} from 'antd';
import {
  BarChart, Bar, LineChart, Line, PieChart, Pie, Cell,
  XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
} from 'recharts';
import {
  DatabaseOutlined, ReloadOutlined, SyncOutlined, CheckCircleOutlined, CloseCircleOutlined,
} from '@ant-design/icons';
import {
  warehouseApi,
  SyncResult,
  WarehouseStatus,
  HeadcountPoint,
  AttendanceTrendPoint,
  PayrollTrendPoint,
  LeaveSummaryPoint,
} from '../../api/warehouseApi';

const { Title } = Typography;

const COLORS = ['#1677ff', '#52c41a', '#faad14', '#f5222d', '#722ed1', '#13c2c2', '#eb2f96'];

const fmtMonth = (year: string, month: string) =>
  `${year}-${month.padStart(2, '0')}`;

export function WarehouseAnalyticsPage() {
  const [status, setStatus] = useState<WarehouseStatus | null>(null);
  const [syncing, setSyncing] = useState(false);
  const [lastSync, setLastSync] = useState<SyncResult | null>(null);
  const [loading, setLoading] = useState(false);

  const [headcount, setHeadcount] = useState<HeadcountPoint[]>([]);
  const [attendance, setAttendance] = useState<AttendanceTrendPoint[]>([]);
  const [payroll, setPayroll] = useState<PayrollTrendPoint[]>([]);
  const [leave, setLeave] = useState<LeaveSummaryPoint[]>([]);

  const fetchStatus = async () => {
    try {
      const s = await warehouseApi.status();
      setStatus(s);
    } catch {
      setStatus({ available: false, engine: 'ClickHouse' });
    }
  };

  const fetchAnalytics = async () => {
    setLoading(true);
    try {
      const [h, a, p, l] = await Promise.all([
        warehouseApi.headcountByDept(),
        warehouseApi.attendanceTrend(),
        warehouseApi.payrollTrend(),
        warehouseApi.leaveSummary(),
      ]);
      setHeadcount(h);
      setAttendance(a);
      setPayroll(p);
      setLeave(l);
    } catch {
      message.error('Failed to load analytics');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchStatus();
    fetchAnalytics();
  }, []);

  const handleSync = async () => {
    setSyncing(true);
    try {
      const result = await warehouseApi.sync();
      setLastSync(result);
      if (result.success) {
        message.success(`Sync complete — ${result.employeesLoaded} employees, ${result.attendanceLoaded} attendance events loaded`);
        fetchAnalytics();
      } else {
        message.error(`Sync failed: ${result.message}`);
      }
    } catch (e: any) {
      message.error(e.response?.data?.detail || 'Sync failed');
    } finally {
      setSyncing(false);
    }
  };

  const pieData = headcount.map(h => ({
    name: h.department_name || 'Unknown',
    value: Number(h.headcount),
  }));

  const payrollData = payroll.map(p => ({
    period: fmtMonth(p.period_year, p.period_month),
    gross: Number(p.total_gross),
    net: Number(p.total_net),
    employees: Number(p.employee_count),
  }));

  const attData = attendance.map(a => ({
    date: a.event_date,
    checkIns: Number(a.check_ins),
    checkOuts: Number(a.check_outs),
  }));

  return (
    <div style={{ padding: 24 }}>
      {/* Header */}
      <Row gutter={16} align="middle" style={{ marginBottom: 20 }}>
        <Col flex={1}>
          <Space align="center">
            <DatabaseOutlined style={{ fontSize: 24, color: '#1677ff' }} />
            <Title level={3} style={{ margin: 0 }}>Analytics Warehouse</Title>
            {status && (
              <Tag color={status.available ? 'success' : 'error'} icon={status.available ? <CheckCircleOutlined /> : <CloseCircleOutlined />}>
                {status.engine} {status.available ? 'Online' : 'Offline'}
              </Tag>
            )}
          </Space>
        </Col>
        <Col>
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => { fetchStatus(); fetchAnalytics(); }} loading={loading}>
              Refresh
            </Button>
            <Button
              type="primary"
              icon={<SyncOutlined />}
              onClick={handleSync}
              loading={syncing}
              disabled={!status?.available}
            >
              Sync Now
            </Button>
          </Space>
        </Col>
      </Row>

      {!status?.available && (
        <Alert
          type="warning"
          message="ClickHouse not reachable"
          description="Start the ClickHouse container (docker compose up -d clickhouse) then refresh. Charts will show empty until a sync is run."
          showIcon
          style={{ marginBottom: 20 }}
        />
      )}

      {/* Sync result banner */}
      {lastSync && (
        <Alert
          type={lastSync.success ? 'success' : 'error'}
          message={lastSync.success ? 'Sync completed' : `Sync failed: ${lastSync.message}`}
          description={lastSync.success
            ? `Employees: ${lastSync.employeesLoaded} | Attendance: ${lastSync.attendanceLoaded} | Payroll: ${lastSync.payrollLoaded} | Leave: ${lastSync.leaveLoaded}`
            : undefined}
          closable
          onClose={() => setLastSync(null)}
          style={{ marginBottom: 20 }}
        />
      )}

      {/* KPI strip */}
      <Row gutter={16} style={{ marginBottom: 20 }}>
        <Col xs={12} md={6}>
          <Card size="small">
            <Statistic
              title="Active Employees (warehouse)"
              value={headcount.reduce((s, h) => s + Number(h.headcount), 0)}
              prefix={<DatabaseOutlined />}
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card size="small">
            <Statistic
              title="Attendance Events (60d)"
              value={attendance.reduce((s, a) => s + Number(a.check_ins), 0)}
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card size="small">
            <Statistic
              title="Payroll Periods"
              value={payroll.length}
              suffix="months"
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card size="small">
            <Statistic
              title="Leave Types Tracked"
              value={leave.length}
            />
          </Card>
        </Col>
      </Row>

      {/* Charts row 1 */}
      <Row gutter={16} style={{ marginBottom: 20 }}>
        {/* Headcount by dept — pie */}
        <Col xs={24} md={10}>
          <Card title="Headcount by Department" size="small" loading={loading}>
            {pieData.length > 0 ? (
              <ResponsiveContainer width="100%" height={260}>
                <PieChart>
                  <Pie
                    data={pieData}
                    dataKey="value"
                    nameKey="name"
                    cx="50%"
                    cy="50%"
                    outerRadius={90}
                    label={({ name, percent }) => `${name} (${((percent ?? 0) * 100).toFixed(0)}%)`}
                    labelLine={false}
                  >
                    {pieData.map((_, i) => (
                      <Cell key={i} fill={COLORS[i % COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip formatter={(v: any) => [`${v} employees`]} />
                </PieChart>
              </ResponsiveContainer>
            ) : (
              <div style={{ textAlign: 'center', padding: 40, color: '#999' }}>
                No data — run Sync to load warehouse
              </div>
            )}
          </Card>
        </Col>

        {/* Payroll trend — bar */}
        <Col xs={24} md={14}>
          <Card title="Monthly Payroll Trend (AZN)" size="small" loading={loading}>
            {payrollData.length > 0 ? (
              <ResponsiveContainer width="100%" height={260}>
                <BarChart data={payrollData} margin={{ top: 5, right: 20, bottom: 5, left: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="period" tick={{ fontSize: 11 }} />
                  <YAxis tick={{ fontSize: 11 }} tickFormatter={v => `${(v / 1000).toFixed(0)}k`} />
                  <Tooltip formatter={(v: any) => [`${Number(v).toLocaleString()} AZN`]} />
                  <Legend />
                  <Bar dataKey="gross" name="Gross" fill="#1677ff" />
                  <Bar dataKey="net" name="Net" fill="#52c41a" />
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <div style={{ textAlign: 'center', padding: 40, color: '#999' }}>
                No payroll data — run a payroll run first, then sync
              </div>
            )}
          </Card>
        </Col>
      </Row>

      {/* Charts row 2 */}
      <Row gutter={16}>
        {/* Attendance trend — line */}
        <Col xs={24} md={14}>
          <Card title="Daily Attendance Trend (last 60 days)" size="small" loading={loading}>
            {attData.length > 0 ? (
              <ResponsiveContainer width="100%" height={240}>
                <LineChart data={attData} margin={{ top: 5, right: 20, bottom: 5, left: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="date" tick={{ fontSize: 10 }} interval={6} />
                  <YAxis tick={{ fontSize: 11 }} />
                  <Tooltip />
                  <Legend />
                  <Line type="monotone" dataKey="checkIns" name="Check-ins" stroke="#1677ff" dot={false} />
                  <Line type="monotone" dataKey="checkOuts" name="Check-outs" stroke="#52c41a" dot={false} />
                </LineChart>
              </ResponsiveContainer>
            ) : (
              <div style={{ textAlign: 'center', padding: 40, color: '#999' }}>
                No attendance data
              </div>
            )}
          </Card>
        </Col>

        {/* Leave summary table */}
        <Col xs={24} md={10}>
          <Card title="Leave Utilisation by Type (last 12 months)" size="small" loading={loading}>
            <Table
              size="small"
              pagination={false}
              dataSource={leave}
              rowKey="leave_type_name"
              columns={[
                { title: 'Type', dataIndex: 'leave_type_name', ellipsis: true },
                {
                  title: 'Days',
                  dataIndex: 'days_total',
                  align: 'right',
                  render: (v: string) => Number(v).toFixed(1),
                  sorter: (a, b) => Number(a.days_total) - Number(b.days_total),
                  defaultSortOrder: 'descend',
                },
                {
                  title: 'Approved',
                  dataIndex: 'approved_count',
                  align: 'right',
                  render: (v: string) => <Tag color="green">{v}</Tag>,
                },
                {
                  title: 'Pending',
                  dataIndex: 'pending_count',
                  align: 'right',
                  render: (v: string) => Number(v) > 0 ? <Tag color="orange">{v}</Tag> : <Tag>{v}</Tag>,
                },
              ]}
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
}
