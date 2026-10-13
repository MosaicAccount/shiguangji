package com.shiguangji.file.cleanup;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 笔记 / 草稿正文里的 /profile 引用抽取（纯逻辑，不触碰存储与数据库）
 *
 * <p>背景：上传文件的引用既可能是独立列里的一个完整值（封面 / 相册 / 头像），也可能是<b>一段长文本里
 * 嵌着的若干条路径</b>（笔记正文）。孤儿清理只认前者的时代，转存进正文的图片会在下一轮清理中被静默删掉。
 * 本类把后者也认出来。</p>
 *
 * <p><b>为什么以候选集为字典、而不是用正则去猜路径边界</b>（设计 结论 56）：两种错的代价不对称——
 * 误抽只会让某个文件少删一次（安全），漏抽会让被引用的文件被删（丢图）。正则必须猜「路径到哪结束」：
 * 猜窄了在「引用后紧跟中文」「全角括号」这些形状上漏抽，猜宽了会把后面的说明文字吃进路径（同样漏抽）。
 * 这里反过来——从 {@code /profile/} 位置起取一段宽 token，再拿它的<b>长前缀往短试</b>候选集，
 * 首个命中即收。多吃了尾巴不影响命中，候选集里没有的伪引用自然落空。</p>
 *
 * <p><b>已知残余</b>：token 在空白与引号 / 括号 / Markdown 标记 / 中文标点处截断，因此
 * <b>键名里含空格或这些字符的文件，无法从正文文本里抽出来</b>。本功能上传的图片走 uuid 命名
 * （{@code yyyy/MM/dd/<32位hex>.<ext>}），不存在这种键；历史遗留的「原文件名 + 序号」键
 * （封面 / 相册）另有各自的业务列兜底，不依赖正文抽取。</p>
 *
 * @author shiguangji
 */
public final class ProfileRefExtractor
{
    /** 与 Constants.RESOURCE_PREFIX 一致；本类在 shiguangji-file，不反向依赖 common 的常量也可，但保持一致更安全 */
    private static final String PREFIX = "/profile/";

    /**
     * 单条引用 token 的长度上限
     *
     * 真实键（uuid 命名）约 60 字符；截断只为挡住病态输入（正文里一串没有终止符的长文本），
     * 不影响任何合法引用。
     */
    private static final int MAX_TOKEN = 200;

    private ProfileRefExtractor()
    {
    }

    /**
     * 从若干段正文里挑出「确实是候选文件」的引用
     *
     * @param contents 正文集合（笔记 + 草稿；可为 null）
     * @param candidates 参与对账的候选路径（存储中的对象 ∪ 台账），需带 /profile 前缀；可为 null
     * @return candidates 的子集：在正文里被引用到的那些路径
     */
    public static Set<String> selectReferenced(Collection<String> contents, Set<String> candidates)
    {
        Set<String> referenced = new LinkedHashSet<>();
        if (contents == null || candidates == null || candidates.isEmpty())
        {
            return referenced;
        }

        for (String content : contents)
        {
            if (content == null || content.isEmpty())
            {
                continue;
            }
            int from = 0;
            while (from < content.length())
            {
                int start = content.indexOf(PREFIX, from);
                if (start < 0)
                {
                    break;
                }
                int limit = Math.min(content.length(), start + MAX_TOKEN);
                int end = start;
                while (end < limit && isTokenChar(content.charAt(end)))
                {
                    end++;
                }
                if (end > start)
                {
                    addLongestPrefix(content, start, end, candidates, referenced);
                    String token = content.substring(start, end);
                    if (token.indexOf('%') >= 0)
                    {
                        String decoded = decodeQuietly(token);
                        if (decoded != null)
                        {
                            addLongestPrefix(decoded, 0, decoded.length(), candidates, referenced);
                        }
                    }
                }
                // 只前进一个前缀长度：token 内部可能还嵌着第二条引用（两条引用之间没有终止符时），
                // 跳到 token 末尾会把它整条跳过——漏抽在这里等于误删
                from = start + PREFIX.length();
            }
        }
        return referenced;
    }

    /**
     * 从长到短试前缀，首个命中即收
     *
     * 从长到短是为了优先匹配「更长、更具体」的键：正文里 `/profile/a/b.png` 后面若还跟着文字，
     * 短前缀可能恰好命中另一个键；先试长的就不会误配。
     */
    private static void addLongestPrefix(String text, int start, int end, Set<String> candidates, Set<String> out)
    {
        for (int i = end; i > start; i--)
        {
            String prefix = text.substring(start, i);
            if (candidates.contains(prefix))
            {
                out.add(prefix);
                return;
            }
        }
    }

    /** token 里的合法字符：空白、引号、括号、Markdown 标记与中文标点都视为引用的结束 */
    private static boolean isTokenChar(char c)
    {
        if (Character.isWhitespace(c))
        {
            return false;
        }
        return switch (c)
        {
            case '"', '\'', '<', '>', '|', '(', ')', '[', ']', '*', '#', '`', ',', ';', ':', '!', '?',
                    '（', '）', '，', '。', '、', '；', '：', '！', '？', '“', '”', '‘', '’', '《', '》', '【', '】' -> false;
            default -> true;
        };
    }

    private static String decodeQuietly(String token)
    {
        try
        {
            return URLDecoder.decode(token, StandardCharsets.UTF_8);
        }
        catch (IllegalArgumentException e)
        {
            // 残缺的 %xx：原文不合法，按未解码的原样比对即可
            return null;
        }
    }
}
