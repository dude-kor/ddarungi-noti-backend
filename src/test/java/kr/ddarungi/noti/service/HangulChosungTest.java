package kr.ddarungi.noti.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HangulChosungTest {

    @Test
    void extractsChosungAndKeepsOtherCharacters() {
        assertThat(HangulChosung.extract("망원역 1번출구")).isEqualTo("ㅁㅇㅇ 1ㅂㅊㄱ");
    }

    @Test
    void detectsChosungOnlyQueries() {
        assertThat(HangulChosung.isAllChosung("ㅁㅇㅇ")).isTrue();
        assertThat(HangulChosung.isAllChosung("망원")).isFalse();
        assertThat(HangulChosung.isAllChosung("102")).isFalse();
        assertThat(HangulChosung.isAllChosung("")).isFalse();
    }

    @Test
    void chosungQueryMatchesStationName() {
        assertThat(HangulChosung.extract("망원역 1번출구 앞")).contains("ㅁㅇㅇ");
        assertThat(HangulChosung.extract("합정역 5번출구 앞")).doesNotContain("ㅁㅇㅇ");
    }
}
