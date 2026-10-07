package com.github.sparkzxl.oss.utils;

import java.net.URI;
import java.net.URL;

/** OSS URL helpers. Raw path and query encoding must survive domain replacement. */
public class OssUtils {
    public static String replaceHttpDomain(URL url, String accessDomain) {
        return replaceHttpDomain(url.toExternalForm(), accessDomain);
    }

    public static String replaceHttpDomain(String url, String accessDomain) {
        if (accessDomain == null || accessDomain.isEmpty()) {
            return url;
        }
        URI source = URI.create(url);
        URI target = URI.create(accessDomain);
        if (target.getRawAuthority() == null
                || !("http".equalsIgnoreCase(target.getScheme())
                        || "https".equalsIgnoreCase(target.getScheme()))
                || target.getRawUserInfo() != null
                || target.getRawQuery() != null
                || target.getRawFragment() != null) {
            throw new IllegalArgumentException("Invalid OSS access domain");
        }
        String prefix = target.getRawPath();
        if (prefix == null) {
            prefix = "";
        }
        while (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        String path = source.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        return target.getScheme()
                + "://"
                + target.getRawAuthority()
                + prefix
                + path
                + (source.getRawQuery() == null ? "" : "?" + source.getRawQuery())
                + (source.getRawFragment() == null ? "" : "#" + source.getRawFragment());
    }
}
