// M490 — Policy re-acknowledgement campaign management. HR admins launch
// campaigns to require employees to re-read and re-acknowledge a specific
// policy version (e.g., after updates). Progress tracked per campaign.

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Col,
  DatePicker,
  Drawer,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Progress,
  Row,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs, { Dayjs } from 'dayjs'
import { api } from '../api/client'
import { orgApi } from '../api/org'
import { policiesApi, type PolicyResponse } from '../api/policies'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const { Title, Text } = Typography

type CampaignAudience = 'ALL' | 'DEPARTMENT'
type CampaignStatus = 'DRAFT' | 'ACTIVE' | 'CLOSED'

interface AcknowledgementCampaign {
  id: string
  policyId: string
  policyVersion: number
  name: string
  audience: CampaignAudience
  audienceRef: string | null
  dueDate: string
  status: CampaignStatus
  createdAt: string
  createdBy: string
  launchedAt: string | null
  launchedBy: string | null
  closedAt: string | null
  closedBy: string | null
}

interface CampaignProgress {
  campaignId: string
  totalAudience: number
  ackedCount: number
  byDepartment: DepartmentProgress[]
}

interface DepartmentProgress {
  deptId: string
  deptName: string
  total: number
  acked: number
}

interface OrgUnit {
  id: string
  code: string
  name: string
}

const STATUS_COLOR: Record<CampaignStatus, string> = {
  DRAFT: 'gold',
  ACTIVE: 'blue',
  CLOSED: 'default',
}

