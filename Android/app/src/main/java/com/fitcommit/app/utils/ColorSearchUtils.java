package com.fitcommit.app.utils;

import java.util.Locale;

/**
 * 옷장 검색에서 색상을 한국어·영어 어느 쪽으로 검색해도 찾을 수 있게 해주는 유틸리티입니다.
 *
 * 옷의 색상은 AI 분석 결과나 색상 선택으로 "black", "navy" 같은 영어 이름(또는 #RRGGBB 값)으로
 * 저장되기 때문에, 사용자가 "검정"·"파란색"처럼 검색하면 일치하지 않던 문제를 해결합니다.
 */
public final class ColorSearchUtils {
    /** 각 그룹의 첫 번째 값이 대표 색상 이름이며, 나머지는 같은 색을 가리키는 다른 표기입니다. */
    private static final String[][] COLOR_GROUPS = {
            {"black", "검정", "검정색", "검은색", "까망", "블랙"},
            {"white", "흰색", "하양", "하얀색", "화이트"},
            {"gray", "grey", "회색", "그레이"},
            {"navy", "네이비", "남색", "곤색"},
            {"blue", "파랑", "파란색", "푸른색", "청색", "블루"},
            {"brown", "갈색", "밤색", "브라운"},
            {"beige", "베이지"},
            {"red", "빨강", "빨간색", "붉은색", "레드"},
            {"pink", "분홍", "분홍색", "핑크"},
            {"green", "초록", "초록색", "녹색", "그린"},
            {"yellow", "노랑", "노란색", "옐로우", "옐로"},
            {"purple", "보라", "보라색", "퍼플"},
            {"orange", "주황", "주황색", "오렌지"},
    };

    /** AnalysisResultActivity의 대표 색상 팔레트(앞 12개 그룹 순서와 동일)입니다. */
    private static final int[][] PALETTE_RGB = {
            {20, 20, 20}, {245, 245, 245}, {140, 140, 140}, {20, 35, 90},
            {50, 100, 210}, {120, 80, 50}, {210, 190, 150}, {220, 40, 40},
            {230, 130, 170}, {50, 150, 80}, {230, 210, 50}, {130, 80, 180}
    };

    private ColorSearchUtils() {
    }

    /**
     * 저장된 색상 값에 대응하는 모든 표기(영어·한국어)를 공백으로 이어 반환합니다.
     * 알 수 없는 색상이면 빈 문자열을 반환합니다.
     */
    public static String keywordsFor(String color) {
        if (color == null) return "";
        String normalized = color.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return "";

        String[] group = findGroupByName(normalized);
        if (group == null && normalized.startsWith("#")) {
            group = findGroupByHex(normalized);
        }
        return group == null ? "" : String.join(" ", group);
    }

    private static String[] findGroupByName(String normalized) {
        for (String[] group : COLOR_GROUPS) {
            for (String alias : group) {
                if (normalized.contains(alias)) return group;
            }
        }
        return null;
    }

    private static String[] findGroupByHex(String hex) {
        String digits = hex.substring(1);
        if (digits.length() == 8) digits = digits.substring(2); // #AARRGGBB → RRGGBB
        if (digits.length() == 3) {
            digits = "" + digits.charAt(0) + digits.charAt(0)
                    + digits.charAt(1) + digits.charAt(1)
                    + digits.charAt(2) + digits.charAt(2);
        }
        if (digits.length() != 6) return null;

        int rgb;
        try {
            rgb = Integer.parseInt(digits, 16);
        } catch (NumberFormatException ignored) {
            return null;
        }
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;

        int bestIndex = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < PALETTE_RGB.length; i++) {
            long distance = square(red - PALETTE_RGB[i][0])
                    + square(green - PALETTE_RGB[i][1])
                    + square(blue - PALETTE_RGB[i][2]);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return COLOR_GROUPS[bestIndex];
    }

    private static long square(int value) {
        return (long) value * value;
    }
}
