// Platform-split authentication service.
///
/// On web  (dart:html available) → auth_service_web.dart
///   Manual Authorization Code + PKCE via browser redirect + Dio token exchange.
///
/// On mobile / desktop → auth_service_mobile.dart
///   flutter_appauth (native AppAuth SDK, Authorization Code + PKCE).
///
/// Both files expose the same `AuthService` class with the same public API so
/// all callers can just `import 'auth_service.dart'` without caring about
/// the underlying platform.
export 'auth_service_mobile.dart'
    if (dart.library.html) 'auth_service_web.dart';
