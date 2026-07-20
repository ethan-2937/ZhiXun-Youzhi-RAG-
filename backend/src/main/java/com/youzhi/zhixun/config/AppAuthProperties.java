package com.youzhi.zhixun.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.auth")
public class AppAuthProperties {
    private Mode mode = Mode.DEMO;
    private String allowedCorpId = "";
    private Duration replayTtl = Duration.ofMinutes(5);
    private Demo demo = new Demo();
    private DingTalk dingtalk = new DingTalk();

    public enum Mode {
        DEMO,
        DINGTALK
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public String getAllowedCorpId() {
        return allowedCorpId;
    }

    public void setAllowedCorpId(String allowedCorpId) {
        this.allowedCorpId = allowedCorpId;
    }

    public Duration getReplayTtl() {
        return replayTtl;
    }

    public void setReplayTtl(Duration replayTtl) {
        this.replayTtl = replayTtl;
    }

    public Demo getDemo() {
        return demo;
    }

    public void setDemo(Demo demo) {
        this.demo = demo;
    }

    public DingTalk getDingtalk() {
        return dingtalk;
    }

    public void setDingtalk(DingTalk dingtalk) {
        this.dingtalk = dingtalk;
    }

    public static class Demo {
        private String userId = "test-user-demo-001";
        private String displayName = "演示用户";
        private String department = "产品体验组";

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getDepartment() {
            return department;
        }

        public void setDepartment(String department) {
            this.department = department;
        }
    }

    public static class DingTalk {
        private String clientId = "";
        private String clientSecret = "";
        private String accessTokenUrl = "https://api.dingtalk.com/v1.0/oauth2/accessToken";
        private String userByCodeUrl = "https://oapi.dingtalk.com/topapi/v2/user/getuserinfo";

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getAccessTokenUrl() {
            return accessTokenUrl;
        }

        public void setAccessTokenUrl(String accessTokenUrl) {
            this.accessTokenUrl = accessTokenUrl;
        }

        public String getUserByCodeUrl() {
            return userByCodeUrl;
        }

        public void setUserByCodeUrl(String userByCodeUrl) {
            this.userByCodeUrl = userByCodeUrl;
        }
    }
}
