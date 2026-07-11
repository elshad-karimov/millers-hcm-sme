/// M504 — Employee directory entry (PUBLIC fields only, no salary/PII).
/// GET /api/self/directory?q=
class DirectoryEntry {
  final String id;
  final String fullName;
  final String? departmentName;
  final String? orgUnitName;
  final String? positionTitle;
  final String? workEmail;
  final String? workPhone;
  final String? photoAttachmentId;

  const DirectoryEntry({
    required this.id,
    required this.fullName,
    this.departmentName,
    this.orgUnitName,
    this.positionTitle,
    this.workEmail,
    this.workPhone,
    this.photoAttachmentId,
  });

  factory DirectoryEntry.fromJson(Map<String, dynamic> j) => DirectoryEntry(
        id: j['id']?.toString() ?? '',
        fullName: j['fullName'] as String? ?? '',
        departmentName: j['departmentName'] as String?,
        orgUnitName: j['orgUnitName'] as String?,
        positionTitle: j['positionTitle'] as String?,
        workEmail: j['workEmail'] as String?,
        workPhone: j['workPhone'] as String?,
        photoAttachmentId: j['photoAttachmentId']?.toString(),
      );

  String get initials {
    final parts = fullName.trim().split(RegExp(r'\s+'));
    if (parts.isEmpty || parts.first.isEmpty) return '?';
    if (parts.length == 1) return parts.first[0].toUpperCase();
    return (parts.first[0] + parts.last[0]).toUpperCase();
  }
}
