import { useEffect, useMemo, useState } from 'react'
import {
  Button,
  Card,
  Col,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Tabs,
  Tooltip,
  Typography,
  App as AntdApp,
} from 'antd'
import {
  ArrowUpOutlined,
  CheckCircleOutlined,
  DeleteOutlined,
  PlusOutlined,
  RiseOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import {
  learningApi,
  type Competency,
  type CompetencyCategory,
  type CompetencyRequest,
  type EmployeeCompetency,
  type GapItem,
  type PositionRequirement,
} from '../api/learning'
import { employeesApi, type Employee } from '../api/employees'
import { positionsApi, type Position } from '../api/positions'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

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

const LEVEL_LABELS: Record<number, string> = {
  1: 'Awareness',
  2: 'Basic',
  3: 'Proficient',
  4: 'Advanced',
  5: 'Expert',
}

function gapTag(gap: number, _currentProficiency: number | null) {
  if (gap <= 0) {
    return (
      <Tag color="success" icon={<CheckCircleOutlined />}>
        {gap < 0 ? 'Exceeds' : 'Met'}
      </Tag>
    )
  }
  if (gap === 1) return <Tag color="warning" icon={<ArrowUpOutlined />}>Gap: 1</Tag>
  return <Tag color="error" icon={<RiseOutlined />}>Gap: {gap}</Tag>
}

// ── Catalogue & Awards tab ───────────────────────────────────────────────────

function CatalogueTab({
  competencies,
  employees,
  canManage,
}: {
  competencies: Competency[]
  employees: Employee[]
  canManage: boolean
}) {
  const { message } = AntdApp.useApp()
  const [employeeId, setEmployeeId] = useState<string | undefined>()
  const [awards, setAwards] = useState<EmployeeCompetency[]>([])
  const [loading, setLoading] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)
  const [allCompetencies, setAll] = useState(competencies)
  const [form] = Form.useForm<CompetencyRequest>()

  const compMap = useMemo(() => new Map(allCompetencies.map((c) => [c.id, c])), [allCompetencies])

  useEffect(() => {
    setAll(competencies)
  }, [competencies])

  useEffect(() => {
    if (!employeeId) { setAwards([]); return }
    setLoading(true)
    learningApi
      .employeeCompetencies(employeeId)
      .then(setAwards)
      .catch(() => message.error('Failed to load awards'))
      .finally(() => setLoading(false))
  }, [employeeId, message])

  const onCreate = async (v: CompetencyRequest) => {
    try {
      await learningApi.createCompetency(v)
      message.success('Competency created')
      setCreateOpen(false)
      form.resetFields()
      const c = await learningApi.competencies(false)
      setAll(c)
    } catch {
      message.error('Save failed')
    }
  }

  const competencyColumns: ColumnsType<Competency> = [
    { title: 'Code', dataIndex: 'code', width: 200 },
    { title: 'Name', dataIndex: 'name' },
    {
      title: 'Category', dataIndex: 'category', width: 150,
      render: (c: CompetencyCategory) => <Tag color={CATEGORY_COLOR[c]}>{c}</Tag>,
    },
    {
      title: 'Active', dataIndex: 'active', width: 90,
      render: (a: boolean) => (a ? <Tag color="green">YES</Tag> : <Tag>NO</Tag>),
    },
    { title: 'Description', dataIndex: 'description', ellipsis: true },
  ]

  const awardColumns: ColumnsType<EmployeeCompetency> = [
    {
      title: 'Competency', dataIndex: 'competencyId',
      render: (id: string) => { const c = compMap.get(id); return c ? `${c.code} — ${c.name}` : id },
    },
    {
      title: 'Level', dataIndex: 'proficiency', width: 130,
      render: (p: number) => (
        <Tag color="cyan">Level {p} — {LEVEL_LABELS[p] ?? ''}</Tag>
      ),
    },
    { title: 'Source', dataIndex: 'source', width: 110 },
    { title: 'Awarded', dataIndex: 'awardedAt', width: 130, render: (s: string) => s.slice(0, 10) },
    { title: 'Valid until', dataIndex: 'validUntil', width: 130 },
  ]

  return (
    <>
      <Row gutter={16}>
        <Col xs={24} md={12}>
          <Card
            type="inner"
            title="Catalogue"
            size="small"
            extra={canManage && (
              <Button size="small" type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
                New
              </Button>
            )}
          >
            <Table size="small" rowKey="id" columns={competencyColumns} dataSource={allCompetencies} pagination={false} />
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card
            type="inner"
            title="Awarded to employee"
            size="small"
            extra={
              <Select
                allowClear showSearch optionFilterProp="label"
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
                size="small" rowKey="id" loading={loading}
                columns={awardColumns} dataSource={awards}
                pagination={false}
                locale={{ emptyText: 'No competencies awarded yet' }}
              />
            ) : (
              <Typography.Text type="secondary">Select an employee to see their awarded competencies.</Typography.Text>
            )}
          </Card>
        </Col>
      </Row>

      <Modal open={createOpen} title="New competency" onCancel={() => setCreateOpen(false)} onOk={() => form.submit()} okText="Create">
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
    </>
  )
}

