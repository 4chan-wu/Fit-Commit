import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

class IntroPage extends StatelessWidget {
  const IntroPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Fit Commit')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const SizedBox(height: 12),
            const Text(
              '사진 한 번으로 체형·톤 분석 → 풀코디 추천',
              style: TextStyle(fontSize: 20, fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 12),
            const _IntroCard(
              title: 'AI 스타일 진단',
              desc: '체형/피부톤(추정) 기반으로 핏과 컬러를 추천해요.',
            ),
            const _IntroCard(
              title: '디지털 옷장',
              desc: '내 옷장 아이템을 바탕으로 대체 코디를 만들어요.',
            ),
            const _IntroCard(
              title: '부족템 추천',
              desc: '옷장에 없는 기본템을 가성비로 추천해요.',
            ),
            const Spacer(),
            FilledButton(
              onPressed: () => context.go('/app'),
              child: const Text('시작하기'),
            ),
          ],
        ),
      ),
    );
  }
}

class _IntroCard extends StatelessWidget {
  final String title;
  final String desc;

  const _IntroCard({required this.title, required this.desc});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: const TextStyle(fontWeight: FontWeight.w700)),
            const SizedBox(height: 6),
            Text(desc),
          ],
        ),
      ),
    );
  }
}
