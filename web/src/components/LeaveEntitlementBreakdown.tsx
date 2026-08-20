import { useCallback, useEffect, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Empty,
  Form,
  InputNumber,
  Input,
  Modal,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Tooltip,
  Typography,
  App as AntdApp,
} from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import {
  COMPONENT_LABELS,
  COUNTS_TOWARD_TOTAL,
  leaveEntitlementApi,
  type EntitlementBreakdown,
  type EntitlementComponent,
  type EntitlementComponentCode,
} from '../api/leaveEntitlement'
import { leaveApi, type LeaveType } from '../api/leave'

interface Props {
  employeeId: string
  /** HR_TEAM_WRITE — gates recalculation and manual overrides. */
  canEdit: boolean
}

/**
 * M151 — itemised annual leave entitlement.
 *
 * Shows what the total is made of, and why each line is there. The `basis`
 * column is the point of the screen: a total nobody can justify is a total
 * nobody can defend in a labour inspection.
 */
export function LeaveEntitlementBreakdown({ employeeId, canEdit }: Props) {
  const { message } = AntdApp.useApp()
  const [types, setTypes] = useState<LeaveType[]>([])
  const [leaveTypeId, setLeaveTypeId] = useState<string>()
  const [year, setYear] = useState<number>(new Date().getFullYear())
  const [data, setData] = useState<EntitlementBreakdown>()
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [manualOpen, setManualOpen] = useState(false)
  const [form] = Form.useForm()

  // Only component-driven types have a breakdown to show; the rest still run
  // on the accrual chain and would render an empty table with no explanation.
  useEffect(() => {
    leaveApi
      .types(true)
      .then((all) => {
        const eligible = all.filter((t) => t.entitlementComponentsEnabled)
        setTypes(eligible)
        setLeaveTypeId((current) => current ?? eligible[0]?.id)
      })
      .catch(() => message.error('Failed to load leave types'))
      .finally(() => setLoading(false))
  }, [message])

  const load = useCallback(() => {
    if (!leaveTypeId) return
    setLoading(true)
    leaveEntitlementApi
      .breakdown(employeeId, leaveTypeId, year)
      .then(setData)
      .catch(() => message.error('Failed to load entitlement breakdown'))
      .finally(() => setLoading(false))
  }, [employeeId, leaveTypeId, year, message])

  useEffect(() => { load() }, [load])

  const recalculate = () => {
    if (!leaveTypeId) return
    setBusy(true)
    leaveEntitlementApi
      .recalculate(employeeId, leaveTypeId, year)
      .then((d) => {
        setData(d)
        message.success('Entitlement recalculated')
      })
      .catch((e) => message.error(e?.response?.data?.message ?? 'Recalculation failed'))
      .finally(() => setBusy(false))
  }

  const submitManual = async () => {
    const v = await form.validateFields()
    if (!leaveTypeId) return
    setBusy(true)
    leaveEntitlementApi
      .setManual(employeeId, leaveTypeId, {
        componentCode: v.componentCode,
        days: v.days,
        basis: v.basis,
      }, year)
      .then((d) => {
        setData(d)
        setManualOpen(false)
        form.resetFields()
        message.success('Component saved')
      })
      .catch((e) => message.error(e?.response?.data?.message ?? 'Save failed'))
      .finally(() => setBusy(false))
  }

  const columns = [
    {
      title: 'Component',
      dataIndex: 'componentCode',
      render: (c: EntitlementComponentCode) => COMPONENT_LABELS[c] ?? c,
    },
    {
      title: 'Days',
      dataIndex: 'days',
      width: 130,
      align: 'right' as const,
      render: (d: number, row: EntitlementComponent) =>
        COUNTS_TOWARD_TOTAL[row.componentCode] ? (
          <strong>{d}</strong>
        ) : (
          // Shown but not added. Rendering it like the others would make the
          // column stop adding up on screen with no explanation.
          <Tooltip title="Earned rest days, not annual vacation — excluded from the entitlement total">
            <Typography.Text type="secondary">{d} (excl.)</Typography.Text>
          </Tooltip>
        ),
    },
    {
      title: 'Source',
      dataIndex: 'source',
      width: 110,
      render: (s: string) =>
        s === 'MANUAL' ? (
          <Tooltip title="Entered by HR — a recalculation will not overwrite it">
            <Tag color="orange">Manual</Tag>
          </Tooltip>
        ) : (
          <Tooltip title="Computed from employee, position and dependent data">
            <Tag color="blue">Derived</Tag>
          </Tooltip>
        ),
    },
    {
      title: 'Basis',
      dataIndex: 'basis',
      render: (b?: string | null) =>
        b ? <Typography.Text type="secondary">{b}</Typography.Text> : '—',
    },
    {
      title: 'Updated by',
      dataIndex: 'updatedBy',
      width: 130,
      render: (v?: string | null) => v ?? '—',
    },
  ]

  if (!loading && types.length === 0) {
    return (
      <Alert
        type="info"
        showIcon
        message="No leave type uses the itemised entitlement model yet"
        description="Enable it on a leave type and configure its base-days schedule and experience brackets to see a breakdown here. Types on the accrual model keep their existing behaviour."
      />
    )
  }

  const years = Array.from({ length: 5 }, (_, i) => new Date().getFullYear() - 2 + i)

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Select
          style={{ minWidth: 220 }}
          value={leaveTypeId}
          onChange={setLeaveTypeId}
          options={types.map((t) => ({ value: t.id, label: `${t.code} — ${t.name}` }))}
        />
        <Select
          style={{ width: 110 }}
          value={year}
          onChange={setYear}
          options={years.map((y) => ({ value: y, label: String(y) }))}
        />
        {canEdit && (
          <>
            <Button icon={<ReloadOutlined />} onClick={recalculate} loading={busy}>
              Recalculate
            </Button>
            <Button onClick={() => setManualOpen(true)}>Add / override component</Button>
          </>
        )}
      </Space>

      {loading ? (
        <div style={{ textAlign: 'center', padding: 32 }}><Spin /></div>
      ) : (
        <Card
          size="small"
          title={`Annual entitlement ${year}`}
          extra={
            <Typography.Title level={5} style={{ margin: 0 }}>
              {data?.totalDays ?? 0} days
            </Typography.Title>
          }
        >
          <Table<EntitlementComponent>
            rowKey="id"
            size="small"
            pagination={false}
            columns={columns}
            dataSource={data?.components ?? []}
            locale={{
              emptyText: (
                <Empty description="No components yet — run a recalculation to derive them" />
              ),
            }}
            summary={(rows) => (
              <Table.Summary.Row>
                <Table.Summary.Cell index={0}><strong>Total</strong></Table.Summary.Cell>
                <Table.Summary.Cell index={1} align="right">
                  <strong>
                    {rows
                      .filter((r) => COUNTS_TOWARD_TOTAL[r.componentCode])
                      .reduce((sum, r) => sum + Number(r.days ?? 0), 0)}
                  </strong>
                </Table.Summary.Cell>
                <Table.Summary.Cell index={2} colSpan={3} />
              </Table.Summary.Row>
            )}
          />
        </Card>
      )}

      <Alert
        type="info"
        showIcon
        message="Derived components follow the data, not this screen"
        description="Base days come from the position classification, seniority from total professional experience, harmful-conditions days from the position, and the children uplift from dependent records. To change a derived line, fix the underlying record and recalculate. Drivers are read as at 1 January, so a threshold crossed mid-year takes effect next leave year."
      />

      <Modal
        title="Add or override a component"
        open={manualOpen}
        onCancel={() => setManualOpen(false)}
        onOk={submitManual}
        confirmLoading={busy}
        okText="Save"
      >
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="A manual component is never overwritten by a recalculation"
          description="Use it for blood-donation days and for cases the rules cannot see. Clear the days field to hand the component back to the resolvers."
        />
        <Form form={form} layout="vertical">
          <Form.Item
            name="componentCode"
            label="Component"
            rules={[{ required: true, message: 'Pick a component' }]}
          >
            <Select
              options={(Object.keys(COMPONENT_LABELS) as EntitlementComponentCode[]).map((c) => ({
                value: c,
                label: COMPONENT_LABELS[c],
              }))}
            />
          </Form.Item>
          <Form.Item
            name="days"
            label="Days"
            tooltip="Leave empty to clear the override."
          >
            <InputNumber min={0} max={365} step={0.5} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="basis"
            label="Reason"
            tooltip="Required when days are set — this is what justifies the entitlement in an audit."
            rules={[
              ({ getFieldValue }) => ({
                validator: (_, v) =>
                  getFieldValue('days') == null || (v && String(v).trim())
                    ? Promise.resolve()
                    : Promise.reject(new Error('A reason is required when granting days')),
              }),
              { max: 500 },
            ]}
          >
            <Input.TextArea rows={2} placeholder="e.g. 2 blood donations in 2026 (certificates on file)" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}
