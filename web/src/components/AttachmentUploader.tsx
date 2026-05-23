import { useEffect, useRef, useState } from 'react'
import {
  Alert,
  Button,
  Image,
  List,
  Popconfirm,
  Space,
  Tag,
  Tooltip,
  Typography,
  Upload,
  App as AntdApp,
} from 'antd'
import { DeleteOutlined, DownloadOutlined, FileOutlined, UploadOutlined } from '@ant-design/icons'
import type { UploadRequestOption } from 'rc-upload/lib/interface'
import {
  attachmentsApi,
  type Attachment,
} from '../api/attachments'
import { useAuth } from '../auth/AuthContext'

interface Props {
  ownerModule: string
  ownerEntity: string
  /**
   * Owner id. When null/empty, the uploader renders disabled with a hint —
   * useful on Create forms before the parent entity has been saved.
   */
  ownerId?: string | null
  /** Optional cap on number of files shown; default 50. */
  pageSize?: number
}

function formatBytes(bytes: number | null | undefined): string {
  if (bytes == null) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
}

/** True for content types we know how to render inline as an `<img>`. */
function isPreviewable(contentType?: string | null): boolean {
  if (!contentType) return false
  return (
    contentType.startsWith('image/') &&
    // Excludes things browsers can't render — covers all the realistic
    // mainstream cases (jpeg/png/gif/webp/svg/avif/bmp).
    !contentType.includes('tiff')
  )
}

/**
 * Reusable attachment list + uploader, scoped to (ownerModule, ownerEntity,
 * ownerId). Drops into any detail page. Delete is HR-only — employee role
 * gets no delete button.
 */
