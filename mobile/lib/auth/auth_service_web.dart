// Web-only OIDC implementation.
///
/// flutter_appauth has no web plugin, so we implement Authorization Code +
/// PKCE (RFC 7636) manually using dart:html for the browser redirect and
/// Dio for the token exchange.  Tokens are kept in sessionStorage so they
/// survive hot-restarts but are cleared when the tab closes.
///
/// Flow:
///  1. login()  → build auth URL → window.location.href → Keycloak login form
///  2. Keycloak → POST /token (PKCE) → redirect to oidcWebRedirectUrl?code=…
///  3. App load → _AppGate calls isLoggedIn → _handleCallback() runs:
///       a. exchanges code for tokens via Dio
///       b. stores tokens in sessionStorage
///       c. history.replaceState removes code/state from address bar
///  4. Subsequent loads: no ?code → _handleCallback no-ops; token read from
///     sessionStorage.

// ignore: avoid_web_libraries_in_flutter
import 'dart:convert';
// ignore: avoid_web_libraries_in_flutter
import 'dart:html' as html;
import 'dart:math';

import 'package:crypto/crypto.dart';
import 'package:dio/dio.dart';

import '../config/app_config.dart';

class AuthService {
  AuthService._();
  static final AuthService instance = AuthService._();

  static const _kAccessToken  = 'hcm_access_token';
  static const _kRefreshToken = 'hcm_refresh_token';
  static const _kVerifier     = 'hcm_pkce_verifier';
  static const _kState        = 'hcm_oidc_state';

  bool _callbackHandled = false;

  // Reads straight from sessionStorage so it's always current after a reload.
  String? get _accessToken =>
      html.window.sessionStorage[_kAccessToken];

  // ── Public API ─────────────────────────────────────────────────────────────

  /// Returns a valid access token or null when not authenticated.
  Future<String?> getAccessToken() async {
    await _handleCallback();
    return _accessToken;
  }

  Future<bool> get isLoggedIn async => (await getAccessToken()) != null;

  /// Redirects the browser to Keycloak's authorisation endpoint.
  Future<void> login() async {
    final verifier   = _generateVerifier();
    final challenge  = _generateChallenge(verifier);
    final state      = _generateState();

    html.window.sessionStorage[_kVerifier] = verifier;
    html.window.sessionStorage[_kState]    = state;

    final authUri = Uri.parse(
      '${AppConfig.oidcIssuer}/protocol/openid-connect/auth',
    ).replace(queryParameters: {
      'response_type'         : 'code',
      'client_id'             : AppConfig.oidcClientId,
      'redirect_uri'          : AppConfig.oidcWebRedirectUrl,
      'scope'                 : 'openid profile email offline_access',
      'state'                 : state,
      'code_challenge'        : challenge,
      'code_challenge_method' : 'S256',
    });

    html.window.location.href = authUri.toString();
  }

  /// Clears local tokens and redirects to Keycloak's end-session endpoint.
  Future<void> logout() async {
    html.window.sessionStorage.remove(_kAccessToken);
    html.window.sessionStorage.remove(_kRefreshToken);

    final logoutUri = Uri.parse(
      '${AppConfig.oidcIssuer}/protocol/openid-connect/logout',
    ).replace(queryParameters: {
      'post_logout_redirect_uri' : AppConfig.oidcWebRedirectUrl,
      'client_id'                : AppConfig.oidcClientId,
    });

    html.window.location.href = logoutUri.toString();
  }

  // ── Biometric stubs (web has no biometric hardware) ─────────────────────────
  // The login/settings screens call these unconditionally; on web they are
  // no-ops so biometric UI simply stays hidden/disabled.
  Future<bool> isBiometricEnabled() async => false;

  Future<void> setBiometricEnabled(bool enabled) async {}

  Future<bool> loginWithBiometric() async => false;

  // ── Callback handler ───────────────────────────────────────────────────────

  Future<void> _handleCallback() async {
    if (_callbackHandled) return;
    _callbackHandled = true;

    final uri           = Uri.parse(html.window.location.href);
    final code          = uri.queryParameters['code'];
    final returnedState = uri.queryParameters['state'];
    if (code == null) return; // Not a callback URL — nothing to do.

    final savedState = html.window.sessionStorage[_kState];
    final verifier   = html.window.sessionStorage[_kVerifier];

    // Always clean up PKCE + state from storage.
    html.window.sessionStorage.remove(_kVerifier);
    html.window.sessionStorage.remove(_kState);

    // CSRF guard: state must match.
    if (returnedState != savedState || verifier == null) return;

    try {
      final resp = await Dio().post<Map<String, dynamic>>(
        '${AppConfig.oidcIssuer}/protocol/openid-connect/token',
        data: {
          'grant_type'   : 'authorization_code',
          'client_id'    : AppConfig.oidcClientId,
          'code'         : code,
          'redirect_uri' : AppConfig.oidcWebRedirectUrl,
          'code_verifier': verifier,
        },
        options: Options(contentType: 'application/x-www-form-urlencoded'),
      );
      final data = resp.data!;
      if (data['access_token'] is String) {
        html.window.sessionStorage[_kAccessToken] =
            data['access_token'] as String;
      }
      if (data['refresh_token'] is String) {
        html.window.sessionStorage[_kRefreshToken] =
            data['refresh_token'] as String;
      }
    } catch (_) {
      // Token exchange failed — user lands back on LoginScreen.
    }

    // Remove ?code=…&state=… from the address bar without triggering a reload.
    html.window.history.replaceState(null, '', '/');
  }

  // ── PKCE helpers ───────────────────────────────────────────────────────────

  String _generateVerifier() {
    final bytes = List<int>.generate(32, (_) => Random.secure().nextInt(256));
    return base64UrlEncode(bytes).replaceAll('=', '');
  }

  String _generateChallenge(String verifier) {
    final digest = sha256.convert(utf8.encode(verifier));
    return base64UrlEncode(digest.bytes).replaceAll('=', '');
  }

  String _generateState() {
    final bytes = List<int>.generate(16, (_) => Random.secure().nextInt(256));
    return base64UrlEncode(bytes).replaceAll('=', '');
  }
}
