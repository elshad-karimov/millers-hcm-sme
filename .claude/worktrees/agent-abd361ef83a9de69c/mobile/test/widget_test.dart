import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:millers_hcm/main.dart';

void main() {
  testWidgets('App renders without crashing', (WidgetTester tester) async {
    await tester.pumpWidget(const ProviderScope(child: MillersHcmApp()));
    // Auth gate shows a progress indicator while checking token state.
    expect(find.byType(CircularProgressIndicator), findsAny);
  });
}
