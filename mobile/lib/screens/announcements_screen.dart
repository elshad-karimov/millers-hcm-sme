import 'package:flutter/material.dart';
import '../api/self_api.dart';
import '../models/announcement.dart';
import '../widgets/common.dart';

/// M499 — company announcements (GET /api/self/announcements).
class AnnouncementsScreen extends StatefulWidget {
  const AnnouncementsScreen({super.key});

  @override
  State<AnnouncementsScreen> createState() => _AnnouncementsScreenState();
}

class _AnnouncementsScreenState extends State<AnnouncementsScreen> {
  late Future<List<Announcement>> _future;

  @override
  void initState() {
    super.initState();
    _future = SelfApi.instance.getAnnouncements();
  }

  void _reload() =>
      setState(() => _future = SelfApi.instance.getAnnouncements());

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Announcements',
            style: TextStyle(fontWeight: FontWeight.bold, color: kBrandColor)),
        actions: [
          IconButton(
              icon: const Icon(Icons.refresh_outlined), onPressed: _reload),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async => _reload(),
        color: kBrandColor,
        child: FutureBuilder<List<Announcement>>(
          future: _future,
          builder: (context, snap) {
            if (snap.connectionState == ConnectionState.waiting) {
              return const Center(child: CircularProgressIndicator());
            }
            if (snap.hasError) {
              return ErrorRetry(
                  message: 'Failed to load announcements', onRetry: _reload);
            }
            final items = snap.data!;
            if (items.isEmpty) {
              return ListView(children: const [
                SizedBox(height: 160),
                EmptyState(
                    icon: Icons.campaign_outlined,
                    message: 'No announcements right now.'),
              ]);
            }
            return ListView.builder(
              padding: const EdgeInsets.all(12),
              itemCount: items.length,
              itemBuilder: (ctx, i) => _AnnouncementCard(item: items[i]),
            );
          },
        ),
      ),
    );
  }
}

class _AnnouncementCard extends StatelessWidget {
  const _AnnouncementCard({required this.item});
  final Announcement item;

  @override
  Widget build(BuildContext context) {
    return Card(
      elevation: 0,
      color: Colors.grey.shade50,
      margin: const EdgeInsets.only(bottom: 10),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: () => showModalBottomSheet(
          context: context,
          isScrollControlled: true,
          shape: const RoundedRectangleBorder(
              borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
          builder: (_) => _AnnouncementSheet(item: item),
        ),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    width: 40,
                    height: 40,
                    decoration: BoxDecoration(
                      color: kBrandColor.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: const Icon(Icons.campaign_rounded,
                        color: kBrandColor, size: 22),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(item.title,
                        style: const TextStyle(
                            fontWeight: FontWeight.bold, fontSize: 15)),
                  ),
                  const Icon(Icons.chevron_right, color: Colors.grey),
                ],
              ),
              const SizedBox(height: 8),
              Text(
                item.body,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(color: Colors.grey.shade600, fontSize: 13),
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Icon(Icons.event_outlined,
                      size: 14, color: Colors.grey.shade400),
                  const SizedBox(width: 4),
                  Text(shortDate(item.publishFrom),
                      style: TextStyle(
                          color: Colors.grey.shade500, fontSize: 12)),
                  const Spacer(),
                  if (item.audience != null)
                    StatusPill(
                        label: item.audience!.replaceAll('_', ' '),
                        color: Colors.indigo),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _AnnouncementSheet extends StatelessWidget {
  const _AnnouncementSheet({required this.item});
  final Announcement item;

  @override
  Widget build(BuildContext context) {
    return DraggableScrollableSheet(
      expand: false,
      initialChildSize: 0.6,
      maxChildSize: 0.9,
      builder: (context, scroll) => SingleChildScrollView(
        controller: scroll,
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 32),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Center(
              child: Container(
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                    color: Colors.grey.shade300,
                    borderRadius: BorderRadius.circular(2)),
              ),
            ),
            const SizedBox(height: 20),
            Text(item.title,
                style: const TextStyle(
                    fontSize: 20, fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            Row(
              children: [
                Icon(Icons.event_outlined,
                    size: 14, color: Colors.grey.shade400),
                const SizedBox(width: 4),
                Text(shortDate(item.publishFrom),
                    style:
                        TextStyle(color: Colors.grey.shade500, fontSize: 12)),
              ],
            ),
            const SizedBox(height: 16),
            Text(item.body,
                style: const TextStyle(fontSize: 15, height: 1.5)),
          ],
        ),
      ),
    );
  }
}
