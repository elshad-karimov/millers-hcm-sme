import { api } from './client'

export type ScanStatus = 'PENDING' | 'CLEAN' | 'INFECTED' | 'SKIPPED' | 'ERROR'

export interface Attachment {
  id: string
  attachmentNo: string
  bucket: string
  objectKey: string
  /** M36: true iff a pre-baked 256-px JPEG thumbnail exists in MinIO. */
  hasThumbnail?: boolean
  originalFilename?: string | null
  contentType?: string | null
  sizeBytes?: number | null
  ownerModule: string
  ownerEntity: string
  ownerId: string
  uploadedBy: string
  uploadedAt: string
  /** M50 (PRD 14.8): ClamAV scan outcome. CLEAN for user uploads that pass; SKIPPED for server-generated exports. */
  scanStatus?: ScanStatus | null
}

export interface PresignedUrlResponse {
  url: string
  filename: string | null
  contentType: string | null
  expiresAt: string
  expiresInSeconds: number
}

export const attachmentsApi = {
  list: (ownerModule: string, ownerEntity: string, ownerId: string) =>
    api
      .get<Attachment[]>('/attachments', { params: { ownerModule, ownerEntity, ownerId } })
      .then((r) => r.data),

  upload: (ownerModule: string, ownerEntity: string, ownerId: string, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return api
      .post<Attachment>('/attachments', form, {
        params: { ownerModule, ownerEntity, ownerId },
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((r) => r.data)
  },

  /**
   * M50 (PRD 14.8): fetches a MinIO presigned GET URL (valid 15 min by default).
   * The caller authenticates once with a Bearer JWT; the returned URL lets the
   * browser download the file directly from MinIO without routing bytes through
   * the backend — satisfying the PRD "signed, time-limited URL" requirement.
   */
  presignedUrl: (id: string): Promise<PresignedUrlResponse> =>
    api.get<PresignedUrlResponse>(`/attachments/${id}/url`).then((r) => r.data),

  /**
   * Triggers a browser save-as dialog for the attachment.
   * Uses a MinIO presigned URL (M50) so the download goes direct to object
   * storage rather than streaming through the backend.
   */
  download: async (a: Attachment) => {
    const { url, filename } = await attachmentsApi.presignedUrl(a.id)
    const link = document.createElement('a')
    link.href = url
    link.download = filename ?? a.originalFilename ?? 'attachment.bin'
    link.target = '_blank'
    link.rel = 'noopener noreferrer'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
  },

  /**
   * Returns an in-memory blob URL for inline preview (img / pdf-embed etc.).
   * Still streams via the backend (JWT-authenticated) because cross-origin
   * fetch from MinIO presigned URLs requires CORS to be enabled on the bucket.
   * Caller is responsible for {@link URL.revokeObjectURL} when no longer needed.
   */
  previewBlob: async (id: string): Promise<string> => {
    const res = await api.get(`/attachments/${id}/download`, { responseType: 'blob' })
    return URL.createObjectURL(res.data as Blob)
  },

  /**
   * M36: blob URL for the pre-baked 256-px JPEG thumbnail. Falls back
   * on the server side to the original blob when no thumb exists.
   * Caller is responsible for {@link URL.revokeObjectURL} when no longer needed.
   */
  thumbnailBlob: async (id: string): Promise<string> => {
    const res = await api.get(`/attachments/${id}/thumbnail`, { responseType: 'blob' })
    return URL.createObjectURL(res.data as Blob)
  },

  remove: (id: string) => api.delete<Attachment>(`/attachments/${id}`).then((r) => r.data),
}
