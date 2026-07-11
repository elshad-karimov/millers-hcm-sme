import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:firebase_core/firebase_core.dart';

import 'auth/auth_service.dart';
import 'config/app_config.dart';
import 'config/settings_service.dart';
import 'notifications/push_service.dart';
import 'screens/home_screen.dart';
import 'screens/login_screen.dart';

/// Background FCM handler — must be a top-level function.
// ignore: unused_element
Future<void> _fcmBackgroundHandler(dynamic message) async {
  // Handled by the OS notification tray; no in-app action needed here.
}

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  // M506: load the persisted language / notification preferences before UI.
  await SettingsService.instance.load();
  // Firebase init is optional — if google-services.json / GoogleService-Info.plist
  // is not present (dev / CI without FCM) the app still runs without push.
  try {
    await Firebase.initializeApp();
    await PushService.instance.init();
  } catch (_) {
    // FCM unavailable in this build/environment — silently continue.
  }
  runApp(
    const ProviderScope(
      child: MillersHcmApp(),
    ),
  );
}

class MillersHcmApp extends StatelessWidget {
  const MillersHcmApp({super.key});

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<Locale>(
      valueListenable: SettingsService.instance.locale,
      builder: (context, locale, _) {
        return MaterialApp(
          title: 'Millers HCM',
          debugShowCheckedModeBanner: false,
          locale: locale,
          localizationsDelegates: const [
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: const [Locale('en'), Locale('az')],
          theme: ThemeData(
            colorScheme: ColorScheme.fromSeed(
              seedColor: const Color(AppConfig.brandColorValue),
              brightness: Brightness.light,
            ),
            useMaterial3: true,
            fontFamily: 'Roboto',
            appBarTheme: const AppBarTheme(
              centerTitle: true,
              elevation: 0,
            ),
            elevatedButtonTheme: ElevatedButtonThemeData(
              style: ElevatedButton.styleFrom(
                minimumSize: const Size.fromHeight(48),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(8),
                ),
              ),
            ),
            cardTheme: CardThemeData(
              elevation: 1,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
              ),
            ),
          ),
          home: const _AppGate(),
          routes: {
            '/login': (_) => const LoginScreen(),
            '/home': (_) => const HomeScreen(),
          },
        );
      },
    );
  }
}

/// Checks auth state on startup and routes accordingly.
class _AppGate extends StatefulWidget {
  const _AppGate();

  @override
  State<_AppGate> createState() => _AppGateState();
}

class _AppGateState extends State<_AppGate> {
  late final Future<bool> _authCheck =
      AuthService.instance.isLoggedIn;

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<bool>(
      future: _authCheck,
      builder: (ctx, snap) {
        if (snap.connectionState == ConnectionState.waiting) {
          return const Scaffold(
            body: Center(child: CircularProgressIndicator()),
          );
        }
        if (snap.data == true) return const HomeScreen();
        return const LoginScreen();
      },
    );
  }
}
