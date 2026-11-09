package kr.ddarungi.noti.seoul;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.ddarungi.noti.seoul.SeoulBikeApiResponse.Row;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Deserialisation safety of the Seoul Open API envelope: string numbers, blanks, nulls, odd shapes. */
class SeoulBikeApiResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private SeoulBikeApiResponse parse(String json) throws JsonProcessingException {
        return SeoulBikeApiResponse.parse(mapper, json);
    }

    // ---- numbers that arrive as strings ----------------------------------------------------------------------

    @Test
    void numbersDeliveredAsStringsAreParsed() throws Exception {
        SeoulBikeApiResponse r = parse("""
                {"bikeListHist":{"list_total_count":1,
                  "RESULT":{"CODE":"INFO-000","MESSAGE":"정상 처리되었습니다"},
                  "row":[{"stationId":"ST-4","stationName":"102. 망원역 1번출구 앞",
                          "parkingBikeTotCnt":"7","rackTotCnt":"15","shared":"47",
                          "stationLatitude":"37.55585780","stationLongitude":"126.91046300","stationDt":"2026110908"}]}}
                """);

        assertThat(r.effectiveCode()).isEqualTo("INFO-000");
        assertThat(r.rootKey()).isEqualTo("bikeListHist");
        assertThat(r.totalCount()).isEqualTo(1);
        Row row = r.rows().get(0);
        assertThat(row.stationId()).isEqualTo("ST-4");
        assertThat(row.bikeCnt()).isEqualTo(7);
        assertThat(row.rackCnt()).isEqualTo(15);
        assertThat(row.sharedPct()).isEqualTo(47);
        assertThat(row.hasBikeCount()).isTrue();
        assertThat(row.stationLatitude()).isEqualByComparingTo("37.5558578");   // scaled to DECIMAL(10,7)
        assertThat(row.stationLongitude()).isEqualByComparingTo("126.910463");
        assertThat(row.stationDt()).isEqualTo("2026110908");
    }

    @Test
    void numbersDeliveredAsJsonNumbersStillWork() throws Exception {
        SeoulBikeApiResponse r = parse("""
                {"bikeListHist":{"row":[{"stationId":"ST-6","parkingBikeTotCnt":0,"rackTotCnt":10.0,
                  "shared":47,"stationLatitude":37.550077,"stationLongitude":126.914023}]}}
                """);

        Row row = r.rows().get(0);
        assertThat(row.parkingBikeTotCnt()).isEqualTo(0);
        assertThat(row.hasBikeCount()).isTrue();          // a real 0 is NOT "unknown"
        assertThat(row.rackCnt()).isEqualTo(10);
        assertThat(row.stationLatitude()).isEqualByComparingTo("37.550077");
    }

    // ---- blank / null / garbage cells ------------------------------------------------------------------------

    @Test
    void blankNullAndGarbageCountsBecomeUnknownInsteadOfFailing() throws Exception {
        SeoulBikeApiResponse r = parse("""
                {"bikeListHist":{"row":[
                  {"stationId":"A","parkingBikeTotCnt":"","rackTotCnt":"   ","shared":null},
                  {"stationId":"B","parkingBikeTotCnt":"N/A","rackTotCnt":"-","shared":"abc"},
                  {"stationId":"C"},
                  {"stationId":"D","parkingBikeTotCnt":true,"rackTotCnt":{"x":1},"shared":[1,2]},
                  {"stationId":"E","parkingBikeTotCnt":"\\u00A0\\u3000","rackTotCnt":"null","shared":"NaN"}
                ]}}
                """);

        assertThat(r.rows()).hasSize(5);
        for (Row row : r.rows()) {
            assertThat(row.parkingBikeTotCnt()).isNull();
            assertThat(row.rackTotCnt()).isNull();
            assertThat(row.shared()).isNull();
            assertThat(row.hasBikeCount()).isFalse();     // must not drive a "0 bikes" push
            assertThat(row.bikeCnt()).isZero();           // display fallback only
        }
    }

    @Test
    void oddButRecoverableNumberFormats() {
        assertThat(SeoulBikeApiResponse.parseIntOrNull(" 12 ")).isEqualTo(12);
        assertThat(SeoulBikeApiResponse.parseIntOrNull(" 12 ")).isEqualTo(12);
        assertThat(SeoulBikeApiResponse.parseIntOrNull("1,024")).isEqualTo(1024);
        assertThat(SeoulBikeApiResponse.parseIntOrNull("47%")).isEqualTo(47);
        assertThat(SeoulBikeApiResponse.parseIntOrNull("7.9")).isEqualTo(7);
        assertThat(SeoulBikeApiResponse.parseIntOrNull("+5")).isEqualTo(5);
        assertThat(SeoulBikeApiResponse.parseIntOrNull("007")).isEqualTo(7);
    }

    @Test
    void corruptNumbersAreRejectedNotThrown() {
        for (String bad : new String[]{"-1", "-0.5e", "99999999999999999999", "1e3", "0x10", "٣", "5 5", "--5", ".", ""}) {
            assertThat(SeoulBikeApiResponse.parseIntOrNull(bad)).isNull();
        }
        assertThat(SeoulBikeApiResponse.parseIntOrNull(null)).isNull();
    }

    @Test
    void coordinatesAreValidatedAndScaled() throws Exception {
        SeoulBikeApiResponse r = parse("""
                {"bikeListHist":{"row":[
                  {"stationId":"A","stationLatitude":"37.123456789","stationLongitude":"127.1"},
                  {"stationId":"B","stationLatitude":"0.0","stationLongitude":"0"},
                  {"stationId":"C","stationLatitude":"abc","stationLongitude":""},
                  {"stationId":"D","stationLatitude":"999","stationLongitude":"-181"}
                ]}}
                """);

        assertThat(r.rows().get(0).stationLatitude()).isEqualByComparingTo("37.1234568");   // HALF_UP at 7 places
        assertThat(r.rows().get(0).stationLongitude()).isEqualByComparingTo("127.1");
        for (int i = 1; i < 4; i++) {
            assertThat(r.rows().get(i).stationLatitude()).isNull();
            assertThat(r.rows().get(i).stationLongitude()).isNull();
        }
    }

    // ---- envelope shapes -------------------------------------------------------------------------------------

    @Test
    void anyWrapperKeyIsAccepted() throws Exception {
        SeoulBikeApiResponse r = parse("""
                {"rentBikeStatus":{"list_total_count":"2","RESULT":{"CODE":"INFO-000","MESSAGE":"ok"},
                  "row":[{"stationId":"ST-1","parkingBikeTotCnt":"3"},{"stationId":"ST-2","parkingBikeTotCnt":"4"}]}}
                """);

        assertThat(r.rootKey()).isEqualTo("rentBikeStatus");
        assertThat(r.totalCount()).isEqualTo(2);                       // list_total_count as a string
        assertThat(r.rows()).hasSize(2);
    }

    @Test
    void unwrappedRowsAreAccepted() throws Exception {
        SeoulBikeApiResponse r = parse("""
                {"RESULT":{"CODE":"INFO-000","MESSAGE":"ok"},"row":[{"stationId":"ST-1","parkingBikeTotCnt":"3"}]}
                """);

        assertThat(r.rows()).hasSize(1);
        assertThat(r.effectiveCode()).isEqualTo("INFO-000");
    }

    @Test
    void rowAsSingleObjectOrNullOrMissing() throws Exception {
        assertThat(parse("{\"x\":{\"row\":{\"stationId\":\"A\"}}}").rows()).hasSize(1);
        assertThat(parse("{\"x\":{\"row\":null}}").rows()).isEmpty();
        assertThat(parse("{\"x\":{\"RESULT\":{\"CODE\":\"INFO-000\"}}}").rows()).isEmpty();
        assertThat(parse("{\"x\":{\"row\":\"oops\"}}").rows()).isEmpty();
        assertThat(parse("{\"x\":{\"row\":[]}}").rows()).isEmpty();
    }

    @Test
    void badRowElementsAreSkippedAndDuplicatesCollapse() throws Exception {
        SeoulBikeApiResponse r = parse("""
                {"x":{"row":[null,"text",42,[],{"stationId":"  "},{"stationName":"no id"},
                  {"stationId":"ST-1","parkingBikeTotCnt":"1"},{"stationId":"ST-1","parkingBikeTotCnt":"9"},
                  {"stationId":"ST-2"}]}}
                """);

        List<String> ids = r.rows().stream().map(Row::stationId).toList();
        assertThat(ids).containsExactly("ST-1", "ST-2");
        assertThat(r.rows().get(0).bikeCnt()).isEqualTo(9);            // last duplicate wins
    }

    @Test
    void unknownFieldsAreIgnored() throws Exception {
        SeoulBikeApiResponse r = parse("""
                {"x":{"row":[{"stationId":"ST-1","parkingBikeTotCnt":"1","brandNewField":{"a":[1,2]}}],"extra":1}}
                """);

        assertThat(r.rows()).hasSize(1);
    }

    // ---- results & errors ------------------------------------------------------------------------------------

    @Test
    void topLevelErrorResultIsExposed() throws Exception {
        SeoulBikeApiResponse r = parse("{\"RESULT\":{\"CODE\":\"INFO-200\",\"MESSAGE\":\"해당하는 데이터가 없습니다.\"}}");

        assertThat(r.rootKey()).isNull();
        assertThat(r.rows()).isEmpty();
        assertThat(r.effectiveCode()).isEqualTo("INFO-200");
        assertThat(r.result().message()).contains("데이터가 없습니다");
    }

    @Test
    void flatErrorShapeIsExposed() throws Exception {
        SeoulBikeApiResponse r = parse("{\"CODE\":\"INFO-100\",\"MESSAGE\":\"인증키가 유효하지 않습니다.\"}");

        assertThat(r.effectiveCode()).isEqualTo("INFO-100");
        assertThat(r.rows()).isEmpty();
    }

    @Test
    void missingResultWithAWrapperIsTreatedAsOk() throws Exception {
        assertThat(parse("{\"x\":{\"row\":[{\"stationId\":\"A\"}]}}").effectiveCode()).isEqualTo("INFO-000");
    }

    @Test
    void unrecognisedStructureIsUnknownNeverAnException() throws Exception {
        for (String json : new String[]{"{}", "[]", "null", "42", "\"text\"", "[{\"stationId\":\"A\"}]",
                "{\"a\":1,\"b\":[1,2]}"}) {
            SeoulBikeApiResponse r = parse(json);
            assertThat(r.effectiveCode()).isEqualTo("UNKNOWN");
            assertThat(r.rows()).isEmpty();
        }
    }

    @Test
    void syntacticallyBrokenJsonIsTheOnlyHardFailure() {
        assertThatThrownBy(() -> parse("<html>502 Bad Gateway</html>")).isInstanceOf(JsonProcessingException.class);
        assertThatThrownBy(() -> parse("{\"bikeListHist\":{\"row\":[")).isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void oneCorruptCellNeverCostsTheRestOfTheSnapshot() throws Exception {
        StringBuilder json = new StringBuilder("{\"bikeListHist\":{\"RESULT\":{\"CODE\":\"INFO-000\"},\"row\":[");
        for (int i = 0; i < 2700; i++) {
            if (i > 0) json.append(',');
            String bike = i == 1500 ? "\"???\"" : "\"" + (i % 20) + "\"";
            json.append("{\"stationId\":\"ST-").append(i).append("\",\"parkingBikeTotCnt\":").append(bike)
                    .append(",\"rackTotCnt\":\"20\"}");
        }
        json.append("]}}");

        SeoulBikeApiResponse r = parse(json.toString());

        assertThat(r.rows()).hasSize(2700);
        assertThat(r.rows().get(1500).hasBikeCount()).isFalse();
        assertThat(r.rows().get(1499).hasBikeCount()).isTrue();
    }
}
