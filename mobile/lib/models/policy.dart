/// A published policy the employee can browse + acknowledge.
///
/// Backed by GET /api/self/policies which returns a `SelfPolicyView`
/// (M138): a nested `policy` object plus `acknowledged` / `acknowledgedAt`.
class Policy {
  final String id;
  final String code;
  final String title;
  final String? summary;
  final String category;
  final int version;
  final String bodyFormat; // MARKDOWN | HTML | PLAIN_TEXT
  final String? bodyText;
  final String? attachmentUrl;
  final String? effectiveFrom;
  final String? effectiveTo;
  final bool requiresAck;
  final bool acknowledged;
  final String? acknowledgedAt;

  const Policy({
    required this.id,
    required this.code,
    required this.title,
    this.summary,
    required this.category,
    required this.version,
    required this.bodyFormat,
    this.bodyText,
    this.attachmentUrl,
    this.effectiveFrom,
    this.effectiveTo,
    this.requiresAck = false,
    this.acknowledged = false,
    this.acknowledgedAt,
  });

  /// Parses one `SelfPolicyView` row: `{ policy: {...}, acknowledged, acknowledgedAt }`.
  factory Policy.fromSelfView(Map<String, dynamic> j) {
    final p = (j['policy'] as Map<String, dynamic>?) ?? j;
    return Policy(
      id: p['id'] as String,
      code: p['code'] as String? ?? '',
      title: p['title'] as String? ?? '',
      summary: p['summary'] as String?,
      category: p['category'] as String? ?? '',
      version: (p['version'] as num?)?.toInt() ?? 1,
      bodyFormat: p['bodyFormat'] as String? ?? 'PLAIN_TEXT',
      bodyText: p['bodyText'] as String?,
      attachmentUrl: p['attachmentUrl'] as String?,
      effectiveFrom: p['effectiveFrom'] as String?,
      effectiveTo: p['effectiveTo'] as String?,
      requiresAck: p['requiresAck'] as bool? ?? false,
      acknowledged: j['acknowledged'] as bool? ?? false,
      acknowledgedAt: j['acknowledgedAt'] as String?,
    );
  }
}
