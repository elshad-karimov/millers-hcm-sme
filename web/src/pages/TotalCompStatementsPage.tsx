import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import {
  compensationApi,
  type TotalCompStatementDto,
  type TotalCompStatementStatus,
} from '../api/compensation'
import { employeesApi, type Employee } from '../api/employees'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const { Title, Text } = Typography

const STATUS_COLOR: Record<TotalCompStatementStatus, string> = {
  GENERATED: 'default',
  RELEASED: 'green',
}

export function TotalCompStatementsPage() {
  const { hasRole } = useAuth()
  const { message, modal } = AntdApp.useApp()
  const canWrite = hasRole(...RoleSets.COMPENSATION_WRITE)

  const [statements, setStatements] = useState<TotalCompStatementDto[]>([])
  const [loading, setLoading] = useState(false)
  const [year, setYear] = useState(new Date().getFullYear())

  const [generateModalOpen, setGenerateModalOpen] = useState(false)
  const [employees, setEmployees] = useState<Employee[]>([])
  const [loadingEmployees, setLoadingEmployees] = useState(false)
  const [selectedEmployeeId, setSelectedEmployeeId] = useState<string | undefined>()

  const load = () => {
    setLoading(true)
    compensationApi
      .listStatements(year)
      .then(setStatements)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load statements'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [year])

  const handleGenerateAll = () => {
    modal.confirm({
      title: 'Generate All Statements?',
      content: `Generate total compensation statements for all active employees for ${year}?`,
      okText: 'Generate All',
      onOk: async () => {
        try {
          const result = await compensationApi.generateAllStatements(year)
          message.success(`${result.generated} statements generated`)
          load()
        } catch (err) {
          message.error(
            (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
              'Generate failed',
          )
        }
      },
    })
  }

  const handleReleaseAll = () => {
    modal.confirm({
      title: 'Release All Statements?',
      content: `Release all GENERATED statements for ${year}? Employees will be able to download them.`,
      okText: 'Release All',
      onOk: async () => {
        try {
          const result = await compensationApi.releaseAllStatements(year)
          message.success(`${result.released} statements released`)
          load()
        } catch (err) {
          message.error(
            (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
              'Release failed',
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

  const handleGenerate = async () => {
    if (!selectedEmployeeId) {
      message.warning('Select an employee')
      return
    }

    try {
      await compensationApi.generateStatement(selectedEmployeeId, year)
      message.success('Statement generated')
      setGenerateModalOpen(false)
      setSelectedEmployeeId(undefined)
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Generate failed',
      )
    }
  }

  const handleRelease = (stmt: TotalCompStatementDto) => {
    modal.confirm({
      title: 'Release Statement?',
      content: `Release statement for ${stmt.employeeName ?? stmt.employeeId}? The employee will be able to download it.`,
      okText: 'Release',
      onOk: async () => {
        try {
          await compensationApi.releaseStatement(stmt.id)
          message.success('Statement released')
          load()
        } catch (err) {
          message.error(
            (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
              'Release failed',
          )
        }
      },
    })
  }

  const handleDownload = (stmt: TotalCompStatementDto) => {
    compensationApi
      .downloadStatement(stmt.id, stmt.employeeName ?? stmt.employeeId, stmt.year)
      .catch((err) => {
        message.error(
          (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
            'Download failed',
        )
      })
  }

  const columns: ColumnsType<TotalCompStatementDto> = [
    {
      title: 'Employee',
      dataIndex: 'employeeName',
      key: 'employeeName',
      render: (_val, rec) => rec.employeeName ?? rec.employeeId,
    },
    { title: 'Year', dataIndex: 'year', key: 'year', width: 80 },
    {
      title: 'Base Salary',
      dataIndex: 'baseSalary',
      key: 'baseSalary',
      width: 120,
      align: 'right',
      render: (v: number) => v.toLocaleString(),
    },
    {
      title: 'Allowances',
      dataIndex: 'allowancesTotal',
      key: 'allowancesTotal',
      width: 110,
      align: 'right',
      render: (v: number) => v.toLocaleString(),
    },
    {
      title: 'Bonus',
      dataIndex: 'bonusTotal',
      key: 'bonusTotal',
      width: 100,
      align: 'right',
      render: (v: number) => v.toLocaleString(),
    },
    {
      title: 'Incentives',
      dataIndex: 'incentivesTotal',
      key: 'incentivesTotal',
      width: 110,
      align: 'right',
      render: (v: number) => v.toLocaleString(),
    },
    {
      title: 'Commission',
      dataIndex: 'commissionTotal',
      key: 'commissionTotal',
      width: 110,
      align: 'right',
      render: (v: number) => v.toLocaleString(),
    },
    {
      title: 'Employer Contributions',
      dataIndex: 'employerContributionsTotal',
      key: 'employerContributionsTotal',
      width: 140,
      align: 'right',
      render: (v: number) => v.toLocaleString(),
    },
    {
      title: 'Total Compensation',
      dataIndex: 'totalComp',
      key: 'totalComp',
      width: 150,
      align: 'right',
      render: (v: number) => (
        <Text strong style={{ fontSize: 16 }}>
          {v.toLocaleString()}
        </Text>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (s: TotalCompStatementStatus) => (
        <Tag color={STATUS_COLOR[s]}>{s}</Tag>
      ),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 180,
      render: (_, rec) => (
        <Space>
          {rec.status === 'GENERATED' && canWrite && (
            <Button size="small" onClick={() => handleRelease(rec)}>
              Release
            </Button>
          )}
          <Button
            size="small"
            icon={<DownloadOutlined />}
            onClick={() => handleDownload(rec)}
          >
            Download
          </Button>
        </Space>
      ),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
        <Title level={2}>Total Compensation Statements</Title>
        <Space>
          <Select
            value={year}
            onChange={setYear}
            style={{ width: 100 }}
            options={[2024, 2025, 2026].map((y) => ({ value: y, label: y }))}
          />
          {canWrite && (
            <>
              <Button onClick={() => setGenerateModalOpen(true)}>Generate</Button>
              <Button type="primary" onClick={handleGenerateAll}>
                Generate All
              </Button>
              <Button onClick={handleReleaseAll}>Release All</Button>
            </>
          )}
        </Space>
      </div>

      <Card>
        <Table
          dataSource={statements}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 20 }}
        />
      </Card>

      <Modal
        title="Generate Statement"
        open={generateModalOpen}
        onCancel={() => {
          setGenerateModalOpen(false)
          setSelectedEmployeeId(undefined)
        }}
        onOk={handleGenerate}
        okText="Generate"
        width={500}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Text>Select employee:</Text>
          <Select
            showSearch
            placeholder="Type employee name or number..."
            style={{ width: '100%' }}
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
          <Text type="secondary" style={{ fontSize: 12 }}>
            The statement will be generated for year {year}.
          </Text>
        </Space>
      </Modal>
    </div>
  )
}
