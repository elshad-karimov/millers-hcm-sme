import { useEffect, useMemo, useState } from 'react'
import {
  Button,
  Card,
  DatePicker,
  Empty,
  Popconfirm,
  Space,
  Spin,
  Tag,
  Tree,
  Typography,
  Input,
  Select,
  App as AntdApp,
} from 'antd'
import type { DataNode } from 'antd/es/tree'
import { useNavigate, useSearchParams } from 'react-router-dom'
import dayjs from 'dayjs'
import {
  orgApi,
  type OrgTreeNode,
  type OrgUnitResponse,
  type StructureVersion,
  type VersionStatus,
} from '../api/org'
import { useAuth } from '../auth/AuthContext'
import { WorkflowPanel } from '../components/WorkflowPanel'
import { brand } from '../theme'

const STATUS_COLOR: Record<VersionStatus, string> = {
  DRAFT: 'default',
  PENDING_APPROVAL: 'gold',
  APPROVED: 'cyan',
  ACTIVE: 'green',
  REJECTED: 'red',
  ARCHIVED: 'default',
}

const TYPE_COLOR: Record<string, string> = {
  COMPANY: 'magenta',
  BRANCH: 'volcano',
  DIVISION: 'orange',
  DEPARTMENT: 'blue',
  SECTION: 'geekblue',
  UNIT: 'purple',
  TEAM: 'cyan',
}

