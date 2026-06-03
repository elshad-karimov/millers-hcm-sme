// M92 — 9-box succession grid page.
// HR-only (HR_READ). Picks a review cycle, shows a 3×3 grid of cells with
// employee chips. Click a cell to drill into the list; click a chip to
// inline-edit the potential rating.

import { useEffect, useMemo, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Col,
  Empty,
  Form,
  InputNumber,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate, useParams } from 'react-router-dom'
import {
  successionApi,
  type Band,
  type GridCell,
  type GridEmployee,
  type SuccessionGrid,
} from '../../api/succession'
import { performanceApi, type ReviewCycle } from '../../api/performance'

const { Title, Text, Paragraph } = Typography

const BAND_LABEL: Record<Band, string> = {
  LOW: 'Low',
  MID: 'Mid',
  HIGH: 'High',
}

/** Background for each (perf, pot) cell — the canonical 9-box colour palette. */
function cellBg(perf: Band, pot: Band): string {
  // Top-right (HIGH/HIGH) is green; bottom-left (LOW/LOW) is red; diagonal yellow.
  const score = scoreOf(perf) + scoreOf(pot)
  if (score >= 5) return '#d9f7be'   // green
  if (score >= 4) return '#f6ffed'   // light green
  if (score >= 3) return '#fffbe6'   // yellow
  if (score >= 2) return '#fff1f0'   // light red
  return '#ffccc7'                    // red
}

function scoreOf(b: Band): number {
  return b === 'HIGH' ? 3 : b === 'MID' ? 2 : 1
}

