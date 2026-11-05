package kr.ddarungi.noti.seoul;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/** Envelope of Seoul Open API `bikeListHist` (root key assumed to equal the service name). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BikeListHistResponse(@JsonProperty("bikeListHist") Body body,
                                   @JsonProperty("RESULT") Result result) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(@JsonProperty("list_total_count") Integer total,
                       @JsonProperty("RESULT") Result result,
                       List<Row> row) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(@JsonProperty("CODE") String code, @JsonProperty("MESSAGE") String message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Row(String stationId, String stationName, Integer parkingBikeTotCnt, Integer rackTotCnt,
                      Integer shared, BigDecimal stationLatitude, BigDecimal stationLongitude,
                      String stationDt) {
        public int bikeCnt() { return parkingBikeTotCnt == null ? 0 : parkingBikeTotCnt; }
        public int rackCnt() { return rackTotCnt == null ? 0 : rackTotCnt; }
        public int sharedPct() { return shared == null ? 0 : shared; }
    }
}
