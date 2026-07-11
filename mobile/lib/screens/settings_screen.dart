import 'package:flutter/material.dart';
import '../auth/auth_service.dart';
import '../auth/biometric_service.dart';
import '../config/app_config.dart';
import '../config/settings_service.dart';
import '../widgets/common.dart';

/// M506 — settings: language, biometric login, notification preference,
/// and app / session info.
class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  final _settings = SettingsService.instance;

  bool _biometricAvailable = false;
  bool _biometricEnabled = false;
  bool _notificationsEnabled = true;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final available = await BiometricService.instance.isAvailable();
    final enabled = await AuthService.instance.isBiometricEnabled();
    if (!mounted) return;
    setState(() {
      _biometricAvailable = available;
      _biometricEnabled = enabled;
      _notificationsEnabled = _settings.notificationsEnabled;
      _loading = false;
    });
  }

  Future<void> _setBiometric(bool value) async {
    // Enabling requires a successful biometric check so we know it works.
    if (value) {
      final ok = await BiometricService.instance.authenticate();
      if (!ok) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
            content: Text('Biometric verification was not completed')));
        return;
      }
    }
    await AuthService.instance.setBiometricEnabled(value);
    if (!mounted) return;
    setState(() => _biometricEnabled = value);
  }

  Future<void> _setNotifications(bool value) async {
    await _settings.setNotificationsEnabled(value);
    if (!mounted) return;
    setState(() => _notificationsEnabled = value);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Settings',
            style: TextStyle(fontWeight: FontWeight.bold, color: kBrandColor)),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                _sectionLabel('Language'),
                Card(
                  elevation: 0,
                  color: Colors.grey.shade50,
                  child: Padding(
                    padding: const EdgeInsets.all(12),
                    child: ValueListenableBuilder<Locale>(
                      valueListenable: _settings.locale,
                      builder: (context, locale, _) {
                        return SegmentedButton<String>(
                          segments: const [
                            ButtonSegment(
                                value: 'en',
                                label: Text('English'),
                                icon: Icon(Icons.language)),
                            ButtonSegment(
                                value: 'az',
                                label: Text('Azərbaycan'),
                                icon: Icon(Icons.translate)),
                          ],
                          selected: {locale.languageCode},
                          onSelectionChanged: (s) =>
                              _settings.setLanguage(s.first),
                        );
                      },
                    ),
                  ),
                ),
                const SizedBox(height: 20),
                _sectionLabel('Security'),
                Card(
                  elevation: 0,
                  color: Colors.grey.shade50,
                  child: SwitchListTile(
                    activeThumbColor: kBrandColor,
                    secondary: const Icon(Icons.fingerprint,
                        color: kBrandColor),
                    title: const Text('Biometric login'),
                    subtitle: Text(_biometricAvailable
                        ? 'Use fingerprint / Face ID to sign in'
                        : 'Not available on this device'),
                    value: _biometricEnabled,
                    onChanged:
                        _biometricAvailable ? _setBiometric : null,
                  ),
                ),
                const SizedBox(height: 20),
                _sectionLabel('Notifications'),
                Card(
                  elevation: 0,
                  color: Colors.grey.shade50,
                  child: SwitchListTile(
                    activeThumbColor: kBrandColor,
                    secondary: const Icon(Icons.notifications_active_outlined,
                        color: kBrandColor),
                    title: const Text('Push & in-app notifications'),
                    subtitle:
                        const Text('Receive alerts for approvals and updates'),
                    value: _notificationsEnabled,
                    onChanged: _setNotifications,
                  ),
                ),
                const SizedBox(height: 20),
                _sectionLabel('About'),
                Card(
                  elevation: 0,
                  color: Colors.grey.shade50,
                  child: const Column(
                    children: [
                      ListTile(
                        leading:
                            Icon(Icons.info_outline, color: kBrandColor),
                        title: Text('App version'),
                        subtitle: Text(AppConfig.appVersion),
                      ),
                      Divider(height: 1, indent: 56),
                      ListTile(
                        leading: Icon(Icons.dns_outlined, color: kBrandColor),
                        title: Text('Server'),
                        subtitle: Text(AppConfig.apiBaseUrl),
                      ),
                    ],
                  ),
                ),
              ],
            ),
    );
  }

  Widget _sectionLabel(String text) => Padding(
        padding: const EdgeInsets.only(left: 4, bottom: 8),
        child: Text(text,
            style: TextStyle(
                fontWeight: FontWeight.bold,
                fontSize: 13,
                color: Colors.grey.shade600)),
      );
}
