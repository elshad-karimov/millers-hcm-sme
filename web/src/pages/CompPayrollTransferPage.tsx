import { useEffect, useState } from 'react'
import {
  Alert,
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
import {
  compensationApi,
  type PayrollCompTransferDto,
} from '../api/compensation'
import { payrollApi, type PayrollRun } from '../api/payroll'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const { Title, Text } = Typography

export function CompPayrollTransferPage() {
  const { hasRole } = useAuth()
  const { message, modal } = AntdApp.useApp()
  const canWrite = hasRole(...RoleSets.COMPENSATION_WRITE)

  const [runs, setRuns] = useState<PayrollRun[]>([])
  const [loadingRuns, setLoadingRuns] = useState(false)
  const [selectedRunId, setSelectedRunId] = useState<string | undefined>()

  const [transfers, setTransfers] = useState<PayrollCompTransferDto[]>([])
  const [loadingTransfers, setLoadingTransfers] = useState(false)

  const [transferring, setTransferring] = useState(false)

  const loadRuns = () => {
    setLoadingRuns(true)
    payrollApi
      .runs()
      .then((data) => {
        // Only show open / non-PAID runs as transfer targets
        const openRuns = data.filter((r) => r.status !== 'PAID' && r.status !== 'CLOSED')
        setRuns(openRuns)
      })
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load payroll runs'),
      )
      .finally(() => setLoadingRuns(false))
  }

  const loadTransfers = () => {
    setLoadingTransfers(true)
    compensationApi
      .listTransfers()
      .then(setTransfers)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load transfers'),
      )
      .finally(() => setLoadingTransfers(false))
  }

  useEffect(() => {
    loadRuns()
    loadTransfers()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleTransfer = () => {
    if (!selectedRunId) {
      message.warning('Select a payroll run')
      return
    }

    const run = runs.find((r) => r.id === selectedRunId)
    const runLabel = run ? `${run.runNo} (${run.periodYear}-${String(run.periodMonth).padStart(2, '0')})` : selectedRunId

    modal.confirm({
      title: 'Transfer Approved Payouts to Payroll?',
      content: `Transfer all APPROVED incentive/commission payouts to run ${runLabel}? This will add them as bonuses.`,
      okText: 'Transfer',
      onOk: async () => {
        setTransferring(true)
        try {
          const result = await compensationApi.transferToPayroll(selectedRunId)
          message.success(
            `Transferred ${result.transferredCount} payouts (skipped ${result.skippedCount})`,
          )
          loadTransfers()
        } catch (err) {
          message.error(
            (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
              'Transfer failed',
          )
        } finally {
          setTransferring(false)
        }
      },
    })
  }

  const runMap = new Map(runs.map((r) => [r.id, r]))

  const columns: ColumnsType<PayrollCompTransferDto> = [
    {
      title: 'Employee',
      dataIndex: 'employeeName',
      key: 'employeeName',
      render: (_val, rec) => rec.employeeName ?? rec.employeeId,
    },
    { title: 'Source Type', dataIndex: 'sourceType', key: 'sourceType', width: 120 },
    {
      title: 'Amount',
      dataIndex: 'amount',
      key: 'amount',
      width: 120,
      align: 'right',
      render: (v: number) => v.toLocaleString(),
    },
    {
      title: 'Target Run',
      dataIndex: 'targetRunId',
      key: 'targetRunId',
      width: 180,
      render: (id: string) => {
        const run = runMap.get(id)
        return run
          ? `${run.runNo} (${run.periodYear}-${String(run.periodMonth).padStart(2, '0')})`
          : id.slice(0, 8)
      },
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (s: string) => <Tag color="green">{s}</Tag>,
    },
    {
      title: 'Transferred',
      dataIndex: 'transferredAt',
      key: 'transferredAt',
      width: 160,
      render: (v?: string) => (v ? new Date(v).toLocaleString() : '—'),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <div style={{ marginBottom: 16 }}>
        <Title level={2}>Compensation → Payroll Transfer</Title>
      </div>

      <Alert
        type="info"
        message="This page transfers APPROVED incentive and commission payouts to a payroll run as bonuses."
        style={{ marginBottom: 16 }}
        closable
      />

      <Card title="Transfer to Run" size="small" style={{ marginBottom: 16 }}>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Text>
            Select a payroll run (only DRAFT, CALCULATED, or UNDER_REVIEW runs are shown):
          </Text>
          <Space>
            <Select
              placeholder="Select payroll run..."
              style={{ width: 400 }}
              loading={loadingRuns}
              onChange={setSelectedRunId}
              options={runs.map((r) => ({
                value: r.id,
                label: `${r.runNo} — ${r.periodYear}-${String(r.periodMonth).padStart(2, '0')} — ${r.status}`,
              }))}
            />
            <Button
              type="primary"
              onClick={handleTransfer}
              loading={transferring}
              disabled={!selectedRunId || !canWrite}
            >
              Transfer Approved Payouts
            </Button>
          </Space>
          <Text type="secondary" style={{ fontSize: 12 }}>
            Idempotent. Skips terminated employees. Payouts marked APPROVED that haven't been
            transferred yet will be added to the run.
          </Text>
        </Space>
      </Card>

      <Card title="Transfer Log" size="small">
        <Table
          dataSource={transfers}
          columns={columns}
          rowKey="id"
          loading={loadingTransfers}
          pagination={{ pageSize: 20 }}
        />
      </Card>
    </div>
  )
}
