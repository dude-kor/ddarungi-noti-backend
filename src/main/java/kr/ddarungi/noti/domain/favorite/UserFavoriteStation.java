package kr.ddarungi.noti.domain.favorite;

import jakarta.persistence.*;
import kr.ddarungi.noti.domain.device.UserDevice;
import kr.ddarungi.noti.domain.station.StationMaster;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "user_favorite_station",
        uniqueConstraints = @UniqueConstraint(name = "uk_device_station", columnNames = {"device_id", "station_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserFavoriteStation {

    /** Quota shown in SCR-06 ("3 / 5개"). */
    public static final int MAX_PER_DEVICE = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "favorite_id")
    private Long favoriteId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false, foreignKey = @ForeignKey(name = "fk_fav_device"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UserDevice device;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_fav_station"))
    private StationMaster station;

    @Column(name = "sort_order", nullable = false, columnDefinition = "TINYINT NOT NULL DEFAULT 0")
    private int sortOrder;

    @Builder
    private UserFavoriteStation(UserDevice device, StationMaster station, int sortOrder) {
        this.device = device;
        this.station = station;
        this.sortOrder = sortOrder;
    }

    public void changeOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
