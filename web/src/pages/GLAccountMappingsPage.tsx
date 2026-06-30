import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Drawer,
  Form,
  Input,
  Radio,
  Select,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  payrollApi,
  type GLAccountMapping,
  type GLAccountMappingRequest,
  type GLAccountType,
} from '../api/payroll'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const ACCOUNT_TYPE_COLOR: Record<GLAccountType, string> = {
  DEBIT: 'red',
  CREDIT: 'green',
}

export function GLAccountMappingsPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const canWrite = hasRole(...RoleSets.PAYROLL_WRITE)

  const [rows, setRows] = useState<GLAccountMapping[]>([])
  const [loading, setLoading] = useState(false)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [form] = Form.useForm<GLAccountMappingRequest>()

  const load = () => {
    setLoading(true)
    payrollApi
      .glMappings()
      .then(setRows)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load mappings'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openCreate = () => {
    form.resetFields()
    setDrawerOpen(true)
  }

  const submit = async (v: GLAccountMappingRequest) => {
    try {
      await payrollApi.createGLMapping(v)
      message.success('GL mapping created')
      setDrawerOpen(false)
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Save failed',
      )
    }
  }

  const columns: ColumnsType<GLAccountMapping> = [
    {
      title: 'Component Kind',
      dataIndex: 'componentKind',
      width: 140,
    },
    {
      title: 'Component Code',
      dataIndex: 'componentCode',
      render: (v: string | null) => v ?? <Typography.Text type="secondary">(default)</Typography.Text>,
    },
    {
      title: 'Account Type',
      dataIndex: 'accountType',
      width: 120,
      render: (t: GLAccountType) => <Tag color={ACCOUNT_TYPE_COLOR[t]}>{t}</Tag>,
    },
    {
      title: 'GL Code',
      dataIndex: 'glAccountCode',
      width: 120,
    },
    {
      title: 'GL Name',
      dataIndex: 'glAccountName',
    },
    {
      title: 'Active',
      dataIndex: 'isActive',
      width: 80,
      render: (v: boolean) => (v ? 'Yes' : 'No'),
    },
  ]

  return (
    <Card
      title={<Typography.Title level={4} style={{ margin: 0 }}>GL Account Mappings</Typography.Title>}
      extra={
        canWrite && (
          <Button type="primary" onClick={openCreate}>
            Create Mapping
          </Button>
        )
      }
    >
      <Table rowKey="id" columns={columns} dataSource={rows} loading={loading} pagination={false} />

      <Drawer
        open={drawerOpen}
        title="Create GL Mapping"
        onClose={() => setDrawerOpen(false)}
        width={400}
        extra={
          <Button type="primary" onClick={() => form.submit()}>
            Save
          </Button>
        }
      >
        <Form form={form} layout="vertical" onFinish={submit}>
          <Form.Item
            name="componentKind"
            label="Component Kind"
            rules={[{ required: true, message: 'Component kind is required' }]}
          >
            <Select
              options={[
                { value: 'EARNING', label: 'Earning' },
                { value: 'DEDUCTION', label: 'Deduction' },
                { value: 'TAX', label: 'Tax' },
                { value: 'DSMF_EE', label: 'DSMF Employee' },
                { value: 'DSMF_ER', label: 'DSMF Employer' },
                { value: 'MMI_EE', label: 'MMI Employee' },
                { value: 'MMI_ER', label: 'MMI Employer' },
                { value: 'UNEMPL_EE', label: 'Unemployment Employee' },
                { value: 'UNEMPL_ER', label: 'Unemployment Employer' },
                { value: 'NET_PAY', label: 'Net Pay' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="componentCode"
            label="Component Code"
            help="Leave blank for kind-level default mapping"
          >
            <Input maxLength={20} />
          </Form.Item>
          <Form.Item
            name="accountType"
            label="Account Type"
            rules={[{ required: true, message: 'Account type is required' }]}
          >
            <Radio.Group>
              <Radio value="DEBIT">Debit</Radio>
              <Radio value="CREDIT">Credit</Radio>
            </Radio.Group>
          </Form.Item>
          <Form.Item
            name="glAccountCode"
            label="GL Account Code"
            rules={[{ required: true, message: 'GL code is required' }]}
          >
            <Input maxLength={20} />
          </Form.Item>
          <Form.Item
            name="glAccountName"
            label="GL Account Name"
            rules={[{ required: true, message: 'GL name is required' }]}
          >
            <Input maxLength={100} />
          </Form.Item>
        </Form>
      </Drawer>
    </Card>
  )
}
