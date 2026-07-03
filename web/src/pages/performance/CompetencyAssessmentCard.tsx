// HCM_12 M393 — per-review competency assessment (PRD §17).
// Seeds rows from the employee's position requirements; self / manager / final
// levels 1–5; gap = required − final (positive = development need, feeds M399).

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Form,
  InputNumber,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  competencyAssessmentsApi,
  type CompetencyAssessment,
} from '../../api/performance'
import { learningApi, type Competency } from '../../api/learning'

const { Text } = Typography

function levelTag(v?: number | null) {
  return v == null ? <Text type="secondary">—</Text> : <Tag>{v}</Tag>
}

function gapTag(gap?: number | null) {
  if (gap == null) return <Text type="secondary">—</Text>
  if (gap > 0) return <Tag color="red">-{gap} below</Tag>
  if (gap < 0) return <Tag color="green">+{-gap} above</Tag>
  return <Tag color="blue">meets</Tag>
}

export function CompetencyAssessmentCard({
  reviewId,
  canEdit,
}: {
  reviewId: string
  canEdit: boolean
}) {
  const { message } = AntdApp.useApp()
  const [rows, setRows] = useState<CompetencyAssessment[]>([])
  const [loading, setLoading] = useState(true)
  const [seeding, setSeeding] = useState(false)

  const [addOpen, setAddOpen] = useState(false)
  const [competencies, setCompetencies] = useState<Competency[]>([])
  const [addForm] = Form.useForm<{ competencyId: string; requiredLevel?: number }>()
  const [savingAdd, setSavingAdd] = useState(false)

  const [rating, setRating] = useState<CompetencyAssessment | null>(null)
  const [rateForm] = Form.useForm<{
    selfLevel?: number
    managerLevel?: number
    finalLevel?: number
    comment?: string
  }>()
  const [savingRate, setSavingRate] = useState(false)

  const load = () => {
    setLoading(true)
    competencyAssessmentsApi
      .list(reviewId)
      .then(setRows)
      .catch(() => message.error('Failed to load competency assessments'))
      .finally(() => setLoading(false))
  }
  useEffect(load, [reviewId]) // eslint-disable-line react-hooks/exhaustive-deps

  const seed = async () => {
    setSeeding(true)
    try {
      const seeded = await competencyAssessmentsApi.init(reviewId)
      setRows(seeded)
      message.success('Seeded from the position’s required competencies')
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      message.error(err.response?.data?.message ?? 'Seeding failed')
    } finally {
      setSeeding(false)
    }
  }

  const openAdd = () => {
    addForm.resetFields()
    setAddOpen(true)
    if (!competencies.length) {
      learningApi
        .competencies()
        .then(setCompetencies)
        .catch(() => message.error('Failed to load competency catalog'))
    }
  }

  const saveAdd = async () => {
    const v = await addForm.validateFields()
    setSavingAdd(true)
    try {
      await competencyAssessmentsApi.add(reviewId, v.competencyId, v.requiredLevel)
      message.success('Competency added')
      setAddOpen(false)
      load()
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      message.error(err.response?.data?.message ?? 'Add failed')
    } finally {
      setSavingAdd(false)
    }
  }

  const saveRate = async () => {
    if (!rating) return
    const v = await rateForm.validateFields()
    setSavingRate(true)
    try {
      await competencyAssessmentsApi.rate(reviewId, rating.id, v)
      message.success('Assessment saved')
      setRating(null)
      load()
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      message.error(err.response?.data?.message ?? 'Save failed')
    } finally {
      setSavingRate(false)
    }
  }

  const columns: ColumnsType<CompetencyAssessment> = [
    {
      title: 'Competency',
      key: 'competency',
      render: (_, a) => (
        <Space direction="vertical" size={0}>
          <Text strong>{a.competencyName ?? a.competencyId}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {a.competencyCode}
            {a.category ? ` · ${a.category}` : ''}
          </Text>
        </Space>
      ),
    },
    { title: 'Required', dataIndex: 'requiredLevel', width: 90, align: 'center', render: levelTag },
    { title: 'Self', dataIndex: 'selfLevel', width: 70, align: 'center', render: levelTag },
    { title: 'Manager', dataIndex: 'managerLevel', width: 90, align: 'center', render: levelTag },
    { title: 'Final', dataIndex: 'finalLevel', width: 70, align: 'center', render: levelTag },
    {
      title: (
        <Tooltip title="Gap = required − final. Positive = development need (feeds development plans).">
          Gap
        </Tooltip>
      ),
      dataIndex: 'gap',
      width: 110,
      align: 'center',
      render: gapTag,
    },
    {
      title: 'Comment',
      dataIndex: 'comment',
      ellipsis: true,
      render: (v) => v ?? <Text type="secondary">—</Text>,
    },
    ...(canEdit
      ? [
          {
            title: '',
            key: 'actions',
            width: 80,
            render: (_: unknown, a: CompetencyAssessment) => (
              <Button
                size="small"
                onClick={() => {
                  rateForm.setFieldsValue({
                    selfLevel: a.selfLevel ?? undefined,
                    managerLevel: a.managerLevel ?? undefined,
                    finalLevel: a.finalLevel ?? undefined,
                    comment: a.comment ?? undefined,
                  })
                  setRating(a)
                }}
              >
                Assess
              </Button>
            ),
          } as ColumnsType<CompetencyAssessment>[number],
        ]
      : []),
  ]

  return (
    <Card
      size="small"
      title="Competency assessment"
      style={{ marginTop: 16 }}
      extra={
        canEdit && (
          <Space size={8}>
            <Button size="small" loading={seeding} onClick={seed}>
              Seed from position
            </Button>
            <Button size="small" onClick={openAdd}>
              Add competency
            </Button>
          </Space>
        )
      }
    >
      <Table
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={false}
        locale={{
          emptyText:
            'No competencies yet — seed from the position’s requirements or add manually.',
        }}
      />

      <Modal
        title="Add competency"
        open={addOpen}
        onCancel={() => setAddOpen(false)}
        onOk={saveAdd}
        confirmLoading={savingAdd}
        okText="Add"
        destroyOnClose
      >
        <Form form={addForm} layout="vertical">
          <Form.Item name="competencyId" label="Competency" rules={[{ required: true }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={competencies
                .filter((c) => !rows.some((r) => r.competencyId === c.id))
                .map((c) => ({ value: c.id, label: `${c.code} — ${c.name}` }))}
            />
          </Form.Item>
          <Form.Item name="requiredLevel" label="Required level (1–5, optional)">
            <InputNumber min={1} max={5} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={rating ? `Assess — ${rating.competencyName ?? ''}` : ''}
        open={!!rating}
        onCancel={() => setRating(null)}
        onOk={saveRate}
        confirmLoading={savingRate}
        okText="Save"
        destroyOnClose
      >
        <Form form={rateForm} layout="vertical">
          <Space size={12} style={{ display: 'flex' }}>
            <Form.Item name="selfLevel" label="Self (1–5)">
              <InputNumber min={1} max={5} />
            </Form.Item>
            <Form.Item name="managerLevel" label="Manager (1–5)">
              <InputNumber min={1} max={5} />
            </Form.Item>
            <Form.Item name="finalLevel" label="Final (1–5)">
              <InputNumber min={1} max={5} />
            </Form.Item>
          </Space>
          <Form.Item name="comment" label="Comment">
            <Input.TextArea rows={2} maxLength={1000} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