export function PolicyCampaignsPage() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canWrite = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [campaigns, setCampaigns] = useState<AcknowledgementCampaign[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm()

  const [policies, setPolicies] = useState<PolicyResponse[]>([])
  const [orgUnits, setOrgUnits] = useState<OrgUnit[]>([])
  const [selectedAudience, setSelectedAudience] = useState<CampaignAudience>('ALL')

  const [progressDrawer, setProgressDrawer] = useState<AcknowledgementCampaign | null>(null)
  const [progress, setProgress] = useState<CampaignProgress | null>(null)
  const [loadingProgress, setLoadingProgress] = useState(false)

  useEffect(() => {
    load()
    loadPolicies()
    loadOrgUnits()
  }, []) // eslint-disable-line

  const load = () => {
    setLoading(true)
    api
      .get<AcknowledgementCampaign[]>('/policies/campaigns')
      .then((r) => setCampaigns(r.data))
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load campaigns'))
      .finally(() => setLoading(false))
  }

  const loadPolicies = () => {
    policiesApi
      .list()
      .then(setPolicies)
      .catch(() => message.error('Failed to load policies'))
  }

  const loadOrgUnits = () => {
    // Org units are versioned — they hang off the active structure version,
    // and there is no flat list endpoint. (`/organization/units` was called
    // here for a while; nothing has ever served it.) No active version is a
    // legitimate state on a fresh tenant, not an error: no units to offer yet.
    orgApi
      .active()
      .then((v) => (v ? orgApi.units(v.id) : []))
      .then(setOrgUnits)
      .catch(() => message.error('Failed to load org units'))
  }

  const startCreate = () => {
    form.resetFields()
    setSelectedAudience('ALL')
    form.setFieldsValue({
      audience: 'ALL',
      dueDate: dayjs().add(14, 'days'),
    })
    setOpen(true)
  }

  const submit = async () => {
    try {
      const values = await form.validateFields()
      const payload = {
        policyId: values.policyId,
        policyVersion: values.policyVersion,
        name: values.name,
        audience: values.audience,
        audienceRef: values.audience === 'DEPARTMENT' ? values.audienceRef : null,
        dueDate: (values.dueDate as Dayjs).format('YYYY-MM-DD'),
      }
      await api.post('/policies/campaigns', payload)
      message.success('Campaign created')
      setOpen(false)
      load()
    } catch (e) {
      message.error((e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Failed to create campaign')
    }
  }

  const launch = async (id: string) => {
    try {
      await api.post(`/policies/campaigns/${id}/launch`)
      message.success('Campaign launched')
      load()
    } catch (e) {
      message.error((e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Failed to launch')
    }
  }

  const close = async (id: string) => {
    try {
      await api.post(`/policies/campaigns/${id}/close`)
      message.success('Campaign closed')
      load()
    } catch (e) {
      message.error((e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Failed to close')
    }
  }

  const openProgress = async (c: AcknowledgementCampaign) => {
    setProgressDrawer(c)
    setLoadingProgress(true)
    try {
      const r = await api.get<CampaignProgress>(`/policies/campaigns/${c.id}/progress`)
      setProgress(r.data)
    } catch (e) {
      message.error((e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Failed to load progress')
    } finally {
      setLoadingProgress(false)
    }
  }

  const cols: ColumnsType<AcknowledgementCampaign> = [
    { title: 'Name', dataIndex: 'name' },
    {
      title: 'Policy',
      render: (_, r) => {
        const p = policies.find((pol) => pol.id === r.policyId)
        return p ? `${p.code} v${r.policyVersion}` : `v${r.policyVersion}`
      },
    },
    {
      title: 'Audience',
      dataIndex: 'audience',
      render: (v: CampaignAudience, r) => {
        if (v === 'ALL') return <Tag color="purple">ALL</Tag>
        const dept = orgUnits.find((u) => u.id === r.audienceRef)
        return (
          <Space size={4}>
            <Tag color="blue">DEPARTMENT</Tag>
            {dept && <Text type="secondary">{dept.name}</Text>}
          </Space>
        )
      },
    },
    {
      title: 'Due',
      dataIndex: 'dueDate',
      render: (s: string) => dayjs(s).format('YYYY-MM-DD'),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      render: (s: CampaignStatus) => <Tag color={STATUS_COLOR[s]}>{s}</Tag>,
    },
    {
      title: '',
      width: 280,
      align: 'right',
      render: (_, r) => (
        <Space size={4}>
          {r.status !== 'DRAFT' && <Button size="small" onClick={() => openProgress(r)}>Progress</Button>}
          {canWrite && r.status === 'DRAFT' && (
            <Popconfirm title="Launch this campaign? Audience will be notified." onConfirm={() => launch(r.id)}>
              <Button size="small" type="primary">Launch</Button>
            </Popconfirm>
          )}
          {canWrite && r.status === 'ACTIVE' && (
            <Popconfirm title="Close this campaign?" onConfirm={() => close(r.id)}>
              <Button size="small" danger>Close</Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  const deptCols: ColumnsType<DepartmentProgress> = [
    { title: 'Department', dataIndex: 'deptName' },
    { title: 'Total', dataIndex: 'total', align: 'right', width: 100 },
    { title: 'Acked', dataIndex: 'acked', align: 'right', width: 100 },
    {
      title: 'Progress',
      width: 200,
      render: (_, r) => {
        const pct = r.total > 0 ? Math.round((r.acked / r.total) * 100) : 0
        return <Progress percent={pct} size="small" />
      },
    },
  ]

  if (loading) return <Spin />

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
        <Title level={3} style={{ margin: 0 }}>Policy re-acknowledgement campaigns</Title>
        {canWrite && <Button type="primary" onClick={startCreate}>New campaign</Button>}
      </Space>

      <Card>
        <Table
          rowKey="id"
          columns={cols}
          dataSource={campaigns}
          size="small"
          pagination={{ pageSize: 25 }}
          locale={{ emptyText: <Empty description="No campaigns yet" /> }}
        />
      </Card>

      <Modal
        open={open}
        title="New re-acknowledgement campaign"
        onCancel={() => setOpen(false)}
        onOk={submit}
        width={640}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="Campaign name" rules={[{ required: true, max: 240 }]}>
            <Input placeholder="e.g., Q1 2026 Code of Conduct refresh" />
          </Form.Item>
          <Row gutter={12}>
            <Col span={16}>
              <Form.Item name="policyId" label="Policy" rules={[{ required: true }]}>
                <Select
                  showSearch
                  filterOption={(input, option) =>
                    (option?.label ?? '').toLowerCase().includes(input.toLowerCase())
                  }
                  options={policies.map((p) => ({
                    value: p.id,
                    label: `${p.code} v${p.version} — ${p.title}`,
                  }))}
                  onChange={(policyId) => {
                    const p = policies.find((pol) => pol.id === policyId)
                    if (p) form.setFieldValue('policyVersion', p.version)
                  }}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="policyVersion" label="Version" rules={[{ required: true }]}>
                <Input type="number" disabled />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="audience" label="Audience" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'ALL', label: 'All employees' },
                { value: 'DEPARTMENT', label: 'Specific department' },
              ]}
              onChange={(v) => setSelectedAudience(v)}
            />
          </Form.Item>
          {selectedAudience === 'DEPARTMENT' && (
            <Form.Item name="audienceRef" label="Department" rules={[{ required: true }]}>
              <Select
                showSearch
                filterOption={(input, option) =>
                  (option?.label ?? '').toLowerCase().includes(input.toLowerCase())
                }
                options={orgUnits.map((u) => ({
                  value: u.id,
                  label: `${u.code} — ${u.name}`,
                }))}
              />
            </Form.Item>
          )}
          <Form.Item name="dueDate" label="Due date" rules={[{ required: true }]}>
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        open={!!progressDrawer}
        title={progressDrawer ? `Progress — ${progressDrawer.name}` : ''}
        onClose={() => { setProgressDrawer(null); setProgress(null) }}
        width={680}
      >
        {loadingProgress && <Spin />}
        {!loadingProgress && progress && (
          <Space direction="vertical" size="large" style={{ width: '100%' }}>
            <Card>
              <Space direction="vertical" size="small" style={{ width: '100%' }}>
                <Text strong>Overall progress</Text>
                <Progress
                  percent={
                    progress.totalAudience > 0
                      ? Math.round((progress.ackedCount / progress.totalAudience) * 100)
                      : 0
                  }
                  strokeColor="#52c41a"
                />
                <Text type="secondary">
                  {progress.ackedCount} / {progress.totalAudience} acknowledged
                </Text>
              </Space>
            </Card>
            {progress.byDepartment.length > 0 && (
              <Card title="By department">
                <Table
                  rowKey="deptId"
                  columns={deptCols}
                  dataSource={progress.byDepartment}
                  size="small"
                  pagination={false}
                />
              </Card>
            )}
          </Space>
        )}
      </Drawer>
    </Space>
  )
}
