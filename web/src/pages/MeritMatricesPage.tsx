import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Drawer,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
  App as AntdApp,
  Divider,
} from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import {
  compensationApi,
  type MeritMatrixDto,
  type MeritMatrixRequest,
  type MeritMatrixCellDto,
  type PerformanceBand,
  type MeritRangePosition,
  type MeritSuggestionDto,
} from '../api/compensation'
import { employeesApi, type Employee } from '../api/employees'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const { Title, Text } = Typography

const PERFORMANCE_BANDS: PerformanceBand[] = ['EXCELLENT', 'GOOD', 'MEETS', 'BELOW']
const RANGE_POSITIONS: MeritRangePosition[] = ['LOW', 'MID', 'HIGH']

interface FormValues {
  code: string
  name: string
  isActive: boolean
}

export function MeritMatricesPage() {
  const { hasRole } = useAuth()
  const { message, modal } = AntdApp.useApp()
  const canWrite = hasRole(...RoleSets.COMPENSATION_WRITE)

  const [rows, setRows] = useState<MeritMatrixDto[]>([])
  const [loading, setLoading] = useState(false)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editing, setEditing] = useState<MeritMatrixDto | null>(null)
  const [form] = Form.useForm<FormValues>()

  // Grid state for cells
  const [cells, setCells] = useState<MeritMatrixCellDto[]>([])

  // Merit suggestion panel
  const [employees, setEmployees] = useState<Employee[]>([])
  const [loadingEmployees, setLoadingEmployees] = useState(false)
  const [selectedEmployeeId, setSelectedEmployeeId] = useState<string | undefined>()
  const [selectedMatrixId, setSelectedMatrixId] = useState<string | undefined>()
  const [suggestion, setSuggestion] = useState<MeritSuggestionDto | null>(null)
  const [loadingSuggestion, setLoadingSuggestion] = useState(false)

  const load = () => {
    setLoading(true)
    compensationApi
      .listMeritMatrices()
      .then(setRows)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load merit matrices'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const initializeCells = (existingCells?: MeritMatrixCellDto[]) => {
    const cellMap = new Map<string, MeritMatrixCellDto>()
    existingCells?.forEach((c) => {
      const key = `${c.performanceBand}-${c.rangePosition}`
      cellMap.set(key, c)
    })

    const newCells: MeritMatrixCellDto[] = []
    PERFORMANCE_BANDS.forEach((pb) => {
      RANGE_POSITIONS.forEach((rp) => {
        const key = `${pb}-${rp}`
        const existing = cellMap.get(key)
        newCells.push(
          existing || {
            performanceBand: pb,
            rangePosition: rp,
            meritPct: 0,
          },
        )
      })
    })
    setCells(newCells)
  }

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ isActive: true })
    initializeCells()
    setDrawerOpen(true)
  }

  const openEdit = (m: MeritMatrixDto) => {
    setEditing(m)
    form.setFieldsValue({
      code: m.code,
      name: m.name,
      isActive: m.isActive,
    })
    // Load full matrix with cells
    compensationApi
      .getMeritMatrix(m.id)
      .then((full) => {
        initializeCells(full.cells)
      })
      .catch(() => {
        message.error('Failed to load matrix cells')
        initializeCells()
      })
    setDrawerOpen(true)
  }

  const updateCell = (performanceBand: PerformanceBand, rangePosition: MeritRangePosition, meritPct: number) => {
    setCells((prev) =>
      prev.map((c) =>
        c.performanceBand === performanceBand && c.rangePosition === rangePosition
          ? { ...c, meritPct }
          : c,
      ),
    )
  }

  const submit = async (v: FormValues) => {
    const payload: MeritMatrixRequest = {
      code: v.code,
      name: v.name,
      isActive: v.isActive,
      cells,
    }

    try {
      if (editing) {
        await compensationApi.updateMeritMatrix(editing.id, payload)
        message.success('Merit matrix updated')
      } else {
        await compensationApi.createMeritMatrix(payload)
        message.success('Merit matrix created')
      }
      setDrawerOpen(false)
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Save failed',
      )
    }
  }

  const handleDeactivate = (m: MeritMatrixDto) => {
    modal.confirm({
      title: 'Deactivate Merit Matrix?',
      content: `Deactivate ${m.code} — ${m.name}?`,
      okText: 'Deactivate',
      onOk: async () => {
        try {
          await compensationApi.deactivateMeritMatrix(m.id)
          message.success('Merit matrix deactivated')
          load()
        } catch (err) {
          message.error(
            (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
              'Deactivate failed',
          )
        }
      },
    })
  }

  const handleSearchEmployees = (search: string) => {
    if (search.length < 2) {
      setEmployees([])
      return
    }
    setLoadingEmployees(true)
    employeesApi
      .list({ search, size: 50 })
      .then((resp) => setEmployees(resp.content))
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to search employees'),
      )
      .finally(() => setLoadingEmployees(false))
  }

  const handleSuggest = () => {
    if (!selectedEmployeeId || !selectedMatrixId) {
      message.warning('Select both employee and matrix')
      return
    }
    setLoadingSuggestion(true)
    compensationApi
      .suggestMerit(selectedEmployeeId, selectedMatrixId)
      .then(setSuggestion)
      .catch((err) => {
        message.error(err?.response?.data?.message ?? 'Failed to suggest merit')
        setSuggestion(null)
      })
      .finally(() => setLoadingSuggestion(false))
  }

  const columns: ColumnsType<MeritMatrixDto> = [
    { title: 'Code', dataIndex: 'code', key: 'code', width: 150 },
    { title: 'Name', dataIndex: 'name', key: 'name' },
    {
      title: 'Active',
      dataIndex: 'isActive',
      key: 'isActive',
      width: 80,
      render: (v) => <Tag color={v ? 'green' : 'default'}>{v ? 'Active' : 'Inactive'}</Tag>,
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 100,
      render: (_, rec) => (
        <Space>
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => openEdit(rec)}
            disabled={!canWrite}
          />
          {rec.isActive && (
            <Button
              type="link"
              size="small"
              danger
              icon={<DeleteOutlined />}
              onClick={() => handleDeactivate(rec)}
              disabled={!canWrite}
            />
          )}
        </Space>
      ),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
        <Title level={2}>Merit Matrices</Title>
        {canWrite && (
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            Create Merit Matrix
          </Button>
        )}
      </div>

      <Card>
        <Table
          dataSource={rows}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={false}
        />
      </Card>

      <Divider />

      <Card title="Merit Suggestion" style={{ marginTop: 16 }}>
        <Space direction="vertical" style={{ width: '100%' }} size="large">
          <Space wrap>
            <Select
              showSearch
              placeholder="Select employee..."
              style={{ width: 300 }}
              filterOption={false}
              onSearch={handleSearchEmployees}
              onChange={setSelectedEmployeeId}
              loading={loadingEmployees}
              notFoundContent={loadingEmployees ? 'Loading...' : 'Type to search'}
              options={employees.map((e) => ({
                value: e.id,
                label: `${e.employeeNo} — ${e.firstName} ${e.lastName}`,
              }))}
            />
            <Select
              placeholder="Select merit matrix..."
              style={{ width: 250 }}
              onChange={setSelectedMatrixId}
              options={rows
                .filter((r) => r.isActive)
                .map((r) => ({ value: r.id, label: `${r.code} — ${r.name}` }))}
            />
            <Button
              type="primary"
              onClick={handleSuggest}
              loading={loadingSuggestion}
              disabled={!selectedEmployeeId || !selectedMatrixId}
            >
              Suggest Merit
            </Button>
          </Space>

          {suggestion && (
            <div style={{ padding: 16, background: '#f5f5f5', borderRadius: 4 }}>
              <Space direction="vertical" size="small">
                <div>
                  <Text strong>Performance Band: </Text>
                  <Tag color="blue">{suggestion.performanceBand}</Tag>
                </div>
                <div>
                  <Text strong>Range Position: </Text>
                  <Tag color="cyan">{suggestion.rangePosition}</Tag>
                </div>
                <div>
                  <Text strong>Merit %: </Text>
                  <Text style={{ fontSize: 16, color: '#52c41a' }}>{suggestion.meritPct.toFixed(2)}%</Text>
                </div>
                <Divider style={{ margin: '8px 0' }} />
                <div>
                  <Text strong>Current Salary: </Text>
                  <Text>{suggestion.currentSalary.toLocaleString()}</Text>
                </div>
                <div>
                  <Text strong>Merit Amount: </Text>
                  <Text>{suggestion.meritAmount.toLocaleString()}</Text>
                </div>
                <div>
                  <Text strong>New Salary: </Text>
                  <Text style={{ fontSize: 16, fontWeight: 600 }}>
                    {suggestion.newSalary.toLocaleString()}
                  </Text>
                </div>
              </Space>
            </div>
          )}
        </Space>
      </Card>

      <Drawer
        title={editing ? 'Edit Merit Matrix' : 'Create Merit Matrix'}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={700}
      >
        <Form form={form} layout="vertical" onFinish={submit}>
          <Form.Item name="code" label="Code" rules={[{ required: true, message: 'Required' }]}>
            <Input disabled={!!editing} />
          </Form.Item>

          <Form.Item name="name" label="Name" rules={[{ required: true, message: 'Required' }]}>
            <Input />
          </Form.Item>

          <Form.Item name="isActive" label="Active" valuePropName="checked">
            <Switch />
          </Form.Item>

          <Divider>Merit % Grid</Divider>

          <div style={{ marginBottom: 16 }}>
            <Text type="secondary" style={{ fontSize: 12 }}>
              Rows = Performance Bands, Columns = Range Positions
            </Text>
          </div>

          <table
            style={{
              width: '100%',
              borderCollapse: 'collapse',
              marginBottom: 24,
            }}
          >
            <thead>
              <tr>
                <th style={{ border: '1px solid #d9d9d9', padding: 8, background: '#fafafa' }}>
                  Performance / Position
                </th>
                {RANGE_POSITIONS.map((rp) => (
                  <th
                    key={rp}
                    style={{
                      border: '1px solid #d9d9d9',
                      padding: 8,
                      background: '#fafafa',
                      textAlign: 'center',
                    }}
                  >
                    {rp}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {PERFORMANCE_BANDS.map((pb) => (
                <tr key={pb}>
                  <td
                    style={{
                      border: '1px solid #d9d9d9',
                      padding: 8,
                      background: '#fafafa',
                      fontWeight: 600,
                    }}
                  >
                    {pb}
                  </td>
                  {RANGE_POSITIONS.map((rp) => {
                    const cell = cells.find(
                      (c) => c.performanceBand === pb && c.rangePosition === rp,
                    )
                    return (
                      <td
                        key={rp}
                        style={{
                          border: '1px solid #d9d9d9',
                          padding: 4,
                          textAlign: 'center',
                        }}
                      >
                        <InputNumber
                          min={0}
                          max={100}
                          precision={2}
                          value={cell?.meritPct ?? 0}
                          onChange={(val) => updateCell(pb, rp, val ?? 0)}
                          style={{ width: '100%' }}
                          addonAfter="%"
                        />
                      </td>
                    )
                  })}
                </tr>
              ))}
            </tbody>
          </table>

          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                {editing ? 'Update' : 'Create'}
              </Button>
              <Button onClick={() => setDrawerOpen(false)}>Cancel</Button>
            </Space>
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  )
}
