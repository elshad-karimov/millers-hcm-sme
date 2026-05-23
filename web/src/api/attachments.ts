import { api } from './client'

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
   * Streams the file via axios (so the JWT header is applied) and triggers
   * a browser save-as. Returns once the save dialog has been initiated.
   */
  download: async (a: Attachment) => {
    const res = await api.get(`/attachments/${a.id}/download`, { responseType: 'blob' })
    const url = URL.createObjectURL(res.data as Blob)
    const link = document.createElement('a')
    link.href = url
    link.download = a.originalFilename ?? 'attachment.bin'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  },

  /**
   * Returns an in-memory blob URL for inline preview (img / pdf-embed
   * etc.). Caller is responsible for {@link URL.revokeObjectURL} when
   * the URL is no longer needed — leaking blobs holds the bytes in
   * browser memory.
   *
   * Separate from {@link download} because that one triggers save-as;
   * this one renders inline so we don't want the click flow.
   */
  previewBlob: async (id: string): Promise<string> => {
    const res = await api.get(`/attachments/${id}/download`, { responseType: 'blob' })
    return URL.createObjectURL(res.data as Blob)
  },

  /**
   * M36: blob URL for the pre-baked 256-px JPEG thumbnail. Falls back
   * on the server side to the original blob when no thumb exists
   * (pre-M36 rows, generation failures), so callers don't have to
   * branch on `hasThumbnail`. Cheap enough for inline rendering — a
   * typical 4 MB photo's thumbnail is ~10 KB.
   *
   * Caller is responsible for {@link URL.revokeObjectURL} once the
   * URL is no longer needed.
   */
  thumbnailBlob: async (id: string): Promise<string> => {
    const res = await api.get(`/attachments/${id}/thumbnail`, { responseType: 'blob' })
    return URL.createObjectURL(res.data as Blob)
  },

  remove: (id: string) => api.delete<Attachment>(`/attachments/${id}`).then((r) => r.data),
}
