import { useEffect, useMemo, useState } from 'react'
import {
  Button,
  Card,
  Col,
  Form,
  Input,
  Modal,
  Row,
  Select,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  learningApi,
  type Competency,
  type CompetencyCategory,
  type CompetencyRequest,
  type EmployeeCompetency,
} from '../api/learning'
import { employeesApi, type Employee } from '../api/employees'
import { useAuth } from '../auth/AuthContext'

const CATEGORIES: CompetencyCategory[] = [
  'TECHNICAL',
  'BEHAVIOURAL',
  'LEADERSHIP',
  'COMPLIANCE',
  'LANGUAGE',
  'OTHER',
]

const CATEGORY_COLOR: Record<CompetencyCategory, string> = {
  TECHNICAL: 'geekblue',
  BEHAVIOURAL: 'magenta',
  LEADERSHIP: 'purple',
  COMPLIANCE: 'red',
  LANGUAGE: 'cyan',
  OTHER: 'default',
}

export function CompetenciesPage() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canManage = hasRole('HR_ADMIN', 'SYSTEM_ADMIN')

  const [competencies, setCompetencies] = useState<Competency[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [employeeId, setEmployeeId] = useState<string | undefined>()
  const [awards, setAwards] = useState<EmployeeCompetency[]>([])
  const [loading, setLoading] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)
  const [form] = Form.useForm<CompetencyRequest>()

  useEffect(() => {
    Promise.all([
      learningApi.competencies(false),
      employeesApi.list({ size: 500 }).then((p) => p.content),
    ]).then(([c, e]) => {
      setCompetencies(c)
      setEmployees(e)
    })
  }, [])

  useEffect(() => {
    if (!employeeId) {
      setAwards([])
      return
    }
    setLoading(true)
    learningApi
      .employeeCompetencies(employeeId)
      .then(setAwards)
      .catch((err) => message.error(err?.response?.data?.message ?? 'Failed to load awards'))
      .finally(() => setLoading(false))
  }, [employeeId, message])

  const compMap = useMemo(() => new Map(competencies.map((c) => [c.id, c])), [competencies])

  const onCreate = async (v: CompetencyRequest) => {
    try {
      await learningApi.createCompetency(v)
      message.success('Competency created')
      setCreateOpen(false)
      form.resetFields()
      const c = await learningApi.competencies(false)
      setCompetencies(c)
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Save failed',
      )
    }
  }

  const competencyColumns: ColumnsType<Competency> = [
    { title: 'Code', dataIndex: 'code', width: 200 },
    { title: 'Name', dataIndex: 'name' },
    {
      title: 'Category',
      dataIndex: 'category',
      width: 150,
      render: (c: CompetencyCategory) => <Tag color={CATEGORY_COLOR[c]}>{c}</Tag>,
    },
    {
      title: 'Active',
      dataIndex: 'active',
      width: 90,
      render: (a: boolean) => (a ? <Tag color="green">YES</Tag> : <Tag>NO</Tag>),
    },
    { title: 'Description', dataIndex: 'description', ellipsis: true },
  ]

  const awardColumns: ColumnsType<EmployeeCompetency> = [
    {
      title: 'Competency',
      dataIndex: 'competencyId',
      render: (id: string) => {
        const c = compMap.get(id)
        return c ? `${c.code} — ${c.name}` : id
      },
    },
    {
      title: 'Proficiency',
      dataIndex: 'proficiency',
      width: 110,
      render: (p: number) => <Tag color="cyan">Level {p}</Tag>,
    },
    { title: 'Source', dataIndex: 'source', width: 110 },
    {
      title: 'Awarded',
      dataIndex: 'awardedAt',
      width: 130,
      render: (s: string) => s.slice(0, 10),
    },
    { title: 'Valid until', dataIndex: 'validUntil', width: 130 },
  ]

  return (
    <Card
      title={<Typography.Title level={4} style={{ margin: 0 }}>Competencies</Typography.Title>}
      extra={
        canManage && (
          <Button type="primary" onClick={() => setCreateOpen(true)}>New competency</Button>
        )
      }
    >
      <Row gutter={16}>
        <Col xs={24} md={12}>
          <Card type="inner" title="Catalogue" size="small">
            <Table
              size="small"
              rowKey="id"
              columns={competencyColumns}
              dataSource={competencies}
              pagination={false}
            />
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card
            type="inner"
            title="Awarded to employee"
            size="small"
            extra={
              <Select
                allowClear
                showSearch
                optionFilterProp="label"
                placeholder="Select employee"
                style={{ width: 240 }}
                options={employees.map((e) => ({
                  value: e.id,
                  label: `${e.employeeNo} — ${e.firstName} ${e.lastName}`,
                }))}
                value={employeeId}
                onChange={setEmployeeId}
              />
            }
          >
            {employeeId ? (
              <Table
                size="small"
                rowKey="id"
                loading={loading}
                columns={awardColumns}
                dataSource={awards}
                pagination={false}
                locale={{ emptyText: 'No competencies awarded yet' }}
              />
            ) : (
              <Typography.Text type="secondary">
                Select an employee to see their awarded competencies.
              </Typography.Text>
            )}
          </Card>
        </Col>
      </Row>

      <Modal
        open={createOpen}
        title="New competency"
        onCancel={() => setCreateOpen(false)}
        onOk={() => form.submit()}
        okText="Create"
      >
        <Form form={form} layout="vertical" onFinish={onCreate} initialValues={{ active: true }}>
          <Form.Item name="code" label="Code" rules={[{ required: true, max: 64 }]}>
            <Input placeholder="e.g. INFOSEC_AWARENESS" />
          </Form.Item>
          <Form.Item name="name" label="Name" rules={[{ required: true, max: 200 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="category" label="Category" rules={[{ required: true }]}>
            <Select options={CATEGORIES.map((c) => ({ value: c, label: c }))} />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
