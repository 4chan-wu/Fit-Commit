import 'package:flutter/material.dart';

class ShopPage extends StatelessWidget {
  const ShopPage({super.key});

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: const [
        Text('상점', style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
        SizedBox(height: 12),
        Card(
          child: Padding(
            padding: EdgeInsets.all(14),
            child: Text('🛒 부족템 추천 (mock: products.json)'),
          ),
        ),
        Card(
          child: Padding(
            padding: EdgeInsets.all(14),
            child: Text('🧴 그루밍: 향수/왁스/제모/시계'),
          ),
        ),
      ],
    );
  }
}
