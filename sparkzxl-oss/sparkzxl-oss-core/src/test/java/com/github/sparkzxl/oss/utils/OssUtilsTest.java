package com.github.sparkzxl.oss.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.net.URL;

public class OssUtilsTest {
    @Test
    public void preservesSignedEncodingAndJoiningSlash() throws Exception {
        String original =
                "http://127.0.0.1:9000/user-sys/a%20b%2Bc.png?X-Amz-Credential=a%2Fb&X-Amz-Signature=abc";
        for (String domain : new String[] {"http://127.0.0.1:9000", "http://127.0.0.1:9000/"}) {
            assertEquals(original, OssUtils.replaceHttpDomain(original, domain));
            assertEquals(original, OssUtils.replaceHttpDomain(new URL(original), domain));
        }
        assertEquals(original, OssUtils.replaceHttpDomain(original, null));
        assertEquals(
                original.replace("http://127.0.0.1:9000", "https://cdn.example.test/base"),
                OssUtils.replaceHttpDomain(original, "https://cdn.example.test/base/"));
    }
}
