import 'package:flutter/material.dart';
import '../api/self_api.dart';
import '../models/employee.dart';
import '../models/personal_info.dart';
import '../widgets/common.dart';

/// M503 — self-service personal-info edit. Each changed field is submitted as
/// a separate change request via POST /api/self/personal-info/submit and goes
/// to HR for approval. Bank details are intentionally NOT editable here.
class ProfileEditScreen extends StatefulWidget {
  const ProfileEditScreen({super.key, required this.employee});
  final Employee employee;

  @override
  State<ProfileEditScreen> createState() => _ProfileEditScreenState();
}

class _ProfileEditScreenState extends State<ProfileEditScreen> {
  late final _phone = TextEditingController(text: widget.employee.phone ?? '');
  late final _email = TextEditingController(text: widget.employee.email ?? '');
  final _addr1 = TextEditingController();
  final _addr2 = TextEditingController();
  final _city = TextEditingController();
  final _district = TextEditingController();
  final _postal = TextEditingController();
  final _country = TextEditingController();
  final _emName = TextEditingController();
  final _emPhone = TextEditingController();
  final _reason = TextEditingController();
  String? _maritalStatus;

  late Future<List<PersonalInfoChange>> _pending;
  bool _submitting = false;

  static const _maritalOptions = <String>[
    'SINGLE',
    'MARRIED',
    'DIVORCED',
    'WIDOWED',
    'CIVIL_PARTNERSHIP',
    'OTHER',
  ];

  @override
  void initState() {
    super.initState();
    _pending = SelfApi.instance.getPersonalInfoChanges();
  }

  @override
  void dispose() {
    for (final c in [
      _phone, _email, _addr1, _addr2, _city, _district,
      _postal, _country, _emName, _emPhone, _reason,
    ]) {
      c.dispose();
    }
    super.dispose();
  }

  /// Collects the fields the user actually changed / filled in.
  Map<String, String> _collectChanges() {
    final changes = <String, String>{};
    void add(String key, String value, {String? original}) {
      final v = value.trim();
      if (v.isEmpty) return;
      if (original != null && v == original.trim()) return; // unchanged
      changes[key] = v;
    }

    add('phone', _phone.text, original: widget.employee.phone);
    add('email', _email.text, original: widget.employee.email);
    add('addressLine1', _addr1.text);
    add('addressLine2', _addr2.text);
    add('city', _city.text);
    add('district', _district.text);
    add('postalCode', _postal.text);
    add('country', _country.text);
    add('emergencyContactName', _emName.text);
    add('emergencyContactPhone', _emPhone.text);
    if (_maritalStatus != null) changes['maritalStatus'] = _maritalStatus!;
    return changes;
  }

