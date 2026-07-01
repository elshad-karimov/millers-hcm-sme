import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  App as AntdApp,
  Divider,
  Alert,
} from 'antd'
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import {
  compensationApi,
  type MarketSalarySurveyDto,
  type CreateMarketSurveyRequest,
  type MarketSalaryDataDto,
  type AddMarketDataRequest,
  type MarketComparisonDto,
} from '../api/compensation'
import { employeesApi, type Employee } from '../api/employees'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const { Title, Text } = Typography

interface SurveyFormValues {
  provider: string
  surveyYear: number
  country?: string
  currency?: string
  notes?: string
}

interface DataFormValues {
  jobCode?: string
  gradeCode?: string
  location?: string
  p25?: number
  p50?: number
  p75?: number
  p90?: number
  currency?: string
}

export function MarketDataPage() {
  const { hasRole } = useAuth()
  const { message, modal } = AntdApp.useApp()
  const canWrite = hasRole(...RoleSets.COMPENSATION_WRITE)

  const [surveys, setSurveys] = useState<MarketSalarySurveyDto[]>([])
  const [loadingSurveys, setLoadingSurveys] = useState(false)
  const [selectedSurvey, setSelectedSurvey] = useState<MarketSalarySurveyDto | null>(null)

  const [surveyDrawerOpen, setSurveyDrawerOpen] = useState(false)
  const [surveyForm] = Form.useForm<SurveyFormValues>()

  const [surveyData, setSurveyData] = useState<MarketSalaryDataDto[]>([])
  const [loadingData, setLoadingData] = useState(false)
  const [dataModalOpen, setDataModalOpen] = useState(false)
  const [dataForm] = Form.useForm<DataFormValues>()

  // Comparison panel
  const [employees, setEmployees] = useState<Employee[]>([])
  const [loadingEmployees, setLoadingEmployees] = useState(false)
  const [selectedEmployeeId, setSelectedEmployeeId] = useState<string | undefined>()
  const [comparisonSurveyId, setComparisonSurveyId] = useState<string | undefined>()
  const [comparison, setComparison] = useState<MarketComparisonDto | null>(null)
  const [loadingComparison, setLoadingComparison] = useState(false)

  const loadSurveys = () => {
    setLoadingSurveys(true)
    compensationApi
      .listMarketSurveys()
      .then(setSurveys)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load surveys'),
      )
      .finally(() => setLoadingSurveys(false))
  }

  const loadData = (surveyId: string) => {
    setLoadingData(true)
    compensationApi
      .listMarketData(surveyId)
      .then(setSurveyData)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load market data'),
      )
      .finally(() => setLoadingData(false))
  }

  useEffect(() => {
    loadSurveys()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (selectedSurvey) {
      loadData(selectedSurvey.id)
    } else {
      setSurveyData([])
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedSurvey])

  const openCreateSurvey = () => {
    surveyForm.resetFields()
    surveyForm.setFieldsValue({ currency: 'AZN', surveyYear: new Date().getFullYear() })
    setSurveyDrawerOpen(true)
  }

  const submitSurvey = async (v: SurveyFormValues) => {
    const payload: CreateMarketSurveyRequest = {
      provider: v.provider,
      surveyYear: v.surveyYear,
      country: v.country,
      currency: v.currency,
      notes: v.notes,
    }

    try {
      await compensationApi.createMarketSurvey(payload)
      message.success('Survey created')
      setSurveyDrawerOpen(false)
      loadSurveys()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Create failed',
      )
    }
  }

  const handleDeleteSurvey = (survey: MarketSalarySurveyDto) => {
    modal.confirm({
      title: 'Delete Survey?',
      content: `Delete ${survey.provider} (${survey.surveyYear})?`,
      okText: 'Delete',
      okType: 'danger',
      onOk: async () => {
        try {
          await compensationApi.deleteMarketSurvey(survey.id)
          message.success('Survey deleted')
          if (selectedSurvey?.id === survey.id) {
            setSelectedSurvey(null)
          }
          loadSurveys()
        } catch (err) {
          message.error(
            (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
              'Delete failed',
          )
        }
      },
    })
  }

  const openAddData = () => {
    if (!selectedSurvey) {
      message.warning('Select a survey first')
      return
    }
    dataForm.resetFields()
    dataForm.setFieldsValue({ currency: selectedSurvey.currency ?? 'AZN' })
    setDataModalOpen(true)
  }

  const submitData = async (v: DataFormValues) => {
    if (!selectedSurvey) return

    const payload: AddMarketDataRequest = {
      jobCode: v.jobCode,
      gradeCode: v.gradeCode,
      location: v.location,
      p25: v.p25,
      p50: v.p50,
      p75: v.p75,
      p90: v.p90,
      currency: v.currency,
    }

    try {
      await compensationApi.addMarketData(selectedSurvey.id, payload)
      message.success('Market data added')
      setDataModalOpen(false)
      loadData(selectedSurvey.id)
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Add failed',
      )
    }
  }

  const handleDeleteData = (row: MarketSalaryDataDto) => {
    modal.confirm({
      title: 'Delete Data Row?',
      content: 'Delete this market data row?',
      okText: 'Delete',
      okType: 'danger',
      onOk: async () => {
        try {
          await compensationApi.deleteMarketData(row.id)
          message.success('Data deleted')
          if (selectedSurvey) {
            loadData(selectedSurvey.id)
          }
        } catch (err) {
          message.error(
            (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
              'Delete failed',
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

  const handleCompare = () => {
    if (!selectedEmployeeId) {
      message.warning('Select an employee')
      return
    }
    setLoadingComparison(true)
    compensationApi
      .compareMarket(selectedEmployeeId, comparisonSurveyId)
      .then(setComparison)
      .catch((err) => {
        message.error(err?.response?.data?.message ?? 'Comparison failed')
        setComparison(null)
      })
      .finally(() => setLoadingComparison(false))
  }

  const surveyCols: ColumnsType<MarketSalarySurveyDto> = [
    { title: 'Provider', dataIndex: 'provider', key: 'provider' },
    { title: 'Year', dataIndex: 'surveyYear', key: 'surveyYear', width: 100 },
    { title: 'Country', dataIndex: 'country', key: 'country', width: 120 },
    { title: 'Currency', dataIndex: 'currency', key: 'currency', width: 100 },
    {
      title: 'Actions',
      key: 'actions',
      width: 100,
      render: (_, rec) => (
        <Space>
          <Button
            type="link"
            size="small"
            onClick={() => setSelectedSurvey(rec)}
          >
            View Data
          </Button>
          {canWrite && (
            <Button
              type="link"
              size="small"
              danger
              icon={<DeleteOutlined />}
              onClick={() => handleDeleteSurvey(rec)}
            />
          )}
        </Space>
      ),
    },
  ]

  const dataCols: ColumnsType<MarketSalaryDataDto> = [
    { title: 'Job Code', dataIndex: 'jobCode', key: 'jobCode', width: 120 },
    { title: 'Grade', dataIndex: 'gradeCode', key: 'gradeCode', width: 100 },
    { title: 'Location', dataIndex: 'location', key: 'location', width: 120 },
    {
      title: 'P25',
      dataIndex: 'p25',
      key: 'p25',
      width: 110,
      align: 'right',
      render: (v?: number) => v?.toLocaleString() ?? '—',
    },
    {
      title: 'P50',
      dataIndex: 'p50',
      key: 'p50',
      width: 110,
      align: 'right',
      render: (v?: number) => v?.toLocaleString() ?? '—',
    },
    {
      title: 'P75',
      dataIndex: 'p75',
      key: 'p75',
      width: 110,
      align: 'right',
      render: (v?: number) => v?.toLocaleString() ?? '—',
    },
    {
      title: 'P90',
      dataIndex: 'p90',
      key: 'p90',
      width: 110,
      align: 'right',
      render: (v?: number) => v?.toLocaleString() ?? '—',
    },
    {
      title: '',
      key: 'del',
      width: 60,
      render: (_, rec) =>
        canWrite ? (
          <Button
            type="link"
            size="small"
            danger
            icon={<DeleteOutlined />}
            onClick={() => handleDeleteData(rec)}
          />
        ) : null,
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
        <Title level={2}>Market Salary Data</Title>
        {canWrite && (
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreateSurvey}>
            New Survey
          </Button>
        )}
      </div>

      <Card title="Surveys" size="small" style={{ marginBottom: 16 }}>
        <Table
          dataSource={surveys}
          columns={surveyCols}
          rowKey="id"
          loading={loadingSurveys}
          pagination={false}
          size="small"
          rowSelection={
            selectedSurvey
              ? {
                  type: 'radio',
                  selectedRowKeys: [selectedSurvey.id],
                  onChange: (keys) => {
                    const s = surveys.find((x) => x.id === keys[0])
                    setSelectedSurvey(s ?? null)
                  },
                }
              : undefined
          }
        />
      </Card>

      {selectedSurvey && (
        <Card
          title={`Data for ${selectedSurvey.provider} (${selectedSurvey.surveyYear})`}
          size="small"
          extra={
            canWrite ? (
              <Button size="small" icon={<PlusOutlined />} onClick={openAddData}>
                Add Data
              </Button>
            ) : null
          }
          style={{ marginBottom: 16 }}
        >
          <Table
            dataSource={surveyData}
            columns={dataCols}
            rowKey="id"
            loading={loadingData}
            pagination={false}
            size="small"
          />
        </Card>
      )}

      <Divider />

      <Card title="Compare Employee to Market" size="small">
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
              placeholder="Survey (optional)"
              style={{ width: 250 }}
              allowClear
              onChange={setComparisonSurveyId}
              options={surveys.map((s) => ({
                value: s.id,
                label: `${s.provider} (${s.surveyYear})`,
              }))}
            />
            <Button
              type="primary"
              onClick={handleCompare}
              loading={loadingComparison}
              disabled={!selectedEmployeeId}
            >
              Compare
            </Button>
          </Space>

          {comparison && (
            <Alert
              type="info"
              message={
                <Space direction="vertical" size="small">
                  <div>
                    <Text strong>Current Salary: </Text>
                    <Text style={{ fontSize: 16 }}>
                      {comparison.currentSalary.toLocaleString()}
                    </Text>
                  </div>
                  {comparison.gradeCode && (
                    <div>
                      <Text strong>Grade: </Text>
                      <Text>{comparison.gradeCode}</Text>
                    </div>
                  )}
                  {comparison.surveyProvider && (
                    <div>
                      <Text strong>Survey: </Text>
                      <Text>
                        {comparison.surveyProvider} ({comparison.surveyYear})
                      </Text>
                    </div>
                  )}
                  {comparison.p25 != null && (
                    <div>
                      <Text strong>Market Percentiles: </Text>
                      <Space>
                        <Tag>P25: {comparison.p25.toLocaleString()}</Tag>
                        <Tag>P50: {comparison.p50?.toLocaleString() ?? '—'}</Tag>
                        <Tag>P75: {comparison.p75?.toLocaleString() ?? '—'}</Tag>
                        <Tag>P90: {comparison.p90?.toLocaleString() ?? '—'}</Tag>
                      </Space>
                    </div>
                  )}
                  {comparison.marketRatio != null && (
                    <div>
                      <Text strong>Market Ratio: </Text>
                      <Text style={{ fontSize: 16, fontWeight: 600 }}>
                        {comparison.marketRatio.toFixed(2)}%
                      </Text>
                    </div>
                  )}
                  {comparison.positionVsMarket && (
                    <div>
                      <Text strong>Position: </Text>
                      <Tag
                        color={
                          comparison.positionVsMarket.includes('Below')
                            ? 'red'
                            : comparison.positionVsMarket.includes('Above')
                            ? 'green'
                            : 'blue'
                        }
                      >
                        {comparison.positionVsMarket}
                      </Tag>
                    </div>
                  )}
                </Space>
              }
            />
          )}
        </Space>
      </Card>

      <Drawer
        title="New Survey"
        open={surveyDrawerOpen}
        onClose={() => setSurveyDrawerOpen(false)}
        width={500}
      >
        <Form form={surveyForm} layout="vertical" onFinish={submitSurvey}>
          <Form.Item
            name="provider"
            label="Provider"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Input placeholder="e.g., Mercer, Willis Towers Watson" />
          </Form.Item>

          <Form.Item
            name="surveyYear"
            label="Survey Year"
            rules={[{ required: true, message: 'Required' }]}
          >
            <InputNumber min={2020} max={2030} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item name="country" label="Country">
            <Input placeholder="e.g., Azerbaijan" />
          </Form.Item>

          <Form.Item name="currency" label="Currency">
            <Select
              options={[
                { value: 'AZN', label: 'AZN' },
                { value: 'USD', label: 'USD' },
                { value: 'EUR', label: 'EUR' },
              ]}
            />
          </Form.Item>

          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={3} />
          </Form.Item>

          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                Create
              </Button>
              <Button onClick={() => setSurveyDrawerOpen(false)}>Cancel</Button>
            </Space>
          </Form.Item>
        </Form>
      </Drawer>

      <Modal
        title="Add Market Data"
        open={dataModalOpen}
        onCancel={() => setDataModalOpen(false)}
        footer={null}
        width={600}
      >
        <Form form={dataForm} layout="vertical" onFinish={submitData}>
          <Form.Item name="jobCode" label="Job Code">
            <Input />
          </Form.Item>

          <Form.Item name="gradeCode" label="Grade Code">
            <Input />
          </Form.Item>

          <Form.Item name="location" label="Location">
            <Input placeholder="e.g., Baku, Remote" />
          </Form.Item>

          <Space style={{ width: '100%', marginBottom: 16 }}>
            <Form.Item name="p25" label="P25" style={{ marginBottom: 0 }}>
              <InputNumber min={0} precision={2} style={{ width: 130 }} />
            </Form.Item>
            <Form.Item name="p50" label="P50" style={{ marginBottom: 0 }}>
              <InputNumber min={0} precision={2} style={{ width: 130 }} />
            </Form.Item>
            <Form.Item name="p75" label="P75" style={{ marginBottom: 0 }}>
              <InputNumber min={0} precision={2} style={{ width: 130 }} />
            </Form.Item>
            <Form.Item name="p90" label="P90" style={{ marginBottom: 0 }}>
              <InputNumber min={0} precision={2} style={{ width: 130 }} />
            </Form.Item>
          </Space>

          <Form.Item name="currency" label="Currency">
            <Select
              options={[
                { value: 'AZN', label: 'AZN' },
                { value: 'USD', label: 'USD' },
                { value: 'EUR', label: 'EUR' },
              ]}
            />
          </Form.Item>

          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                Add
              </Button>
              <Button onClick={() => setDataModalOpen(false)}>Cancel</Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
