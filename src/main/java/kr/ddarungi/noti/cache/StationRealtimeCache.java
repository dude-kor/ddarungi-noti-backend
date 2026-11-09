package kr.ddarungi.noti.cache;

import kr.ddarungi.noti.config.CacheProperties;
import kr.ddarungi.noti.seoul.SeoulBikeApiResponse.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

/**
 * Redis layer. Live key `station:{id}:realtime` (Hash, TTL 60s) per the spec, plus a shadow
 * `station:{id}:snapshot` (30 min) that backs the circuit-breaker "stale" fallback.
 * All reads/writes are pipelined.
 */
@Component
@RequiredArgsConstructor
public class StationRealtimeCache {

    private static final Duration SNAPSHOT_TTL = Duration.ofMinutes(30);
    private static final String FRESH_MARKER = "sync:seoul_api:last";
    private static final int WRITE_BATCH = 500;

    private final StringRedisTemplate redis;
    private final CacheProperties props;

    static String liveKey(String id) { return "station:" + id + ":realtime"; }
    static String snapshotKey(String id) { return "station:" + id + ":snapshot"; }

    public Map<String, StationAvailability> readLive(Collection<String> ids) {
        return read(ids, false);
    }

    public Map<String, StationAvailability> readSnapshot(Collection<String> ids) {
        return read(ids, true);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, StationAvailability> read(Collection<String> ids, boolean snapshot) {
        List<String> list = List.copyOf(ids);
        if (list.isEmpty()) return Map.of();
        List<Object> results = redis.executePipelined(new SessionCallback<Object>() {
            @Override
            public Object execute(RedisOperations ops) {
                for (String id : list) {
                    ops.opsForHash().entries(snapshot ? snapshotKey(id) : liveKey(id));
                }
                return null;
            }
        });
        Map<String, StationAvailability> out = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            Object r = results.get(i);
            if (r instanceof Map<?, ?> m && !m.isEmpty()) {
                out.put(list.get(i), parse((Map<Object, Object>) m, snapshot));
            }
        }
        return out;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void writeAll(List<Row> rows, long nowMs) {
        Duration liveTtl = props.stationRealtimeTtl();
        for (int from = 0; from < rows.size(); from += WRITE_BATCH) {
            List<Row> part = rows.subList(from, Math.min(from + WRITE_BATCH, rows.size()));
            redis.executePipelined(new SessionCallback<Object>() {
                @Override
                public Object execute(RedisOperations ops) {
                    for (Row row : part) {
                        // FIX: a blank/garbage bike count is "unknown", not 0. Caching it as 0 would fire a false
                        //      "0 bikes" push; without a key the dispatcher skips the station (av == null).
                        if (!row.hasBikeCount()) continue;
                        Map<String, String> hash = Map.of(
                                "bike_cnt", String.valueOf(row.bikeCnt()),
                                "rack_tot_cnt", String.valueOf(row.rackCnt()),
                                "shared", String.valueOf(row.sharedPct()),
                                "status", "NORMAL",
                                "updated_at", String.valueOf(nowMs));
                        String live = liveKey(row.stationId());
                        String snap = snapshotKey(row.stationId());
                        ops.opsForHash().putAll(live, hash);
                        ops.expire(live, liveTtl);
                        ops.opsForHash().putAll(snap, hash);
                        ops.expire(snap, SNAPSHOT_TTL);
                    }
                    return null;
                }
            });
        }
    }

    /** Marker meaning "a full Seoul API sync happened within the live TTL". */
    public boolean isFresh() {
        return Boolean.TRUE.equals(redis.hasKey(FRESH_MARKER));
    }

    public void markFresh() {
        redis.opsForValue().set(FRESH_MARKER, "1", props.stationRealtimeTtl());
    }

    private static StationAvailability parse(Map<Object, Object> m, boolean stale) {
        return new StationAvailability(
                toInt(m.get("bike_cnt")), toInt(m.get("rack_tot_cnt")), toInt(m.get("shared")),
                String.valueOf(m.getOrDefault("status", "NORMAL")),
                toLong(m.get("updated_at")), stale);
    }

    // FIX: a corrupted Redis value used to throw NumberFormatException and was reported as "Redis unavailable".
    private static int toInt(Object o) {
        if (o == null) return 0;
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long toLong(Object o) {
        if (o == null) return 0L;
        try {
            return Long.parseLong(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
