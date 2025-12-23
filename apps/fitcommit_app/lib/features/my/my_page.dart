import 'package:flutter/material.dart';

class MyPage extends StatelessWidget {
  const MyPage({super.key});

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: const [
        Text('마이페이지', style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
        SizedBox(height: 12),
        Card(
          child: Padding(
            padding: EdgeInsets.all(14),
            child: Text('❤️ 좋아요한 코디/아이템 (mock)'),
          ),
        ),
        Card(
          child: Padding(
            padding: EdgeInsets.all(14),
            child: Text('🗂 커뮤니티 활동 내역 (데모)'),
          ),
        ),
      ],
    );
  }
}
