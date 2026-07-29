import { useEffect, useMemo, useState } from 'react'
import {
  Button,
  Card,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate } from 'react-router-dom'
import {
  learningApi,
  type Course,
  type Enrollment,
  type EnrollmentStatus,
} from '../api/learning'
import { employeesApi, type Employee } from '../api/employees'
import { EmployeePicker } from '../components/EmployeePicker'

const STATUS_COLOR: Record<EnrollmentStatus, string> = {
  ENROLLED: 'blue',
  IN_PROGRESS: 'gold',
  PASSED: 'green',
  FAILED: 'red',
  WITHDRAWN: 'default',
  EXPIRED: 'volcano',
}

export function MyLearningPage() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()

  const [rows, setRows] = useState<Enrollment[]>([])
  const [courses, setCourses] = useState<Course[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [employeeId, setEmployeeId] = useState<string | undefined>()
  const [status, setStatus] = useState<EnrollmentStatus | undefined>()
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    Promise.all([
      learningApi.courses({ size: 500 }).then((p) => p.content),
      employeesApi.list({ size: 500 }).then((p) => p.content),
    ]).then(([c, e]) => {
      setCourses(c)
      setEmployees(e)
    })
  }, [])

  const load = () => {
    setLoading(true)
    learningApi
      .enrollments({ employeeId, status, size: 200 })
      .then((res) => setRows(res.content))
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load enrollments'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [employeeId, status])

  const courseMap = useMemo(() => new Map(courses.map((c) => [c.id, c])), [courses])
  const empMap = useMemo(() => new Map(employees.map((e) => [e.id, e])), [employees])

  const columns: ColumnsType<Enrollment> = [
    { title: 'Enroll #', dataIndex: 'enrollmentNo', width: 120 },
    {
      title: 'Employee',
      dataIndex: 'employeeId',
      render: (id: string) => {
        const e = empMap.get(id)
        return e ? `${e.employeeNo} ${e.lastName} ${e.firstName}` : id
      },
    },
    {
      title: 'Course',
      dataIndex: 'courseId',
      render: (id: string) => {
        const c = courseMap.get(id)
        return c ? `${c.courseNo} — ${c.title}` : id
      },
    },
    { title: 'Enrolled', dataIndex: 'enrolledAt', width: 130, render: (s: string) => s.slice(0, 10) },
    { title: 'Due', dataIndex: 'dueDate', width: 110 },
    {
      title: 'Attempts',
      dataIndex: 'attemptsUsed',
      width: 90,
      render: (n: number, r) => {
        const c = courseMap.get(r.courseId)
        return c ? `${n} / ${c.maxAttempts}` : n
      },
    },
    {
      title: 'Best',
      dataIndex: 'bestScorePercent',
      width: 90,
      render: (s: number | null) => (s != null ? `${s}%` : '—'),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 120,
      render: (s: EnrollmentStatus) => <Tag color={STATUS_COLOR[s]}>{s}</Tag>,
    },
    {
      title: '',
      width: 110,
      render: (_, r) => (
        <Button size="small" onClick={() => navigate(`/learning/courses/${r.courseId}`)}>
          Open course
        </Button>
      ),
    },
  ]

  return (
    <Card
      title={<Typography.Title level={4} style={{ margin: 0 }}>My learning</Typography.Title>}
    >
      <Space style={{ marginBottom: 12 }} wrap>
        <EmployeePicker
          allowClear
          placeholder="All employees"
          style={{ width: 280 }}
          value={employeeId}
          onChange={setEmployeeId}
        />
        <Select
          allowClear
          placeholder="All statuses"
          style={{ width: 180 }}
          options={(['ENROLLED','IN_PROGRESS','PASSED','FAILED','WITHDRAWN','EXPIRED'] as EnrollmentStatus[])
            .map((s) => ({ value: s, label: s }))}
          value={status}
          onChange={setStatus}
        />
      </Space>
      <Table rowKey="id" loading={loading} columns={columns} dataSource={rows} pagination={false} />
    </Card>
  )
}
