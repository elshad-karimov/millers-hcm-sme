import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import '../api/self_api.dart';
import '../models/document_item.dart';
import '../models/employee.dart';
import '../widgets/common.dart';

/// M502 — self-service document upload + list (POST/GET /api/attachments).
class DocumentsScreen extends StatefulWidget {
  const DocumentsScreen({super.key});

  @override
  State<DocumentsScreen> createState() => _DocumentsScreenState();
}

class _DocumentsScreenState extends State<DocumentsScreen> {
  late Future<Employee> _profile;
  String? _employeeId;
  Future<List<DocumentItem>>? _docs;

  @override
  void initState() {
    super.initState();
    _profile = SelfApi.instance.getProfile();
    _profile.then((emp) {
      if (!mounted) return;
      setState(() {
        _employeeId = emp.id;
        _docs = SelfApi.instance.getDocuments(emp.id);
      });
    }).catchError((_) {});
  }

  void _reloadDocs() {
    final id = _employeeId;
    if (id == null) return;
    setState(() => _docs = SelfApi.instance.getDocuments(id));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('My Documents',
            style: TextStyle(fontWeight: FontWeight.bold, color: kBrandColor)),
        actions: [
          IconButton(
              icon: const Icon(Icons.refresh_outlined),
              onPressed: _reloadDocs),
        ],
      ),
      body: FutureBuilder<Employee>(
        future: _profile,
        builder: (context, snap) {
          if (snap.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snap.hasError || _employeeId == null) {
            return ErrorRetry(
              message: 'Failed to load your profile',
              onRetry: () => setState(() {
                _profile = SelfApi.instance.getProfile();
                _profile.then((emp) {
                  if (!mounted) return;
                  setState(() {
                    _employeeId = emp.id;
                    _docs = SelfApi.instance.getDocuments(emp.id);
                  });
                });
              }),
            );
          }
          return RefreshIndicator(
            onRefresh: () async => _reloadDocs(),
            color: kBrandColor,
            child: FutureBuilder<List<DocumentItem>>(
              future: _docs,
              builder: (context, dsnap) {
                if (dsnap.connectionState == ConnectionState.waiting) {
                  return const Center(child: CircularProgressIndicator());
                }
                if (dsnap.hasError) {
                  return ErrorRetry(
                      message: 'Failed to load documents',
                      onRetry: _reloadDocs);
                }
                final docs = dsnap.data ?? const <DocumentItem>[];
                if (docs.isEmpty) {
                  return ListView(children: const [
                    SizedBox(height: 160),
                    EmptyState(
                        icon: Icons.folder_open_outlined,
                        message: 'No documents uploaded yet.'),
                  ]);
                }
                return ListView.builder(
                  padding: const EdgeInsets.all(12),
                  itemCount: docs.length,
                  itemBuilder: (ctx, i) => _DocCard(doc: docs[i]),
                );
              },
            ),
          );
        },
      ),
      floatingActionButton: _employeeId == null
          ? null
          : FloatingActionButton.extended(
              backgroundColor: kBrandColor,
              foregroundColor: Colors.white,
              icon: const Icon(Icons.upload_file),
              label: const Text('Upload'),
              onPressed: () async {
                final uploaded = await showModalBottomSheet<bool>(
                  context: context,
                  isScrollControlled: true,
                  shape: const RoundedRectangleBorder(
                      borderRadius:
                          BorderRadius.vertical(top: Radius.circular(20))),
                  builder: (_) => _UploadSheet(employeeId: _employeeId!),
                );
                if (uploaded == true) _reloadDocs();
              },
            ),
    );
  }
}

Color scanColor(String? s) {
  switch ((s ?? '').toUpperCase()) {
    case 'CLEAN':
      return Colors.green;
    case 'INFECTED':
      return Colors.red;
    case 'PENDING':
      return Colors.orange;
    default:
      return Colors.blueGrey;
  }
}

class _DocCard extends StatelessWidget {
  const _DocCard({required this.doc});
  final DocumentItem doc;

