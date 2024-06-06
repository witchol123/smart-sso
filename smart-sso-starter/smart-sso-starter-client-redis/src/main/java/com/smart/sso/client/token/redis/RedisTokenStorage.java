package com.smart.sso.client.token.redis;

import com.smart.sso.base.entity.AccessToken;
import com.smart.sso.base.util.JsonUtils;
import com.smart.sso.client.ClientProperties;
import com.smart.sso.client.token.TokenStorage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * 借鉴CAS
 *
 * @author Joe
 */
public final class RedisTokenStorage extends TokenStorage {

    private static final String ST_TOKEN_KEY = "st_token_key_";
    private static final String TOKEN_ST_KEY = "token_st_key_";

    private StringRedisTemplate redisTemplate;

    public RedisTokenStorage(ClientProperties properties, StringRedisTemplate redisTemplate) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void create(String st, AccessToken accessToken) {
        StWrapper wrapper = new StWrapper(accessToken, System.currentTimeMillis() + accessToken.getExpiresIn() * 1000);
        redisTemplate.opsForValue().set(ST_TOKEN_KEY + st, JsonUtils.toJSONString(wrapper), accessToken.getRefreshExpiresIn(),
                TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(TOKEN_ST_KEY + accessToken.getAccessToken(), st);
    }

    @Override
    public AccessToken getAndRefresh(String st) {
        String str = redisTemplate.opsForValue().get(ST_TOKEN_KEY + st);
        if (StringUtils.isEmpty(str)) {
            return null;
        }
        StWrapper wrapper = JsonUtils.parseObject(str, StWrapper.class);
        // accessToken没过期直接返回
        if (!wrapper.isExpired()) {
            return wrapper.accessToken;
        }

        // accessToken已过期，refreshToken没过期，使用refresh接口刷新
        AccessToken accessToken = refreshToken(wrapper.accessToken.getRefreshToken());
        if (accessToken != null) {
            create(st, accessToken);
            return accessToken;
        }
        return null;
    }

    @Override
    public void removeByServiceTicket(String st) {
        redisTemplate.delete(ST_TOKEN_KEY + st);
    }

    @Override
    public void removeByAccessToken(String accessToken) {
        final String st = redisTemplate.opsForValue().get(TOKEN_ST_KEY + accessToken);
        if (st != null) {
            redisTemplate.delete(TOKEN_ST_KEY + accessToken);
            removeByServiceTicket(st);
        }
    }

    public static class StWrapper {
        private AccessToken accessToken;
        private long expired;

        public StWrapper(){
        }

        public StWrapper(AccessToken accessToken, long expired) {
            super();
            this.accessToken = accessToken;
            this.expired = expired;
        }

        public AccessToken getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(AccessToken accessToken) {
            this.accessToken = accessToken;
        }

        public long getExpired() {
            return expired;
        }

        public void setExpired(long expired) {
            this.expired = expired;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expired;
        }
    }
}
