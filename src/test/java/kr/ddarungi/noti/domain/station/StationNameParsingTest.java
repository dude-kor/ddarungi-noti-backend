package kr.ddarungi.noti.domain.station;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Station-name parsing: number / name / status tags, and "never throws" robustness. */
class StationNameParsingTest {

    private static final BigDecimal LAT = new BigDecimal("37.5558578");
    private static final BigDecimal LNG = new BigDecimal("126.9104630");

    private static void assertParsed(String raw, String number, String name) {
        StationNameParser.Parsed p = StationNameParser.parse(raw);
        assertThat(p.number()).isEqualTo(number);
        assertThat(p.name()).isEqualTo(name);
    }

    // ---- numbered names --------------------------------------------------------------------------------------

    @Test
    void plainNumberedName() {
        assertParsed("102. 망원역 1번출구 앞", "102", "망원역 1번출구 앞");
    }

    @Test
    void spacingAndSeparatorVariants() {
        assertParsed("102.망원역 1번출구 앞", "102", "망원역 1번출구 앞");
        assertParsed("102 . 망원역", "102", "망원역");
        assertParsed("  102.   망원역  ", "102", "망원역");
        assertParsed("102． 망원역", "102", "망원역");            // full-width full stop
        assertParsed("102. 망원역", "102", "망원역");            // no-break space
        assertParsed("　102. 망원역", "102", "망원역");           // ideographic space
    }

    @Test
    void numbersInsideTheNameAreNotMistakenForThePrefix() {
        assertParsed("2호선 강남역 1번 출구", null, "2호선 강남역 1번 출구");
        assertParsed("1.5km 코스 입구", null, "1.5km 코스 입구");      // digit after the dot -> not a prefix
        assertParsed("102. 2호선 합정역", "102", "2호선 합정역");
    }

    // ---- tags / status ---------------------------------------------------------------------------------------

    @Test
    void maintenanceTagMarksTheStationOutOfService() {
        StationNameParser.Parsed p = StationNameParser.parse("[점검중] 102. 망원역");

        assertThat(p.number()).isEqualTo("102");
        assertThat(p.name()).isEqualTo("망원역");
        assertThat(p.tags()).containsExactly("점검중");
        assertThat(p.outOfService()).isTrue();
        assertThat(p.temporary()).isFalse();
    }

    @Test
    void tagVariants() {
        assertParsed("[점검중]102. 망원역", "102", "망원역");
        assertParsed("［점검중］ 102. 망원역", "102", "망원역");   // full-width brackets
        assertParsed("(운영중지) 102. 망원역", "102", "망원역");
        assertParsed("102. [점검중] 망원역", "102", "망원역");            // tag after the number
    }

    @Test
    void temporaryStationsAndStackedTags() {
        StationNameParser.Parsed p = StationNameParser.parse("(임시) 3001. 임시 거치소");
        assertThat(p.number()).isEqualTo("3001");
        assertThat(p.name()).isEqualTo("임시 거치소");
        assertThat(p.temporary()).isTrue();
        assertThat(p.outOfService()).isFalse();

        StationNameParser.Parsed both = StationNameParser.parse("[점검중][임시] 102. 망원역");
        assertThat(both.tags()).containsExactly("점검중", "임시");
        assertThat(both.outOfService()).isTrue();
        assertThat(both.temporary()).isTrue();
        assertThat(both.name()).isEqualTo("망원역");
    }

    // ---- hostile / degenerate input: must not throw ----------------------------------------------------------

    @Test
    void namesWithoutNumber() {
        assertParsed("서울숲역 4번출구 앞", null, "서울숲역 4번출구 앞");
        assertParsed("임시 거치소 (번호 없음)", null, "임시 거치소 (번호 없음)");
    }

    @Test
    void emptyAndDegenerateInput() {
        assertParsed(null, null, "");
        assertParsed("", null, "");
        assertParsed("    　 ", null, "");
        assertParsed("102.", "102", "");                 // number but nothing else
        assertParsed("[점검중]", null, "");
        assertParsed("[점검중", null, "[점검중");         // unclosed tag stays text
        assertParsed("[]", null, "[]");                  // empty tag is not a tag
        assertParsed("102", null, "102");                // digits without a dot are just a name
        assertParsed(".", null, ".");
    }

    @Test
    void oversizedNumberStaysTextInsteadOfOverflowing() {
        // 24 digits would overflow int AND long: the number is never converted, and >9 digits is not a prefix.
        assertParsed("123456789012345678901234. 긴 번호", null, "123456789012345678901234. 긴 번호");
    }

    @Test
    void hugeAndAdversarialInputsFinishQuickly() {
        long t0 = System.nanoTime();
        StationNameParser.parse("가".repeat(200_000));
        StationNameParser.parse("[" + "가".repeat(10_000));
        StationNameParser.parse("[".repeat(10_000));
        StationNameParser.parse("(" + " ".repeat(50_000) + ")");
        StationNameParser.parse("(" + " ".repeat(50_000) + "x");        // unclosed: worst case for the tag regex
        StationNameParser.parse("1".repeat(100_000) + ".");
        assertThat((System.nanoTime() - t0) / 1_000_000).isLessThan(2_000L);   // ms: no catastrophic backtracking
    }

