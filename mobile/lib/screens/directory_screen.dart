import 'dart:async';

import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../api/self_api.dart';
import '../models/directory.dart';
import '../widgets/common.dart';

/// M504 — Employee directory: debounced search over GET /api/self/directory?q=.
/// Shows only the PUBLIC fields the API returns (no salary/PII).
class DirectoryScreen extends StatefulWidget {
  const DirectoryScreen({super.key});

  @override
  State<DirectoryScreen> createState() => _DirectoryScreenState();
}

class _DirectoryScreenState extends State<DirectoryScreen> {
  final _controller = TextEditingController();
  Timer? _debounce;
  Future<List<DirectoryEntry>>? _future;

  @override
  void initState() {
    super.initState();
    _search(''); // initial listing
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _controller.dispose();
    super.dispose();
  }

  void _onChanged(String q) {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 350), () => _search(q));
  }

  void _search(String q) {
    setState(() => _future = SelfApi.instance.searchDirectory(q.trim()));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Directory',
            style: TextStyle(fontWeight: FontWeight.bold, color: kBrandColor)),
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 12, 12, 4),
            child: TextField(
              controller: _controller,
              onChanged: _onChanged,
              textInputAction: TextInputAction.search,
              decoration: InputDecoration(
                hintText: 'Search name, department, position…',
                prefixIcon: const Icon(Icons.search),
                suffixIcon: _controller.text.isEmpty
                    ? null
                    : IconButton(
                        icon: const Icon(Icons.clear),
                        onPressed: () {
                          _controller.clear();
                          _search('');
                        },
                      ),
                border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12)),
                contentPadding:
                    const EdgeInsets.symmetric(horizontal: 12, vertical: 0),
              ),
            ),
          ),
          Expanded(
            child: FutureBuilder<List<DirectoryEntry>>(
              future: _future,
              builder: (context, snap) {
                if (snap.connectionState == ConnectionState.waiting) {
                  return const Center(child: CircularProgressIndicator());
                }
                if (snap.hasError) {
                  return ErrorRetry(
                      message: 'Failed to load directory',
                      onRetry: () => _search(_controller.text));
                }
                final list = snap.data!;
                if (list.isEmpty) {
                  return const EmptyState(
                      icon: Icons.person_search_outlined,
                      message: 'No colleagues found.');
                }
                return ListView.builder(
                  padding: const EdgeInsets.all(12),
                  itemCount: list.length,
                  itemBuilder: (context, i) => _DirectoryCard(entry: list[i]),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

class _DirectoryCard extends StatelessWidget {
  const _DirectoryCard({required this.entry});
  final DirectoryEntry entry;

  @override
  Widget build(BuildContext context) {
    final subtitle = [
      if (entry.positionTitle != null && entry.positionTitle!.isNotEmpty)
        entry.positionTitle!,
      if (entry.departmentName != null && entry.departmentName!.isNotEmpty)
        entry.departmentName!,
    ].join('  ·  ');

    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      elevation: 0,
      color: Colors.grey.shade50,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: ListTile(
        leading: AttachmentAvatar(
            attachmentId: entry.photoAttachmentId, initials: entry.initials),
        title: Text(entry.fullName,
            style: const TextStyle(fontWeight: FontWeight.w600)),
        subtitle: subtitle.isEmpty ? null : Text(subtitle),
        trailing: const Icon(Icons.chevron_right),
        onTap: () => showModalBottomSheet(
          context: context,
          shape: const RoundedRectangleBorder(
              borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
          builder: (_) => _DirectoryDetailSheet(entry: entry),
        ),
      ),
    );
  }
}

class _DirectoryDetailSheet extends StatelessWidget {
  const _DirectoryDetailSheet({required this.entry});
  final DirectoryEntry entry;

  Future<void> _launch(BuildContext context, Uri uri) async {
    final ok = await canLaunchUrl(uri) && await launchUrl(uri);
    if (!ok && context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Could not open ${uri.scheme}')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                AttachmentAvatar(
                    attachmentId: entry.photoAttachmentId,
                    initials: entry.initials,
                    radius: 30),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(entry.fullName,
                          style: const TextStyle(
                              fontSize: 18, fontWeight: FontWeight.bold)),
                      if (entry.positionTitle != null)
                        Text(entry.positionTitle!,
                            style: TextStyle(color: Colors.grey.shade600)),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            if (entry.departmentName != null)
              _row(Icons.business_outlined, entry.departmentName!),
            if (entry.orgUnitName != null)
              _row(Icons.account_tree_outlined, entry.orgUnitName!),
            if (entry.workEmail != null && entry.workEmail!.isNotEmpty)
              ListTile(
                contentPadding: EdgeInsets.zero,
                leading: const Icon(Icons.email_outlined, color: kBrandColor),
                title: Text(entry.workEmail!),
                trailing: const Icon(Icons.open_in_new, size: 16),
                onTap: () => _launch(
                    context, Uri(scheme: 'mailto', path: entry.workEmail)),
              ),
            if (entry.workPhone != null && entry.workPhone!.isNotEmpty)
              ListTile(
                contentPadding: EdgeInsets.zero,
                leading: const Icon(Icons.phone_outlined, color: kBrandColor),
                title: Text(entry.workPhone!),
                trailing: const Icon(Icons.call, size: 16),
                onTap: () => _launch(
                    context, Uri(scheme: 'tel', path: entry.workPhone)),
              ),
          ],
        ),
      ),
    );
  }

  Widget _row(IconData icon, String text) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 6),
        child: Row(
          children: [
            Icon(icon, size: 18, color: Colors.grey.shade500),
            const SizedBox(width: 10),
            Expanded(child: Text(text)),
          ],
        ),
      );
}
