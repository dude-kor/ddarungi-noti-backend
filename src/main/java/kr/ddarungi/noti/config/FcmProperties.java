package kr.ddarungi.noti.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fcm")
public record FcmProperties(boolean enabled, String credentialsPath) {
}