    @Test
    void randomInputNeverThrowsAndNeverReturnsNull() {
        String alphabet = "0123456789.．。  　[]()［］（）점검중임시망원역abcXYZ-_,\n\t😀\uD83D";
        Random rnd = new Random(20261109L);
        for (int i = 0; i < 30_000; i++) {
            int len = rnd.nextInt(40);
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < len; k++) sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));

            StationNameParser.Parsed p = StationNameParser.parse(sb.toString());

            assertThat(p).isNotNull();
            assertThat(p.name()).isNotNull();
            assertThat(p.tags()).isNotNull();
            if (p.number() != null) {
                assertThat(p.number().matches("[0-9]{1,9}")).isTrue();
            }
        }
    }

    // ---- StationMaster.fromOpenApi ---------------------------------------------------------------------------

    @Test
    void factorySplitsNumberAndName() {
        StationMaster s = StationMaster.fromOpenApi("ST-4", "102. 망원역 1번출구 앞", 15, LAT, LNG);

        assertThat(s.getStationId()).isEqualTo("ST-4");
        assertThat(s.getStationNo()).isEqualTo("102");
        assertThat(s.getStationName()).isEqualTo("망원역 1번출구 앞");
        assertThat(s.getRackTotCnt()).isEqualTo((short) 15);
        assertThat(s.getStatus()).isEqualTo(StationStatus.NORMAL);
    }

    @Test
    void factoryMarksMaintenanceStationsClosed() {
        StationMaster s = StationMaster.fromOpenApi("ST-5", "[점검중] 103. 망원역 2번출구", 14, LAT, LNG);

        assertThat(s.getStationNo()).isEqualTo("103");
        assertThat(s.getStationName()).isEqualTo("망원역 2번출구");
        assertThat(s.getStatus()).isEqualTo(StationStatus.CLOSED);
    }

    @Test
    void factoryFallsBackToTheIdWhenThereIsNothingBetter() {
        StationMaster noNumber = StationMaster.fromOpenApi("ST-9", "서울숲역 4번출구 앞", 18, LAT, LNG);
        assertThat(noNumber.getStationNo()).isEqualTo("ST-9");
        assertThat(noNumber.getStationName()).isEqualTo("서울숲역 4번출구 앞");

        for (String raw : new String[]{null, "", "  ", "[점검중]", "102."}) {
            StationMaster s = StationMaster.fromOpenApi("ST-1", raw, 10, LAT, LNG);
            assertThat(s.getStationName()).isEqualTo("ST-1");        // station_name is NOT NULL, never ""
        }
    }

    @Test
    void factoryClampsEverythingToTheColumnSizes() {
        StationMaster longName = StationMaster.fromOpenApi("ST-1", "102. " + "가".repeat(300), 10, LAT, LNG);
        assertThat(longName.getStationName().length()).isEqualTo(128);

        StationMaster emoji = StationMaster.fromOpenApi("ST-2", "😀".repeat(100), 10, LAT, LNG);
        assertThat(emoji.getStationName().length() <= 128).isTrue();
        assertThat(Character.isHighSurrogate(
                emoji.getStationName().charAt(emoji.getStationName().length() - 1))).isFalse();   // no split pair

        assertThat(StationMaster.fromOpenApi("ST-3", "1. a", 70_000, LAT, LNG).getRackTotCnt()).isEqualTo(Short.MAX_VALUE);
        assertThat(StationMaster.fromOpenApi("ST-3", "1. a", -5, LAT, LNG).getRackTotCnt()).isEqualTo((short) 0);
    }

    @Test
    void factoryRejectsOnlyAnUnusableId() {
        assertThatThrownBy(() -> StationMaster.fromOpenApi(null, "102. a", 1, LAT, LNG))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StationMaster.fromOpenApi("  ", "102. a", 1, LAT, LNG))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StationMaster.fromOpenApi("X".repeat(17), "102. a", 1, LAT, LNG))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refreshKeepsGoodCoordinatesWhenTheFreshOnesAreUnparseable() {
        StationMaster s = StationMaster.fromOpenApi("ST-4", "102. 망원역 1번출구 앞", 15, LAT, LNG);

        s.updateFromOpenApi("망원역 1번출구", 20, null, null);

        assertThat(s.getStationName()).isEqualTo("망원역 1번출구");
        assertThat(s.getRackTotCnt()).isEqualTo((short) 20);
        assertThat(s.getStationLat()).isEqualByComparingTo(LAT);
        assertThat(s.getStationLng()).isEqualByComparingTo(LNG);
    }

    @Test
    void syncFromPicksUpNumberNameAndStatus() {
        StationMaster s = StationMaster.fromOpenApi("ST-4", "102. 망원역 1번출구 앞", 15, LAT, LNG);

        s.syncFrom(StationMaster.fromOpenApi("ST-4", "[점검중] 110. 망원역 3번출구", 16, null, null));

        assertThat(s.getStationNo()).isEqualTo("110");
        assertThat(s.getStationName()).isEqualTo("망원역 3번출구");
        assertThat(s.getStatus()).isEqualTo(StationStatus.CLOSED);
        assertThat(s.getRackTotCnt()).isEqualTo((short) 16);
        assertThat(s.getStationLat()).isEqualByComparingTo(LAT);       // kept
    }
}
