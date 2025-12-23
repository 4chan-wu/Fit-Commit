import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'features/intro/intro_page.dart';
import 'features/shell/home_shell.dart';
import 'features/splash/splash_page.dart';

final appRouterProvider = Provider<GoRouter>((ref) {
  return GoRouter(
    initialLocation: '/splash',
    routes: [
      GoRoute(
        path: '/splash',
        builder: (context, state) => const SplashPage(),
      ),
      GoRoute(
        path: '/intro',
        builder: (context, state) => const IntroPage(),
      ),
      GoRoute(
        path: '/app',
        builder: (context, state) => const HomeShell(),
      ),
    ],
  );
});

/// (확장용) stream 기반 refresh가 필요할 때 쓰는 유틸
class GoRouterRefreshStream extends ChangeNotifier {
  GoRouterRefreshStream(Stream<dynamic> stream) {
    _sub = stream.asBroadcastStream().listen((_) => notifyListeners());
  }

  late final StreamSubscription<dynamic> _sub;

  @override
  void dispose() {
    _sub.cancel();
    super.dispose();
  }
}
