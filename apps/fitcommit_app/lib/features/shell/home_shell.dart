import 'package:flutter/material.dart';

import '../closet/closet_page.dart';
import '../my/my_page.dart';
import '../recommend/recommend_page.dart';
import '../shop/shop_page.dart';

class HomeShell extends StatefulWidget {
  const HomeShell({super.key});

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  int _index = 0;

  final List<Widget> _pages = const [
    _HomePage(),
    RecommendPage(),
    ClosetPage(),
    ShopPage(),
    MyPage(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: _pages[_index],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        onDestinationSelected: (value) {
          setState(() => _index = value);
        },
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.home_outlined),
            label: '홈',
          ),
          NavigationDestination(
            icon: Icon(Icons.auto_awesome_outlined),
            label: '추천',
          ),
          NavigationDestination(
            icon: Icon(Icons.checkroom_outlined),
            label: '옷장',
          ),
          NavigationDestination(
            icon: Icon(Icons.storefront_outlined),
            label: '상점',
          ),
          NavigationDestination(
            icon: Icon(Icons.person_outline),
            label: '마이',
          ),
        ],
      ),
    );
  }
}

class _HomePage extends StatelessWidget {
  const _HomePage();

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        const Text(
          '오늘의 Fit Commit',
          style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800),
        ),
        const SizedBox(height: 12),
        const Card(
          child: Padding(
            padding: EdgeInsets.all(14),
            child: Text('📸 사진으로 체형/톤 분석 (데모)'),
          ),
        ),
        const Card(
          child: Padding(
            padding: EdgeInsets.all(14),
            child: Text('👔 내 옷장 기반 코디 추천 (mock)'),
          ),
        ),
        const Card(
          child: Padding(
            padding: EdgeInsets.all(14),
            child: Text('🛒 부족한 기본템 추천 + 그루밍'),
          ),
        ),
        const SizedBox(height: 20),
        FilledButton(
          onPressed: () {},
          child: const Text('사진 업로드(데모 버튼)'),
        ),
      ],
    );
  }
}
