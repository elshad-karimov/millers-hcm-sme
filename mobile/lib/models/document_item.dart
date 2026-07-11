/// An uploaded attachment (M16) — GET /api/attachments, POST /api/attachments.
class DocumentItem {
  final String id;
  final String? attachmentNo;
  final String? originalFilename;
  final String? contentType;
  final int? sizeBytes;
  final String? uploadedAt;
  final String? scanStatus; // PENDING | CLEAN | INFECTED | SKIPPED

  const DocumentItem({
    required this.id,
    this.attachmentNo,
    this.originalFilename,
    this.contentType,
    this.sizeBytes,
    this.uploadedAt,
    this.scanStatus,
  });

  factory DocumentItem.fromJson(Map<String, dynamic> j) => DocumentItem(
        id: j['id'] as String,
        attachmentNo: j['attachmentNo'] as String?,
        originalFilename: j['originalFilename'] as String?,
        contentType: j['contentType'] as String?,
        sizeBytes: (j['sizeBytes'] as num?)?.toInt(),
        uploadedAt: j['uploadedAt'] as String?,
        scanStatus: j['scanStatus'] as String?,
      );

  String get sizeLabel {
    final b = sizeBytes;
    if (b == null) return '';
    if (b < 1024) return '$b B';
    if (b < 1024 * 1024) return '${(b / 1024).toStringAsFixed(0)} KB';
    return '${(b / (1024 * 1024)).toStringAsFixed(1)} MB';
  }
}
