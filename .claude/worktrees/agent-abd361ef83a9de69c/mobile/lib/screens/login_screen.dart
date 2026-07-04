import 'package:flutter/material.dart';
import '../auth/auth_service.dart';
import '../auth/biometric_service.dart';

const Color brandColor = Color(0xFF5B3FE5);

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  bool _loading = false;
  bool _biometricLoading = false;

  /// Whether to show the biometric button:
  ///   - device supports biometrics AND has enrolled credentials
  ///   - user previously opted in
  bool _showBiometric = false;

  @override
  void initState() {
    super.initState();
    _checkBiometric();
  }

  Future<void> _checkBiometric() async {
    final available = await BiometricService.instance.isAvailable();
    final enabled = await AuthService.instance.isBiometricEnabled();
    if (mounted) {
      setState(() => _showBiometric = available && enabled);
    }
  }

  // ------ Keycloak full login -----------------------------------------------

  Future<void> _handleLogin() async {
    setState(() => _loading = true);
    try {
      await AuthService.instance.login();
      if (!mounted) return;

      // After a successful Keycloak login, offer biometric enrolment if the
      // device supports it but the user hasn't opted in yet.
      await _offerBiometricEnrolment();

      if (!mounted) return;
      Navigator.of(context).pushReplacementNamed('/home');
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Sign-in failed: $e'),
          backgroundColor: Colors.red.shade700,
        ),
      );
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  /// Shows a bottom sheet asking the user whether to enable biometric login.
  /// Only shown when the device supports biometrics and they haven't opted in.
  Future<void> _offerBiometricEnrolment() async {
    final available = await BiometricService.instance.isAvailable();
    final alreadyEnabled = await AuthService.instance.isBiometricEnabled();
    if (!available || alreadyEnabled || !mounted) return;

    final confirm = await showModalBottomSheet<bool>(
      context: context,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) => Padding(
        padding: const EdgeInsets.fromLTRB(24, 20, 24, 32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: Colors.grey.shade300,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            const SizedBox(height: 20),
            const Icon(Icons.fingerprint_rounded, size: 48, color: brandColor),
            const SizedBox(height: 12),
            const Text(
              'Enable Biometric Login',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8),
            Text(
              'Use your fingerprint or Face ID to sign in quickly next time.',
              textAlign: TextAlign.center,
              style: TextStyle(color: Colors.grey.shade600, fontSize: 14),
            ),
            const SizedBox(height: 24),
            SizedBox(
              width: double.infinity,
              height: 48,
              child: ElevatedButton(
                onPressed: () => Navigator.of(ctx).pop(true),
                style: ElevatedButton.styleFrom(
                  backgroundColor: brandColor,
                  foregroundColor: Colors.white,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                ),
                child: const Text('Enable', style: TextStyle(fontSize: 16)),
              ),
            ),
            const SizedBox(height: 10),
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(false),
              child: Text(
                'Not now',
                style: TextStyle(color: Colors.grey.shade500),
              ),
            ),
          ],
        ),
      ),
    );

    if (confirm == true) {
      await AuthService.instance.setBiometricEnabled(true);
    }
  }

  // ------ Biometric quick login ---------------------------------------------

  Future<void> _handleBiometricLogin() async {
    setState(() => _biometricLoading = true);
    try {
      final ok = await AuthService.instance.loginWithBiometric();
      if (!mounted) return;
      if (ok) {
        Navigator.of(context).pushReplacementNamed('/home');
      } else {
        // Biometric auth failed or tokens expired — let the user do full login.
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: const Text(
                'Biometric authentication failed. Please sign in with Keycloak.'),
            backgroundColor: Colors.orange.shade700,
          ),
        );
        // Disable the biometric button so the user doesn't keep retrying.
        await AuthService.instance.setBiometricEnabled(false);
        if (mounted) setState(() => _showBiometric = false);
      }
    } finally {
      if (mounted) setState(() => _biometricLoading = false);
    }
  }

  // ------ UI ----------------------------------------------------------------

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 40, vertical: 32),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                // Logo mark — purple rounded rectangle with "M"
                Container(
                  width: 80,
                  height: 80,
                  decoration: BoxDecoration(
                    color: brandColor,
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: const Center(
                    child: Text(
                      'M',
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 44,
                        fontWeight: FontWeight.w900,
                        height: 1,
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 24),
                // App name
                const Text(
                  'Millers HCM',
                  style: TextStyle(
                    fontSize: 28,
                    fontWeight: FontWeight.bold,
                    color: brandColor,
                    letterSpacing: 0.2,
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  'Human Capital Management',
                  style: TextStyle(
                    fontSize: 14,
                    color: Colors.grey.shade600,
                    letterSpacing: 0.1,
                  ),
                ),
                const SizedBox(height: 56),

                // ── Biometric button (shown only when enrolled + opted-in) ──
                if (_showBiometric) ...[
                  SizedBox(
                    width: double.infinity,
                    height: 52,
                    child: ElevatedButton.icon(
                      onPressed:
                          _biometricLoading ? null : _handleBiometricLogin,
                      icon: _biometricLoading
                          ? const SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(
                                strokeWidth: 2.5,
                                color: Colors.white,
                              ),
                            )
                          : const Icon(Icons.fingerprint_rounded, size: 24),
                      label: Text(
                        _biometricLoading
                            ? 'Authenticating…'
                            : 'Sign in with Biometrics',
                        style: const TextStyle(
                            fontSize: 16, fontWeight: FontWeight.w600),
                      ),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFF2D2D2D),
                        foregroundColor: Colors.white,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                        elevation: 2,
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                ],

                // ── Full Keycloak login ──
                SizedBox(
                  width: double.infinity,
                  height: 52,
                  child: ElevatedButton.icon(
                    onPressed: _loading ? null : _handleLogin,
                    icon: _loading
                        ? const SizedBox(
                            width: 20,
                            height: 20,
                            child: CircularProgressIndicator(
                              strokeWidth: 2.5,
                              color: Colors.white,
                            ),
                          )
                        : const Icon(Icons.lock_outline_rounded),
                    label: Text(
                      _loading ? 'Signing in…' : 'Sign in with Keycloak',
                      style: const TextStyle(
                          fontSize: 16, fontWeight: FontWeight.w600),
                    ),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: brandColor,
                      foregroundColor: Colors.white,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                      elevation: 2,
                    ),
                  ),
                ),

                const SizedBox(height: 32),
                Text(
                  'Secure enterprise access via Keycloak OIDC',
                  style: TextStyle(
                    fontSize: 12,
                    color: Colors.grey.shade400,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
