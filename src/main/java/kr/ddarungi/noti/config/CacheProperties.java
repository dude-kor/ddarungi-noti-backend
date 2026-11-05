package kr.ddarungi.noti.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "cache")
public record CacheProperties(Duration stationRealtimeTtl) {

    public CacheProperties {
        if (stationRealtimeTtl == null) stationRealtimeTtl = Duration.ofSeconds(60);
    }
}
