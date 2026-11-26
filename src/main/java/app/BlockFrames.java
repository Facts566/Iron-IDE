package app;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Поиск блоков кода для отрисовки рамок:
 * <ul>
 *   <li>функции — сплошная серая рамка;</li>
 *   <li>ООП-конструкции ({@code class}, {@code enum}, {@code struct} и т.п.) —
 *       пунктирная серая рамка.</li>
 * </ul>
 *
 * <p>Блок определяется по открывающей фигурной скобке: смотрим на «заголовок»
 * перед ней (текст от предыдущей {@code ;}/{@code &#125;}/{@code &#123;}).
 * Строки и комментарии при этом маскируются, чтобы скобки внутри них не
 * влияли на разбор.</p>
 */
final class BlockFrames {

    /** Один блок кода: диапазон абзацев и тип (ООП или функция). */
    static final class Block {
        final int startParagraph;
        final int endParagraph;
        final boolean oop;

        Block(int startParagraph, int endParagraph, boolean oop) {
            this.startParagraph = startParagraph;
            this.endParagraph = endParagraph;
            this.oop = oop;
        }
    }

    private static final Pattern OOP_HEADER = Pattern.compile(
            "\\b(class|struct|enum|interface|trait|object|record|union" +
            "|protocol|impl|module|namespace|extension)\\b");

    private static final Pattern CALL =
            Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");

    private static final Pattern NEW_BEFORE =
            Pattern.compile("\\bnew\\s*$");

    /** Имена, после которых {@code (} — это не объявление функции. */
    private static final Set<String> NOT_FUNCTION = Set.of(
            "if", "for", "foreach", "while", "switch", "catch", "synchronized",
            "lock", "return", "new", "throw", "else", "do", "try", "case",
            "using", "sizeof", "typeof", "alignof", "decltype", "static_assert",
            "assert", "defined", "template", "requires", "when", "select", "go",
            "defer", "delete", "await", "yield", "super", "this");

    private BlockFrames() {
    }

    /** Возвращает все блоки в тексте (в порядке появления). */
    static List<Block> detect(String text) {
        List<Block> blocks = new ArrayList<>();
        if (text.isEmpty()) return blocks;

        String masked = maskLiterals(text);
        int[] lineStarts = lineStarts(masked);

        int statementStart = 0;
        int n = masked.length();

        for (int i = 0; i < n; i++) {
            char c = masked.charAt(i);
            if (c == ';' || c == '}') {
                statementStart = i + 1;
            } else if (c == '{') {
                int headerStart = firstNonSpace(masked, statementStart, i);
                String header = masked.substring(statementStart, i);

                boolean oop = OOP_HEADER.matcher(header).find();
                boolean function = !oop && isFunctionHeader(header);

                if (oop || function) {
                    int close = matchBrace(masked, i);
                    int endOffset = close < 0 ? n - 1 : close;
                    int startParagraph = paragraphOf(lineStarts, headerStart);
                    int endParagraph = paragraphOf(lineStarts, endOffset);
                    if (endParagraph >= startParagraph) {
                        blocks.add(new Block(startParagraph, endParagraph, oop));
                    }
                }
                statementStart = i + 1;
            }
        }
        return blocks;
    }

    /** Заменяет содержимое строк и комментариев пробелами (переводы строк остаются). */
    private static String maskLiterals(String text) {
        char[] chars = text.toCharArray();
        for (int[] range : SyntaxHighlighter.findLiterals(text)) {
            for (int i = range[0]; i < range[1] && i < chars.length; i++) {
                if (chars[i] != '\n') chars[i] = ' ';
            }
        }
        return new String(chars);
    }

    private static int[] lineStarts(String text) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') starts.add(i + 1);
        }
        int[] result = new int[starts.size()];
        for (int i = 0; i < result.length; i++) result[i] = starts.get(i);
        return result;
    }

    private static int paragraphOf(int[] lineStarts, int offset) {
        int lo = 0;
        int hi = lineStarts.length - 1;
        int answer = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lineStarts[mid] <= offset) {
                answer = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return answer;
    }

    private static int firstNonSpace(String text, int from, int to) {
        for (int i = from; i < to; i++) {
            if (!Character.isWhitespace(text.charAt(i))) return i;
        }
        return from;
    }

    private static int matchBrace(String text, int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /**
     * Является ли заголовок объявлением функции/конструктора, а не вызовом
     * или управляющей конструкцией.
     */
    private static boolean isFunctionHeader(String header) {
        Matcher matcher = CALL.matcher(header);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (NOT_FUNCTION.contains(name)) continue;

            int nameStart = matcher.start(1);
            if (parenDepth(header, nameStart) != 0) continue;

            String before = header.substring(0, nameStart);
            if (before.indexOf('=') >= 0) continue;
            if (NEW_BEFORE.matcher(before).find()) continue;

            int p = nameStart - 1;
            while (p >= 0 && Character.isWhitespace(header.charAt(p))) p--;
            if (p >= 0 && header.charAt(p) == '.') continue;

            if (header.indexOf(')', matcher.end()) < 0) continue;
            return true;
        }
        return false;
    }

    private static int parenDepth(String text, int end) {
        int depth = 0;
        for (int i = 0; i < end; i++) {
            char c = text.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
        }
        return depth;
    }
}
