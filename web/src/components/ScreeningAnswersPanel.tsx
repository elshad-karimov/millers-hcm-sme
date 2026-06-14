import { useEffect, useState } from 'react'
import { Empty, Table, Tag } from 'antd'
import { recruitmentApi, type ScreeningAnswer } from '../api/recruitment'

/**
 * M289 — Recruitment PRD §15: the candidate's stored screening answers
 * and their pass/fail outcome (recruiter view). A failed knockout
 * answer is what auto-rejected the application at apply time.
 */
export function ScreeningAnswersPanel({ applicationId }: { applicationId: string }) {
  const [rows, setRows] = useState<ScreeningAnswer[]>([])

  useEffect(() => {
    recruitmentApi
      .screeningAnswers(applicationId)
      .then(setRows)
      .catch(() => setRows([]))
  }, [applicationId])

  return (
    <Table<ScreeningAnswer>
      rowKey="questionId"
      size="small"
      dataSource={rows}
      pagination={false}
      locale={{
        emptyText: <Empty description="No screening answers — applied without a knockout filter" />,
      }}
      columns={[
        { title: 'Question', dataIndex: 'questionText', ellipsis: true },
        {
          title: 'Answer',
          dataIndex: 'answerText',
          width: 160,
          render: (a?: string | null) => (a && a.trim() ? a : <span style={{ color: '#bbb' }}>—</span>),
        },
        {
          title: 'Knockout',
          dataIndex: 'knockout',
          width: 100,
          render: (b: boolean) => (b ? <Tag color="volcano">Knockout</Tag> : <Tag>Screening</Tag>),
        },
        {
          title: 'Outcome',
          dataIndex: 'passed',
          width: 100,
          render: (passed: boolean, r: ScreeningAnswer) =>
            passed ? (
              <Tag color="green">Pass</Tag>
            ) : (
              <Tag color={r.knockout ? 'red' : 'orange'}>Fail</Tag>
            ),
        },
      ]}
    />
  )
}
