package com.youzhi.zhixun.dingtalk;

public interface DingTalkIdentityClient {
    DingTalkIdentity exchangeAuthorizationCode(String code);
}
