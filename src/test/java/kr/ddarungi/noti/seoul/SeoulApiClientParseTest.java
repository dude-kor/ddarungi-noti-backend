package kr.ddarungi.noti.seoul;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.ddarungi.noti.config.SeoulApiProperties;
import kr.ddarungi.noti.seoul.SeoulBikeApiResponse.Row;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Parsing only: the HTTP side is exercised against WireMock in local/dev runs. */
class SeoulApiClientParseTest {

    private final SeoulApiClient client = new SeoulApiClient(
            null,
            new SeoulApiProperties("http://localhost", "KEY", 1000, 3000, null, null),
            new ObjectMapper());

    @Test
    void parsesRowsFromANormalResponse() {
        String json = """
                {"bikeListHist":{"list_total_count":2,
                  "RESULT":{"CODE":"INFO-000","MESSAGE":"정상 처리되었습니다"},
                  "row":[
                    {"stationId":"ST-4","stationName":"102. 망원역 1번출구 앞","parkingBikeTotCnt":7,"rackTotCnt":15,
                     "shared":47,"stationLatitude":37.5558578,"stationLongitude":126.9104630,"stationDt":"2026110508"},
                    {"stationId":"ST-6","stationName":"104. 합정역 1번출구 앞","parkingBikeTotCnt":0,"rackTotCnt":10,
                     "shared":0,"stationLatitude":37.5500770,"stationLongitude":126.9140230,"stationDt":"2026110508"}
                  ]}}
                """;

        List<Row> rows = client.parse(json);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).stationId()).isEqualTo("ST-4");
        assertThat(rows.get(0).bikeCnt()).isEqualTo(7);
        assertThat(rows.get(0).rackCnt()).isEqualTo(15);
        assertThat(rows.get(1).bikeCnt()).isZero();
    }

    @Test
    void stringNumbersUnderAnyWrapperKeyAreAccepted() {
        String json = """
                {"rentBikeStatus":{"list_total_count":1,"RESULT":{"CODE":"INFO-000","MESSAGE":"ok"},
                  "row":[{"stationId":"ST-4","stationName":"102. 망원역 1번출구 앞","parkingBikeTotCnt":"7",
                          "rackTotCnt":"15","shared":"47","stationLatitude":"37.55","stationLongitude":"126.91"}]}}
                """;

        List<Row> rows = client.parse(json);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).bikeCnt()).isEqualTo(7);
    }

    @Test
    void invalidKeyAnsweredWithHttp200IsAFailure() {
        assertThatThrownBy(() -> client.parse("{\"RESULT\":{\"CODE\":\"INFO-100\",\"MESSAGE\":\"인증키가 유효하지 않습니다.\"}}"))
                .isInstanceOf(SeoulApiException.class)
                .hasMessageContaining("INFO-100");
        assertThatThrownBy(() -> client.parse("{\"CODE\":\"INFO-100\",\"MESSAGE\":\"x\"}"))
                .isInstanceOf(SeoulApiException.class)
                .hasMessageContaining("INFO-100");
    }

    @Test
    void aGarbageCellDoesNotFailTheWholeResponse() {
        String json = """
                {"bikeListHist":{"RESULT":{"CODE":"INFO-000"},"row":[
                  {"stationId":"ST-4","parkingBikeTotCnt":"N/A","rackTotCnt":"","shared":null},
                  {"stationId":"ST-5","parkingBikeTotCnt":"3","rackTotCnt":"14"}]}}
                """;

        List<Row> rows = client.parse(json);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).hasBikeCount()).isFalse();
        assertThat(rows.get(1).bikeCnt()).isEqualTo(3);
    }

    @Test
    void noDataCodeMeansEmptyPage() {
        String json = """
                {"RESULT":{"CODE":"INFO-200","MESSAGE":"해당하는 데이터가 없습니다."}}
                """;

        assertThat(client.parse(json)).isEmpty();
    }

    @Test
    void errorCodeIsReportedAsFailure() {
        String json = """
                {"RESULT":{"CODE":"ERROR-500","MESSAGE":"서버 오류입니다."}}
                """;

        assertThatThrownBy(() -> client.parse(json))
                .isInstanceOf(SeoulApiException.class)
                .hasMessageContaining("ERROR-500");
    }

    @Test
    void blankAndGarbageBodiesAreFailures() {
        assertThatThrownBy(() -> client.parse("  ")).isInstanceOf(SeoulApiException.class);
        assertThatThrownBy(() -> client.parse("<html>")).isInstanceOf(SeoulApiException.class);
        assertThatThrownBy(() -> client.parse("{}")).isInstanceOf(SeoulApiException.class);
    }
}
