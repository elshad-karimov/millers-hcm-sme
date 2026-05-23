import { Button, Card, Space, Typography } from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import type { ReactNode } from 'react'

interface Props {
  title: string
  backTo: string
  /** Optional element shown right-aligned in the card header. */
  extra?: ReactNode
  children: ReactNode
}

/**
 * Wraps a New/Edit form page with a consistent header: a "Back to list"
 * button on the left and the page title alongside it.
 */
export function FormPageShell({ title, backTo, extra, children }: Props) {
  const navigate = useNavigate()
  return (
    <Card
      title={
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(backTo)}>
            Back to list
          </Button>
          <Typography.Title level={4} style={{ margin: 0 }}>
            {title}
          </Typography.Title>
        </Space>
      }
      extra={extra}
    >
      {children}
    </Card>
  )
}
