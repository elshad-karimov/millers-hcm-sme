/// Company announcement returned by GET /api/self/announcements (M430).
class Announcement {
  final String id;
  final String title;
  final String body;
  final String? publishFrom;
  final String? publishTo;
  final String? audience;
  final bool active;

  const Announcement({
    required this.id,
    required this.title,
    required this.body,
    this.publishFrom,
    this.publishTo,
    this.audience,
    this.active = true,
  });

  factory Announcement.fromJson(Map<String, dynamic> j) => Announcement(
        id: j['id'] as String,
        title: j['title'] as String? ?? '',
        body: j['body'] as String? ?? '',
        publishFrom: j['publishFrom'] as String?,
        publishTo: j['publishTo'] as String?,
        audience: j['audience'] as String?,
        active: j['active'] as bool? ?? true,
      );
}
