import { useState } from 'react'
import {
  Button,
  DatePicker,
  message,
  Select,
  Space,
  Table,
  Typography,
} from 'antd'
import { PlayCircleOutlined } from '@ant-design/icons'
import { attendanceApi } from '../api/attendance'
import dayjs, { type Dayjs } from 'dayjs'

const { Title, Text } = Typography
const { RangePicker } = DatePicker

const REPORT_TYPES = [
  { label: 'Daily Roll Call', value: 'DAILY_ROLL_CALL' },
  { label: 'Monthly Summary', value: 'MONTHLY_SUMMARY' },
  { label: 'Lateness Analysis', value: 'LATENESS_ANALYSIS' },
  { label: 'Absence Trend', value: 'ABSENCE_TREND' },
  { label: 'Overtime Analysis', value: 'OT_ANALYSIS' },
  { label: 'Exception Report', value: 'EXCEPTION_REPORT' },
  { label: 'Correction History', value: 'CORRECTION_HISTORY' },
]

export function AttendanceReportsPage() {
  const [reportType, setReportType] = useState<string>('DAILY_ROLL_CALL')
  const [dateRange, setDateRange] = useState<[Dayjs, Dayjs]>([
    dayjs().subtract(7, 'days'),
    dayjs(),
  ])
  const [data, setData] = useState<any[]>([])
  const [columns, setColumns] = useState<any[]>([])
  const [loading, setLoading] = useState(false)

  function handleRun() {
    if (!reportType || !dateRange) {
      message.warning('Please select report type and date range')
      return
    }

    setLoading(true)
    const [from, to] = dateRange
    attendanceApi.attendanceReport(reportType, from.format('YYYY-MM-DD'), to.format('YYYY-MM-DD'))
      .then((rows) => {
        setData(rows)
        if (rows.length > 0) {
          // Derive columns from the first row
          const cols = Object.keys(rows[0]).map((key) => ({
            title: key.replace(/_/g, ' ').replace(/\b\w/g, (l: string) => l.toUpperCase()),
            dataIndex: key,
            key,
            ellipsis: true,
          }))
          setColumns(cols)
        } else {
          setColumns([])
        }
        message.success(`${rows.length} rows returned`)
      })
      .catch(() => message.error('Failed to run report'))
      .finally(() => setLoading(false))
  }

  return (
    <div style={{ padding: 24 }}>
      <Title level={3} style={{ marginBottom: 16 }}>Attendance Reports</Title>

      <Space direction="vertical" size="middle" style={{ width: '100%', marginBottom: 24 }}>
        <Space size="middle">
          <Select
            value={reportType}
            onChange={setReportType}
            style={{ width: 240 }}
            options={REPORT_TYPES}
          />
          <RangePicker
            value={dateRange}
            onChange={(dates) => dates && setDateRange(dates as [Dayjs, Dayjs])}
            format="YYYY-MM-DD"
          />
          <Button type="primary" icon={<PlayCircleOutlined />} onClick={handleRun} loading={loading}>
            Run Report
          </Button>
        </Space>

        <Text type="secondary">
          Select a report type and date range, then click Run to generate results.
        </Text>
      </Space>

      {data.length > 0 ? (
        <Table
          rowKey={(_, i) => i ?? 0}
          dataSource={data}
          columns={columns}
          loading={loading}
          size="small"
          pagination={{ pageSize: 50 }}
          scroll={{ x: 'max-content' }}
        />
      ) : (
        !loading && (
          <div style={{ textAlign: 'center', padding: 40 }}>
            <Text type="secondary">No report run yet. Select a type and date range above.</Text>
          </div>
        )
      )}
    </div>
  )
}
