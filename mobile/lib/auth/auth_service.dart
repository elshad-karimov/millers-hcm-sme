import 'package:flutter_appauth/flutter_appauth.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../config/app_config.dart';

/// Handles Keycloak OIDC login / logout / token refresh via
/// Authorization Code + PKCE (matching the web app's keycloak-js flow).
class AuthService {
  AuthService._();
  static final AuthService instance = AuthService._();

  final _appAuth = const FlutterAppAuth();
  final _storage = const FlutterSecureStorage();

  static const _keyAccessToken = 'hcm_access_token';
  static const _keyRefreshToken = 'hcm_refresh_token';
  static const _keyIdToken = 'hcm_id_token';

  String? _accessToken;
  String? _refreshToken;

  // --- Public API -----------------------------------------------------------

  /// Returns a valid access token, refreshing silently if needed.
  Future<String?> getAccessToken() async {
    if (_accessToken != null) return _accessToken;
    await _loadFromStorage();
    if (_refreshToken != null) {
      await _refresh();
    }
    return _accessToken;
  }

  Future<bool> get isLoggedIn async => (await getAccessToken()) != null;

  /// Launches the Keycloak login page (Authorization Code + PKCE).
  Future<void> login() async {
    final result = await _appAuth.authorizeAndExchangeCode(
      AuthorizationTokenRequest(
        AppConfig.oidcClientId,
        AppConfig.oidcRedirectUrl,
        issuer: AppConfig.oidcIssuer,
        scopes: ['openid', 'profile', 'email', 'offline_access'],
        promptValues: ['login'],
      ),
    );
    await _save(result.accessToken, result.refreshToken, result.idToken);
  }

  /// Ends the Keycloak session and clears local tokens.
  Future<void> logout() async {
    final idToken = await _storage.read(key: _keyIdToken);
    try {
      await _appAuth.endSession(
        EndSessionRequest(
          idTokenHint: idToken,
          postLogoutRedirectUrl: AppConfig.oidcEndSessionRedirectUrl,
          issuer: AppConfig.oidcIssuer,
        ),
      );
    } catch (_) {
      // Swallow — even if the OIDC end-session fails, clear local tokens.
    }
    await _clear();
  }

  // --- Internal helpers -----------------------------------------------------

  Future<void> _loadFromStorage() async {
    _accessToken = await _storage.read(key: _keyAccessToken);
    _refreshToken = await _storage.read(key: _keyRefreshToken);
  }

  Future<void> _refresh() async {
    try {
      final result = await _appAuth.token(
        TokenRequest(
          AppConfig.oidcClientId,
          AppConfig.oidcRedirectUrl,
          issuer: AppConfig.oidcIssuer,
          refreshToken: _refreshToken,
          grantType: GrantType.refreshToken,
          scopes: ['openid', 'profile', 'email', 'offline_access'],
        ),
      );
      await _save(result?.accessToken, result?.refreshToken, result?.idToken);
      if (result == null) await _clear();
    } catch (_) {
      await _clear();
    }
  }

  Future<void> _save(
      String? access, String? refresh, String? id) async {
    _accessToken = access;
    _refreshToken = refresh;
    if (access != null) {
      await _storage.write(key: _keyAccessToken, value: access);
    }
    if (refresh != null) {
      await _storage.write(key: _keyRefreshToken, value: refresh);
    }
    if (id != null) {
      await _storage.write(key: _keyIdToken, value: id);
    }
  }

  Future<void> _clear() async {
    _accessToken = null;
    _refreshToken = null;
    await _storage.deleteAll();
  }
}