  Future<void> _submit() async {
    final changes = _collectChanges();
    if (changes.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('Change at least one field to submit')));
      return;
    }
    setState(() => _submitting = true);
    final reason = _reason.text.trim();
    int ok = 0;
    String? firstError;
    for (final entry in changes.entries) {
      try {
        await SelfApi.instance.submitPersonalInfoChange(
          fieldKey: entry.key,
          newValue: entry.value,
          reason: reason.isEmpty ? null : reason,
        );
        ok++;
      } catch (e) {
        firstError ??= '$e';
      }
    }
    if (!mounted) return;
    setState(() => _submitting = false);
    if (ok > 0) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text('$ok change request(s) submitted for HR approval'),
        backgroundColor: Colors.green,
      ));
      Navigator.pop(context, true);
    } else {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text('Failed: ${firstError ?? 'unknown error'}'),
        backgroundColor: Colors.red.shade700,
      ));
    }
  }

  Color _statusColor(String s) {
    switch (s.toUpperCase()) {
      case 'APPROVED':
      case 'APPLIED':
        return Colors.green;
      case 'REJECTED':
      case 'CANCELLED':
        return Colors.red;
      default:
        return Colors.orange;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Edit Profile',
            style: TextStyle(fontWeight: FontWeight.bold, color: kBrandColor)),
      ),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          // Bank-details notice.
          Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: Colors.amber.withValues(alpha: 0.12),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: Colors.amber.withValues(alpha: 0.4)),
            ),
            child: Row(
              children: [
                Icon(Icons.lock_outline, color: Colors.amber.shade800, size: 20),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    'Bank and salary details cannot be changed on mobile. '
                    'Please contact HR for those updates.',
                    style: TextStyle(
                        color: Colors.amber.shade900, fontSize: 13),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 8),
          // Pending requests.
          FutureBuilder<List<PersonalInfoChange>>(
            future: _pending,
            builder: (context, snap) {
              final pending = (snap.data ?? [])
                  .where((c) => c.status.toUpperCase() == 'PENDING')
                  .toList();
              if (pending.isEmpty) return const SizedBox.shrink();
              return Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const SizedBox(height: 12),
                  const Text('Awaiting HR approval',
                      style: TextStyle(
                          fontWeight: FontWeight.bold, fontSize: 14)),
                  const SizedBox(height: 8),
                  ...pending.map((c) => Card(
                        elevation: 0,
                        color: Colors.grey.shade50,
                        margin: const EdgeInsets.only(bottom: 8),
                        child: ListTile(
                          dense: true,
                          leading: const Icon(Icons.hourglass_top_outlined,
                              color: Colors.orange),
                          title: Text(_prettyField(c.fieldKey),
                              style: const TextStyle(
                                  fontWeight: FontWeight.w600, fontSize: 13)),
                          subtitle: Text('New: ${c.newValue ?? ''}',
                              style: const TextStyle(fontSize: 12)),
                          trailing: StatusPill(
                              label: c.status, color: _statusColor(c.status)),
                        ),
                      )),
                ],
              );
            },
          ),
          const SizedBox(height: 12),
          const Text('Contact',
              style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
          const SizedBox(height: 8),
          _field(_phone, 'Phone', icon: Icons.phone_outlined),
          _field(_email, 'Email', icon: Icons.email_outlined),
          const SizedBox(height: 16),
          const Text('Home address',
              style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
          const SizedBox(height: 8),
          _field(_addr1, 'Address line 1'),
          _field(_addr2, 'Address line 2'),
          _field(_city, 'City'),
          _field(_district, 'District'),
          _field(_postal, 'Postal code'),
          _field(_country, 'Country (ISO code, e.g. AZ)'),
          const SizedBox(height: 16),
          const Text('Personal',
              style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
          const SizedBox(height: 8),
          DropdownButtonFormField<String>(
            initialValue: _maritalStatus,
            decoration: InputDecoration(
              labelText: 'Marital status (leave to keep current)',
              border:
                  OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
            ),
            items: _maritalOptions
                .map((m) => DropdownMenuItem(
                    value: m, child: Text(_prettyField(m))))
                .toList(),
            onChanged: (v) => setState(() => _maritalStatus = v),
          ),
          const SizedBox(height: 16),
          const Text('Emergency contact',
              style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
          const SizedBox(height: 8),
          _field(_emName, 'Contact name', icon: Icons.contact_emergency_outlined),
          _field(_emPhone, 'Contact phone', icon: Icons.phone_in_talk_outlined),
          const SizedBox(height: 16),
          _field(_reason, 'Reason for change (optional)', maxLines: 2),
          const SizedBox(height: 24),
          SizedBox(
            width: double.infinity,
            height: 52,
            child: FilledButton.icon(
              onPressed: _submitting ? null : _submit,
              style: FilledButton.styleFrom(
                  backgroundColor: kBrandColor,
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12))),
              icon: _submitting
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(
                          strokeWidth: 2.5, color: Colors.white))
                  : const Icon(Icons.send_outlined),
              label: Text(_submitting ? 'Submitting…' : 'Submit for approval',
                  style: const TextStyle(
                      fontSize: 16, fontWeight: FontWeight.w600)),
            ),
          ),
          const SizedBox(height: 8),
          Text(
            'Each changed field becomes a separate request that HR must approve '
            'before it updates your record.',
            style: TextStyle(color: Colors.grey.shade500, fontSize: 12),
          ),
        ],
      ),
    );
  }

  Widget _field(TextEditingController c, String label,
      {IconData? icon, int maxLines = 1}) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: TextField(
        controller: c,
        maxLines: maxLines,
        decoration: InputDecoration(
          labelText: label,
          prefixIcon: icon == null ? null : Icon(icon),
          border:
              OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
        ),
      ),
    );
  }

  String _prettyField(String key) {
    const map = {
      'phone': 'Phone',
      'email': 'Email',
      'addressLine1': 'Address line 1',
      'addressLine2': 'Address line 2',
      'city': 'City',
      'district': 'District',
      'postalCode': 'Postal code',
      'country': 'Country',
      'maritalStatus': 'Marital status',
      'emergencyContactName': 'Emergency contact name',
      'emergencyContactPhone': 'Emergency contact phone',
    };
    if (map.containsKey(key)) return map[key]!;
    // Enum-style values (SINGLE, CIVIL_PARTNERSHIP…).
    return key
        .split('_')
        .map((w) => w.isEmpty
            ? w
            : '${w[0].toUpperCase()}${w.substring(1).toLowerCase()}')
        .join(' ');
  }
}
