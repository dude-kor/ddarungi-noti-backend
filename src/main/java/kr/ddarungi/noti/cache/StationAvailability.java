package kr.ddarungi.noti.cache;

/**
 * Live availability of one station, as stored in the `station:{id}:realtime` hash
 * (bike_cnt, rack_tot_cnt, shared, status, updated_at). {@code stale} marks data served from the
 * snapshot fallback while the Seoul API / circuit breaker is down.
 */
public record StationAvailability(int bikeCnt, int rackTotCnt, int shared, String status,
                                  long updatedAt, boolean stale) {
}
