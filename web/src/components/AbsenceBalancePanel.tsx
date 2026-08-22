import { useCallback, useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Empty,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Typography,
} from 'antd'
import { leaveApi, type LeaveBalance, type LeaveType } from '../api/leave'

const { Text } = Typography

/**
 * An employee's leave balances, with a way to open the first one.
 *
 * <p>Lives in its own component because it is needed in two places that used
 * to disagree: the profile has a Time & absence tab, the edit form did not,
 * and people pressed Edit to add entitlement — reasonably — and found the tab
 * gone. Duplicating the table in both would have been two things to keep in
 * step; this is one.
 *
 * <p>Balances are separate records with their own ledger, not fields on the
 * employee row, so everything here takes effect immediately rather than
 * waiting for the form's Save. The panel says so where it is used inside a
 * form, because a button that acts while Cancel sits next to it is otherwise a
 * trap.
 */
export function AbsenceBalancePanel({
  employeeId,
  canManage,
  immediateNote,
}: {
  employeeId: string
  /** Server-side this is HR_ADMIN / SYSTEM_ADMIN; the button follows it. */
  canManage: boolean
  /** Say that changes here do not wait for Save — true inside the edit form. */
  immediateNote?: boolean
}) {
  const { message } = AntdApp.useApp()
  const [balances, setBalances] = useState<LeaveBalance[]>([])
  const [types, setTypes] = useState<LeaveType[]>([])
  const [loading, setLoading] = useState(true)

  const [open, setOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [typeId, setTypeId] = useState<string | undefined>()
  const [year, setYear] = useState<number>(new Date().getFullYear())
  const [extraDays, setExtraDays] = useState<number>(0)

  const load = useCallback(() => {
    setLoading(true)
    Promise.all([
      leaveApi.balances({ employeeId }).catch(() => [] as LeaveBalance[]),
      leaveApi.types(true).catch(() => [] as LeaveType[]),
    ])
      .then(([b, t]) => {
        setBalances(b)
        setTypes(t)
      })
      .finally(() => setLoading(false))
  }, [employeeId])

  useEffect(() => { load() }, [load])

  const add = () => {
    if (!typeId) return
    setSaving(true)
    leaveApi
      .adjustBalance({
        employeeId,
        leaveTypeId: typeId,
        year,
        deltaDays: extraDays,
        reason: 'Entitlement opened from the employee screen',
      })
      .then(() => {
        setOpen(false)
        setExtraDays(0)
        message.success('Entitlement added')
        load()
      })
      .catch((e) => message.error(e?.response?.data?.message ?? 'Could not add the entitlement'))
      .finally(() => setSaving(false))
  }

  return (
    <>
      {immediateNote && (
        <Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 8 }}>
          Leave balances are saved as soon as you add them — they are not part of Save changes.
        </Text>
      )}
      <Table
        rowKey="id"
        size="small"
        loading={loading}
        dataSource={balances}
        pagination={false}
        locale={{
          emptyText: (
            <Empty description="No leave balance for this year yet">
              {canManage && (
                <Button type="primary" ghost onClick={() => setOpen(true)}>
                  Add entitlement
                </Button>
              )}
            </Empty>
          ),
        }}
        columns={[
          {
            title: 'Leave type',
            dataIndex: 'leaveTypeId',
            render: (v: string) => types.find((t) => t.id === v)?.name ?? v,
          },
          { title: 'Year', dataIndex: 'year', width: 80 },
          { title: 'Entitled', dataIndex: 'entitlementDays', width: 100 },
          { title: 'Carried fwd', dataIndex: 'carriedForwardDays', width: 120 },
          { title: 'Adjustments', dataIndex: 'adjustmentDays', width: 115 },
          { title: 'Used', dataIndex: 'usedDays', width: 80 },
          { title: 'Reserved', dataIndex: 'reservedDays', width: 100 },
          {
            title: 'Remaining',
            dataIndex: 'remainingDays',
            width: 110,
            render: (v: number) => <strong>{v}</strong>,
          },
        ]}
      />
      {/* Once a balance exists the empty state is gone, so the action needs a
          home of its own — a second leave type is the common next step. */}
      {canManage && balances.length > 0 && (
        <Button size="small" style={{ marginTop: 12 }} onClick={() => setOpen(true)}>
          Add entitlement
        </Button>
      )}

      <Modal
        title="Add entitlement"
        open={open}
        confirmLoading={saving}
        okText="Add"
        okButtonProps={{ disabled: !typeId }}
        onCancel={() => setOpen(false)}
        onOk={add}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <div>
            <Text type="secondary">Leave type</Text>
            <Select
              showSearch
              optionFilterProp="label"
              style={{ width: '100%' }}
              placeholder="Pick a leave type"
              value={typeId}
              onChange={setTypeId}
              options={types.map((t) => ({ label: t.name, value: t.id }))}
            />
          </div>
          <div>
            <Text type="secondary">Year</Text>
            <InputNumber
              style={{ width: '100%' }}
              value={year}
              onChange={(v) => setYear(v ?? new Date().getFullYear())}
            />
          </div>
          <div>
            <Text type="secondary">Extra days on top of the standard entitlement</Text>
            <InputNumber
              style={{ width: '100%' }}
              value={extraDays}
              onChange={(v) => setExtraDays(v ?? 0)}
            />
          </div>
          <Text type="secondary" style={{ fontSize: 12 }}>
            Leave this at 0 to give the standard days for the leave type. It is recorded as an
            adjustment on the balance ledger either way, so the history shows who added it.
          </Text>
        </Space>
      </Modal>
    </>
  )
}
