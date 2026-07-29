import { useEffect, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  DatePicker,
  Space,
  Table,
  Tag,
  Typography,
  Upload,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { UploadProps } from 'antd'
import dayjs from 'dayjs'
import {
  attendanceApi,
  type AttendanceEvent,
  type EventType,
} from '../api/attendance'
import { employeesApi, type Employee } from '../api/employees'
import { EmployeePicker } from '../components/EmployeePicker'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

export function AttendanceEventsPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const canImport = hasRole(...RoleSets.HR_TEAM_WRITE)

  const [employees, setEmployees] = useState<Employee[]>([])
  const [rows, setRows] = useState<AttendanceEvent[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(50)
  const [loading, setLoading] = useState(false)
  const [employeeId, setEmployeeId] = useState<string | undefined>()
  const [range, setRange] = useState<[dayjs.Dayjs, dayjs.Dayjs]>([
    dayjs().subtract(7, 'day').startOf('day'),
    dayjs().endOf('day'),
  ])
  const [importResult, setImportResult] = useState<{ imported: number; errors: string[] } | null>(
    null,
  )

  const load = () => {
    setLoading(true)
    attendanceApi
      .events({
        fromDate: range[0].format('YYYY-MM-DD'),
        toDate: range[1].format('YYYY-MM-DD'),
        employeeId,
        page,
        size,
      })
      .then((res) => {
        setRows(res.content)
        setTotal(res.totalElements)
      })
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load events'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    employeesApi.list({ size: 500 }).then((r) => setEmployees(r.content))
  }, [])

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, size, employeeId, range])

  const uploadProps: UploadProps = {
    accept: '.csv',
    showUploadList: false,
    customRequest: async ({ file, onSuccess, onError }) => {
      try {
        const result = await attendanceApi.importCsv(file as File)
        setImportResult(result)
        if (result.imported > 0) {
          message.success(`Imported ${result.imported} events`)
        }
        if (result.errors.length > 0) {
          message.warning(`${result.errors.length} rows had errors`)
        }
        onSuccess?.(result)
        load()
      } catch (err) {
        message.error(
          (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
            'Import failed',
        )
        onError?.(err as Error)
      }
    },
  }

  const employeeMap = new Map(employees.map((e) => [e.id, e]))

  const columns: ColumnsType<AttendanceEvent> = [
    {
      title: 'Time',
      dataIndex: 'eventTime',
      render: (v: string) => dayjs(v).format('YYYY-MM-DD HH:mm:ss'),
      width: 180,
    },
    {
      title: 'Employee',
      dataIndex: 'employeeId',
      render: (id: string) => {
        const e = employeeMap.get(id)
        return e ? `${e.employeeNo} — ${e.firstName} ${e.lastName}` : id
      },
    },
    {
      title: 'Type',
      dataIndex: 'eventType',
      render: (t: EventType) => (
        <Tag color={t === 'IN' ? 'green' : 'volcano'}>{t}</Tag>
      ),
      width: 80,
    },
    { title: 'Device', dataIndex: 'deviceId', width: 120 },
    { title: 'Location', dataIndex: 'location' },
    { title: 'Source', dataIndex: 'source', width: 90 },
  ]

  return (
    <Card
      title={<Typography.Title level={4} style={{ margin: 0 }}>Attendance events</Typography.Title>}
      extra={
        canImport && (
          <Upload {...uploadProps}>
            <Button type="primary">Import CSV</Button>
          </Upload>
        )
      }
    >
      <Space style={{ marginBottom: 12 }} wrap>
        <DatePicker.RangePicker
          value={range}
          onChange={(v) => v && v[0] && v[1] && setRange([v[0], v[1]])}
        />
        <EmployeePicker
          allowClear
          placeholder="All employees"
          style={{ width: 280 }}
          value={employeeId}
          onChange={(v) => {
            setEmployeeId(v)
            setPage(0)
          }}
        />
      </Space>

      {importResult && (
        <Alert
          type={importResult.errors.length === 0 ? 'success' : 'warning'}
          showIcon
          closable
          onClose={() => setImportResult(null)}
          style={{ marginBottom: 12 }}
          message={`Imported ${importResult.imported} events${
            importResult.errors.length > 0 ? `, ${importResult.errors.length} errors` : ''
          }`}
          description={
            importResult.errors.length > 0 ? (
              <ul style={{ margin: 0, paddingLeft: 18 }}>
                {importResult.errors.slice(0, 10).map((e, i) => (
                  <li key={i}>{e}</li>
                ))}
                {importResult.errors.length > 10 && (
                  <li>… and {importResult.errors.length - 10} more</li>
                )}
              </ul>
            ) : null
          }
        />
      )}

      <Table
        rowKey="id"
        columns={columns}
        dataSource={rows}
        loading={loading}
        pagination={{
          current: page + 1,
          pageSize: size,
          total,
          onChange: (p, s) => {
            setPage(p - 1)
            setSize(s)
          },
          showSizeChanger: true,
        }}
      />

      <Card type="inner" title="CSV format" size="small" style={{ marginTop: 12 }}>
        <Typography.Paragraph style={{ marginBottom: 8 }}>
          Header is required. Recognised columns:{' '}
          <code>employee_no</code> (or <code>employee_id</code>), <code>event_time</code>,{' '}
          <code>event_type</code> (<code>IN</code> / <code>OUT</code>), <code>device_id</code>,{' '}
          <code>device_employee_code</code>, <code>location</code>.
        </Typography.Paragraph>
        <Typography.Paragraph>
          <code style={{ display: 'block', whiteSpace: 'pre' }}>
{`employee_no,event_time,event_type,device_id,location
EMP-00001,2026-05-19T09:02:30,IN,DEV-01,Baku HQ
EMP-00001,2026-05-19T18:14:05,OUT,DEV-01,Baku HQ`}
          </code>
        </Typography.Paragraph>
      </Card>
    </Card>
  )
}
