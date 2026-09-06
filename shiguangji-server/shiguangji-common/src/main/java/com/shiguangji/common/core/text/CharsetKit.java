package com.shiguangji.common.core.text;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 字符集工具类
 *
 * @author shiguangji
 */
public final class CharsetKit
{
    public static final String UTF_8 = StandardCharsets.UTF_8.name();
    public static final Charset CHARSET_UTF_8 = StandardCharsets.UTF_8;

    private CharsetKit()
    {
    }
}
