import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Col,
  Empty,
  Row,
  Space,
  Statistic,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import {
  ArrowUpOutlined,
  ArrowDownOutlined,
  RocketOutlined,
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import {
  payrollApi,
  type PayrollControlBoardResponse,
  type PayrollRunStatus,
  type RunType,
} from '../api/payroll'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const STATUS_COLOR: Record<PayrollRunStatus, string> = {
  DRAFT: 'default',
  CALCULATED: 'gold',
  UNDER_REVIEW: 'orange',
  APPROVED: 'cyan',
  PAID: 'green',
  CLOSED: 'blue',
  REOPENED: 'magenta',
}

const RUN_TYPE_COLOR: Record<RunType, string> = {
  REGULAR: 'blue',
  OFF_CYCLE: 'orange',
}

export function PayrollControlBoardPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const canWrite = hasRole(...RoleSets.PAYROLL_WRITE)

  const [data, setData] = useState<PayrollControlBoardResponse | null>(null)
  const [loading, setLoading] = useState(false)

  const load = () => {
    setLoading(true)
    payrollApi
      .controlBoard()
      .then(setData)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load control board'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  if (!loading && !data?.currentRun) {
    return (
      <Card title={<Typography.Title level={4} style={{ margin: 0 }}>Payroll Control Board</Typography.Title>}>
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="No open payroll run"
        >
          {canWrite && (
            <Button type="primary" onClick={() => navigate('/payroll/runs')}>
              Create Payroll Run
            </Button>
          )}
        </Empty>
      </Card>
    )
  }

  const varianceColor = data && data.momGrossVariancePct >= 0 ? 'green' : 'red'
  const varianceIcon =
    data && data.momGrossVariancePct >= 0 ? <ArrowUpOutlined /> : <ArrowDownOutlined />

  return (
    <Card
      title={<Typography.Title level={4} style={{ margin: 0 }}>Payroll Control Board</Typography.Title>}
      loading={loading}
    >
      {data && data.currentRun && (
        <>
          <Row gutter={16} style={{ marginBottom: 24 }}>
            <Col span={6}>
              <Card>
                <Statistic
                  title="Current Run Status"
                  value={data.currentRun.status}
                  valueRender={() => (
                    <Space>
                      <Tag color={STATUS_COLOR[data.currentRun!.status]}>
                        {data.currentRun!.status.replace(/_/g, ' ')}
                      </Tag>
                      <Tag color={RUN_TYPE_COLOR[data.currentRun!.runType]}>
                        {data.currentRun!.runType}
                      </Tag>
                    </Space>
                  )}
                />
                <Typography.Text type="secondary">
                  {data.currentRun.periodYear}/{String(data.currentRun.periodMonth).padStart(2, '0')}
                </Typography.Text>
              </Card>
            </Col>
            <Col span={6}>
              <Card>
                <Statistic title="Headcount in Run" value={data.headcount} />
              </Card>
            </Col>
            <Col span={6}>
              <Card>
                <Statistic
                  title="Total Gross"
                  value={data.totalGross}
                  precision={2}
                  suffix="AZN"
                />
              </Card>
            </Col>
            <Col span={6}>
              <Card>
                <Statistic
                  title="Total Net"
                  value={data.totalNet}
                  precision={2}
                  suffix="AZN"
                />
              </Card>
            </Col>
          </Row>

          <Row gutter={16} style={{ marginBottom: 24 }}>
            <Col span={6}>
              <Card>
                <Statistic
                  title="MoM Gross Variance"
                  value={Math.abs(data.momGrossVariancePct)}
                  precision={1}
                  suffix="%"
                  valueStyle={{ color: varianceColor }}
                  prefix={varianceIcon}
                />
              </Card>
            </Col>
            <Col span={6}>
              <Card>
                <Statistic
                  title="Outstanding Loan Balance"
                  value={data.outstandingLoanBalance}
                  precision={2}
                  suffix="AZN"
                />
              </Card>
            </Col>
            <Col span={6}>
              <Card>
                <Statistic
                  title="Pending Advance Requests"
                  value={data.pendingAdvanceCount}
                  valueRender={(v) => (
                    <Button
                      type="link"
                      style={{ padding: 0, fontSize: 24, fontWeight: 600 }}
                      onClick={() => navigate('/payroll/advances')}
                    >
                      {v}
                    </Button>
                  )}
                />
              </Card>
            </Col>
            <Col span={6}>
              <Card>
                <Statistic
                  title="Total Tax"
                  value={data.totalTax}
                  precision={2}
                  suffix="AZN"
                />
              </Card>
            </Col>
          </Row>

          <Card title="Quick Actions" style={{ marginTop: 16 }}>
            <Space>
              {canWrite && (
                <Button
                  type="primary"
                  icon={<RocketOutlined />}
                  onClick={() => navigate('/payroll/runs')}
                >
                  Create Run
                </Button>
              )}
              <Button onClick={() => navigate(`/payroll/runs/${data.currentRun!.id}`)}>
                View Current Run
              </Button>
            </Space>
          </Card>
        </>
      )}
    </Card>
  )
}
