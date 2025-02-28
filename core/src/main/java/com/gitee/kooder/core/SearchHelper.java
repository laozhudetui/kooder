/**
 * Copyright (c) 2021, OSChina (oschina.net@gmail.com).
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitee.kooder.core;

import com.gitee.kooder.models.CodeLine;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.highlight.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Search Toolbox
 * @author Winter Lau (javayou@gmail.com)
 */
public class SearchHelper {

    private final static Logger log = LoggerFactory.getLogger(SearchHelper.class);

    private final static int MAX_LINE_LENGTH = 256;
    private final static Analyzer highlight_analyzer = AnalyzerFactory.getHighlightInstance();

    private final static Formatter hl_fmt = new SimpleHTMLFormatter("<em class='highlight'>", "</em>");

    public static void main(String[] args) {
        String text = "SQL:SELECT * FROM osc_users WHERE gender = 'M'";
        System.out.println("RESULT:"+hlcode(text, "SELECT *"));
    }

    /**
     * 关键字切分
     *
     * @param sentence 要分词的句子
     * @return 返回分词结果
     */
    public static List<String> splitKeywords(String sentence) {
        return AnalyzerFactory.splitKeywords(sentence);
    }

    /**
     * 重整搜索关键短语
     *
     * @param key
     * @return
     */
    public static String cleanupKey(String key) {
        return String.join(" ", splitKeywords(key));
    }

    /**
     * 对一段文本执行语法高亮处理
     *
     * @param text 要处理高亮的文本
     * @param key  高亮的关键字
     * @return 返回格式化后的HTML文本
     */
    public static String highlight(String text, String key) {
        return highlight(text, key, Integer.MAX_VALUE);
    }

    /**
     * 对一段文本执行语法高亮处理
     *
     * @param text 要处理高亮的文本
     * @param key  高亮的关键字
     * @parma maxLen
     * @return 返回格式化后的HTML文本
     */
    public static String highlight(String text, String key, int maxLen) {
        if (StringUtils.isBlank(key) || StringUtils.isBlank(text))
            return text;

        key = QueryParser.escape(key);

        String result = null;
        if(maxLen < text.length())
            text = StringUtils.left(text, maxLen);
        try {
            QueryParser parser = new QueryParser(null, highlight_analyzer);
            Query query = parser.parse(key);
            QueryScorer scorer = new QueryScorer(query);
            Highlighter hig = new Highlighter(hl_fmt, scorer);
            TokenStream tokens = highlight_analyzer.tokenStream(null, new StringReader(text));
            String[] fragments = hig.getBestFragments(tokens, text, hig.getMaxDocCharsToAnalyze());
            result = String.join( "", fragments);
        } catch (Exception e) {
            log.warn("Unabled to hightlight text("+key+"): " + text, e);
        }

        return StringUtils.isBlank(result) ? text : result;
    }

    /**
     * 对一段代码执行语法高亮处理
     *
     * @param text 要处理高亮的文本
     * @param key  高亮的关键字
     * @return 返回格式化后的HTML文本
     */
    public static String hlcode(String text, String key) {
        key = QueryParser.escape(key);
        String result = AnalyzerFactory.getCodeAnalyzer().highlight(text, key);
        if(StringUtils.isBlank(result))
            result = text;
        return result;
    }

    /**
     * 高亮标识出源码中的关键字
     * @param code
     * @param key
     * @param maxLines
     * @return
     */
    public static List<CodeLine> hl_lines(String code, String key, int maxLines) {
        if(StringUtils.isBlank(code) || StringUtils.isBlank(key))
            return null;

        List<CodeLine> codeLines = new ArrayList<>();
        key = QueryParser.escape(key);

        try {
            String[] lines = StringUtils.split(code, "\r\n");
            for (int i = 0; i < lines.length && codeLines.size() < maxLines; i++) {
                if (StringUtils.isBlank(lines[i]))
                    continue;
                if (StringUtils.trim(lines[i]).length() < key.length())
                    continue;

                String line = html(StringUtils.abbreviate(lines[i], MAX_LINE_LENGTH));
                line = AnalyzerFactory.getCodeAnalyzer().highlight(line, key);

                if(StringUtils.isNotBlank(line)) {
                    codeLines.add(new CodeLine(i+1, line));
                }
            }
            //补充点内容，免得看起来太干巴
            int minLines = maxLines / 2 ;
            if(codeLines.size() < minLines) {
                int lastLineNo = (codeLines.size() == 0) ? 0 : codeLines.get(codeLines.size() - 1).getLine();
                for (int i = lastLineNo + 1; i <= lines.length; i++) {
                    codeLines.add(new CodeLine(i, html(StringUtils.abbreviate(lines[i-1], MAX_LINE_LENGTH))));
                    if(codeLines.size() >= minLines)
                        break;
                }
            }

        } catch (Exception e) {
            log.warn("Failed to highlighter code line", e);
        }
        return codeLines;
    }
    /**
     * 格式化HTML文本
     *
     * @param content
     * @return
     */
    public static String html(String content) {
        if (content == null) return "";
        content = StringUtils.replace(content, "<", "&lt;");
        content = StringUtils.replace(content, ">", "&gt;");
        return content;
    }
}
