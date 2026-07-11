import 'package:local_auth/local_auth.dart';

/// Thin wrapper around the `local_auth` plugin.
///
/// Responsibilities:
///   - Check whether the device supports biometrics and has enrolled credentials.
///   - Trigger an authentication prompt and return the result.
///
/// Does NOT store any session state — that lives in [AuthService].
class BiometricService {
  BiometricService._();
  static final BiometricService instance = BiometricService._();

  final _auth = LocalAuthentication();

  /// Returns `true` when the device can support biometrics AND at least one
  /// credential (fingerprint, face, iris) is enrolled.
  Future<bool> isAvailable() async {
    try {
      final canCheck = await _auth.canCheckBiometrics;
      if (!canCheck) return false;
      final enrolled = await _auth.getAvailableBiometrics();
      return enrolled.isNotEmpty;
    } catch (_) {
      return false;
    }
  }

  /// Shows the OS biometric prompt.
  ///
  /// Returns `true` if the user authenticated successfully.
  /// Returns `false` on failure, cancellation, or any error.
  Future<bool> authenticate() async {
    try {
      return await _auth.authenticate(
        localizedReason: 'Authenticate to access Millers HCM',
        options: const AuthenticationOptions(
          biometricOnly: true,
          stickyAuth: true, // keep the dialog open if the app is backgrounded
        ),
      );
    } catch (_) {
      return false;
    }
  }

  /// M509 — authenticate for a sensitive action, allowing the device PIN /
  /// pattern / password to be used when biometrics are unavailable or fail
  /// (`biometricOnly: false`). Falls back to a plain prompt on devices with no
  /// secured lock screen.
  Future<bool> authenticateSensitive(String reason) async {
    try {
      return await _auth.authenticate(
        localizedReason: reason,
        options: const AuthenticationOptions(
          biometricOnly: false, // allow device credential (PIN/pattern/password)
          stickyAuth: true,
        ),
      );
    } catch (_) {
      return false;
    }
  }
}

/// M509 — remembers when the user last re-authenticated to view payslip amounts,
/// so re-auth is required at most once per [_validity] window.
class ReauthGate {
  ReauthGate._();
  static final ReauthGate payslip = ReauthGate._();

  static const _validity = Duration(minutes: 5);
  DateTime? _lastAuth;

  bool get isValid =>
      _lastAuth != null && DateTime.now().difference(_lastAuth!) < _validity;

  void markAuthenticated() => _lastAuth = DateTime.now();

  void invalidate() => _lastAuth = null;
}
