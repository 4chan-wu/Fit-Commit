import 'package:flutter/material.dart';

class RecommendPage extends StatelessWidget {
  const RecommendPage({super.key});

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: const [
        Text('추천', style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
        SizedBox(height: 12),
        Card(
          child: Padding(
            padding: EdgeInsets.all(14),
            child: Text('✅ 오늘의 추천 코디 (mock)'),
          ),
        ),
        Card(
          child: Padding(
            padding: EdgeInsets.all(14),
            child: Text('✅ 상황 선택: 데일리/데이트/출근'),
          ),
        ),
        Card(
          child: Padding(
            padding: EdgeInsets.all(14),
            child: Text('✅ 체형/톤 기반 스타일 가이드 (추정)'),
          ),
        ),
      ],
    );
  }
}
