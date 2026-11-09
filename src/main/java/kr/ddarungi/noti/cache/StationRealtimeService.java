package kr.ddarungi.noti.cache;

import kr.ddarungi.noti.seoul.SeoulBikeApiResponse.Row;
import kr.ddarungi.noti.seoul.SeoulApiClient;
import kr.ddarungi.noti.seoul.SeoulApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Cache-aside with thundering-herd protection.
 * hit -> return; miss -> one instance takes `lock:sync:seoul_api`, calls Seoul once, pipelines into Redis;
 * everybody else waits on the lock, sees the fresh marker and reads from cache.
 * API failure / open breaker -> stale snapshot. This method never throws.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StationRealtimeService {

    private static final String LOCK_NAME = "lock:sync:seoul_api";

    private final StationRealtimeCache cache;
    private final SeoulApiClient client;
    private final RedissonClient redisson;

    public Map<String, StationAvailability> getAvailability(Collection<String> stationIds) {
        if (stationIds == null || stationIds.isEmpty()) return Map.of();
        List<String> ids = List.copyOf(new LinkedHashSet<>(stationIds));
        Map<String, StationAvailability> result = new HashMap<>();
        try {
            result.putAll(cache.readLive(ids));
            if (result.size() >= ids.size() || cache.isFresh()) return result;   // hit, or station not reported

            try {
                refresh();
                result.putAll(cache.readLive(ids));
            } catch (Exception e) {
                log.warn("Seoul API refresh failed, serving stale snapshot: {}", e.toString());
                List<String> missing = ids.stream().filter(id -> !result.containsKey(id)).toList();
                result.putAll(cache.readSnapshot(missing));
            }
        } catch (Exception e) {
            log.error("Redis unavailable while reading availability", e);
        }
        return result;
    }

    /** Warms the cache for all stations; used by getAvailability on a miss. */
    void refresh() {
        RLock lock = redisson.getLock(LOCK_NAME);
        boolean locked = false;
        try {
            locked = lock.tryLock(3, 20, TimeUnit.SECONDS);
            if (!locked) {
                if (cache.isFresh()) return;                       // another instance finished meanwhile
                throw new IllegalStateException("Could not acquire " + LOCK_NAME);
            }
            if (cache.isFresh()) return;                           // double-check after acquiring
            List<Row> rows = client.fetchAllStations();
            if (rows.isEmpty()) throw new SeoulApiException("Seoul API returned no stations");
            cache.writeAll(rows, System.currentTimeMillis());
            cache.markFresh();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) lock.unlock();
        }
    }
}