// ── Gap Analysis tab ─────────────────────────────────────────────────────────

function GapAnalysisTab({ employees }: { employees: Employee[] }) {
  const { message } = AntdApp.useApp()
  const [employeeId, setEmployeeId] = useState<string | undefined>()
  const [gaps, setGaps] = useState<GapItem[]>([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!employeeId) { setGaps([]); return }
    setLoading(true)
    learningApi
      .gapAnalysis(employeeId)
      .then(setGaps)
      .catch((err: { response?: { data?: { message?: string } } }) =>
        message.error(err?.response?.data?.message ?? 'Failed to load gap analysis'))
      .finally(() => setLoading(false))
  }, [employeeId, message])

  const gapColumns: ColumnsType<GapItem> = [
    {
      title: 'Competency',
      render: (_: unknown, r: GapItem) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{r.competencyCode}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>{r.competencyName}</Typography.Text>
        </Space>
      ),
    },
    {
      title: 'Category', dataIndex: 'competencyCategory', width: 130,
      render: (c: string) => <Tag color={CATEGORY_COLOR[c as CompetencyCategory] ?? 'default'}>{c}</Tag>,
    },
    {
      title: 'Required', dataIndex: 'requiredProficiency', width: 140, align: 'center',
      render: (p: number) => <Tag color="purple">Level {p} — {LEVEL_LABELS[p]}</Tag>,
    },
    {
      title: 'Current', dataIndex: 'currentProficiency', width: 140, align: 'center',
      render: (p: number | null) =>
        p != null
          ? <Tag color="cyan">Level {p} — {LEVEL_LABELS[p]}</Tag>
          : <Tag>Not assessed</Tag>,
    },
    {
      title: 'Gap', dataIndex: 'gap', width: 110, align: 'center',
      render: (gap: number, r: GapItem) => gapTag(gap, r.currentProficiency),
    },
    {
      title: 'Recommended courses',
      render: (_: unknown, r: GapItem) => {
        if (r.gap <= 0) return <Typography.Text type="secondary" style={{ fontSize: 12 }}>—</Typography.Text>
        if (r.recommendedCourses.length === 0)
          return <Typography.Text type="secondary" style={{ fontSize: 12 }}>No published courses found</Typography.Text>
        return (
          <Space wrap>
            {r.recommendedCourses.map((c) => (
              <Tooltip key={c.courseId} title={`Awards Level ${c.awardedLevel} — ${LEVEL_LABELS[c.awardedLevel]}`}>
                <Tag style={{ cursor: 'default' }}>{c.courseCode} — {c.courseTitle}</Tag>
              </Tooltip>
            ))}
          </Space>
        )
      },
    },
  ]

  const metCount = gaps.filter((g) => g.gap <= 0).length
  const gapCount = gaps.filter((g) => g.gap > 0).length

  return (
    <Card type="inner" title="Competency Gap Analysis" size="small">
      <Space style={{ marginBottom: 16 }}>
        <Select
          allowClear showSearch optionFilterProp="label"
          placeholder="Select employee"
          style={{ width: 280 }}
          options={employees.map((e) => ({
            value: e.id,
            label: `${e.employeeNo} — ${e.firstName} ${e.lastName}`,
          }))}
          value={employeeId}
          onChange={setEmployeeId}
        />
        {gaps.length > 0 && (
          <Space>
            <Tag color="error">Gaps: {gapCount}</Tag>
            <Tag color="success">Met / exceeds: {metCount}</Tag>
          </Space>
        )}
      </Space>

      {!employeeId && (
        <Typography.Text type="secondary">
          Select an employee to see how their current competency levels compare to their position requirements.
        </Typography.Text>
      )}

      {employeeId && (
        <Table
          size="small"
          rowKey="competencyId"
          loading={loading}
          columns={gapColumns}
          dataSource={gaps}
          pagination={false}
          rowClassName={(r: GapItem) =>
            r.gap > 1 ? 'row-danger' : r.gap === 1 ? 'row-warning' : ''
          }
          locale={{ emptyText: 'No position requirements defined — configure them in the Position Requirements tab.' }}
        />
      )}
    </Card>
  )
}

// ── Position Requirements tab (HR_ADMIN / SYSTEM_ADMIN) ──────────────────────

