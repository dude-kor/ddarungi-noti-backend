-- =====================================================================================================================
-- V1__init.sql  -  따릉이 알리미 baseline schema (MySQL 8.0.16+, InnoDB, utf8mb4)
--
-- FIX: the schema used to be produced by `spring.jpa.hibernate.ddl-auto=update`, which cannot drop/alter indexes,
--      silently diverges between environments and ignores CHECK constraints. Flyway now owns the DDL; Hibernate is
--      set to `none`. Entities carry the same @Table/@Index metadata purely as documentation - keep them in sync.
--
-- Locking notes (InnoDB, REPEATABLE READ - the MySQL default):
--  * Dispatcher scan and API reads are plain SELECTs = non-locking snapshot reads: no record or gap locks.
--  * Every FK column has an index with that column FIRST. Without one InnoDB scans (and locks) a wide range of the
--    child table whenever a parent row is deleted/updated, and creates an implicit index of its own.
--  * Bulk device deactivation updates BY PRIMARY KEY in ascending order (DeviceService.deactivateAll): equality on a
--    unique index locks only the matching records (no next-key/gap lock) and a fixed order excludes lock cycles.
--    Never deactivate with `WHERE push_token IN (...)` / ranges: that locks gaps of idx_push_token and blocks
--    concurrent INSERTs of neighbouring tokens.
--  * station_master rows are never deleted (closing = status 'CLOSED'), so child FKs use the default RESTRICT and a
--    daily master sync can never cascade into the schedule tables.
--  * Only device -> schedule/favorite is ON DELETE CASCADE (account removal). A cascading delete locks the child rows
--    through uk_device_slot / uk_device_station - both lead with device_id, so it is a narrow index-range lock.
-- =====================================================================================================================

CREATE TABLE station_master (
    station_id      VARCHAR(16)   NOT NULL,
    station_no      VARCHAR(16)   NOT NULL,
    station_name    VARCHAR(128)  NOT NULL,
    rack_tot_cnt    SMALLINT      NULL,
    station_lat     DECIMAL(10,7) NULL,
    station_lng     DECIMAL(10,7) NULL,
    station_address VARCHAR(255)  NULL,
    status          VARCHAR(16)   NOT NULL DEFAULT 'NORMAL',
    CONSTRAINT pk_station_master PRIMARY KEY (station_id),
    -- FIX: station_no is deliberately NOT unique (temporary stations reuse numbers); it is a lookup index only.
    INDEX idx_station_no   (station_no),
    INDEX idx_station_name (station_name),
    CONSTRAINT chk_station_status CHECK (status IN ('NORMAL', 'CLOSED', 'UNKNOWN')),
    CONSTRAINT chk_station_rack   CHECK (rack_tot_cnt IS NULL OR rack_tot_cnt >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE user_device (
    device_id    BIGINT       NOT NULL AUTO_INCREMENT,
    device_token VARCHAR(64)  NOT NULL,
    push_token   VARCHAR(255) NOT NULL,
    platform     VARCHAR(16)  NOT NULL,
    app_version  VARCHAR(16)  NULL,
    is_active    TINYINT(1)   NOT NULL DEFAULT 1,
    CONSTRAINT pk_user_device PRIMARY KEY (device_id),
    CONSTRAINT uk_device_token UNIQUE (device_token),
    INDEX idx_push_token (push_token),
    CONSTRAINT chk_device_platform CHECK (platform IN ('ANDROID', 'IOS')),
    CONSTRAINT chk_device_active   CHECK (is_active IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE user_alert_schedule (
    schedule_id     BIGINT      NOT NULL AUTO_INCREMENT,
    device_id       BIGINT      NOT NULL,
    slot_type       VARCHAR(16) NOT NULL,
    station_id      VARCHAR(16) NOT NULL,
    notify_time     TIME        NOT NULL,                 -- always HH:mm:00
    notify_days     TINYINT     NOT NULL,                 -- bitmask Mon=1 Tue=2 Wed=4 Thu=8 Fri=16 Sat=32 Sun=64
    threshold_mode  VARCHAR(16) NOT NULL DEFAULT 'ALWAYS',
    threshold_count SMALLINT    NOT NULL DEFAULT 3,
    is_active       TINYINT(1)  NOT NULL DEFAULT 1,
    CONSTRAINT pk_user_alert_schedule PRIMARY KEY (schedule_id),
    -- also serves FK fk_schedule_device (device_id is its leading column)
    CONSTRAINT uk_device_slot UNIQUE (device_id, slot_type),
    -- Dispatcher scan: is_active = 1 AND notify_time = ? AND (notify_days & ?) <> 0.
    -- Equality prefix (is_active, notify_time) selects the minute; notify_days is third so the bit test is evaluated
    -- on the index entry itself (index condition pushdown) before any table row is read. station_id/device_id trail
    -- so the id scan is index-only apart from the join to user_device.
    INDEX idx_scheduler_lookup (is_active, notify_time, notify_days, station_id, device_id),
    -- FK index with station_id FIRST (idx_scheduler_lookup has it fourth, which InnoDB does not accept for the FK).
    INDEX idx_schedule_station (station_id),
    CONSTRAINT fk_schedule_device  FOREIGN KEY (device_id)  REFERENCES user_device (device_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_schedule_station FOREIGN KEY (station_id) REFERENCES station_master (station_id),
    CONSTRAINT chk_schedule_slot      CHECK (slot_type IN ('COMMUTE_IN', 'COMMUTE_OUT')),
    CONSTRAINT chk_schedule_mode      CHECK (threshold_mode IN ('ALWAYS', 'BELOW_N')),
    CONSTRAINT chk_schedule_days      CHECK (notify_days BETWEEN 1 AND 127),
    CONSTRAINT chk_schedule_time      CHECK (SECOND(notify_time) = 0),
    CONSTRAINT chk_schedule_threshold CHECK (threshold_count >= 0),
    CONSTRAINT chk_schedule_active    CHECK (is_active IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE user_favorite_station (
    favorite_id BIGINT      NOT NULL AUTO_INCREMENT,
    device_id   BIGINT      NOT NULL,
    station_id  VARCHAR(16) NOT NULL,
    sort_order  TINYINT     NOT NULL DEFAULT 0,
    CONSTRAINT pk_user_favorite_station PRIMARY KEY (favorite_id),
    -- also serves FK fk_fav_device (device_id is its leading column)
    CONSTRAINT uk_device_station UNIQUE (device_id, station_id),
    INDEX idx_fav_station (station_id),
    CONSTRAINT fk_fav_device  FOREIGN KEY (device_id)  REFERENCES user_device (device_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_fav_station FOREIGN KEY (station_id) REFERENCES station_master (station_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
