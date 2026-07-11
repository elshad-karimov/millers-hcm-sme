/// A self-service personal-info change request (M79) —
/// GET /api/self/personal-info, POST /api/self/personal-info/submit.
class PersonalInfoChange {
  final String id;
  final String? requestNo;
  final String fieldKey;
  final String? oldValue;
  final String? newValue;
  final String? reason;
  final String status; // PENDING | APPROVED | REJECTED | APPLIED | CANCELLED
  final String? submittedAt;

  const PersonalInfoChange({
    required this.id,
    this.requestNo,
    required this.fieldKey,
    this.oldValue,
    this.newValue,
    this.reason,
    required this.status,
    this.submittedAt,
  });

  factory PersonalInfoChange.fromJson(Map<String, dynamic> j) =>
      PersonalInfoChange(
        id: j['id'] as String,
        requestNo: j['requestNo'] as String?,
        fieldKey: j['fieldKey'] as String? ?? '',
        oldValue: j['oldValue'] as String?,
        newValue: j['newValue'] as String?,
        reason: j['reason'] as String?,
        status: j['status'] as String? ?? 'PENDING',
        submittedAt: j['submittedAt'] as String?,
      );
}
