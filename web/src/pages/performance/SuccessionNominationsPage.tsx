// M103 — Named successor nominations.
// HR Admin can nominate a specific employee as the successor for a
// position at a given readiness tier. Complements the 9-box bucket
// view with durable, auditable records.

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { Link } from 'react-router-dom'
import {
  nominationsApi,
  READINESS_COLOR,
  READINESS_LABEL,
  type NominationResponse,
  type Readiness,
} from '../../api/successionNominations'
import { positionsApi } from '../../api/positions'

const { Title, Text } = Typography

const READINESS_OPTIONS = (
  ['READY_NOW', 'READY_SOON', 'READY_LONG_TERM', 'UNDER_DEVELOPMENT'] as Readiness[]
).map((r) => ({ value: r, label: READINESS_LABEL[r] }))

export function SuccessionNominationsPage() {
  const { message } = AntdApp.useApp()
  const [positions, setPositions] = useState<{ id: string; code: string; title: string }[]>([])
  const [selectedPositionId, setSelectedPositionId] = useState<string | null>(null)
  const [nominations, setNominations] = useState<NominationResponse[]>([])
  const [loadingPositions, setLoadingPositions] = useState(true)
  const [loadingNominations, setLoadingNominations] = useState(false)
  const [nominateOpen, setNominateOpen] = useState(false)
  const [nominateForm] = Form.useForm<{
    nomineeEmployeeId: string
    readinessTier: Readiness
    notes?: string
  }>()
  const [nominating, setNominating] = useState(false)

  useEffect(() => {
    setLoadingPositions(true)
    positionsApi
      .list({ status: 'ACTIVE', size: 500 })
      .then((res) =>
        setPositions(
          res.content.map((p) => ({
            id: p.id,
            code: p.code,
            title: p.title,
          })),
        ),
      )
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load positions'))
      .finally(() => setLoadingPositions(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const loadNominations = (positionId: string) => {
    setLoadingNominations(true)
    nominationsApi
      .forPosition(positionId)
      .then(setNominations)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load nominations'))
      .finally(() => setLoadingNominations(false))
  }

  const onPositionChange = (id: string) => {
    setSelectedPositionId(id)
    loadNominations(id)
  }

  const submitNominate = async () => {
    if (!selectedPositionId) return
    const v = await nominateForm.validateFields()
    setNominating(true)
    try {
      await nominationsApi.nominate(selectedPositionId, v)
      message.success('Nominated')
      setNominateOpen(false)
      nominateForm.resetFields()
      loadNominations(selectedPositionId)
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Nomination failed',
      )
    } finally {
      setNominating(false)
    }
  }

  const cancelNomination = async (nom: NominationResponse) => {
    try {
      await nominationsApi.cancel(nom.id, { reason: 'Cancelled from nominations page' })
      message.success('Cancelled')
      if (selectedPositionId) loadNominations(selectedPositionId)
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Cancel failed',
      )
    }
  }

  const cols: ColumnsType<NominationResponse> = [
    {
      title: 'Nominee',
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          <Link to={`/employees/${r.nomineeEmployeeId}`}>{r.nomineeName ?? '—'}</Link>
          <Text type="secondary" style={{ fontSize: 11 }}>{r.nomineeEmployeeNo}</Text>
        </Space>
      ),
    },
    { title: 'Department', dataIndex: 'department', render: (v) => v ?? '—' },
    {
      title: 'Readiness',
      dataIndex: 'readinessTier',
      width: 160,
      filters: READINESS_OPTIONS.map((o) => ({ text: o.label, value: o.value })),
      onFilter: (val, r) => r.readinessTier === val,
      render: (t: Readiness) => (
        <Tag color={READINESS_COLOR[t]}>{READINESS_LABEL[t]}</Tag>
      ),
    },
    { title: 'Notes', dataIndex: 'notes', render: (v) => v ?? '—' },
    { title: 'By', dataIndex: 'nominatedBy', width: 140, render: (v) => v ?? '—' },
    {
      title: 'Date',
      dataIndex: 'createdAt',
      width: 130,
      render: (v: string) => dayjs(v).format('YYYY-MM-DD'),
    },
    {
      title: '',
      width: 100,
      render: (_, r) =>
        r.active ? (
          <Popconfirm
            title="Cancel this nomination?"
            onConfirm={() => cancelNomination(r)}
            okText="Cancel"
            cancelText="Keep"
          >
            <Button size="small" danger>Cancel</Button>
          </Popconfirm>
        ) : (
          <Tag color="default">Cancelled</Tag>
        ),
    },
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
        <Title level={3} style={{ margin: 0 }}>Succession nominations</Title>
        <Text type="secondary">
          Formal named-successor records for auditors.
          Complements the 9-box bucket view with durable, position-specific designations.
        </Text>
      </Space>

      <Card>
        <Space wrap style={{ marginBottom: 16 }}>
          <Text strong>Position:</Text>
          <Select
            showSearch
            optionFilterProp="label"
            loading={loadingPositions}
            style={{ minWidth: 340 }}
            placeholder="Pick a position to view / nominate successors"
            onChange={onPositionChange}
            options={positions.map((p) => ({
              value: p.id,
              label: `${p.code} — ${p.title}`,
            }))}
          />
          {selectedPositionId && (
            <Button type="primary" onClick={() => setNominateOpen(true)}>
              Nominate successor…
            </Button>
          )}
        </Space>

        {!selectedPositionId ? (
          <Empty description="Select a position to see its succession nominations." />
        ) : loadingNominations ? (
          <Spin />
        ) : (
          <Table
            rowKey="id"
            columns={cols}
            dataSource={nominations}
            pagination={false}
            size="small"
            locale={{
              emptyText: (
                <Empty description="No active nominations for this position yet." />
              ),
            }}
          />
        )}
      </Card>

      <Modal
        open={nominateOpen}
        title={`Nominate successor for ${
          positions.find((p) => p.id === selectedPositionId)?.title ?? '…'
        }`}
        onCancel={() => setNominateOpen(false)}
        onOk={submitNominate}
        confirmLoading={nominating}
        okText="Nominate"
      >
        <Form form={nominateForm} layout="vertical">
          <Form.Item
            name="nomineeEmployeeId"
            label="Nominee employee ID"
            rules={[{ required: true, message: 'Required' }]}
            extra="UUID of the designated successor (paste from the employee page)."
          >
            <Input placeholder="00000000-0000-0000-0000-000000000000" />
          </Form.Item>
          <Form.Item
            name="readinessTier"
            label="Readiness tier"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Select options={READINESS_OPTIONS} />
          </Form.Item>
          <Form.Item name="notes" label="Notes (optional)">
            <Input.TextArea rows={3} placeholder="Context for auditors / HR committee" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}
