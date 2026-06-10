import { useEffect, useState } from 'react'
import { Card, Descriptions, Space, Tag } from 'antd'
import { selfApi, type SelfApprovalLimit } from '../api/self'

const LIMIT_LABEL: Record<SelfApprovalLimit['limitType'], string> = {
  PURCHASE_ORDER: 'Purchase orders',
  EXPENSE_REPORT: 'Expense reports',
  CONTRACT: 'Contracts',
  INVOICE: 'Invoices',
  TRAVEL: 'Travel costs',
  GENERAL: 'General',
}

/**
 * M264 — Phase F.7 "💳 Your approval authority" widget.
 *
 * <p>Renders nothing when the employee holds no active approval limits
 * (most employees) so the dashboard stays clean. When at least one
 * limit is active (typically managers + senior staff), shows a
 * Descriptions card listing each type → max amount.
 */
export function ApprovalLimitsWidget() {
  const [rows, setRows] = useState<SelfApprovalLimit[] | null>(null)

  useEffect(() => {
    selfApi
      .approvalLimits()
      .then(setRows)
      .catch(() => setRows([]))
  }, [])

  if (rows == null || rows.length === 0) return null

  const fmt = (v: number, ccy: string) =>
    `${Number(v).toLocaleString(undefined, { maximumFractionDigits: 2 })} ${ccy}`

  return (
    <Card
      size="small"
      title={
        <Space>
          <span>💳 Your approval authority</span>
          <Tag color="blue">{rows.length}</Tag>
        </Space>
      }
      style={{ marginBottom: 16 }}
    >
      <Descriptions size="small" column={1} bordered>
        {rows.map((r) => (
          <Descriptions.Item key={r.id} label={LIMIT_LABEL[r.limitType] ?? r.limitType}>
            <strong>{fmt(r.maxAmount, r.currency)}</strong>
          </Descriptions.Item>
        ))}
      </Descriptions>
    </Card>
  )
}
