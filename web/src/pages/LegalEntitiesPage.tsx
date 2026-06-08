// M140 — Legal entity admin (HR-admin only).
//
// Manages the registered-company master that sits above the org-unit
// hierarchy. Drives payroll bank file generation, statutory deductions,
// and letter-engine company-seal printing. Bank account is AES-encrypted
// at rest and role-gated to HR_ADMIN / SYSTEM_ADMIN on display.

import { useEffect, useState } from 'react'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Col,
  DatePicker,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Row,
  Space,
  Spin,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { legalEntitiesApi, type LegalEntityRequest, type LegalEntityResponse } from '../api/legalEntities'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const { Title, Text } = Typography

export function LegalEntitiesPage() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canWrite = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [items, setItems] = useState<LegalEntityResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<LegalEntityResponse | null>(null)
  const [form] = Form.useForm<LegalEntityRequest & {
    window?: [ReturnType<typeof dayjs>, ReturnType<typeof dayjs>]
  }>()

  const load = () => {
    setLoading(true)
    legalEntitiesApi.list(false)
      .then(setItems)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load legal entities'))
      .finally(() => setLoading(false))
  }
  useEffect(load, []) // eslint-disable-line

  const startCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ active: true, currency: 'AZN', country: 'AZ' })
    setOpen(true)
  }

  const startEdit = (e: LegalEntityResponse) => {
    setEditing(e)
    form.setFieldsValue({
      code: e.code,
      name: e.name,
      registrationNumber: e.registrationNumber ?? undefined,
      taxId: e.taxId ?? undefined,
      socialInsuranceRegNumber: e.socialInsuranceRegNumber ?? undefined,
      legalAddress: e.legalAddress ?? undefined,
      country: e.country ?? undefined,
      currency: e.currency ?? undefined,
      fiscalCalendar: e.fiscalCalendar ?? undefined,
      payrollBankName: e.payrollBankName ?? undefined,
      // Plaintext only present for cleared roles. The form leaves it
      // blank otherwise; submitting blank does not overwrite the stored
      // value because the service treats undefined as "no change".
      payrollBankAccount: e.payrollBankAccount ?? undefined,
      payrollBankSwift: e.payrollBankSwift ?? undefined,
      defaultCostCentreCode: e.defaultCostCentreCode ?? undefined,
      chartOfAccountsRef: e.chartOfAccountsRef ?? undefined,
      legalRepresentativeName: e.legalRepresentativeName ?? undefined,
      legalRepresentativeTitle: e.legalRepresentativeTitle ?? undefined,
      companySealUrl: e.companySealUrl ?? undefined,
      active: e.active,
      window: e.effectiveFrom
        ? [dayjs(e.effectiveFrom), e.effectiveTo ? dayjs(e.effectiveTo) : dayjs(e.effectiveFrom).add(10, 'year')]
        : undefined,
      notes: e.notes ?? undefined,
    })
    setOpen(true)
  }

  const submit = async () => {
    const v = await form.validateFields()
    const payload: LegalEntityRequest = {
      ...v,
      country: v.country?.toUpperCase(),
      currency: v.currency?.toUpperCase(),
      payrollBankSwift: v.payrollBankSwift?.toUpperCase(),
      effectiveFrom: v.window?.[0]?.format('YYYY-MM-DD'),
      effectiveTo: v.window?.[1]?.format('YYYY-MM-DD'),
    }
    // Drop the window helper before submit.
    delete (payload as unknown as Record<string, unknown>).window
    try {
      if (editing) {
        await legalEntitiesApi.update(editing.id, payload)
        message.success('Legal entity updated')
      } else {
        await legalEntitiesApi.create(payload)
        message.success('Legal entity created')
      }
      setOpen(false)
      load()
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Save failed',
      )
    }
  }

  const toggleActive = async (e: LegalEntityResponse) => {
    try {
      if (e.active) await legalEntitiesApi.deactivate(e.id)
      else await legalEntitiesApi.activate(e.id)
      message.success(e.active ? 'Deactivated' : 'Activated')
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Failed',
      )
    }
  }

  const cols: ColumnsType<LegalEntityResponse> = [
    {
      title: 'Code / name', width: 280,
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          <Text strong>{r.code}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>{r.name}</Text>
        </Space>
      ),
    },
    {
      title: 'Country / currency', width: 140,
      render: (_, r) => (
        <Space size={4}>
          {r.country && <Tag>{r.country}</Tag>}
          {r.currency && <Tag color="blue">{r.currency}</Tag>}
        </Space>
      ),
    },
    { title: 'Tax id', dataIndex: 'taxId', render: (v?: string | null) => v ?? '—' },
    { title: 'Reg #', dataIndex: 'registrationNumber', render: (v?: string | null) => v ?? '—' },
    {
      title: 'Payroll bank', width: 220,
      render: (_, r) => r.payrollBankName
        ? (
          <Space direction="vertical" size={0}>
            <Text>{r.payrollBankName}</Text>
            {r.payrollBankAccountMasked && (
              <Text type="secondary" style={{ fontSize: 11 }}>{r.payrollBankAccountMasked}</Text>
            )}
          </Space>
        )
        : <Text type="secondary">—</Text>,
    },
    {
      title: 'Status', width: 100,
      render: (_, r) => r.active
        ? <Tag color="green">ACTIVE</Tag>
        : <Tag color="default">INACTIVE</Tag>,
    },
    {
      title: '', width: 220, align: 'right',
      render: (_, r) => canWrite
        ? (
          <Space size={4}>
            <Button size="small" onClick={() => startEdit(r)}>Edit</Button>
            <Popconfirm
              title={r.active ? 'Deactivate this legal entity?' : 'Reactivate this legal entity?'}
              onConfirm={() => toggleActive(r)}
            >
              <Button size="small" danger={r.active}>
                {r.active ? 'Deactivate' : 'Reactivate'}
              </Button>
            </Popconfirm>
          </Space>
        )
        : null,
    },
  ]

  if (loading) return <Spin />

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
        <Title level={3} style={{ margin: 0 }}>Legal entities</Title>
        {canWrite && <Button type="primary" onClick={startCreate}>New legal entity…</Button>}
      </Space>

      <Alert
        type="info"
        showIcon
        message="What this is"
        description="A legal entity is a registered company that owns payroll, statutory filings, and contracts. Link COMPANY-type org units to a legal entity to anchor each registered company's sub-tree."
      />

      <Card>
        <Table
          rowKey="id"
          columns={cols}
          dataSource={items}
          size="small"
          pagination={{ pageSize: 25 }}
          locale={{ emptyText: <Empty description="No legal entities yet" /> }}
        />
      </Card>

      <Modal
        open={open}
        title={editing ? `Edit — ${editing.code}` : 'New legal entity'}
        onCancel={() => setOpen(false)}
        onOk={submit}
        okText={editing ? 'Save' : 'Create'}
        width={840}
      >
        <Form form={form} layout="vertical">
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="code" label="Code" rules={[{ required: true, max: 60 }]}>
                <Input placeholder="MILLERS-AZ" disabled={!!editing} />
              </Form.Item>
            </Col>
            <Col span={16}>
              <Form.Item name="name" label="Registered name" rules={[{ required: true, max: 240 }]}>
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="taxId" label="Tax ID">
                <Input maxLength={80} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="registrationNumber" label="Registration #">
                <Input maxLength={80} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="socialInsuranceRegNumber" label="Social insurance reg #">
                <Input maxLength={80} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="legalAddress" label="Legal address">
            <Input.TextArea rows={2} maxLength={500} />
          </Form.Item>
          <Row gutter={12}>
            <Col span={6}>
              <Form.Item name="country" label="Country (ISO 3166-1)"
                rules={[{ pattern: /^[A-Z]{2}$/i, message: '2-letter code' }]}>
                <Input maxLength={2} placeholder="AZ" />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="currency" label="Currency (ISO 4217)"
                rules={[{ pattern: /^[A-Z]{3}$/i, message: '3-letter code' }]}>
                <Input maxLength={3} placeholder="AZN" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="fiscalCalendar" label="Fiscal calendar"
                tooltip="Free text, e.g. JAN-DEC or APR-MAR.">
                <Input maxLength={40} placeholder="JAN-DEC" />
              </Form.Item>
            </Col>
          </Row>

          <Title level={5} style={{ margin: '8px 0' }}>Payroll bank (encrypted at rest)</Title>
          <Row gutter={12}>
            <Col span={10}>
              <Form.Item name="payrollBankName" label="Bank name">
                <Input maxLength={160} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="payrollBankAccount" label="Account number"
                tooltip="Only HR_ADMIN / SYSTEM_ADMIN can view plaintext or save.">
                <Input maxLength={64} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="payrollBankSwift" label="SWIFT / BIC"
                rules={[{ pattern: /^[A-Z0-9]{8}([A-Z0-9]{3})?$|^$/, message: '8 or 11 chars' }]}>
                <Input maxLength={11} />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="defaultCostCentreCode" label="Default cost centre">
                <Input maxLength={60} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="chartOfAccountsRef" label="Chart-of-accounts reference">
                <Input maxLength={120} />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="legalRepresentativeName" label="Legal representative">
                <Input maxLength={160} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="legalRepresentativeTitle" label="Title">
                <Input maxLength={120} />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={12}>
            <Col span={16}>
              <Form.Item name="window" label="Effective window">
                <DatePicker.RangePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="active" label="Active" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="companySealUrl" label="Seal image URL">
                <Input placeholder="https://…" />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={2} maxLength={4000} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}
