package kr.ddarungi.noti.seoul;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import kr.ddarungi.noti.config.SeoulApiProperties;
import kr.ddarungi.noti.seoul.BikeListHistResponse.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Client for `bikeListHist`: {base}/{KEY}/json/bikeListHist/{START}/{END}/{yyyyMMddHH}.
 *
 * The Open API serves at most 1,000 rows per call, so all stations (~2,700) are collected in chunks
 * (1~1000, 1001~2000, ...). Collection is all-or-nothing: a failed chunk fails the whole fetch so a
 * partial snapshot never reaches the cache.
 */
@Component
public class SeoulApiClient {

    private static final Logger log = LoggerFactory.getLogger(SeoulApiClient.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("yyyyMMddHH");

    static final String CODE_OK = "INFO-000";
    static final String CODE_NO_DATA = "INFO-200";

    private final RestClient restClient;
    private final SeoulApiProperties props;
    private final ObjectMapper mapper;

    public SeoulApiClient(RestClient seoulRestClient, SeoulApiProperties props, ObjectMapper mapper) {
        this.restClient = seoulRestClient;
        this.props = props;
        this.mapper = mapper;
    }

    /**
     * Every station for the current hour; if that hour has no data yet, the previous hour.
     * Guarded by the `seoulApi` circuit breaker (opens at 50% failures for 30s; timeouts 2s/3s).
     *
     * @throws SeoulApiException when the API reports an error or has no data for either hour
     */
    @CircuitBreaker(name = "seoulApi")
    public List<Row> fetchAllStations() {
        ZonedDateTime hour = ZonedDateTime.now(KST).truncatedTo(ChronoUnit.HOURS);
        List<Row> rows = fetchHour(hour);
        if (rows.isEmpty()) {
            log.info("bikeListHist has no data for {}, trying previous hour", HOUR.format(hour));
            rows = fetchHour(hour.minusHours(1));
        }
        if (rows.isEmpty()) {
            throw new SeoulApiException("bikeListHist returned no data for the last two hours");
        }
        return rows;
    }

    private List<Row> fetchHour(ZonedDateTime hour) {
        String stationDt = HOUR.format(hour);
        List<Row> all = new ArrayList<>();
        for (int start = 1; start <= props.maxStations(); start += props.chunkSize()) {
            int end = start + props.chunkSize() - 1;
            List<Row> chunk = fetchChunk(start, end, stationDt);
            all.addAll(chunk);
            if (chunk.size() < props.chunkSize()) {
                break;      // short or empty page = last page
            }
        }
        log.debug("bikeListHist {} -> {} stations", stationDt, all.size());
        return all;
    }

    private List<Row> fetchChunk(int start, int end, String stationDt) {
        String body = restClient.get()
                .uri("/{key}/json/bikeListHist/{start}/{end}/{dt}", props.key(), start, end, stationDt)
                .retrieve()
                .body(String.class);     // 4xx/5xx and timeouts throw -> counted by the circuit breaker
        return parse(body);
    }

    /**
     * Normal:   {"bikeListHist":{"list_total_count":N,"RESULT":{"CODE":"INFO-000",..},"row":[..]}}
     * No data / error: {"RESULT":{"CODE":"INFO-200","MESSAGE":".."}}
     */
    List<Row> parse(String json) {
        if (json == null || json.isBlank()) {
            throw new SeoulApiException("Empty response body from bikeListHist");
        }
        BikeListHistResponse response;
        try {
            response = mapper.readValue(json, BikeListHistResponse.class);
        } catch (JsonProcessingException e) {
            throw new SeoulApiException("Unparseable bikeListHist response", e);
        }

        BikeListHistResponse.Body body = response.body();
        BikeListHistResponse.Result result = body != null && body.result() != null ? body.result() : response.result();
        String code = result != null && result.code() != null ? result.code() : (body != null ? CODE_OK : "UNKNOWN");

        if (CODE_NO_DATA.equals(code)) {
            return List.of();
        }
        if (!CODE_OK.equals(code)) {
            throw new SeoulApiException("bikeListHist error " + code
                    + (result != null ? ": " + result.message() : ""));
        }
        if (body == null || body.row() == null) {
            return List.of();
        }
        return body.row().stream()
                .filter(r -> r.stationId() != null && !r.stationId().isBlank())
                .toList();
    }
}
