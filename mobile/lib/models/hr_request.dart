/// HR service request (M429) — GET/POST /api/self/hr-requests.
class HrRequest {
  final String id;
  final String requestNo;
  final String category;
  final String priority;
  final String subject;
  final String? description;
  final String status;
  final String? assignedToUsername;
  final String? slaDue;
  final String? resolvedAt;
  final String? resolutionNotes;
  final String? createdAt;

  const HrRequest({
    required this.id,
    required this.requestNo,
    required this.category,
    required this.priority,
    required this.subject,
    this.description,
    required this.status,
    this.assignedToUsername,
    this.slaDue,
    this.resolvedAt,
    this.resolutionNotes,
    this.createdAt,
  });

  factory HrRequest.fromJson(Map<String, dynamic> j) => HrRequest(
        id: j['id'] as String,
        requestNo: j['requestNo'] as String? ?? '',
        category: j['category'] as String? ?? 'OTHER',
        priority: j['priority'] as String? ?? 'NORMAL',
        subject: j['subject'] as String? ?? '',
        description: j['description'] as String?,
        status: j['status'] as String? ?? 'OPEN',
        assignedToUsername: j['assignedToUsername'] as String?,
        slaDue: j['slaDue'] as String?,
        resolvedAt: j['resolvedAt'] as String?,
        resolutionNotes: j['resolutionNotes'] as String?,
        createdAt: j['createdAt'] as String?,
      );
}

/// A comment on an HR service request (M437).
class HrRequestComment {
  final String id;
  final String authorUsername;
  final String body;
  final bool isInternal;
  final String? createdAt;

  const HrRequestComment({
    required this.id,
    required this.authorUsername,
    required this.body,
    this.isInternal = false,
    this.createdAt,
  });

  factory HrRequestComment.fromJson(Map<String, dynamic> j) => HrRequestComment(
        id: j['id'] as String,
        authorUsername: j['authorUsername'] as String? ?? '',
        body: j['body'] as String? ?? '',
        isInternal: j['isInternal'] as bool? ?? false,
        createdAt: j['createdAt'] as String?,
      );
}