export function OrgStructurePage() {
  const { hasRole } = useAuth()
  const { message, modal } = AntdApp.useApp()
  const navigate = useNavigate()
  const canEditDraft = hasRole('HR_ADMIN', 'HR_SPECIALIST')
  const canApprove = hasRole('HR_ADMIN', 'SYSTEM_ADMIN')

  const [searchParams, setSearchParams] = useSearchParams()
  const initialVersionId = searchParams.get('versionId')

  const [versions, setVersions] = useState<StructureVersion[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(initialVersionId)
  const [tree, setTree] = useState<OrgTreeNode | null>(null)
  const [units, setUnits] = useState<OrgUnitResponse[]>([])
  const [loading, setLoading] = useState(false)

  const goToNewUnit = (parentId?: string | null) => {
    if (!selectedId) return
    const p = parentId ? `&parentId=${parentId}` : ''
    navigate(`/organization/units/new?versionId=${selectedId}${p}`)
  }
  const goToEditUnit = (unitId: string) => {
    navigate(`/organization/units/${unitId}/edit`)
  }

  const selected = versions.find((v) => v.id === selectedId)

  const loadVersions = async () => {
    const data = await orgApi.versions()
    setVersions(data)
    if (!selectedId && data.length) {
      setSelectedId(data.find((v) => v.status === 'ACTIVE')?.id ?? data[0].id)
    }
    return data
  }

  useEffect(() => {
    if (selectedId && searchParams.get('versionId') !== selectedId) {
      setSearchParams({ versionId: selectedId }, { replace: true })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId])

  const loadVersionDetail = async (id: string) => {
    setLoading(true)
    try {
      const [t, u] = await Promise.all([orgApi.tree(id), orgApi.units(id)])
      setTree(t)
      setUnits(u)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadVersions().catch((err) =>
      message.error(err?.response?.data?.message ?? 'Failed to load versions'),
    )
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (selectedId) loadVersionDetail(selectedId).catch(() => undefined)
  }, [selectedId])

  const treeData: DataNode[] = useMemo(() => {
    if (!tree) return []
    const toNode = (n: OrgTreeNode): DataNode => ({
      key: n.id,
      title: (
        <Space size="small">
          <Tag color={TYPE_COLOR[n.unitType]}>{n.unitType}</Tag>
          <span style={{ fontWeight: 600 }}>{n.name}</span>
          <Typography.Text type="secondary">{n.code}</Typography.Text>
        </Space>
      ),
      children: n.children.map(toNode),
    })
    return [toNode(tree)]
  }, [tree])

  const createDraft = () => {
    let effectiveDate = dayjs()
    let reason = ''
    modal.confirm({
      title: 'Create draft version',
      content: (
        <Space direction="vertical" style={{ width: '100%' }}>
          <DatePicker
            defaultValue={effectiveDate}
            onChange={(d) => (effectiveDate = d!)}
            style={{ width: '100%' }}
          />
          <Input.TextArea
            placeholder="Change reason (optional)"
            rows={3}
            onChange={(e) => (reason = e.target.value)}
          />
        </Space>
      ),
      onOk: async () => {
        const v = await orgApi.createDraft(effectiveDate.format('YYYY-MM-DD'), reason || undefined)
        message.success(`Draft v${v.versionNumber} created`)
        const all = await loadVersions()
        setSelectedId(all.find((x) => x.id === v.id)?.id ?? v.id)
      },
    })
  }

  const rollback = () => {
    let target: string | undefined
    let effectiveDate = dayjs()
    let reason = ''
    modal.confirm({
      title: 'Rollback to past version',
      content: (
        <Space direction="vertical" style={{ width: '100%' }}>
          <Select
            placeholder="Select source version"
            style={{ width: '100%' }}
            options={versions
              .filter((v) => v.status === 'ARCHIVED' || v.status === 'ACTIVE')
              .map((v) => ({
                value: v.id,
                label: `v${v.versionNumber} — ${v.status} (effective ${v.effectiveDate})`,
              }))}
            onChange={(v) => (target = v)}
          />
          <DatePicker
            defaultValue={effectiveDate}
            onChange={(d) => (effectiveDate = d!)}
            style={{ width: '100%' }}
          />
          <Input.TextArea
            placeholder="Reason"
            rows={3}
            onChange={(e) => (reason = e.target.value)}
          />
        </Space>
      ),
      onOk: async () => {
        if (!target) {
          message.warning('Select a source version')
          return Promise.reject()
        }
        const v = await orgApi.rollback(target, effectiveDate.format('YYYY-MM-DD'), reason)
        message.success(`Created v${v.versionNumber} (Pending Approval) by rolling back`)
        const all = await loadVersions()
        setSelectedId(all.find((x) => x.id === v.id)?.id ?? v.id)
      },
    })
  }

  const transition = async (action: 'submit' | 'approve' | 'reject' | 'activate') => {
    if (!selected) return
    try {
      let result: StructureVersion
      if (action === 'submit') result = await orgApi.submit(selected.id)
      else if (action === 'approve') result = await orgApi.approve(selected.id)
      else if (action === 'reject') result = await orgApi.reject(selected.id)
      else result = await orgApi.activate(selected.id)
      message.success(`v${result.versionNumber} → ${result.status}`)
      await loadVersions()
    } catch (err) {
      message.error((err as any).response?.data?.message ?? 'Transition failed')
    }
  }

  const treeIsEditable = selected?.status === 'DRAFT' && canEditDraft

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card
        title={
          <Typography.Title level={4} style={{ margin: 0 }}>
            Organizational Structure
          </Typography.Title>
        }
        extra={
          canEditDraft && (
            <Space>
              <Button onClick={rollback}>Rollback…</Button>
              <Button type="primary" onClick={createDraft}>
                New draft
              </Button>
            </Space>
          )
        }
      >
        <div style={{ display: 'grid', gridTemplateColumns: '280px 1fr', gap: 16 }}>
          <Card type="inner" title="Versions" size="small">
            {versions.length === 0 ? (
              <Empty description="No versions yet" />
            ) : (
              <Space direction="vertical" style={{ width: '100%' }}>
                {versions.map((v) => {
                  const isSelected = selectedId === v.id
                  return (
                    <div
                      key={v.id}
                      onClick={() => setSelectedId(v.id)}
                      style={{
                        padding: 12,
                        borderRadius: 8,
                        cursor: 'pointer',
                        border: isSelected
                          ? `1px solid ${brand.purple}`
                          : '1px solid rgba(0,0,0,0.08)',
                        background: isSelected ? 'rgba(91,63,229,0.06)' : '#fff',
                      }}
                    >
                      <Space direction="vertical" size={2}>
                        <Space>
                          <strong>v{v.versionNumber}</strong>
                          <Tag color={STATUS_COLOR[v.status]}>{v.status.replace(/_/g, ' ')}</Tag>
                        </Space>
                        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                          effective {v.effectiveDate} · {v.createdBy}
                        </Typography.Text>
                      </Space>
                    </div>
                  )
                })}
              </Space>
            )}
          </Card>

          <Card
            type="inner"
            size="small"
            title={
              selected ? (
                <Space>
                  <strong>v{selected.versionNumber}</strong>
                  <Tag color={STATUS_COLOR[selected.status]}>
                    {selected.status.replace(/_/g, ' ')}
                  </Tag>
                  <Typography.Text type="secondary">
                    effective {selected.effectiveDate}
                  </Typography.Text>
                </Space>
              ) : (
                'Select a version'
              )
            }
            extra={
              selected && (
                <Space wrap>
                  {selected.status === 'DRAFT' && canEditDraft && (
                    <Button onClick={() => transition('submit')}>Submit for approval</Button>
                  )}
                  {selected.status === 'PENDING_APPROVAL' && (
                    <Typography.Text type="secondary">
                      Approve / reject via the workflow below
                    </Typography.Text>
                  )}
                  {selected.status === 'APPROVED' && canApprove && (
                    <Popconfirm
                      title="Activate this version?"
                      description="The current ACTIVE version will be archived."
                      onConfirm={() => transition('activate')}
                    >
                      <Button type="primary">Activate</Button>
                    </Popconfirm>
                  )}
                  {treeIsEditable && (
                    <Button onClick={() => goToNewUnit(null)}>Add root unit</Button>
                  )}
                </Space>
              )
            }
          >
            {loading ? (
              <div style={{ textAlign: 'center', padding: 32 }}>
                <Spin />
              </div>
            ) : !tree ? (
              <Empty description="No units in this version" />
            ) : (
              <Tree
                defaultExpandAll
                treeData={treeData}
                titleRender={(node) => {
                  const id = node.key as string
                  const unit = units.find((u) => u.id === id)!
                  return (
                    <Space>
                      <Tag color={TYPE_COLOR[unit.unitType]}>{unit.unitType}</Tag>
                      <span style={{ fontWeight: 600 }}>{unit.name}</span>
                      <Typography.Text type="secondary">{unit.code}</Typography.Text>
                      {treeIsEditable && (
                        <Space size="small" style={{ marginLeft: 8 }}>
                          <Button
                            size="small"
                            onClick={(e) => {
                              e.stopPropagation()
                              goToNewUnit(unit.id)
                            }}
                          >
                            Add child
                          </Button>
                          <Button
                            size="small"
                            onClick={(e) => {
                              e.stopPropagation()
                              goToEditUnit(unit.id)
                            }}
                          >
                            Edit
                          </Button>
                          <Popconfirm
                            title="Remove this unit?"
                            onConfirm={async (e) => {
                              e?.stopPropagation?.()
                              try {
                                await orgApi.removeUnit(unit.id)
                                message.success('Unit removed')
                                if (selectedId) await loadVersionDetail(selectedId)
                              } catch (err) {
                                message.error(
                                  (err as any).response?.data?.message ?? 'Remove failed',
                                )
                              }
                            }}
                          >
                            <Button size="small" danger onClick={(e) => e.stopPropagation()}>
                              Remove
                            </Button>
                          </Popconfirm>
                        </Space>
                      )}
                    </Space>
                  )
                }}
              />
            )}
          </Card>
        </div>
      </Card>

      {selected && (
        <Card
          title={
            <Typography.Title level={5} style={{ margin: 0 }}>
              Workflow
            </Typography.Title>
          }
        >
          <WorkflowPanel
            module="ORGANIZATION"
            entity="StructureVersion"
            subjectId={selected.id}
            onChanged={() => loadVersions().catch(() => undefined)}
          />
        </Card>
      )}

    </Space>
  )
}
