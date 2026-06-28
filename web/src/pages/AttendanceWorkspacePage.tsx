import { useCallback, useEffect, useState } from 'react'
import {
  Card,
  Col,
  DatePicker,
  message,
  Row,
  Statistic,
  Table,
  Typography,
} from 'antd'
import {
  UserOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ClockCircleOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { attendanceApi, type AttendanceWorkspace } from '../api/attendance'
import dayjs, { type Dayjs } from 'dayjs'

const { Title, Text } = Typography

export function AttendanceWorkspacePage() {
  const [data, setData] = useState<AttendanceWorkspace | null>(null)
  const [loading, setLoading] = useState(false)
  const [selectedDate, setSelectedDate] = useState<Dayjs>(dayjs())

  const load = useCallback((date: Dayjs) => {
    setLoading(true)
    attendanceApi.workspace(date.format('YYYY-MM-DD'))
      .then(setData)
      .catch(() => message.error('Failed to load workspace'))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => { load(selectedDate) }, [load, selectedDate])

  function handleDateChange(date: Dayjs | null) {
    if (date) {
      setSelectedDate(date)
    }
  }

  const lateColumns = [
    {
      title: 'Employee ID',
      dataIndex: 'employeeId',
      render: (v: string) => <Text code>{v?.substring(0, 8)}...</Text>,
    },
    {
      title: 'Name',
      dataIndex: 'employeeName',
    },
    {
      title: 'Clock In',
      dataIndex: 'clockIn',
    },
    {
      title: 'Late Minutes',
      dataIndex: 'lateMinutes',
      render: (v: number) => `${v} min`,
    },
  ]

  const absentColumns = [
    {
      title: 'Employee ID',
      dataIndex: 'employeeId',
      render: (v: string) => <Text code>{v?.substring(0, 8)}...</Text>,
    },
    {
      title: 'Name',
      dataIndex: 'employeeName',
    },
    {
      title: 'Department',
      dataIndex: 'department',
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <Title level={3} style={{ margin: 0 }}>Attendance Workspace</Title>
        <DatePicker
          value={selectedDate}
          onChange={handleDateChange}
          format="YYYY-MM-DD"
          style={{ width: 200 }}
        />
      </div>

      <Text type="secondary" style={{ display: 'block', marginBottom: 24 }}>
        Daily attendance dashboard for {selectedDate.format('YYYY-MM-DD')}.
      </Text>

      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={6}>
          <Card>
            <Statistic
              title="Total Employees"
              value={data?.totalEmployees ?? 0}
              prefix={<UserOutlined />}
              loading={loading}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="Present"
              value={data?.presentCount ?? 0}
              prefix={<CheckCircleOutlined style={{ color: '#52c41a' }} />}
              valueStyle={{ color: '#52c41a' }}
              loading={loading}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="Absent"
              value={data?.absentCount ?? 0}
              prefix={<CloseCircleOutlined style={{ color: '#ff4d4f' }} />}
              valueStyle={{ color: '#ff4d4f' }}
              loading={loading}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="Late"
              value={data?.lateCount ?? 0}
              prefix={<ClockCircleOutlined style={{ color: '#faad14' }} />}
              valueStyle={{ color: '#faad14' }}
              loading={loading}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={8}>
          <Card>
            <Statistic
              title="Missing Clock-Out"
              value={data?.missingClockOutCount ?? 0}
              prefix={<WarningOutlined style={{ color: '#ff4d4f' }} />}
              loading={loading}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic
              title="Pending Corrections"
              value={data?.pendingCorrections ?? 0}
              prefix={<ClockCircleOutlined style={{ color: '#1890ff' }} />}
              loading={loading}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic
              title="Open Exceptions"
              value={data?.openExceptions ?? 0}
              prefix={<WarningOutlined style={{ color: '#faad14' }} />}
              loading={loading}
            />
          </Card>
        </Col>
      </Row>

      <Title level={4} style={{ marginTop: 32, marginBottom: 16 }}>Late Employees</Title>
      <Table
        rowKey={(r) => r.employeeId || Math.random()}
        dataSource={data?.lateEmployees ?? []}
        columns={lateColumns}
        loading={loading}
        size="small"
        pagination={false}
      />

      <Title level={4} style={{ marginTop: 32, marginBottom: 16 }}>Absent Employees</Title>
      <Table
        rowKey={(r) => r.employeeId || Math.random()}
        dataSource={data?.absentEmployees ?? []}
        columns={absentColumns}
        loading={loading}
        size="small"
        pagination={false}
      />
    </div>
  )
}
