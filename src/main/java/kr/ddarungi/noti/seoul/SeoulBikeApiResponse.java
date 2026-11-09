package kr.ddarungi.noti.seoul;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Defensive model of a Seoul OpenAPI bike response ({@code bikeListHist}, {@code bikeList}/{@code rentBikeStatus}).
 *
 * <p>FIX: the previous DTO bound Jackson straight onto {@code Integer}/{@code BigDecimal} fields and assumed the root
 * key. Seoul's JSON is not typed reliably: counts arrive as {@code "5"}, {@code ""}, {@code null} or numbers, the
 * wrapper key differs per service ({@code rentBikeStatus}, {@code bikeListHist}, ...) and errors are returned as a bare
 * {@code {"RESULT":{...}}} or {@code {"CODE":..,"MESSAGE":..}} with HTTP 200. A single bad cell must never fail
 * the whole 2,700-station snapshot, so this class reads a {@link JsonNode} tree by hand:
 * <ul>
 *   <li>the wrapper is found structurally (first object that has {@code row} or {@code RESULT}), not by name;</li>
 *   <li>{@code row} may be an array, a single object or absent;</li>
 *   <li>every numeric cell goes through {@link #toIntOrNull}/{@link #toCoordinateOrNull}, which return {@code null}
 *       ("unknown") instead of throwing.</li>
 * </ul>
 * Only syntactically broken JSON raises {@link JsonProcessingException}.
 *
 * @param rootKey    name of the wrapper object that held the rows, or {@code null} if there was none
 * @param totalCount {@code list_total_count}, or {@code null}
 * @param result     {@code RESULT.CODE/MESSAGE}, or {@code null} when the body carried none
 * @param rows       valid rows only (non-blank stationId, de-duplicated by stationId, last one wins)
 */
public record SeoulBikeApiResponse(String rootKey, Integer totalCount, Result result, List<Row> rows) {

    /** Code assumed when a wrapper with rows is present but carries no RESULT block. */
    public static final String CODE_ASSUMED_OK = "INFO-000";
    /** Code reported when the body had no recognizable structure at all. */
    public static final String CODE_UNKNOWN = "UNKNOWN";

    private static final Pattern PLAIN_NUMBER = Pattern.compile("[+-]?[0-9]+(?:\\.[0-9]+)?");
    private static final int MAX_ROWS_GUARD = 100_000;

    public SeoulBikeApiResponse {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }

    /** Result code to act on: the explicit one, else OK when a wrapper exists, else UNKNOWN. */
    public String effectiveCode() {
        if (result != null && result.code() != null && !result.code().isBlank()) {
            return result.code();
        }
        return rootKey != null ? CODE_ASSUMED_OK : CODE_UNKNOWN;
    }

    // ---------------------------------------------------------------- nested types

    public record Result(String code, String message) {
    }

    /**
     * One station. Counts are {@code Integer} so that "unknown" (blank/garbage/null) stays distinguishable from a real 0.
     */
    public record Row(String stationId, String stationName, Integer parkingBikeTotCnt, Integer rackTotCnt,
                      Integer shared, BigDecimal stationLatitude, BigDecimal stationLongitude, String stationDt) {

        /** Bikes available; unknown counts as 0 for display only - check {@link #hasBikeCount()} before alerting. */
        public int bikeCnt() { return parkingBikeTotCnt == null ? 0 : parkingBikeTotCnt; }
        public int rackCnt() { return rackTotCnt == null ? 0 : rackTotCnt; }
        public int sharedPct() { return shared == null ? 0 : shared; }

        /** False when the API sent a blank/garbage bike count: such a row must not drive a "0 bikes" push. */
        public boolean hasBikeCount() { return parkingBikeTotCnt != null; }

        static Row from(JsonNode n) {
            return new Row(
                    text(n.get("stationId")),
                    text(n.get("stationName")),
                    toIntOrNull(n.get("parkingBikeTotCnt")),
                    toIntOrNull(n.get("rackTotCnt")),
                    toIntOrNull(n.get("shared")),
                    toCoordinateOrNull(n.get("stationLatitude"), 90),
                    toCoordinateOrNull(n.get("stationLongitude"), 180),
                    text(n.get("stationDt")));
        }
    }

    // ---------------------------------------------------------------- parsing

    /**
     * @throws JsonProcessingException if {@code json} is not syntactically valid JSON
     */
    public static SeoulBikeApiResponse parse(ObjectMapper mapper, String json) throws JsonProcessingException {
        JsonNode root = mapper.readTree(json);
        if (root == null || !root.isObject()) {
            // FIX: "[]", "null", "42", "" are valid JSON but not an API envelope -> UNKNOWN, never an NPE/CCE.
            return new SeoulBikeApiResponse(null, null, new Result(CODE_UNKNOWN, "Unexpected JSON root"), List.of());
        }

        // FIX: locate the wrapper structurally instead of hard-coding "bikeListHist".
        String rootKey = null;
        JsonNode body = null;
        for (Iterator<Map.Entry<String, JsonNode>> it = root.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            JsonNode v = e.getValue();
            if (v != null && v.isObject() && (v.has("row") || v.has("RESULT"))) {
                rootKey = e.getKey();
                body = v;
                break;
            }
        }
        if (body == null && root.has("row")) {          // unwrapped variant
            body = root;
        }

        Result result = readResult(body != null ? body.get("RESULT") : null);
        if (result == null) result = readResult(root.get("RESULT"));
        if (result == null && root.has("CODE")) {       // flat error: {"CODE":"INFO-100","MESSAGE":".."}
            result = new Result(text(root.get("CODE")), text(root.get("MESSAGE")));
        }

        Integer total = body == null ? null : toIntOrNull(body.get("list_total_count"));
        List<Row> rows = body == null ? List.of() : readRows(body.get("row"));
        return new SeoulBikeApiResponse(rootKey, total, result, rows);
    }

    private static Result readResult(JsonNode n) {
        if (n == null || !n.isObject()) return null;
        return new Result(text(n.get("CODE")), text(n.get("MESSAGE")));
    }

    private static List<Row> readRows(JsonNode rowNode) {
        if (rowNode == null || rowNode.isNull() || rowNode.isMissingNode()) return List.of();

        List<JsonNode> candidates = new ArrayList<>();
        if (rowNode.isArray()) {
            for (JsonNode item : rowNode) candidates.add(item);
        } else if (rowNode.isObject()) {                 // FIX: a lone row can be serialized as an object
            candidates.add(rowNode);
        }

        Map<String, Row> byId = new LinkedHashMap<>();
        for (JsonNode c : candidates) {
            if (c == null || !c.isObject()) continue;    // FIX: null / scalar array elements are skipped, not fatal
            Row row = Row.from(c);
            if (row.stationId() == null) continue;       // a row we cannot key is useless
            byId.put(row.stationId(), row);
            if (byId.size() >= MAX_ROWS_GUARD) break;    // runaway-payload guard
        }
        return new ArrayList<>(byId.values());
    }

    // ---------------------------------------------------------------- safe cell readers

    /** Scalar -> trimmed text; null for missing/null/blank/containers. NBSP and ideographic spaces count as blank. */
    static String text(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode() || !n.isValueNode()) return null;
        String s = clean(n.asText(null));
        return s == null || s.isEmpty() ? null : s;
    }

    /**
     * Number or numeric string ("5", " 12 ", "1,024", "47%", 7, 7.0) -> int; anything else
     * ("", null, "N/A", "-", "abc", true, objects, negatives, out-of-range) -> null. Never throws.
     */
    public static Integer toIntOrNull(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) return null;
        if (n.isNumber()) {
            return fitInt(n.decimalValue());
        }
        if (n.isTextual()) {
            return parseIntOrNull(n.asText());
        }
        return null;
    }

    /** String variant of {@link #toIntOrNull(JsonNode)}. */
    public static Integer parseIntOrNull(String raw) {
        String s = clean(raw);
        if (s == null || s.isEmpty()) return null;
        s = s.replace(",", "");
        if (s.endsWith("%")) s = s.substring(0, s.length() - 1).strip();
        if (!PLAIN_NUMBER.matcher(s).matches()) return null;
        try {
            return fitInt(new BigDecimal(s));
        } catch (NumberFormatException e) {   // unreachable after the regex, kept as a belt-and-braces guard
            return null;
        }
    }

    private static Integer fitInt(BigDecimal v) {
        BigDecimal whole = v.setScale(0, RoundingMode.DOWN);   // "7.9" -> 7: counts are never fractional
        if (whole.signum() < 0 || whole.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
            return null;                                       // negative or absurd count = corrupt cell
        }
        return whole.intValueExact();
    }

    /**
     * Latitude/longitude cell -> BigDecimal scaled to 7 places (the column is DECIMAL(10,7)), or null when blank,
     * non-numeric, out of range, or exactly 0 (Seoul uses 0 for "no coordinates").
     */
    static BigDecimal toCoordinateOrNull(JsonNode n, int absLimit) {
        String s;
        if (n == null || n.isNull() || n.isMissingNode()) return null;
        if (n.isNumber()) {
            s = n.decimalValue().toPlainString();
        } else if (n.isTextual()) {
            s = clean(n.asText());
        } else {
            return null;
        }
        if (s == null || !PLAIN_NUMBER.matcher(s).matches()) return null;
        BigDecimal v = new BigDecimal(s);
        if (v.signum() == 0 || v.abs().compareTo(BigDecimal.valueOf(absLimit)) > 0) return null;
        return v.setScale(7, RoundingMode.HALF_UP);
    }

    private static String clean(String s) {
        if (s == null) return null;
        return s.replace(' ', ' ').replace('　', ' ').replace('﻿', ' ').strip();
    }
}
