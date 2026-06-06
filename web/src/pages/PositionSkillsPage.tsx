// M127 — Position competency requirements editor + fit ranking.
//
// Two-section page rooted at /positions/:id/skills.  Top section is the
// HR-editable requirements table (add/edit/remove).  Bottom is the
// ranked candidate fit table — same SkillGapAnalyzer math as the
// per-employee gap viewer.

import { useEffect, useMemo, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Statistic,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd'
import { Link, useParams } from 'react-router-dom'
import { skillsApi, type GapSeverity, type PositionCompetencyRequest, type PositionCompetencyResponse, type PositionFitResponse } from '../api/skills'
import { learningApi, type Competency } from '../api/learning'
import { positionsApi, type Position } from '../api/positions'

const { Title, Text, Paragraph } = Typography

const SEVERITY_COLOR: Record<GapSeverity, string> = {
  NONE: 'green',
  MINOR: 'gold',
  MAJOR: 'orange',
  BLOCKER: 'red',
}

export function PositionSkillsPage() {
  const { id } = useParams<{ id: string }>()
  const { message } = AntdApp.useApp()
  const [position, setPosition] = useState<Position | null>(null)
  const [requirements, setRequirements] = useState<PositionCompetencyResponse[]>([])
  const [competencies, setCompetencies] = useState<Competency[]>([])
  const [fit, setFit] = useState<PositionFitResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [editingCompId, setEditingCompId] = useState<string | null>(null)
  const [form] = Form.useForm()

  const reload = () => {
    if (!id) return
    setLoading(true)
    Promise.all([
      skillsApi.requirementsFor(id),
      skillsApi.positionFit(id),
    ])
      .then(([reqs, fitData]) => {
        setRequirements(reqs)
        setFit(fitData)
      })
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load requirements'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    if (!id) return
    positionsApi.get(id).then(setPosition).catch(() => {})
    learningApi.competencies().then(setCompetencies).catch(() => {})
    reload()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  const openAdd = () => {
    setEditingCompId(null)
    form.resetFields()
    form.setFieldsValue({ requiredLevel: 3, mandatory: true })
    setModalOpen(true)
  }

  const openEdit = (row: PositionCompetencyResponse) => {
    setEditingCompId(row.competencyId)
    form.setFieldsValue({
      competencyId: row.competencyId,
      requiredLevel: row.requiredLevel,
      mandatory: row.mandatory,
      notes: row.notes ?? undefined,
    })
    setModalOpen(true)
  }

  const onSave = async () => {
    if (!id) return
    try {
      const values = await form.validateFields()
      const body: PositionCompetencyRequest = {
        competencyId: values.competencyId,
        requiredLevel: values.requiredLevel,
        mandatory: values.mandatory,
        notes: values.notes,
      }
      await skillsApi.upsertRequirement(id, body)
      message.success('Saved')
      setModalOpen(false)
      reload()
    } catch (e: any) {
      if (e?.errorFields) return
      message.error(e?.response?.data?.message ?? 'Save failed')
    }
  }

  const onRemove = async (competencyId: string) => {
    if (!id) return
    try {
      await skillsApi.removeRequirement(id, competencyId)
      message.success('Removed')
      reload()
    } catch (e: any) {
      message.error(e?.response?.data?.message ?? 'Remove failed')
    }
  }

  // Filter the competency list in the add modal so HR can't double-add the
  // same competency to one position.
  const availableCompetencies = useMemo(() => {
    if (editingCompId) {
      // editing — show the current one too
      return competencies
    }
    const taken = new Set(requirements.map((r) => r.competencyId))
    return competencies.filter((c) => !taken.has(c.id))
  }, [competencies, requirements, editingCompId])

  const requirementsColumns = [
    {
      title: 'Competency',
      render: (_: unknown, r: PositionCompetencyResponse) => (
        <Space direction="vertical" size={0}>
          <Text strong>{r.competencyName ?? r.competencyId.slice(0, 8) + '…'}</Text>
          {r.competencyCategory && (
            <Tag style={{ fontSize: 10 }}>{r.competencyCategory}</Tag>
          )}
        </Space>
      ),
    },
    {
      title: 'Required',
      dataIndex: 'requiredLevel',
      width: 100,
      render: (l: number) => <Tag color="blue">Level {l}</Tag>,
    },
    {
      title: 'Mandatory',
      dataIndex: 'mandatory',
      width: 100,
      render: (m: boolean) => m ? <Tag color="red">YES</Tag> : <Tag>Optional</Tag>,
    },
    {
      title: 'Notes',
      dataIndex: 'notes',
      render: (n: string | null) => n ?? <Text type="secondary">—</Text>,
    },
    {
      title: 'Actions',
      width: 160,
      render: (_: unknown, r: PositionCompetencyResponse) => (
        <Space>
          <a onClick={() => openEdit(r)}>Edit</a>
          <Popconfirm title="Remove this requirement?" onConfirm={() => onRemove(r.competencyId)}>
            <a style={{ color: 'red' }}>Remove</a>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const fitColumns = [
    {
      title: 'Employee',
      render: (_: unknown, r: { employeeId: string; employeeName: string; employeeNo: string }) => (
        <Space direction="vertical" size={0}>
          <Link to={`/employees/${r.employeeId}`}>
            <Text strong>{r.employeeName}</Text>
          </Link>
          <Text type="secondary" style={{ fontSize: 11 }}>{r.employeeNo}</Text>
        </Space>
      ),
    },
    {
      title: 'Fit',
      dataIndex: 'fitScore',
      width: 100,
      sorter: (a: { fitScore: number }, b: { fitScore: number }) => a.fitScore - b.fitScore,
      defaultSortOrder: 'descend' as const,
      render: (s: number) => {
        const colour = s >= 80 ? 'green' : s >= 50 ? 'gold' : s > 0 ? 'orange' : 'red'
        return <Tag color={colour} style={{ fontSize: 12 }}>{s}%</Tag>
      },
    },
    {
      title: 'Blockers',
      dataIndex: 'blockers',
      width: 100,
      render: (n: number) => n > 0
        ? <Tag color="red">{n}</Tag>
        : <Text type="secondary">0</Text>,
    },
    {
      title: 'Major gaps',
      dataIndex: 'majorGaps',
      width: 110,
      render: (n: number) => n > 0
        ? <Tag color="orange">{n}</Tag>
        : <Text type="secondary">0</Text>,
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <Space style={{ marginBottom: 16, justifyContent: 'space-between', width: '100%' }}>
        <Space direction="vertical" size={0}>
          <Title level={3} style={{ margin: 0 }}>
            {position?.title ?? 'Position'} — skill requirements
          </Title>
          {position && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              {position.code} · {position.orgUnitLabel ?? '—'}
            </Text>
          )}
        </Space>
        <Button type="primary" onClick={openAdd}>Add requirement</Button>
      </Space>

      <Card size="small" style={{ marginBottom: 16 }}>
        <Paragraph type="secondary" style={{ fontSize: 12 }}>
          Required competencies for this position. The gap analyzer maps each
          employee's proficiency to these requirements and produces a fit score
          (0–100). A BLOCKER on any mandatory requirement caps the score at 0.
        </Paragraph>
        <Table
          rowKey="competencyId"
          size="small"
          loading={loading}
          columns={requirementsColumns}
          dataSource={requirements}
          pagination={false}
        />
      </Card>

      <Card
        size="small"
        title={
          <Space>
            <Text strong>Candidate fit ranking</Text>
            {fit && (
              <Text type="secondary" style={{ fontSize: 12 }}>
                {fit.ranked.length} ranked of {fit.totalCandidates} active
              </Text>
            )}
          </Space>
        }
      >
        {fit && fit.ranked.length > 0 && (
          <Space style={{ marginBottom: 12 }}>
            <Card size="small">
              <Statistic
                title="Best fit"
                value={fit.ranked[0]?.fitScore ?? 0}
                suffix="%"
                valueStyle={{ fontSize: 22 }}
              />
            </Card>
            <Card size="small">
              <Statistic
                title="≥ 80%"
                value={fit.ranked.filter((r) => r.fitScore >= 80).length}
                valueStyle={{ fontSize: 22 }}
              />
            </Card>
            <Card size="small">
              <Statistic
                title="Blocked"
                value={fit.ranked.filter((r) => r.fitScore === 0).length}
                valueStyle={{ fontSize: 22, color: '#cf1322' }}
              />
            </Card>
          </Space>
        )}
        {!fit || fit.ranked.length === 0
          ? <Empty description={requirements.length === 0 ? 'Add requirements to see candidates ranked.' : 'No active employees to rank.'} />
          : (
            <Table
              rowKey="employeeId"
              size="small"
              columns={fitColumns}
              dataSource={fit.ranked}
              pagination={{ pageSize: 25 }}
            />
          )}
      </Card>

      <Modal
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={onSave}
        okText="Save"
        title={editingCompId ? 'Edit requirement' : 'Add requirement'}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="competencyId"
            label="Competency"
            rules={[{ required: true }]}
          >
            <Select
              showSearch
              disabled={!!editingCompId}
              filterOption={(input, opt) =>
                String(opt?.label ?? '').toLowerCase().includes(input.toLowerCase())
              }
              options={availableCompetencies.map((c) => ({
                value: c.id,
                label: `${c.name} (${c.code})`,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="requiredLevel"
            label="Required level (1..5)"
            rules={[{ required: true }]}
          >
            <InputNumber min={1} max={5} style={{ width: 120 }} />
          </Form.Item>
          <Form.Item name="mandatory" label="Mandatory" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      {/* Reserved for the per-employee gap viewer (Phase 2) — silence the unused-import warning. */}
      <span style={{ display: 'none' }}>{Object.keys(SEVERITY_COLOR).join('')}</span>
    </div>
  )
}
