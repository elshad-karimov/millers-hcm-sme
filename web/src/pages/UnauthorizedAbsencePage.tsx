import { useEffect, useState } from 'react'
import {
  Button,
  Col,
  DatePicker,
  Form,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Table,
  Tag,
  App as AntdApp,
} from 'antd'
import { SearchOutlined, CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import type { ColumnsType } from 'antd/es/table'
import {
  unauthorizedAbsenceApi,
  type AbsenceScanResult,
} from '../api/unauthorizedAbsence'
import { leaveApi, type LeaveType } from '../api/leave'
import { EmployeePicker } from '../components/EmployeePicker'

const STATUS_COLOR: Record<string, string> = {
  PENDING: 'orange',
  CONVERTED: 'green',
  DISMISSED: 'default',
}

export function UnauthorizedAbsencePage() {
  const { message } = AntdApp.useApp()
  const [records, setRecords] = useState<AbsenceScanResult[]>([])
  const [types, setTypes] = useState<LeaveType[]>([])
  const [loading, setLoading] = useState(false)
  const [selectedEmployeeId, setSelectedEmployeeId] = useState<string | undefined>()
  const [scanRange, setScanRange] = useState<[dayjs.Dayjs, dayjs.Dayjs] | null>(null)
  const [selected, setSelected] = useState<AbsenceScanResult[]>([])
  const [convertModal, setConvertModal] = useState(false)
  const [dismissModal, setDismissModal] = useState(false)
  const [convertTypeId, setConvertTypeId] = useState<string | undefined>()
  const [actionNotes, setActionNotes] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    Promise.all([
      leaveApi.types(true),
      unauthorizedAbsenceApi.listPending(),
    ]).then(([t, p]) => {
      setTypes(t)
      setRecords(p)
    })
  }, [])

  const doScan = async () => {
    if (!selectedEmployeeId || !scanRange) {
      message.warning('Select an employee and date range first')
      return
    }
    setLoading(true)
    try {
      const results = await unauthorizedAbsenceApi.scan(
        selectedEmployeeId,
        scanRange[0].format('YYYY-MM-DD'),
        scanRange[1].format('YYYY-MM-DD'),
      )
      setRecords(results)
      setSelected([])
    } catch {
      message.error('Scan failed')
    } finally {
      setLoading(false)
    }
  }

  const pendingSelected = selected.filter((r) => r.status === 'PENDING')

  const doConvert = async () => {
    if (!convertTypeId || pendingSelected.length === 0) return
    setSaving(true)
    try {
      const payload = {
        employeeId: pendingSelected[0].employeeId,
        absenceDates: pendingSelected.map((r) => r.absenceDate),
        leaveTypeId: convertTypeId,
        notes: actionNotes || undefined,
      }
      const updated = await unauthorizedAbsenceApi.convert(payload)
      setRecords((prev) => {
        const map = new Map(updated.map((r) => [r.id, r]))
        return prev.map((r) => map.get(r.id) ?? r)
      })
      setSelected([])
      setConvertModal(false)
      setActionNotes('')
      message.success(`Converted ${updated.length} absence(s) to leave`)
    } catch (err) {
      message.error((err as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Convert failed')
    } finally {
      setSaving(false)
    }
  }

  const doDismiss = async () => {
    if (pendingSelected.length === 0) return
    setSaving(true)
    try {
      const payload = {
        employeeId: pendingSelected[0].employeeId,
        absenceDates: pendingSelected.map((r) => r.absenceDate),
        notes: actionNotes || undefined,
      }
      const updated = await unauthorizedAbsenceApi.dismiss(payload)
      setRecords((prev) => {
        const map = new Map(updated.map((r) => [r.id, r]))
        return prev.map((r) => map.get(r.id) ?? r)
      })
      setSelected([])
      setDismissModal(false)
      setActionNotes('')
      message.success(`Dismissed ${updated.length} absence(s)`)
    } catch {
      message.error('Dismiss failed')
    } finally {
      setSaving(false)
    }
  }

  const allSameEmployee =
    selected.length > 0 && selected.every((r) => r.employeeId === selected[0].employeeId)

  const columns: ColumnsType<AbsenceScanResult> = [
    { title: 'Employee', render: (_, r) => `${r.employeeNo} — ${r.employeeName}` },
    { title: 'Absence date', dataIndex: 'absenceDate' },
    {
      title: 'Status',
      render: (_, r) => <Tag color={STATUS_COLOR[r.status]}>{r.status}</Tag>,
    },
    { title: 'Converted to', dataIndex: 'leaveTypeName' },
    { title: 'Notes', dataIndex: 'notes' },
    { title: 'Resolved by', dataIndex: 'resolvedBy' },
  ]

  return (
    <div style={{ padding: 24 }}>
      <h2 style={{ margin: '0 0 16px' }}>Unauthorized Absences</h2>

      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={7}>
          <EmployeePicker
            allowClear
            placeholder="Select employee to scan"
            style={{ width: '100%' }}
            value={selectedEmployeeId}
            onChange={setSelectedEmployeeId}
          />
        </Col>
        <Col span={7}>
          <DatePicker.RangePicker
            style={{ width: '100%' }}
            value={scanRange}
            onChange={(v) => setScanRange(v as [dayjs.Dayjs, dayjs.Dayjs] | null)}
          />
        </Col>
        <Col span={4}>
          <Button icon={<SearchOutlined />} onClick={doScan} loading={loading}>
            Scan
          </Button>
        </Col>
        <Col span={6} style={{ textAlign: 'right' }}>
          <Space>
            <Button
              type="primary"
              icon={<CheckCircleOutlined />}
              disabled={pendingSelected.length === 0 || !allSameEmployee}
              onClick={() => setConvertModal(true)}
            >
              Convert to Leave ({pendingSelected.length})
            </Button>
            <Button
              danger
              icon={<CloseCircleOutlined />}
              disabled={pendingSelected.length === 0 || !allSameEmployee}
              onClick={() => setDismissModal(true)}
            >
              Dismiss
            </Button>
          </Space>
        </Col>
      </Row>

      <Table
        rowKey="id"
        columns={columns}
        dataSource={records}
        loading={loading}
        rowSelection={{
          selectedRowKeys: selected.map((r) => r.id),
          onChange: (_, rows) => setSelected(rows),
          getCheckboxProps: (r) => ({ disabled: r.status !== 'PENDING' }),
        }}
        pagination={{ pageSize: 50 }}
      />

      {/* Convert modal */}
      <Modal
        title={`Convert ${pendingSelected.length} absence(s) to leave`}
        open={convertModal}
        onCancel={() => setConvertModal(false)}
        onOk={doConvert}
        confirmLoading={saving}
        okText="Convert & Approve"
      >
        <Form layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item label="Leave type" required>
            <Select
              options={types.map((t) => ({ value: t.id, label: `${t.code} — ${t.name}` }))}
              value={convertTypeId}
              onChange={setConvertTypeId}
              placeholder="Select leave type"
            />
          </Form.Item>
          <Form.Item label="Notes">
            <Input.TextArea
              rows={2}
              value={actionNotes}
              onChange={(e) => setActionNotes(e.target.value)}
            />
          </Form.Item>
        </Form>
        <div style={{ color: 'rgba(0,0,0,0.45)', fontSize: 12 }}>
          Dates: {pendingSelected.map((r) => r.absenceDate).join(', ')}
        </div>
      </Modal>

      {/* Dismiss modal */}
      <Modal
        title={`Dismiss ${pendingSelected.length} absence(s)`}
        open={dismissModal}
        onCancel={() => setDismissModal(false)}
        onOk={doDismiss}
        confirmLoading={saving}
        okText="Dismiss"
        okButtonProps={{ danger: true }}
      >
        <Form layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item label="Notes">
            <Input.TextArea
              rows={2}
              value={actionNotes}
              onChange={(e) => setActionNotes(e.target.value)}
              placeholder="Reason for dismissing (optional)"
            />
          </Form.Item>
        </Form>
        <div style={{ color: 'rgba(0,0,0,0.45)', fontSize: 12 }}>
          Dates: {pendingSelected.map((r) => r.absenceDate).join(', ')}
        </div>
      </Modal>
    </div>
  )
}
