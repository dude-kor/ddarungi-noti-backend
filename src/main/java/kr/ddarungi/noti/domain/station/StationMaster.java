package kr.ddarungi.noti.domain.station;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mapped from Seoul Open API `bikeListHist` (stationId, stationName, rackTotCnt, lat/lng). */
@Entity
@Table(name = "station_master",
        uniqueConstraints = @UniqueConstraint(name = "uk_station_no", columnNames = "station_no"),
        indexes = @Index(name = "idx_station_name", columnList = "station_name"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StationMaster {

    private static final Pattern STATION_NO = Pattern.compile("^\\s*(\\d+)\\.\\s*(.+)$");

    @Id
    @Column(name = "station_id", length = 16)
    private String stationId;                     // e.g. "ST-4"

    @Column(name = "station_no", length = 16, nullable = false)
    private String stationNo;                     // e.g. "102"

    @Column(name = "station_name", length = 128, nullable = false)
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

    /** Builds a station from the raw Open API `stationName` ("102. 망원역 1번출구 앞"). */
    public static StationMaster fromOpenApi(String stationId, String rawName, int rackTotCnt,
                                            BigDecimal lat, BigDecimal lng) {
        Matcher m = STATION_NO.matcher(rawName == null ? "" : rawName);
        String no = m.matches() ? m.group(1) : stationId;
        String name = m.matches() ? m.group(2).trim() : (rawName == null ? "" : rawName.trim());
        return StationMaster.builder()
                .stationId(stationId).stationNo(no).stationName(name)
                .rackTotCnt((short) rackTotCnt).stationLat(lat).stationLng(lng)
                .status(StationStatus.NORMAL).build();
    }

    /** Syncs mutable attributes on the daily master refresh. */
    public void updateFromOpenApi(String stationName, int rackTotCnt, BigDecimal lat, BigDecimal lng) {
        this.stationName = stationName;
        this.rackTotCnt = (short) rackTotCnt;
        this.stationLat = lat;
        this.stationLng = lng;
    }
}