export function AttachmentUploader({ ownerModule, ownerEntity, ownerId, pageSize = 50 }: Props) {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canDelete = hasRole('HR_ADMIN', 'HR_SPECIALIST', 'SYSTEM_ADMIN')

  const [items, setItems] = useState<Attachment[]>([])
  const [loading, setLoading] = useState(false)
  const [uploading, setUploading] = useState(false)
  /**
   * M36: per-attachment 256-px JPEG thumbnail blob URLs for the inline
   * 48×48 preview tile. Bandwidth-cheap — a typical 4 MB photo's thumb
   * is ~10 KB, vs. the full image previously fetched. The endpoint
   * falls back to the original blob server-side when no pre-baked thumb
   * exists, so this works uniformly across pre-M36 + post-M36 rows.
   */
  const [thumbUrls, setThumbUrls] = useState<Record<string, string>>({})
  const thumbUrlsRef = useRef(thumbUrls)
  thumbUrlsRef.current = thumbUrls
  /**
   * Lazy-loaded full-image blob URLs for the click-to-zoom lightbox.
   * Populated on demand when the user opens AntD's preview — we don't
   * pay the full-image fetch up-front anymore.
   */
  const [fullUrls, setFullUrls] = useState<Record<string, string>>({})
  const fullUrlsRef = useRef(fullUrls)
  fullUrlsRef.current = fullUrls

  const load = () => {
    if (!ownerId) {
      setItems([])
      return
    }
    setLoading(true)
    attachmentsApi
      .list(ownerModule, ownerEntity, ownerId)
      .then((rows) => setItems(rows.slice(0, pageSize)))
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load attachments'),
      )
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ownerModule, ownerEntity, ownerId])

  // M36: fetch the 256-px thumbnail for every image-typed attachment we
  // don't already have one for. Revoke any stale entries (attachment
  // removed, owner switched) so we don't leak browser memory.
  useEffect(() => {
    let cancelled = false
    const liveIds = new Set(items.filter((a) => isPreviewable(a.contentType)).map((a) => a.id))

    // Cleanup: revoke any cached thumb URL whose attachment is no longer in scope.
    setThumbUrls((prev) => {
      const next: Record<string, string> = {}
      for (const [id, url] of Object.entries(prev)) {
        if (liveIds.has(id)) {
          next[id] = url
        } else {
          URL.revokeObjectURL(url)
        }
      }
      return next
    })
    // Same cleanup for the lazy full-image cache.
    setFullUrls((prev) => {
      const next: Record<string, string> = {}
      for (const [id, url] of Object.entries(prev)) {
        if (liveIds.has(id)) {
          next[id] = url
        } else {
          URL.revokeObjectURL(url)
        }
      }
      return next
    })

    // Fetch any missing thumbs.
    items
      .filter((a) => isPreviewable(a.contentType) && !thumbUrlsRef.current[a.id])
      .forEach((a) => {
        attachmentsApi
          .thumbnailBlob(a.id)
          .then((url) => {
            if (cancelled) {
              URL.revokeObjectURL(url)
              return
            }
            setThumbUrls((prev) => ({ ...prev, [a.id]: url }))
          })
          .catch(() => {
            // Preview is opportunistic — swallow + leave thumbnail empty.
          })
      })

    return () => {
      cancelled = true
    }
  }, [items])

  // Revoke all cached blob URLs on unmount.
  useEffect(() => {
    return () => {
      for (const url of Object.values(thumbUrlsRef.current)) {
        URL.revokeObjectURL(url)
      }
      for (const url of Object.values(fullUrlsRef.current)) {
        URL.revokeObjectURL(url)
      }
    }
  }, [])

  /**
   * Lazily fetch the full-resolution blob URL for the click-to-zoom
   * lightbox. Called once per attachment the first time its lightbox
   * opens. Returns the cached URL on subsequent opens.
   */
  const ensureFullUrl = (id: string) => {
    if (fullUrlsRef.current[id]) return
    attachmentsApi
      .previewBlob(id)
      .then((url) => {
        setFullUrls((prev) => {
          if (prev[id]) {
            // Another caller raced us — drop ours to avoid a leak.
            URL.revokeObjectURL(url)
            return prev
          }
          return { ...prev, [id]: url }
        })
      })
      .catch(() => {
        // Lightbox falls back to the thumb image if this fails.
      })
  }

  const onUpload = async (opts: UploadRequestOption) => {
    if (!ownerId) {
      message.warning('Save the parent record before uploading attachments')
      opts.onError?.(new Error('no ownerId'))
      return
    }
    setUploading(true)
    try {
      const file = opts.file as File
      const saved = await attachmentsApi.upload(ownerModule, ownerEntity, ownerId, file)
      opts.onSuccess?.(saved)
      message.success(`Uploaded ${saved.originalFilename}`)
      load()
    } catch (err) {
      const msg =
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
        'Upload failed'
      opts.onError?.(new Error(msg))
      message.error(msg)
    } finally {
      setUploading(false)
    }
  }

  const onDownload = async (a: Attachment) => {
    try {
      await attachmentsApi.download(a)
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Download failed',
      )
    }
  }

  const onDelete = async (a: Attachment) => {
    try {
      await attachmentsApi.remove(a.id)
      message.success(`Deleted ${a.originalFilename}`)
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Delete failed',
      )
    }
  }

  return (
    <Space direction="vertical" size={6} style={{ width: '100%' }}>
      {!ownerId ? (
        <Alert
          type="info"
          showIcon
          message="Save first, then attach"
          description="Attachments can be uploaded after the record is saved — pick a file once the page reloads."
        />
      ) : (
        <Upload
          customRequest={onUpload}
          showUploadList={false}
          multiple
          disabled={uploading}
        >
          <Button icon={<UploadOutlined />} loading={uploading}>
            Upload file
          </Button>
        </Upload>
      )}
      <List
        size="small"
        loading={loading}
        dataSource={items}
        locale={{ emptyText: 'No attachments yet' }}
        renderItem={(a) => (
          <List.Item
            actions={[
              <Tooltip key="dl" title="Download">
                <Button
                  size="small"
                  type="link"
                  icon={<DownloadOutlined />}
                  onClick={() => onDownload(a)}
                />
              </Tooltip>,
              canDelete ? (
                <Popconfirm
                  key="del"
                  title="Delete attachment?"
                  onConfirm={() => onDelete(a)}
                  okButtonProps={{ danger: true }}
                  okText="Delete"
                >
                  <Button size="small" type="link" danger icon={<DeleteOutlined />} />
                </Popconfirm>
              ) : null,
            ].filter(Boolean)}
          >
            <Space align="start" size={10} style={{ width: '100%' }}>
              {isPreviewable(a.contentType) ? (
                thumbUrls[a.id] ? (
                  // M36: 48×48 tile renders the cheap 256-px thumb;
                  // the click-to-zoom lightbox lazily fetches the
                  // full-resolution blob via `previewBlob` the first
                  // time it opens. While that's in flight the lightbox
                  // shows the thumb upscaled — better UX than blank.
                  <Image
                    src={thumbUrls[a.id]}
                    alt={a.originalFilename ?? 'image'}
                    width={48}
                    height={48}
                    style={{ objectFit: 'cover', borderRadius: 4, border: '1px solid #f0f0f0' }}
                    preview={{
                      src: fullUrls[a.id] ?? thumbUrls[a.id],
                      onVisibleChange: (visible) => {
                        if (visible) ensureFullUrl(a.id)
                      },
                    }}
                  />
                ) : (
                  // Skeleton placeholder while the thumb blob is in flight.
                  <div
                    style={{
                      width: 48, height: 48, borderRadius: 4,
                      background: '#f5f5f5', border: '1px solid #f0f0f0',
                    }}
                  />
                )
              ) : (
                // Non-image: neutral file glyph so the row keeps a stable
                // 48px gutter (otherwise text shifts left/right depending
                // on attachment type, which looks janky in a mixed list).
                <div
                  style={{
                    width: 48, height: 48, borderRadius: 4,
                    background: '#fafafa', border: '1px solid #f0f0f0',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    color: '#bfbfbf', fontSize: 22,
                  }}
                >
                  <FileOutlined />
                </div>
              )}
              <Space direction="vertical" size={0}>
                <Typography.Text>
                  {a.originalFilename ?? '(unnamed)'}{' '}
                  <Tag style={{ marginLeft: 6 }}>{a.attachmentNo}</Tag>
                </Typography.Text>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {formatBytes(a.sizeBytes)} · {a.contentType ?? '—'} · uploaded by {a.uploadedBy} ·{' '}
                  {a.uploadedAt.slice(0, 19).replace('T', ' ')}
                </Typography.Text>
              </Space>
            </Space>
          </List.Item>
        )}
      />
    </Space>
  )
}
