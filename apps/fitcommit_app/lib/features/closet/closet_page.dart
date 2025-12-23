import 'package:flutter/material.dart';

class ClosetPage extends StatelessWidget {
  const ClosetPage({super.key});

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: const [
        Text('디지털 옷장', style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
        SizedBox(height: 12),
        Card(
          child: Padding(
            padding: EdgeInsets.all(14),
            child: Text('📦 내 옷 리스트 (mock: items.json)'),
          ),
        ),
        Card(
          child: Padding(
            padding: EdgeInsets.all(14),
            child: Text('➕ 옷 추가하기 (데모)'),
          ),
        ),
      ],
    );
  }
}
