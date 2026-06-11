import { useEffect, useState } from 'react'
import { App as AntdApp, Button, Card, List, Space, Tag, Typography } from 'antd'
import { selfApi, type InternalJob } from '../api/self'

const { Text } = Typography

/**
 * M281 — Recruitment PRD §10: "Internal opportunities" widget.
 *
 * <p>Shows live INTERNAL-channel job postings with a one-click Apply —
 * the candidate profile is derived server-side from the employee
 * record, so there's no form. Renders nothing when no internal
 * postings are live, keeping the dashboard clean.
 */
export function InternalJobsWidget() {
  const { message } = AntdApp.useApp()
  const [jobs, setJobs] = useState<InternalJob[] | null>(null)
  const [applying, setApplying] = useState<Record<string, boolean>>({})

  useEffect(() => {
    selfApi
      .internalJobs()
      .then(setJobs)
      .catch(() => setJobs([]))
  }, [])

  if (jobs == null || jobs.length === 0) return null

  const apply = async (postingId: string) => {
    setApplying((m) => ({ ...m, [postingId]: true }))
    try {
      const res = await selfApi.applyInternalJob(postingId)
      message.success(res.message)
      setJobs((cur) =>
        (cur ?? []).map((j) => (j.postingId === postingId ? { ...j, alreadyApplied: true } : j)),
      )
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Application failed',
      )
    } finally {
      setApplying((m) => {
        const { [postingId]: _, ...rest } = m
        return rest
      })
    }
  }

  return (
    <Card size="small" title="💼 Internal opportunities">
      <List<InternalJob>
        size="small"
        dataSource={jobs}
        renderItem={(j) => (
          <List.Item
            actions={[
              j.alreadyApplied ? (
                <Tag key="applied" color="green">
                  Applied
                </Tag>
              ) : (
                <Button
                  key="apply"
                  size="small"
                  type="primary"
                  loading={applying[j.postingId]}
                  onClick={() => apply(j.postingId)}
                >
                  Apply
                </Button>
              ),
            ]}
          >
            <List.Item.Meta
              title={
                <Space wrap>
                  {j.title}
                  {j.department && <Tag>{j.department}</Tag>}
                  {j.location && <Tag>{j.location}</Tag>}
                </Space>
              }
              description={
                <Space wrap>
                  {j.salaryMin != null && j.salaryMax != null && (
                    <Text type="secondary">
                      {j.salaryMin}–{j.salaryMax} {j.currency ?? ''}
                    </Text>
                  )}
                  {j.applicationDeadline && (
                    <Text type="secondary">Apply by {j.applicationDeadline}</Text>
                  )}
                </Space>
              }
            />
          </List.Item>
        )}
      />
    </Card>
  )
}
