package kr.ddarungi.noti.domain.station;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Mapped from Seoul Open API `bikeListHist` (stationId, stationName, rackTotCnt, lat/lng). */
@Entity
// FIX: station_no is NOT unique in Seoul's data (temporary stations reuse numbers, fallback numbers can clash).
//      The old uk_station_no made one duplicate abort the whole 2,700-row saveAll() of the daily sync.
//      It is now a plain lookup index, matching V1__init.sql.
@Table(name = "station_master",
        indexes = {
                @Index(name = "idx_station_no", columnList = "station_no"),
                @Index(name = "idx_station_name", columnList = "station_name")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StationMaster {

    static final int ID_MAX = 16;
    static final int NO_MAX = 16;
    static final int NAME_MAX = 128;

    @Id
    @Column(name = "station_id", length = ID_MAX)
    private String stationId;                     // e.g. "ST-4"

    @Column(name = "station_no", length = NO_MAX, nullable = false)
    private String stationNo;                     // e.g. "102"

    @Column(name = "station_name", length = NAME_MAX, nullable = false)
    private String stationName;                   // e.g. "망원역 1번출구 앞"

    @Column(name = "rack_tot_cnt")
    private Short rackTotCnt;

    @Column(name = "station_lat", precision = 10, scale = 7)
    private BigDecimal stationLat;

    @Column(name = "station_lng", precision = 10, scale = 7)
    private BigDecimal stationLng;

    @Column(name = "station_address")
    private String stationAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private StationStatus status = StationStatus.NORMAL;

    @Builder
    private StationMaster(String stationId, String stationNo, String stationName, Short rackTotCnt,
                          BigDecimal stationLat, BigDecimal stationLng, String stationAddress,
                          StationStatus status) {
        this.stationId = stationId;
        this.stationNo = stationNo;
        this.stationName = stationName;
        this.rackTotCnt = rackTotCnt;
        this.stationLat = stationLat;
        this.stationLng = stationLng;
        this.stationAddress = stationAddress;
        this.status = status == null ? StationStatus.NORMAL : status;
    }

    /**
     * Builds a station from the raw Open API `stationName` ("102. 망원역 1번출구 앞", "[점검중] 102. 망원역", ...).
     *
     * <p>FIX: delegates to {@link StationNameParser} (bounded regexes, no split(), no Integer.parseInt) so that no input
     * can raise ArrayIndexOutOfBoundsException or NumberFormatException; every column value is clamped to its column
     * size so a long or odd name can no longer fail the INSERT.
     *
     * @throws IllegalArgumentException only when {@code stationId} is blank or longer than the PK column
     *                                  (the caller skips such a row instead of aborting the whole sync)
     */
    public static StationMaster fromOpenApi(String stationId, String rawName, int rackTotCnt,
                                            BigDecimal lat, BigDecimal lng) {
        if (stationId == null || stationId.isBlank()) {
            throw new IllegalArgumentException("stationId is required");
        }
        String id = stationId.strip();
        if (id.length() > ID_MAX) {
            throw new IllegalArgumentException("stationId longer than " + ID_MAX + ": " + id);
        }

        StationNameParser.Parsed p = StationNameParser.parse(rawName);
        String no = p.number() != null ? fit(p.number(), NO_MAX) : id;        // no number -> fall back to the id
        String name = p.name().isEmpty() ? id : fit(p.name(), NAME_MAX);      // nothing left -> never store ""

        return StationMaster.builder()
                .stationId(id).stationNo(no).stationName(name)
                .rackTotCnt(clampRack(rackTotCnt)).stationLat(lat).stationLng(lng)
                .status(p.outOfService() ? StationStatus.CLOSED : StationStatus.NORMAL)
                .build();
    }

    /** Syncs mutable attributes on the daily master refresh. */
    public void updateFromOpenApi(String stationName, int rackTotCnt, BigDecimal lat, BigDecimal lng) {
        if (stationName != null && !stationName.isBlank()) this.stationName = fit(stationName.strip(), NAME_MAX);
        this.rackTotCnt = clampRack(rackTotCnt);
        // FIX: an unparseable coordinate (null) must not wipe a good one that is already stored.
        if (lat != null) this.stationLat = lat;
        if (lng != null) this.stationLng = lng;
    }

    /** Full refresh from a freshly parsed copy (number, name, status, rack count, coordinates). */
    public void syncFrom(StationMaster fresh) {
        this.stationNo = fresh.stationNo;
        this.stationName = fresh.stationName;
        this.rackTotCnt = fresh.rackTotCnt;
        this.status = fresh.status;
        if (fresh.stationLat != null) this.stationLat = fresh.stationLat;
        if (fresh.stationLng != null) this.stationLng = fresh.stationLng;
    }

    // FIX: the (short) cast silently wrapped values above 32,767 and kept negatives.
    static Short clampRack(int rackTotCnt) {
        return (short) Math.max(0, Math.min(rackTotCnt, Short.MAX_VALUE));
    }

    /** Truncates to the column size without cutting a surrogate pair in half. */
    static String fit(String s, int max) {
        if (s.length() <= max) return s;
        int end = max;
        if (Character.isHighSurrogate(s.charAt(end - 1))) end--;
        return s.substring(0, end);
    }
}
