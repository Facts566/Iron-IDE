package app;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.fxmisc.richtext.InlineCssTextArea;

/**
 * Подсветка синтаксиса: функции, типы, ключевые слова, числа, строки и комментарии.
 *
 * <p>Поддерживаются строки в кавычках (двойные, одинарные, обратные) и
 * комментарии: однострочные ({@code //} и {@code #}) и блочные.</p>
 */
final class SyntaxHighlighter {
    // Функции: имя, за которым идёт "(" (объявление или вызов)
    static final Pattern FUNCTIONS =
            Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)[ \\t]*\\(");

    // Типы и классы: имена, начинающиеся с заглавной буквы (String, List, MyClass…)
    static final Pattern TYPES =
            Pattern.compile("\\b[A-Z][A-Za-z0-9_]*\\b");

    // Ключевые слова основных языков программирования
    static final Pattern KEYWORDS = Pattern.compile(
            "\\b(" +
            // управляющие конструкции
            "if|else|elsif|elif|fi|for|foreach|while|until|do|done|switch|case|esac|match|when|then|end|begin|of|in|is|as|and|or|not|xor|mod|break|continue|return|goto|loop|repeat|select|yield|await|async|defer|fallthrough|chan|go|debugger|" +
            // обработка исключений
            "try|catch|finally|throw|throws|raise|panic|rescue|ensure|except|rethrow|" +
            // объявление сущностей
            "class|struct|enum|interface|trait|impl|protocol|extension|object|module|namespace|package|import|export|from|use|using|require|include|extends|implements|new|delete|this|self|super|instanceof|typeof|sizeof|decltype|type|data|record|typedef|typealias|alias|template|typename|operator|friend|union|crate|dyn|move|mut|pub|ref|box|" +
            // модификаторы и доступ
            "static|public|private|protected|internal|final|abstract|virtual|override|sealed|open|inline|extern|constexpr|explicit|mutable|partial|readonly|volatile|synchronized|native|transient|thread_local|noexcept|companion|suspend|lateinit|inner|annotation|init|by|register|restrict|" +
            // объявление функций и переменных
            "def|fn|func|function|fun|procedure|sub|val|var|let|const|lambda|pass|del|assert|nonlocal|global|local|my|our|state|declare|with|dim|" +
            // встроенные типы
            "void|int|integer|float|double|char|boolean|bool|string|byte|short|long|unsigned|signed|auto|uint|ulong|decimal|object|dynamic|any|number|symbol|unknown|never|usize|isize|f32|f64|" +
            // литералы
            "true|false|True|False|null|NULL|nil|None|undefined|NaN|Infinity|nothing|unit|iota|" +
            // оболочки (bash и подобные)
            "export|source|alias|shift|eval|exec|trap|wait|unset|declare|" +
            // SQL
            "select|from|where|group|having|order|join|insert|into|values|update|create|database|drop|alter|primary|foreign|references|between|distinct|limit|offset|union|exists|constraint|trigger|procedure|commit|rollback|transaction|grant|revoke|default|" +
            // PHP
            "isset|empty|exit|die" +
            ")\\b"
    );

    static final Pattern NUMBERS = Pattern.compile(
            "\\b(0[xX][0-9a-fA-F]+|\\d+(\\.\\d+)?([eE][+-]?\\d+)?)\\b"
    );

    static final int LITERAL_STRING = 0;
    static final int LITERAL_COMMENT = 1;

    /** Служебные слова, после которых «(» — это не имя функции. */
    private static final Set<String> NOT_FUNCTION_CALLS = Set.of(
            "if", "for", "foreach", "while", "switch", "catch", "synchronized",
            "lock", "return", "new", "throw", "else", "do", "try", "using",
            "sizeof", "typeof", "alignof", "decltype", "static_assert", "assert",
            "defined", "template", "requires", "when", "select", "go", "defer",
            "delete", "await", "yield");

    private SyntaxHighlighter() {
    }

    static void apply(InlineCssTextArea area) {
        String text = area.getText();
        if (text.isEmpty()) return;

        area.setStyle(0, text.length(), "-fx-fill: #d4d4d4;");

        // Сначала находим строки и комментарии — внутри них код не подсвечиваем
        List<int[]> literals = findLiterals(text);

        // Порядок важен: более поздняя подсветка перекрывает предыдущую.
        // Функции — последними, чтобы жёлтый цвет не перебивался типами.
        highlight(area, text, TYPES, "#4ec9b0", "normal", literals);
        highlight(area, text, KEYWORDS, "#569cd6", "bold", literals);
        highlight(area, text, NUMBERS, "#b5cea8", "normal", literals);
        highlightFunctions(area, text, literals);

        // Строки и комментарии — поверх всего остального
        for (int[] range : literals) {
            area.setStyle(range[0], range[1],
                    range[2] == LITERAL_COMMENT
                            ? "-fx-fill: #6a9955; -fx-font-style: italic;"
                            : "-fx-fill: #ce9178;");
        }
    }

    /** Находит диапазоны [start, end, тип] строк и комментариев. */
    static List<int[]> findLiterals(String text) {
        List<int[]> ranges = new ArrayList<>();
        int n = text.length();
        int i = 0;

        while (i < n) {
            char c = text.charAt(i);

            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '/') {
                int start = i;
                while (i < n && text.charAt(i) != '\n') i++;
                ranges.add(new int[]{start, i, LITERAL_COMMENT});
            } else if (c == '#') {
                int start = i;
                while (i < n && text.charAt(i) != '\n') i++;
                ranges.add(new int[]{start, i, LITERAL_COMMENT});
            } else if (c == '/' && i + 1 < n && text.charAt(i + 1) == '*') {
                int start = i;
                int end = text.indexOf("*/", i + 2);
                i = end == -1 ? n : end + 2;
                ranges.add(new int[]{start, i, LITERAL_COMMENT});
            } else if (c == '"' || c == '\'' || c == '`') {
                int start = i;
                i++;
                while (i < n && text.charAt(i) != c && text.charAt(i) != '\n') i++;
                if (i < n && text.charAt(i) == c) i++; // закрывающая кавычка
                ranges.add(new int[]{start, i, LITERAL_STRING});
            } else {
                i++;
            }
        }
        return ranges;
    }

    private static boolean overlaps(int start, int end, List<int[]> literals) {
        for (int[] range : literals) {
            if (start < range[1] && end > range[0]) return true;
        }
        return false;
    }

    private static void highlightFunctions(InlineCssTextArea area, String text, List<int[]> literals) {
        Matcher matcher = FUNCTIONS.matcher(text);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (NOT_FUNCTION_CALLS.contains(name)) continue;

            int start = matcher.start(1);
            int end = matcher.end(1);
            if (overlaps(start, end, literals)) continue;

            // Имена функций — жёлтым, без фона (рамку рисует BlockOverlay).
            area.setStyle(start, end, "-fx-fill: #ffd700;");
        }
    }

    private static void highlight(InlineCssTextArea area, String text, Pattern pattern,
                                  String color, String fontWeight, List<int[]> literals) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            if (overlaps(matcher.start(), matcher.end(), literals)) continue;
            area.setStyle(matcher.start(), matcher.end(),
                    "-fx-fill: " + color + "; -fx-font-weight: " + fontWeight + ";");
        }
    }
}
