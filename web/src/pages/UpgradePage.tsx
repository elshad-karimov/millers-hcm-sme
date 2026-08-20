// SME editions — what the tenant's plan includes, and what a higher plan adds.

import { useMemo } from 'react'
import { Alert, Card, Empty, List, Space, Tag, Typography } from 'antd'
import { LockOutlined, CheckCircleOutlined } from '@ant-design/icons'
import { useSearchParams } from 'react-router-dom'
import { CATEGORIES } from '../nav/modules'
import { useEnabledModules, type PlanName } from '../nav/moduleSettings'

const { Title, Text, Paragraph } = Typography

const PLAN_ORDER: PlanName[] = ['LITE', 'STANDARD', 'ENTERPRISE']

const labelOf = (key: string) => CATEGORIES.find((c) => c.key === key)?.label ?? key

/**
 * Landing page for a module the tenant's plan doesn't include.
 *
 * Reached from the plan badge, from the locked rows in Tenant Settings, and
 * from a deep link into an out-of-plan module (AppLayout redirects here with
 * ?module=<key> so the page can lead with the one they actually wanted).
 */
export function UpgradePage() {
  const [params] = useSearchParams()
  const requested = params.get('module') ?? undefined
  const { loaded, plan, notInPlan, disabledByTenant, upgrades } = useEnabledModules()

  /** Out-of-plan modules grouped by the plan that unlocks them. */
  const byPlan = useMemo(() => {
    const groups = new Map<PlanName, string[]>()
    for (const key of notInPlan) {
      const target = upgrades.get(key) ?? 'ENTERPRISE'
      const bucket = groups.get(target) ?? []
      bucket.push(key)
      groups.set(target, bucket)
    }
    for (const bucket of groups.values()) {
      bucket.sort((a, b) => labelOf(a).localeCompare(labelOf(b)))
    }
    return PLAN_ORDER.filter((p) => groups.has(p)).map((p) => ({
      plan: p,
      modules: groups.get(p) ?? [],
    }))
  }, [notInPlan, upgrades])

  const requestedPlan = requested ? upgrades.get(requested) : undefined

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <div>
        <Title level={3} style={{ marginBottom: 4 }}>
          Your plan: <Tag color="green">{plan}</Tag>
        </Title>
        <Text type="secondary">
          Modules outside your plan stay switched off for everyone in your organisation.
        </Text>
      </div>

      {requested && requestedPlan && (
        <Alert
          type="info"
          showIcon
          icon={<LockOutlined />}
          message={`${labelOf(requested)} is not included in your ${plan} plan`}
          description={`It becomes available on the ${requestedPlan} plan. Contact your account manager to upgrade — your existing data is untouched either way.`}
        />
      )}

      {requested && !requestedPlan && disabledByTenant.has(requested) && (
        <Alert
          type="warning"
          showIcon
          message={`${labelOf(requested)} has been switched off for your organisation`}
          description="This one is in your plan — an HR admin can switch it back on under Tenant Settings → Modules."
        />
      )}

      {loaded && byPlan.length === 0 ? (
        <Card>
          <Empty
            image={<CheckCircleOutlined style={{ fontSize: 40, color: '#52c41a' }} />}
            description="Every module is included in your plan."
          />
        </Card>
      ) : (
        byPlan.map((group) => (
          <Card
            key={group.plan}
            title={
              <Space>
                <LockOutlined />
                <span>Available on {group.plan}</span>
                <Tag>{group.modules.length}</Tag>
              </Space>
            }
          >
            <List
              dataSource={group.modules}
              renderItem={(key) => (
                <List.Item key={key}>
                  <List.Item.Meta
                    title={labelOf(key)}
                    description={<Text type="secondary">{key}</Text>}
                  />
                </List.Item>
              )}
            />
          </Card>
        ))
      )}

      <Paragraph type="secondary" style={{ marginBottom: 0 }}>
        Upgrading is non-destructive and reversible: a plan change only opens or closes
        access, it never deletes data.
      </Paragraph>
    </Space>
  )
}
