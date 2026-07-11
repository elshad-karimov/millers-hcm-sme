/// In-app notification (PRD §17.5, M154) — GET /api/notifications.
class NotificationItem {
  final String id;
  final String title;
  final String? body;
  final String? module;
  final String? entityType;
  final String? entityId;
  final String? readAt;
  final String? createdAt;
  final String? channel;

  const NotificationItem({
    required this.id,
    required this.title,
    this.body,
    this.module,
    this.entityType,
    this.entityId,
    this.readAt,
    this.createdAt,
    this.channel,
  });

  bool get isRead => readAt != null;

  factory NotificationItem.fromJson(Map<String, dynamic> j) => NotificationItem(
        id: j['id'] as String,
        title: j['title'] as String? ?? '',
        body: j['body'] as String?,
        module: j['module'] as String?,
        entityType: j['entityType'] as String?,
        entityId: j['entityId'] as String?,
        readAt: j['readAt'] as String?,
        createdAt: j['createdAt'] as String?,
        channel: j['channel'] as String?,
      );
}
