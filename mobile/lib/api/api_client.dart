import 'package:dio/dio.dart';
import '../auth/auth_service.dart';
import '../config/app_config.dart';

/// Dio HTTP client pre-configured for the HCM backend.
/// Automatically attaches the Bearer access token and retries once after a
/// silent refresh when a 401 is received.
class ApiClient {
  ApiClient._();
  static final ApiClient instance = ApiClient._();

  late final Dio _dio = _build();

  Dio get dio => _dio;

  Dio _build() {
    final dio = Dio(
      BaseOptions(
        baseUrl: AppConfig.apiBaseUrl,
        connectTimeout: const Duration(seconds: 10),
        receiveTimeout: const Duration(seconds: 30),
        headers: {'Accept': 'application/json'},
      ),
    );

    dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) async {
          final token = await AuthService.instance.getAccessToken();
          if (token != null) {
            options.headers['Authorization'] = 'Bearer $token';
          }
          handler.next(options);
        },
        onError: (err, handler) async {
          if (err.response?.statusCode == 401) {
            // One silent-refresh retry
            await AuthService.instance.login();
            final token = await AuthService.instance.getAccessToken();
            if (token != null) {
              final opts = err.requestOptions;
              opts.headers['Authorization'] = 'Bearer $token';
              try {
                final resp = await dio.fetch(opts);
                handler.resolve(resp);
                return;
              } catch (_) {}
            }
          }
          handler.next(err);
        },
      ),
    );
    return dio;
  }
}