  @override
  Widget build(BuildContext context) {
    return Card(
      elevation: 0,
      color: Colors.grey.shade50,
      margin: const EdgeInsets.only(bottom: 10),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: ListTile(
        contentPadding:
            const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        leading: Container(
          width: 44,
          height: 44,
          decoration: BoxDecoration(
            color: kBrandColor.withValues(alpha: 0.1),
            borderRadius: BorderRadius.circular(10),
          ),
          child: const Icon(Icons.description_outlined,
              color: kBrandColor, size: 22),
        ),
        title: Text(doc.originalFilename ?? doc.attachmentNo ?? 'Document',
            style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
            maxLines: 1,
            overflow: TextOverflow.ellipsis),
        subtitle: Text(
          [shortDate(doc.uploadedAt), doc.sizeLabel]
              .where((s) => s.isNotEmpty)
              .join('  ·  '),
          style: TextStyle(color: Colors.grey.shade500, fontSize: 12),
        ),
        trailing: doc.scanStatus == null
            ? null
            : StatusPill(
                label: doc.scanStatus!, color: scanColor(doc.scanStatus)),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Upload bottom sheet
// ---------------------------------------------------------------------------

class _UploadSheet extends StatefulWidget {
  const _UploadSheet({required this.employeeId});
  final String employeeId;

  @override
  State<_UploadSheet> createState() => _UploadSheetState();
}

class _UploadSheetState extends State<_UploadSheet> {
  static const _docTypes = <String>[
    'ID_CARD',
    'PASSPORT',
    'DIPLOMA',
    'NDA',
    'BANK_LETTER',
    'MEDICAL_CERT',
    'BACKGROUND_CHECK',
    'DRIVING_LICENSE',
    'WORK_PERMIT',
    'OTHER',
  ];

  final _picker = ImagePicker();
  String _docType = 'ID_CARD';
  XFile? _picked;
  bool _uploading = false;
  double _progress = 0;

  String _pretty(String s) => s.replaceAll('_', ' ');

  Future<void> _pick(ImageSource source) async {
    try {
      final file = await _picker.pickImage(source: source, imageQuality: 85);
      if (file != null && mounted) setState(() => _picked = file);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text('Could not open source: $e'),
        backgroundColor: Colors.red.shade700,
      ));
    }
  }

  String _contentType(String name) {
    final lower = name.toLowerCase();
    if (lower.endsWith('.png')) return 'image/png';
    if (lower.endsWith('.gif')) return 'image/gif';
    if (lower.endsWith('.heic')) return 'image/heic';
    if (lower.endsWith('.pdf')) return 'application/pdf';
    return 'image/jpeg';
  }

  Future<void> _upload() async {
    final file = _picked;
    if (file == null) {
      ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Pick a photo or file first')));
      return;
    }
    setState(() {
      _uploading = true;
      _progress = 0;
    });
    try {
      final bytes = await file.readAsBytes();
      // Encode the document type into the stored filename so it is visible
      // in the document list (the attachment API has no separate type field).
      final filename = '${_pretty(_docType)} - ${file.name}';
      await SelfApi.instance.uploadDocument(
        employeeId: widget.employeeId,
        bytes: bytes,
        filename: filename,
        contentType: _contentType(file.name),
        onProgress: (sent, total) {
          if (mounted && total > 0) {
            setState(() => _progress = sent / total);
          }
        },
      );
      if (!mounted) return;
      Navigator.pop(context, true);
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        content: Text('Document uploaded'),
        backgroundColor: Colors.green,
      ));
    } catch (e) {
      if (!mounted) return;
      setState(() => _uploading = false);
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text('Upload failed: $e'),
        backgroundColor: Colors.red.shade700,
      ));
    }
  }

  @override
  Widget build(BuildContext context) {
    final bottom = MediaQuery.of(context).viewInsets.bottom;
    return Padding(
      padding: EdgeInsets.fromLTRB(20, 20, 20, bottom + 20),
      child: Column(
        mainAxisSize: MainAxisSize.min,
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
          const SizedBox(height: 16),
          const Text('Upload Document',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          const SizedBox(height: 16),
          DropdownButtonFormField<String>(
            initialValue: _docType,
            decoration: InputDecoration(
              labelText: 'Document type',
              border:
                  OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
            ),
            items: _docTypes
                .map((t) =>
                    DropdownMenuItem(value: t, child: Text(_pretty(t))))
                .toList(),
            onChanged: (v) => setState(() => _docType = v!),
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  onPressed:
                      _uploading ? null : () => _pick(ImageSource.camera),
                  icon: const Icon(Icons.photo_camera_outlined, size: 18),
                  label: const Text('Camera'),
                  style: OutlinedButton.styleFrom(
                    minimumSize: const Size.fromHeight(48),
                    side: BorderSide(color: Colors.grey.shade300),
                    shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(10)),
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: OutlinedButton.icon(
                  onPressed:
                      _uploading ? null : () => _pick(ImageSource.gallery),
                  icon: const Icon(Icons.photo_library_outlined, size: 18),
                  label: const Text('Gallery'),
                  style: OutlinedButton.styleFrom(
                    minimumSize: const Size.fromHeight(48),
                    side: BorderSide(color: Colors.grey.shade300),
                    shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(10)),
                  ),
                ),
              ),
            ],
          ),
          if (_picked != null) ...[
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: kBrandColor.withValues(alpha: 0.06),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Row(
                children: [
                  const Icon(Icons.attachment, color: kBrandColor, size: 18),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(_picked!.name,
                        maxLines: 1, overflow: TextOverflow.ellipsis),
                  ),
                ],
              ),
            ),
          ],
          if (_uploading) ...[
            const SizedBox(height: 16),
            LinearProgressIndicator(
              value: _progress > 0 && _progress < 1 ? _progress : null,
              color: kBrandColor,
              backgroundColor: kBrandColor.withValues(alpha: 0.15),
            ),
            const SizedBox(height: 6),
            Text('Uploading… ${(_progress * 100).toStringAsFixed(0)}%',
                style: TextStyle(color: Colors.grey.shade600, fontSize: 12)),
          ],
          const SizedBox(height: 20),
          SizedBox(
            width: double.infinity,
            height: 50,
            child: FilledButton.icon(
              onPressed: _uploading ? null : _upload,
              style: FilledButton.styleFrom(
                  backgroundColor: kBrandColor,
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12))),
              icon: const Icon(Icons.cloud_upload_outlined),
              label: const Text('Upload',
                  style:
                      TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
            ),
          ),
        ],
      ),
    );
  }
}
