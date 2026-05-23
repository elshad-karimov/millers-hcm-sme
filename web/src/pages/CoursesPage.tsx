import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Col,
  Row,
  Select,
  Space,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import { useNavigate } from 'react-router-dom'
import {
  learningApi,
  type Course,
  type CourseCategory,
  type CourseStatus,
} from '../api/learning'
import { useAuth } from '../auth/AuthContext'

const CATEGORIES: CourseCategory[] = [
  'COMPLIANCE',
  'ONBOARDING',
  'TECHNICAL',
  'LEADERSHIP',
  'SOFT_SKILLS',
  'OTHER',
]

const CATEGORY_COLOR: Record<CourseCategory, string> = {
  COMPLIANCE: 'red',
  ONBOARDING: 'cyan',
  TECHNICAL: 'geekblue',
  LEADERSHIP: 'purple',
  SOFT_SKILLS: 'magenta',
  OTHER: 'default',
}

const STATUS_COLOR: Record<CourseStatus, string> = {
  DRAFT: 'default',
  PUBLISHED: 'green',
  ARCHIVED: 'orange',
}

export function CoursesPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const canManage = hasRole('HR_ADMIN', 'SYSTEM_ADMIN')

  const [rows, setRows] = useState<Course[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [size] = useState(24)
  const [category, setCategory] = useState<CourseCategory | undefined>()
  const [status, setStatus] = useState<CourseStatus | undefined>(canManage ? undefined : 'PUBLISHED')
  const [loading, setLoading] = useState(false)

  const load = () => {
    setLoading(true)
    learningApi
      .courses({ status, category, page, size })
      .then((res) => {
        setRows(res.content)
        setTotal(res.totalElements)
      })
      .catch((err) => message.error(err?.response?.data?.message ?? 'Failed to load courses'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status, category, page])

  return (
    <Card
      title={
        <Typography.Title level={4} style={{ margin: 0 }}>
          Course catalog
        </Typography.Title>
      }
      extra={
        canManage && (
          <Button type="primary" onClick={() => navigate('/learning/courses/new')}>
            New course
          </Button>
        )
      }
      loading={loading}
    >
      <Space style={{ marginBottom: 16 }} wrap>
        <Select
          allowClear
          placeholder="All categories"
          style={{ width: 200 }}
          options={CATEGORIES.map((c) => ({ value: c, label: c.replace(/_/g, ' ') }))}
          value={category}
          onChange={(v) => {
            setCategory(v)
            setPage(0)
          }}
        />
        {canManage && (
          <Select
            allowClear
            placeholder="All statuses"
            style={{ width: 160 }}
            options={(['DRAFT', 'PUBLISHED', 'ARCHIVED'] as CourseStatus[]).map((s) => ({
              value: s,
              label: s,
            }))}
            value={status}
            onChange={(v) => {
              setStatus(v)
              setPage(0)
            }}
          />
        )}
      </Space>
      <Row gutter={[16, 16]}>
        {rows.map((c) => (
          <Col key={c.id} xs={24} sm={12} md={8} xl={6}>
            <Card
              hoverable
              onClick={() => navigate(`/learning/courses/${c.id}`)}
              style={{ height: '100%' }}
              cover={
                c.coverUrl ? (
                  <img
                    src={c.coverUrl}
                    alt={c.title}
                    style={{ height: 120, objectFit: 'cover' }}
                  />
                ) : (
                  <div
                    style={{
                      height: 80,
                      background: 'linear-gradient(135deg,#5B3FE5 0%, #00B8D4 100%)',
                      color: 'white',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontWeight: 600,
                    }}
                  >
                    {c.courseNo}
                  </div>
                )
              }
            >
              <Space direction="vertical" size={4} style={{ width: '100%' }}>
                <Space>
                  <Tag color={CATEGORY_COLOR[c.category]}>{c.category.replace(/_/g, ' ')}</Tag>
                  <Tag color={STATUS_COLOR[c.status]}>{c.status}</Tag>
                  {c.mandatory && <Tag color="red">MANDATORY</Tag>}
                </Space>
                <Typography.Text strong style={{ fontSize: 14 }}>
                  {c.title}
                </Typography.Text>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {c.code} · {c.durationHours} h · pass {c.passingScore}% · {c.maxAttempts} attempts
                </Typography.Text>
                {c.description && (
                  <Typography.Paragraph
                    ellipsis={{ rows: 2 }}
                    style={{ marginBottom: 0, fontSize: 12 }}
                  >
                    {c.description}
                  </Typography.Paragraph>
                )}
              </Space>
            </Card>
          </Col>
        ))}
      </Row>
      {total > size && (
        <div style={{ textAlign: 'center', marginTop: 16 }}>
          <Space>
            <Button disabled={page === 0} onClick={() => setPage(page - 1)}>Prev</Button>
            <span>{page * size + 1}–{Math.min((page + 1) * size, total)} of {total}</span>
            <Button disabled={(page + 1) * size >= total} onClick={() => setPage(page + 1)}>
              Next
            </Button>
          </Space>
        </div>
      )}
    </Card>
  )
}
