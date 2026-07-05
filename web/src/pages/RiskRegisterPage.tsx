import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Col,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Table,
  Tag,
  App as AntdApp,
  Alert,
} from 'antd'
import { PlusOutlined, CheckOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import type { ColumnsType } from 'antd/es/table'
import {
  riskAssessmentsApi,
  type RiskAssessmentResponse,
  type RiskAssessmentStatus,
  type RiskBand,
  RISK_ASSESSMENT_STATUS_OPTIONS,
  RISK_BAND_COLOR,
} from '../api/ehs'
import { orgApi, type OrgUnitResponse } from '../api/org'
import { locationApi, type LocationResponse } from '../api/location'

export function RiskRegisterPage() {
  const { message } = AntdApp.useApp()
  const [loading, setLoading] = useState(false)
  const [assessments, setAssessments] = useState<RiskAssessmentResponse[]>([])
  const [filterStatus, setFilterStatus] = useState<RiskAssessmentStatus | undefined>()
  const [filterRiskBand, setFilterRiskBand] = useState<RiskBand | undefined>()

  const [modalOpen, setModalOpen] = useState(false)
  const [form] = Form.useForm()
  const [submitting, setSubmitting] = useState(false)
  const [editing, setEditing] = useState<RiskAssessmentResponse | null>(null)

  const [orgUnits, setOrgUnits] = useState<OrgUnitResponse[]>([])
  const [locations, setLocations] = useState<LocationResponse[]>([])

  // Live score preview
  const [likelihood, setLikelihood] = useState<number>(1)
  const [impact, setImpact] = useState<number>(1)

  useEffect(() => {
    loadAssessments()
    loadOrgUnits()
    loadLocations()
  }, [])

  const loadAssessments = async () => {
    setLoading(true)
    try {
      const data = await riskAssessmentsApi.list()
      setAssessments(data)
    } catch (err) {
      message.error('Failed to load risk assessments')
    } finally {
      setLoading(false)
    }
  }

  const loadOrgUnits = async () => {
    try {
      const activeVersion = await orgApi.active()
      if (activeVersion) {
        const units = await orgApi.units(activeVersion.id)
        setOrgUnits(units)
      }
    } catch {
      // non-critical
    }
  }

  const loadLocations = async () => {
    try {
      const data = await locationApi.list(true)
      setLocations(data)
    } catch {
      // non-critical
    }
  }

  const openCreateModal = () => {
    setEditing(null)
    form.resetFields()
    setLikelihood(1)
    setImpact(1)
    setModalOpen(true)
  }

  const openEditModal = (assessment: RiskAssessmentResponse) => {
    setEditing(assessment)
    setLikelihood(assessment.likelihood)
    setImpact(assessment.impact)
    form.setFieldsValue({
      workLocationId: assessment.workLocationId,
      orgUnitId: assessment.orgUnitId,
      jobTask: assessment.jobTask,
      hazard: assessment.hazard,
      likelihood: assessment.likelihood,
      impact: assessment.impact,
      controlMeasures: assessment.controlMeasures,
      responsibleUsername: assessment.responsibleUsername,
      reviewDate: assessment.reviewDate ? dayjs(assessment.reviewDate) : undefined,
    })
    setModalOpen(true)
  }

  const handleSubmit = async (values: any) => {
    setSubmitting(true)
    try {
      if (editing) {
        await riskAssessmentsApi.update(editing.id, {
          jobTask: values.jobTask,
          hazard: values.hazard,
          likelihood: values.likelihood,
          impact: values.impact,
          controlMeasures: values.controlMeasures || undefined,
          responsibleUsername: values.responsibleUsername || undefined,
          reviewDate: values.reviewDate?.format('YYYY-MM-DD') || undefined,
        })
        message.success('Assessment updated')
      } else {
        await riskAssessmentsApi.create({
          workLocationId: values.workLocationId || undefined,
          orgUnitId: values.orgUnitId || undefined,
          jobTask: values.jobTask,
          hazard: values.hazard,
          likelihood: values.likelihood,
          impact: values.impact,
          controlMeasures: values.controlMeasures || undefined,
          responsibleUsername: values.responsibleUsername || undefined,
          reviewDate: values.reviewDate?.format('YYYY-MM-DD') || undefined,
        })
        message.success('Assessment created')
      }
      setModalOpen(false)
      form.resetFields()
      loadAssessments()
    } catch (err) {
      message.error((err as any)?.response?.data?.message || 'Failed to save assessment')
    } finally {
      setSubmitting(false)
    }
  }

  const handleApprove = async (id: string) => {
    try {
      await riskAssessmentsApi.approve(id)
      message.success('Assessment approved')
      loadAssessments()
    } catch (err) {
      message.error((err as any)?.response?.data?.message || 'Failed to approve')
    }
  }

  const filteredAssessments = assessments.filter((a) => {
    if (filterStatus && a.status !== filterStatus) return false
    if (filterRiskBand && a.riskBand !== filterRiskBand) return false
    return true
  })

  const computeRiskScore = (l: number, i: number) => l * i
  const computeRiskBand = (score: number): RiskBand => {
    if (score >= 15) return 'HIGH'
    if (score >= 5) return 'MEDIUM'
    return 'LOW'
  }

  const liveScore = computeRiskScore(likelihood, impact)
  const liveBand = computeRiskBand(liveScore)

  const columns: ColumnsType<RiskAssessmentResponse> = [
    {
      title: 'Job task',
      dataIndex: 'jobTask',
      key: 'jobTask',
      width: 200,
      ellipsis: true,
    },
    {
      title: 'Hazard',
      dataIndex: 'hazard',
      key: 'hazard',
      width: 200,
      ellipsis: true,
    },
    {
      title: 'L',
      dataIndex: 'likelihood',
      key: 'likelihood',
      width: 60,
      align: 'center',
    },
    {
      title: 'I',
      dataIndex: 'impact',
      key: 'impact',
      width: 60,
      align: 'center',
    },
    {
      title: 'Score',
      dataIndex: 'riskScore',
      key: 'riskScore',
      width: 80,
      align: 'center',
      sorter: (a, b) => a.riskScore - b.riskScore,
    },
    {
      title: 'Risk band',
      dataIndex: 'riskBand',
      key: 'riskBand',
      width: 110,
      render: (val: RiskBand) => <Tag color={RISK_BAND_COLOR[val]}>{val}</Tag>,
    },
    {
      title: 'Control measures',
      dataIndex: 'controlMeasures',
      key: 'controlMeasures',
      ellipsis: true,
      render: (text) => text || '—',
    },
    {
      title: 'Responsible',
      dataIndex: 'responsibleUsername',
      key: 'responsibleUsername',
      width: 120,
      render: (text) => text || '—',
    },
    {
      title: 'Review date',
      dataIndex: 'reviewDate',
      key: 'reviewDate',
      width: 110,
      render: (text) => text || '—',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (val: RiskAssessmentStatus) => (
        <Tag color={val === 'APPROVED' ? 'green' : 'default'}>{val}</Tag>
      ),
    },
    {
      title: 'Action',
      key: 'action',
      width: 140,
      render: (_, record) => (
        <Space size="small">
          <Button size="small" onClick={() => openEditModal(record)}>
            Edit
          </Button>
          {record.status === 'DRAFT' && (
            <Button
              size="small"
              type="primary"
              icon={<CheckOutlined />}
              onClick={() => handleApprove(record.id)}
            >
              Approve
            </Button>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <Card
        title="Risk Register"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal}>
            New assessment
          </Button>
        }
      >
        <Space style={{ marginBottom: 16 }} wrap>
          <Select
            placeholder="Filter by status"
            style={{ width: 150 }}
            allowClear
            value={filterStatus}
            onChange={setFilterStatus}
            options={RISK_ASSESSMENT_STATUS_OPTIONS}
          />
          <Select
            placeholder="Filter by risk band"
            style={{ width: 150 }}
            allowClear
            value={filterRiskBand}
            onChange={setFilterRiskBand}
            options={[
              { value: 'LOW', label: 'Low' },
              { value: 'MEDIUM', label: 'Medium' },
              { value: 'HIGH', label: 'High' },
            ]}
          />
        </Space>

        <Table
          loading={loading}
          dataSource={filteredAssessments}
          columns={columns}
          rowKey="id"
          pagination={{ pageSize: 20 }}
          scroll={{ x: 1400 }}
        />
      </Card>

      {/* Create/Edit Modal */}
      <Modal
        title={editing ? 'Edit risk assessment' : 'New risk assessment'}
        open={modalOpen}
        onCancel={() => {
          setModalOpen(false)
          form.resetFields()
        }}
        footer={null}
        width={720}
      >
        <Form form={form} layout="vertical" onFinish={handleSubmit}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="workLocationId" label="Location">
                <Select
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  options={locations.map((l) => ({
                    value: l.id,
                    label: `${l.code} — ${l.name}`,
                  }))}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="orgUnitId" label="Department">
                <Select
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  options={orgUnits.map((u) => ({
                    value: u.id,
                    label: `${u.code} — ${u.name}`,
                  }))}
                />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item
            name="jobTask"
            label="Job task / Activity"
            rules={[{ required: true, max: 500 }]}
          >
            <Input />
          </Form.Item>

          <Form.Item
            name="hazard"
            label="Hazard"
            rules={[{ required: true, max: 500 }]}
          >
            <Input />
          </Form.Item>

          <Row gutter={16}>
            <Col span={8}>
              <Form.Item
                name="likelihood"
                label="Likelihood (1-5)"
                rules={[{ required: true }]}
                initialValue={1}
              >
                <InputNumber
                  min={1}
                  max={5}
                  style={{ width: '100%' }}
                  onChange={(val) => setLikelihood(val || 1)}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                name="impact"
                label="Impact (1-5)"
                rules={[{ required: true }]}
                initialValue={1}
              >
                <InputNumber
                  min={1}
                  max={5}
                  style={{ width: '100%' }}
                  onChange={(val) => setImpact(val || 1)}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <div style={{ marginTop: 30 }}>
                <Alert
                  type="info"
                  message={
                    <div>
                      <strong>Score: {liveScore}</strong>
                      <br />
                      <Tag color={RISK_BAND_COLOR[liveBand]}>{liveBand}</Tag>
                    </div>
                  }
                />
              </div>
            </Col>
          </Row>

          <Form.Item name="controlMeasures" label="Control measures">
            <Input.TextArea rows={3} />
          </Form.Item>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="responsibleUsername" label="Responsible person">
                <Input placeholder="Username" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="reviewDate" label="Review date">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item style={{ marginBottom: 0 }}>
            <Space>
              <Button type="primary" htmlType="submit" loading={submitting}>
                {editing ? 'Update' : 'Create'}
              </Button>
              <Button
                onClick={() => {
                  setModalOpen(false)
                  form.resetFields()
                }}
              >
                Cancel
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
