/// Runtime configuration for the Millers HCM mobile app.
/// All values can be overridden by environment variables injected at build time
/// via `--dart-define` (e.g. `flutter run --dart-define=HCM_API_URL=...`).
class AppConfig {
  AppConfig._();

  // ----- Backend API ---------------------------------------------------------
  /// Base URL of the Spring Boot HCM backend.
  /// Dev default: local backend on port 8082.
  static const String apiBaseUrl = String.fromEnvironment(
    'HCM_API_URL',
    defaultValue: 'http://10.0.2.2:8082/api', // 10.0.2.2 → host loopback in Android emulator
  );

  // ----- Keycloak OIDC -------------------------------------------------------
  /// Keycloak issuer URL.  Must match the backend's `issuer-uri`.
  /// Dev: Keycloak is on the host at port 8090 (direct HTTP, bypasses nginx TLS).
  /// On iOS simulator use `localhost`; on Android emulator use `10.0.2.2`.
  static const String oidcIssuer = String.fromEnvironment(
    'HCM_OIDC_ISSUER',
    defaultValue: 'http://10.0.2.2:8090/realms/millers-hcm',
  );

  static const String oidcClientId = String.fromEnvironment(
    'HCM_OIDC_CLIENT_ID',
    defaultValue: 'hcm-mobile',
  );

  /// Custom URL scheme for the OIDC redirect after login.
  /// Must be registered in the Keycloak client's valid redirect URIs.
  /// Mobile custom-scheme redirect (flutter_appauth).
  static const String oidcRedirectUrl =
      'az.millers.hcm://auth/callback';

  static const String oidcEndSessionRedirectUrl =
      'az.millers.hcm://auth/end-session';

  /// Web redirect URI — must be a real HTTP URL (fixed port 5200 for dev).
  /// Register this URI in the Keycloak hcm-mobile client's valid redirect URIs.
  static const String oidcWebRedirectUrl = String.fromEnvironment(
    'HCM_OIDC_WEB_REDIRECT_URL',
    defaultValue: 'http://localhost:5200/',
  );

  // ----- Brand ---------------------------------------------------------------
  static const int brandColorValue = 0xFF5B3FE5; // Millers purple
}
