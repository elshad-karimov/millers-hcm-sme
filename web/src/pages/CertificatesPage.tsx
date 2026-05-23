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
  type Certificate,
  type Course,
} from '../api/learning'
import { employeesApi, type Employee } from '../api/employees'

export function CertificatesPage() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()

  const [rows, setRows] = useState<Certificate[]>([])
  const [courses, setCourses] = useState<Course[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [employeeId, setEmployeeId] = useState<string | undefined>()
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
      .certificates(employeeId)
      .then(setRows)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load certificates'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [employeeId])

  const courseMap = useMemo(() => new Map(courses.map((c) => [c.id, c])), [courses])
  const empMap = useMemo(() => new Map(employees.map((e) => [e.id, e])), [employees])

  const columns: ColumnsType<Certificate> = [
    { title: 'Cert #', dataIndex: 'certificateNo', width: 140 },
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
    { title: 'Issued', dataIndex: 'issuedAt', width: 130, render: (s: string) => s.slice(0, 10) },
    { title: 'Valid until', dataIndex: 'validUntil', width: 130 },
    {
      title: 'Score',
      dataIndex: 'scorePercent',
      width: 90,
      render: (s: number) => <Tag color="green">{s}%</Tag>,
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
    <Card title={<Typography.Title level={4} style={{ margin: 0 }}>Certificates</Typography.Title>}>
      <Space style={{ marginBottom: 12 }} wrap>
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          placeholder="All employees"
          style={{ width: 280 }}
          options={employees.map((e) => ({
            value: e.id,
            label: `${e.employeeNo} — ${e.firstName} ${e.lastName}`,
          }))}
          value={employeeId}
          onChange={setEmployeeId}
        />
      </Space>
      <Table rowKey="id" loading={loading} columns={columns} dataSource={rows} pagination={false} />
    </Card>
  )
}
