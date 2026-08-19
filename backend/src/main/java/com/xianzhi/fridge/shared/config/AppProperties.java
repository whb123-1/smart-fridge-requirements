package com.xianzhi.fridge.shared.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String timezone = "Asia/Shanghai";
    private String publicUrl;
    private final Security security = new Security();
    private final RateLimit rateLimit = new RateLimit();
    private final Identity identity = new Identity();

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public String getPublicUrl() { return publicUrl; }
    public void setPublicUrl(String publicUrl) { this.publicUrl = publicUrl; }
    public Security getSecurity() { return security; }
    public RateLimit getRateLimit() { return rateLimit; }
    public Identity getIdentity() { return identity; }

    public static class Security {
        private String jwtIssuer = "xianzhi-api";
        private String jwtSigningKey;
        private Duration accessTtl = Duration.ofMinutes(15);
        private Duration refreshTtl = Duration.ofDays(30);
        private String refreshCookieName = "xianzhi_refresh";
        private boolean refreshCookieSecure;
        private List<String> adminUsernames = new ArrayList<>();

        public String getJwtIssuer() { return jwtIssuer; }
        public void setJwtIssuer(String jwtIssuer) { this.jwtIssuer = jwtIssuer; }
        public String getJwtSigningKey() { return jwtSigningKey; }
        public void setJwtSigningKey(String jwtSigningKey) { this.jwtSigningKey = jwtSigningKey; }
        public Duration getAccessTtl() { return accessTtl; }
        public void setAccessTtl(Duration accessTtl) { this.accessTtl = accessTtl; }
        public Duration getRefreshTtl() { return refreshTtl; }
        public void setRefreshTtl(Duration refreshTtl) { this.refreshTtl = refreshTtl; }
        public String getRefreshCookieName() { return refreshCookieName; }
        public void setRefreshCookieName(String refreshCookieName) { this.refreshCookieName = refreshCookieName; }
        public boolean isRefreshCookieSecure() { return refreshCookieSecure; }
        public void setRefreshCookieSecure(boolean refreshCookieSecure) { this.refreshCookieSecure = refreshCookieSecure; }
        public List<String> getAdminUsernames() { return adminUsernames; }
        public void setAdminUsernames(List<String> adminUsernames) { this.adminUsernames = adminUsernames == null ? new ArrayList<>() : adminUsernames; }
    }

    public static class RateLimit {
        private int loginPerIpPerMinute = 5;
        private int loginPerAccountPer15Minutes = 10;

        public int getLoginPerIpPerMinute() { return loginPerIpPerMinute; }
        public void setLoginPerIpPerMinute(int value) { this.loginPerIpPerMinute = value; }
        public int getLoginPerAccountPer15Minutes() { return loginPerAccountPer15Minutes; }
        public void setLoginPerAccountPer15Minutes(int value) { this.loginPerAccountPer15Minutes = value; }
    }

    public static class Identity {
        private String tombstoneKey = "replace-with-a-development-tombstone-key";
        private Duration deletionRetention = Duration.ofDays(90);
        public String getTombstoneKey() { return tombstoneKey; }
        public void setTombstoneKey(String value) { tombstoneKey = value; }
        public Duration getDeletionRetention() { return deletionRetention; }
        public void setDeletionRetention(Duration value) { deletionRetention = value; }
    }
}
