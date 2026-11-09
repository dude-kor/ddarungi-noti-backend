package kr.ddarungi.noti.service;

import kr.ddarungi.noti.cache.StationAvailability;
import kr.ddarungi.noti.cache.StationRealtimeService;
import kr.ddarungi.noti.domain.station.StationMaster;
import kr.ddarungi.noti.domain.station.StationMasterRepository;
import kr.ddarungi.noti.seoul.SeoulBikeApiResponse.Row;
import kr.ddarungi.noti.seoul.SeoulApiClient;
import kr.ddarungi.noti.web.dto.ApiDtos.StationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;

/** Station master sync + in-memory search index (~2,700 rows, so filtering in memory is cheap). */
@Slf4j
@Service
@RequiredArgsConstructor
public class StationService {

    private record Indexed(StationMaster station, String chosung) {}

    private final StationMasterRepository repository;
    private final SeoulApiClient client;
    private final StationRealtimeService realtime;

    private volatile List<Indexed> index;

    // ---- master sync ----
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartupIfEmpty() {
        if (repository.count() == 0) {
            try { syncMaster(); } catch (Exception e) { log.warn("Initial station sync failed: {}", e.toString()); }
        }
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void syncDaily() {
        try { syncMaster(); } catch (Exception e) { log.warn("Daily station sync failed: {}", e.toString()); }
    }

    public int syncMaster() {
        List<Row> rows = client.fetchAllStations();
        Map<String, StationMaster> existing = new HashMap<>();
        repository.findAllById(rows.stream().map(Row::stationId).toList())
                .forEach(s -> existing.put(s.getStationId(), s));

        List<StationMaster> toSave = new ArrayList<>(rows.size());
        for (Row r : rows) {
            // FIX: one malformed row (e.g. an over-long stationId) used to abort the entire sync. Skip and log it.
            StationMaster fresh;
            try {
                fresh = StationMaster.fromOpenApi(r.stationId(), r.stationName(), r.rackCnt(),
                        r.stationLatitude(), r.stationLongitude());
            } catch (IllegalArgumentException e) {
                log.warn("Skipping station row: {}", e.getMessage());
                continue;
            }
            StationMaster old = existing.get(fresh.getStationId());
            if (old == null) {
                toSave.add(fresh);
            } else {
                old.syncFrom(fresh);          // FIX: also refreshes number and status ([점검중] -> CLOSED), keeps good coordinates
                toSave.add(old);
            }
        }
        // FIX: a stable ascending-id order makes every sync lock station_master rows in the same order as any other
        //      writer, so two overlapping batches cannot deadlock each other.
        toSave.sort(Comparator.comparing(StationMaster::getStationId));
        repository.saveAll(toSave);
        index = null;                                                    // rebuild lazily
        log.info("Station master synced: {} stations", toSave.size());
        return toSave.size();
    }

    // ---- search ----
    public List<StationDto> search(String q, int limit) {
        String query = q == null ? "" : q.trim().toLowerCase();
        if (query.isEmpty()) return List.of();
        boolean chosung = HangulChosung.isAllChosung(query);

        List<StationMaster> hits = index().stream()
                .filter(i -> chosung
                        ? i.chosung().contains(query)
                        : i.station().getStationName().toLowerCase().contains(query)
                          || i.station().getStationNo().startsWith(query))
                .map(Indexed::station)
                .sorted(Comparator.comparing((StationMaster s) -> !s.getStationNo().equals(query))
                        .thenComparing(s -> !s.getStationName().startsWith(query))
                        .thenComparing(StationMaster::getStationNo))
                .limit(limit)
                .toList();
        return withAvailability(hits, null);
    }

    /** Stations around a point, nearest first (for the Kakao map markers). */
    public List<StationDto> nearby(double lat, double lng, int radiusM, int limit) {
        record Near(StationMaster s, int dist) {}
        List<Near> near = index().stream()
                .map(Indexed::station)
                .filter(s -> s.getStationLat() != null && s.getStationLng() != null)
                .map(s -> new Near(s, (int) haversine(lat, lng,
                        s.getStationLat().doubleValue(), s.getStationLng().doubleValue())))
                .filter(n -> n.dist() <= radiusM)
                .sorted(Comparator.comparingInt(Near::dist))
                .limit(limit)
                .toList();
        Map<String, StationAvailability> av = realtime.getAvailability(
                near.stream().map(n -> n.s().getStationId()).toList());
        return near.stream()
                .map(n -> StationDto.of(n.s(), av.get(n.s().getStationId()), n.dist()))
                .toList();
    }

    private List<StationDto> withAvailability(List<StationMaster> stations, Integer distance) {
        Map<String, StationAvailability> av = realtime.getAvailability(
                stations.stream().map(StationMaster::getStationId).toList());
        return stations.stream().map(s -> StationDto.of(s, av.get(s.getStationId()), distance)).toList();
    }

    private List<Indexed> index() {
        List<Indexed> local = index;
        if (local == null) {
            local = repository.findAll().stream()
                    .map(s -> new Indexed(s, HangulChosung.extract(s.getStationName())))
                    .toList();
            index = local;
        }
        return local;
    }

    private static double haversine(double lat1, double lng1, double lat2, double lng2) {
        double r = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1), dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }
}
