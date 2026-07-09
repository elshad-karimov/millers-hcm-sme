// M480 — Campaign participation analytics (HR).

import { useState } from 'react'
import {
  Card,
  Col,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { surveysAdminApi, type CampaignResponse } from '../../api/surveys'
import { participationApi, type DepartmentParticipation, type ParticipationAnalytics, type SentimentAnalytics } from '../../api/engagement'

export function ParticipationPage() {
  const { message } = AntdApp.useApp()
  const [campaigns, setCampaigns] = useState<CampaignResponse[]>([])
  const [participation, setParticipation] = useState<ParticipationAnalytics | null>(null)
  const [sentiment, setSentiment] = useState<SentimentAnalytics | null>(null)
  const [loading, setLoading] = useState(false)

  const loadCampaigns = () => {
    surveysAdminApi
      .listCampaigns()
      .then(setCampaigns)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load campaigns'))
  }

  const handleSelect = async (campaignId: string) => {
    setLoading(true)
    try {
      const [part, sent] = await Promise.all([
        participationApi.getParticipation(campaignId),
        participationApi.getSentiment(campaignId),
      ])
      setParticipation(part)
      setSentiment(sent)
    } catch (e: any) {
      message.error(e?.response?.data?.message ?? 'Failed to load analytics')
    } finally {
      setLoading(false)
    }
  }

  const deptColumns: ColumnsType<DepartmentParticipation> = [
    { title: 'Department', dataIndex: 'departmentName' },
    { title: 'Invited', dataIndex: 'invited', width: 100, align: 'right' },
    { title: 'Responded', dataIndex: 'responded', width: 100, align: 'right' },
    {
      title: 'Rate',
      dataIndex: 'rate',
      width: 100,
      align: 'right',
      render: (rate: number, record) =>
        record.suppressed ? (
          <Tag color="orange">Suppressed (&lt;5)</Tag>
        ) : (
          `${(rate * 100).toFixed(1)}%`
        ),
    },
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card title="Campaign Participation Analytics">
        <Space direction="vertical" style={{ width: '100%' }}>
          <Select
            placeholder="Select a campaign"
            style={{ width: '100%' }}
            onFocus={() => {
              if (campaigns.length === 0) loadCampaigns()
            }}
            onChange={handleSelect}
            loading={loading}
          >
            {campaigns.map((c) => (
              <Select.Option key={c.id} value={c.id}>
                {c.name} ({c.status})
              </Select.Option>
            ))}
          </Select>

          {participation && (
            <>
              <Row gutter={16}>
                <Col xs={24} sm={8}>
                  <Card>
                    <Statistic title="Overall Participation" value={participation.overallRate * 100} suffix="%" precision={1} />
                  </Card>
                </Col>
                <Col xs={24} sm={8}>
                  <Card>
                    <Statistic title="Total Invited" value={participation.totalInvited} />
                  </Card>
                </Col>
                <Col xs={24} sm={8}>
                  <Card>
                    <Statistic title="Total Responded" value={participation.totalResponded} />
                  </Card>
                </Col>
              </Row>

              <Card title="By Department">
                <Table rowKey="departmentName" columns={deptColumns} dataSource={participation.byDepartment} pagination={false} />
              </Card>
            </>
          )}

          {sentiment && (
            <Card title="Sentiment">
              <Row gutter={16}>
                <Col xs={24} sm={8}>
                  <Statistic title="Positive" value={sentiment.positive} valueStyle={{ color: 'green' }} />
                </Col>
                <Col xs={24} sm={8}>
                  <Statistic title="Neutral" value={sentiment.neutral} />
                </Col>
                <Col xs={24} sm={8}>
                  <Statistic title="Negative" value={sentiment.negative} valueStyle={{ color: 'red' }} />
                </Col>
              </Row>
            </Card>
          )}
        </Space>
      </Card>
    </Space>
  )
}
