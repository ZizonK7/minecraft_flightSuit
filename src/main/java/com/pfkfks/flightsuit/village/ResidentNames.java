package com.pfkfks.flightsuit.village;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Given names for residents. Residents of one job share a skin, so the name is what tells them apart
 * (user decision): a village never hands out a name it already has while there are unused ones left.
 */
public final class ResidentNames {
    private static final String[] NAMES = {
            "민준", "서연", "도윤", "하은", "지호", "수아", "예준", "지우", "시우", "서윤",
            "주원", "하린", "은우", "지안", "건우", "윤서", "선우", "채원", "유준", "다은",
            "현우", "예린", "준서", "소율", "우진", "가은", "지훈", "나은", "태윤", "시아",
            "동현", "유나", "승민", "서아", "재윤", "하윤", "민성", "수빈", "정우", "예나",
            "태민", "아린", "성민", "다인", "한결", "보라", "철수", "영희", "바다", "초롱",
            "봄이", "여름", "가람", "누리", "다솜", "마루", "새봄", "슬기", "아라", "한별"
    };

    private ResidentNames() {
    }

    public static String pick(RandomSource random, Set<String> taken) {
        List<String> free = new ArrayList<>();
        for (String name : NAMES) {
            if (!taken.contains(name)) {
                free.add(name);
            }
        }
        if (free.isEmpty()) {
            // Sixty people later: fall back to numbered names rather than twins.
            String base = NAMES[random.nextInt(NAMES.length)];
            for (int n = 2; ; n++) {
                if (!taken.contains(base + " " + n)) {
                    return base + " " + n;
                }
            }
        }
        return free.get(random.nextInt(free.size()));
    }
}
