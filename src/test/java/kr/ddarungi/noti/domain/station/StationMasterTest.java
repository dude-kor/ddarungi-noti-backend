package kr.ddarungi.noti.domain.station;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class StationMasterTest {

    private static final BigDecimal LAT = new BigDecimal("37.5558578");
    private static final BigDecimal LNG = new BigDecimal("126.9104630");

    @Test
    void splitsStationNumberFromOpenApiName() {
        StationMaster s = StationMaster.fromOpenApi("ST-4", "102. 망원역 1번출구 앞", 15, LAT, LNG);

        assertThat(s.getStationId()).isEqualTo("ST-4");
        assertThat(s.getStationNo()).isEqualTo("102");
        assertThat(s.getStationName()).isEqualTo("망원역 1번출구 앞");
        assertThat(s.getRackTotCnt()).isEqualTo((short) 15);
        assertThat(s.getStatus()).isEqualTo(StationStatus.NORMAL);
    }

    @Test
    void fallsBackToStationIdWhenNameHasNoNumberPrefix() {
        StationMaster s = StationMaster.fromOpenApi("ST-9", "서울숲역 4번출구 앞", 18, LAT, LNG);

        assertThat(s.getStationNo()).isEqualTo("ST-9");
        assertThat(s.getStationName()).isEqualTo("서울숲역 4번출구 앞");
    }

    @Test
    void refreshKeepsIdentityAndUpdatesAttributes() {
        StationMaster s = StationMaster.fromOpenApi("ST-4", "102. 망원역 1번출구 앞", 15, LAT, LNG);
        s.updateFromOpenApi("망원역 1번출구", 20, new BigDecimal("37.1"), new BigDecimal("127.1"));

        assertThat(s.getStationId()).isEqualTo("ST-4");
        assertThat(s.getStationNo()).isEqualTo("102");
        assertThat(s.getStationName()).isEqualTo("망원역 1번출구");
        assertThat(s.getRackTotCnt()).isEqualTo((short) 20);
        assertThat(s.getStationLat()).isEqualByComparingTo("37.1");
    }
}