function PositionRequirementsTab({ competencies }: { competencies: Competency[] }) {
  const { message } = AntdApp.useApp()
  const [positions, setPositions] = useState<Position[]>([])
  const [positionId, setPositionId] = useState<string | undefined>()
  const [reqs, setReqs] = useState<PositionRequirement[]>([])
  const [loading, setLoading] = useState(false)
  const [addOpen, setAddOpen] = useState(false)
  const [form] = Form.useForm<{ competencyId: string; requiredProficiency: number; notes?: string }>()

  useEffect(() => {
    positionsApi.list({ size: 500 }).then((p) => setPositions(p.content)).catch(() => {})
  }, [])

  useEffect(() => {
    if (!positionId) { setReqs([]); return }
    setLoading(true)
    learningApi
      .positionRequirements(positionId)
      .then(setReqs)
      .finally(() => setLoading(false))
  }, [positionId])

  const onAdd = async (v: { competencyId: string; requiredProficiency: number; notes?: string }) => {
    if (!positionId) return
    try {
      await learningApi.addPositionRequirement(positionId, v)
      message.success('Requirement saved')
      setAddOpen(false)
      form.resetFields()
      const r = await learningApi.positionRequirements(positionId)
      setReqs(r)
    } catch {
      message.error('Save failed')
    }
  }

  const onDelete = async (competencyId: string) => {
    if (!positionId) return
    try {
      await learningApi.removePositionRequirement(positionId, competencyId)
      setReqs((prev) => prev.filter((r) => r.competencyId !== competencyId))
      message.success('Requirement removed')
    } catch {
      message.error('Delete failed')
    }
  }

  const reqColumns: ColumnsType<PositionRequirement> = [
    {
      title: 'Competency',
      render: (_: unknown, r: PositionRequirement) => `${r.competencyCode} — ${r.competencyName}`,
    },
    {
      title: 'Category', dataIndex: 'competencyCategory', width: 130,
      render: (c: string) => <Tag color={CATEGORY_COLOR[c as CompetencyCategory] ?? 'default'}>{c}</Tag>,
    },
    {
      title: 'Required level', dataIndex: 'requiredProficiency', width: 160, align: 'center',
      render: (p: number) => <Tag color="purple">Level {p} — {LEVEL_LABELS[p]}</Tag>,
    },
    { title: 'Notes', dataIndex: 'notes', ellipsis: true },
    {
      title: '', key: 'action', width: 60, align: 'center',
      render: (_: unknown, r: PositionRequirement) => (
        <Popconfirm title="Remove this requirement?" onConfirm={() => onDelete(r.competencyId)}>
          <Button size="small" danger icon={<DeleteOutlined />} />
        </Popconfirm>
      ),
    },
  ]

  return (
    <Card
      type="inner"
      title="Position Competency Requirements"
      size="small"
      extra={
        positionId && (
          <Button size="small" type="primary" icon={<PlusOutlined />} onClick={() => setAddOpen(true)}>
            Add requirement
          </Button>
        )
      }
    >
      <Space style={{ marginBottom: 16 }}>
        <Select
          allowClear showSearch optionFilterProp="label"
          placeholder="Select position"
          style={{ width: 320 }}
          options={positions.map((p) => ({
            value: p.id,
            label: `${p.code} — ${p.title}`,
          }))}
          value={positionId}
          onChange={setPositionId}
        />
      </Space>

      {!positionId && (
        <Typography.Text type="secondary">
          Select a position to view and manage its required competency levels.
        </Typography.Text>
      )}

      {positionId && (
        <Table
          size="small"
          rowKey="competencyId"
          loading={loading}
          columns={reqColumns}
          dataSource={reqs}
          pagination={false}
          locale={{ emptyText: 'No requirements defined for this position yet.' }}
        />
      )}

      <Modal
        open={addOpen}
        title="Add / update competency requirement"
        onCancel={() => { setAddOpen(false); form.resetFields() }}
        onOk={() => form.submit()}
        okText="Save"
      >
        <Form form={form} layout="vertical" onFinish={onAdd}>
          <Form.Item name="competencyId" label="Competency" rules={[{ required: true }]}>
            <Select
              showSearch optionFilterProp="label"
              options={competencies
                .filter((c) => c.active)
                .map((c) => ({ value: c.id, label: `${c.code} — ${c.name}` }))}
            />
          </Form.Item>
          <Form.Item
            name="requiredProficiency"
            label="Required proficiency (1 = Awareness, 5 = Expert)"
            rules={[{ required: true }]}
          >
            <InputNumber min={1} max={5} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}

// ── Root page ────────────────────────────────────────────────────────────────

export function CompetenciesPage() {
  const { hasRole } = useAuth()
  const canManage = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [competencies, setCompetencies] = useState<Competency[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])

  useEffect(() => {
    Promise.all([
      learningApi.competencies(false),
      employeesApi.list({ size: 500 }).then((p) => p.content),
    ]).then(([c, e]) => {
      setCompetencies(c)
      setEmployees(e)
    })
  }, [])

  const tabs = [
    {
      key: 'catalogue',
      label: 'Catalogue & Awards',
      children: (
        <CatalogueTab competencies={competencies} employees={employees} canManage={canManage} />
      ),
    },
    {
      key: 'gap',
      label: 'Gap Analysis',
      children: <GapAnalysisTab employees={employees} />,
    },
    ...(canManage
      ? [
          {
            key: 'requirements',
            label: 'Position Requirements',
            children: <PositionRequirementsTab competencies={competencies} />,
          },
        ]
      : []),
  ]

  return (
    <Card title={<Typography.Title level={4} style={{ margin: 0 }}>Competencies</Typography.Title>}>
      <Tabs items={tabs} />
    </Card>
  )
}
