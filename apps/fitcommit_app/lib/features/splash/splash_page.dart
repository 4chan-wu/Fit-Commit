import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

class SplashPage extends StatefulWidget {
  const SplashPage({super.key});

  @override
  State<SplashPage> createState() => _SplashPageState();
}

class _SplashPageState extends State<SplashPage> {
  @override
  void initState() {
    super.initState();
    Future.delayed(const Duration(milliseconds: 900), () {
      if (!mounted) return;
      context.go('/intro');
    });
  }

  @override
  Widget build(BuildContext context) {
    // ✅ const Scaffold 제거 (내부가 const가 아니어도 안전)
    return Scaffold(
      body: Center(
        child: Text(
          'Fit Commit',
          style: const TextStyle(
            fontSize: 28,
            fontWeight: FontWeight.w700,
          ),
        ),
      ),
    );
  }
}