export function SuccessionGridPage() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const { cycleId: routeCycleId } = useParams<{ cycleId?: string }>()
  const [cycles, setCycles] = useState<ReviewCycle[]>([])
  const [cycleId, setCycleId] = useState<string | undefined>(routeCycleId)
  const [grid, setGrid] = useState<SuccessionGrid | null>(null)
  const [loading, setLoading] = useState(true)
  const [drillCell, setDrillCell] = useState<GridCell | null>(null)
  const [editingEmp, setEditingEmp] = useState<GridEmployee | null>(null)
  const [editForm] = Form.useForm<{ potentialRating: number; potentialNotes?: string }>()
  const [saving, setSaving] = useState(false)

  // Load cycles for the picker
  useEffect(() => {
    performanceApi.cycles()
      .then((cs) => {
        setCycles(cs)
        if (!cycleId && cs.length) setCycleId(cs[0].id)
      })
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load cycles'))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Load grid when cycle changes
  useEffect(() => {
    if (!cycleId) return
    setLoading(true)
    successionApi.grid(cycleId)
      .then(setGrid)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load grid'))
      .finally(() => setLoading(false))
  }, [cycleId, message])

  /** Cells indexed by (perf, pot) for fast lookup during render. */
  const cellMap = useMemo(() => {
    if (!grid) return new Map<string, GridCell>()
    const m = new Map<string, GridCell>()
    grid.cells.forEach((c) => m.set(`${c.performance}-${c.potential}`, c))
    return m
  }, [grid])

  const openEdit = (emp: GridEmployee) => {
    setEditingEmp(emp)
    editForm.setFieldsValue({
      potentialRating: emp.potentialRating,
      potentialNotes: undefined,
    })
  }

  const submitEdit = async () => {
    if (!editingEmp || !cycleId) return
    const v = await editForm.validateFields()
    setSaving(true)
    try {
      await successionApi.setPotential(editingEmp.reviewId, v)
      message.success(`Updated potential for ${editingEmp.employeeName}`)
      setEditingEmp(null)
      const refreshed = await successionApi.grid(cycleId)
      setGrid(refreshed)
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Update failed',
      )
    } finally {
      setSaving(false)
    }
  }

  const drillCols: ColumnsType<GridEmployee> = [
    { title: 'Employee', dataIndex: 'employeeName' },
    { title: 'Department', dataIndex: 'department', render: (v) => v ?? '—' },
    { title: 'Performance', dataIndex: 'performanceRating', render: (v: number) => v?.toFixed(2) },
    { title: 'Potential', dataIndex: 'potentialRating', render: (v: number) => v?.toFixed(2) },
    {
      title: 'Recommendation',
      dataIndex: 'recommendation',
      render: (v: string) => (v ? <Tag>{v.replace(/_/g, ' ')}</Tag> : '—'),
    },
    {
      title: '',
      render: (_, r) => <Button size="small" onClick={() => openEdit(r)}>Edit</Button>,
    },
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
        <Title level={3} style={{ margin: 0 }}>9-box succession grid</Title>
        <Space>
          <Text type="secondary">Cycle:</Text>
          <Select
            style={{ minWidth: 280 }}
            value={cycleId}
            onChange={(v) => { setCycleId(v); navigate(`/performance/succession/${v}`, { replace: true }) }}
            options={cycles.map((c) => ({ value: c.id, label: c.name }))}
            placeholder="Pick a cycle"
          />
        </Space>
      </Space>

      {loading || !grid ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: 64 }}>
          <Spin />
        </div>
      ) : (
        <>
          <Card size="small">
            <Row gutter={16}>
              <Col span={6}><Statistic title="Total reviews" value={grid.totalReviews} /></Col>
              <Col span={6}><Statistic title="Placed" value={grid.placedReviews}
                valueStyle={{ color: '#52c41a' }} /></Col>
              <Col span={6}><Statistic title="Missing performance" value={grid.missingPerformance}
                valueStyle={grid.missingPerformance > 0 ? { color: '#fa8c16' } : {}} /></Col>
              <Col span={6}><Statistic title="Missing potential" value={grid.missingPotential}
                valueStyle={grid.missingPotential > 0 ? { color: '#fa8c16' } : {}} /></Col>
            </Row>
          </Card>

          <Card>
            <div style={{ display: 'flex' }}>
              {/* Y-axis label */}
              <div style={{
                writingMode: 'vertical-rl', transform: 'rotate(180deg)',
                fontWeight: 600, color: '#595959', padding: '0 12px',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                POTENTIAL  →
              </div>
              {/* Grid */}
              <div style={{ flex: 1 }}>
                {(['HIGH', 'MID', 'LOW'] as Band[]).map((pot) => (
                  <Row key={pot} gutter={8} style={{ marginBottom: 8 }}>
                    <Col flex="40px" style={{ display: 'flex', alignItems: 'center',
                      justifyContent: 'center', fontWeight: 600, color: '#595959' }}>
                      {BAND_LABEL[pot]}
                    </Col>
                    {(['LOW', 'MID', 'HIGH'] as Band[]).map((perf) => {
                      const cell = cellMap.get(`${perf}-${pot}`)
                      if (!cell) return <Col key={perf} flex={1} />
                      return (
                        <Col key={perf} flex={1}>
                          <Card
                            size="small"
                            hoverable
                            onClick={() => setDrillCell(cell)}
                            style={{
                              minHeight: 140,
                              background: cellBg(perf, pot),
                              cursor: 'pointer',
                            }}
                            styles={{ body: { padding: 12 } }}
                          >
                            <div style={{ display: 'flex', justifyContent: 'space-between',
                              alignItems: 'baseline', marginBottom: 6 }}>
                              <Text strong style={{ fontSize: 13 }}>{cell.label}</Text>
                              <Tag color={cell.count > 0 ? 'blue' : 'default'}>{cell.count}</Tag>
                            </div>
                            <Space wrap size={[4, 4]}>
                              {cell.employees.slice(0, 5).map((e) => (
                                <Tag key={e.employeeId} style={{ marginRight: 0 }}>
                                  {e.employeeName}
                                </Tag>
                              ))}
                              {cell.employees.length > 5 && (
                                <Text type="secondary" style={{ fontSize: 12 }}>
                                  + {cell.employees.length - 5} more
                                </Text>
                              )}
                              {cell.employees.length === 0 && (
                                <Text type="secondary" style={{ fontSize: 12 }}>empty</Text>
                              )}
                            </Space>
                          </Card>
                        </Col>
                      )
                    })}
                  </Row>
                ))}
                <Row gutter={8}>
                  <Col flex="40px" />
                  {(['LOW', 'MID', 'HIGH'] as Band[]).map((perf) => (
                    <Col key={perf} flex={1} style={{ textAlign: 'center', fontWeight: 600, color: '#595959' }}>
                      {BAND_LABEL[perf]}
                    </Col>
                  ))}
                </Row>
                <div style={{ textAlign: 'center', marginTop: 8, fontWeight: 600, color: '#595959' }}>
                  PERFORMANCE →
                </div>
              </div>
            </div>
          </Card>

          {(grid.missingPerformance + grid.missingPotential > 0) && (
            <Card>
              <Paragraph type="secondary" style={{ margin: 0 }}>
                <strong>{grid.missingPerformance}</strong> review(s) lack a final performance
                rating and <strong>{grid.missingPotential}</strong> lack a potential rating —
                they are NOT placed on the grid. Complete calibration to bring them in.
              </Paragraph>
            </Card>
          )}
        </>
      )}

      {/* Drill modal — full list of employees in the picked cell */}
      <Modal
        open={!!drillCell}
        title={drillCell ? `${drillCell.label} (${drillCell.count})` : ''}
        onCancel={() => setDrillCell(null)}
        footer={null}
        width={900}
      >
        {drillCell && (
          <Table
            rowKey="reviewId"
            columns={drillCols}
            dataSource={drillCell.employees}
            pagination={false}
            size="small"
            locale={{ emptyText: <Empty description="No employees in this cell" /> }}
          />
        )}
      </Modal>

      {/* Edit potential modal */}
      <Modal
        open={!!editingEmp}
        title={editingEmp ? `Set potential for ${editingEmp.employeeName}` : ''}
        onCancel={() => setEditingEmp(null)}
        onOk={submitEdit}
        confirmLoading={saving}
        okText="Save"
      >
        <Form form={editForm} layout="vertical">
          <Form.Item
            name="potentialRating"
            label="Potential rating (1.0–5.0)"
            rules={[
              { required: true, message: 'Required' },
              { type: 'number', min: 1, max: 5, message: 'Between 1 and 5' },
            ]}
          >
            <InputNumber min={1} max={5} step={0.5} style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="potentialNotes" label="Calibration notes (optional)">
            <Input.TextArea rows={3} placeholder="Rationale visible to HR + the employee's manager" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}
