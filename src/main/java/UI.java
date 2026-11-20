import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.fxmisc.richtext.InlineCssTextArea;
import org.fxmisc.richtext.LineNumberFactory;

public class UI {
    private static final String BACKGROUND_COLOR = "#2b2b2b";
    private static final String PANEL_COLOR = "#1b1b1b";

    private static final String BUTTON_STYLE =
            "-fx-background-color: #3b3b3b; -fx-text-fill: #ffffff;";

    private static final int SIDEBAR_WIDTH = 200;
    private static final int TOOL_BUTTON_WIDTH = 100;
    private static final int TOOL_BUTTON_HEIGHT = 40;

    private final BorderPane root;
    private InlineCssTextArea codeField;

    // Функции: имя, за которым идёт "(" (объявление или вызов)
    private static final Pattern FUNCTIONS =
            Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)[ \\t]*\\(");

    // Типы и классы: имена, начинающиеся с заглавной буквы (String, List, MyClass…)
    private static final Pattern TYPES =
            Pattern.compile("\\b[A-Z][A-Za-z0-9_]*\\b");

    // Ключевые слова основных языков программирования
    private static final Pattern KEYWORDS = Pattern.compile(
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

    private static final Pattern NUMBERS = Pattern.compile(
            "\\b(0[xX][0-9a-fA-F]+|\\d+(\\.\\d+)?([eE][+-]?\\d+)?)\\b"
    );

    public UI() {
        root = new BorderPane();
        root.setStyle(backgroundStyle(BACKGROUND_COLOR));

        root.setTop(createTopPanel());
        root.setLeft(createLeftPanel());
        root.setCenter(createCodeField());
    }

    public Parent getRoot() {
        return root;
    }

    public InlineCssTextArea getCodeField() {
        return codeField;
    }

    private HBox createTopPanel() {
        HBox topPanel = new HBox(createButton("File"), createButton("Edit"));
        topPanel.setStyle(backgroundStyle(BACKGROUND_COLOR));
        return topPanel;
    }

    private VBox createLeftPanel() {
        HBox toolsPanel = new HBox(
                -3,
                createButton("Settings", TOOL_BUTTON_WIDTH, TOOL_BUTTON_HEIGHT),
                createButton("Terminal", TOOL_BUTTON_WIDTH, TOOL_BUTTON_HEIGHT)
        );
        toolsPanel.setStyle(backgroundStyle(PANEL_COLOR));
        toolsPanel.setPrefWidth(SIDEBAR_WIDTH);
        toolsPanel.setAlignment(Pos.BOTTOM_CENTER);

        VBox leftPanel = new VBox(10, toolsPanel);
        leftPanel.setStyle(backgroundStyle(PANEL_COLOR));
        leftPanel.setPrefWidth(SIDEBAR_WIDTH);
        leftPanel.setAlignment(Pos.BOTTOM_LEFT);
        return leftPanel;
    }

    private InlineCssTextArea createCodeField() {
        codeField = new InlineCssTextArea();
        codeField.setStyle(
                "-fx-background-color: " + BACKGROUND_COLOR + ";" +
                "-fx-font-size: 18px;"
        );

        String caretCss = ".styled-text-area .caret { -fx-stroke: #ffffff; }";
        String base64Css = Base64.getEncoder().encodeToString(caretCss.getBytes(StandardCharsets.UTF_8));
        codeField.getStylesheets().add("data:text/css;base64," + base64Css);

        codeField.setWrapText(false);

        codeField.textProperty().addListener((obs, oldText, newText) -> {
            applySyntaxHighlighting();
        });

        // --- 1. АВТОМАТИЧЕСКОЕ ЗАКРЫТИЕ СКОБОК ---
        // Важно: RichTextFX вставляет обычные символы на событии KEY_TYPED,
        // поэтому обрабатываем скобки именно там и гасим это событие.
        // Если вставлять пару на KEY_PRESSED, символ вставится второй раз
        // от обработчика RichTextFX (получалось (() вместо ()).
        codeField.addEventFilter(KeyEvent.KEY_TYPED, event -> {
            String character = event.getCharacter();

            boolean isOpener = character.equals("{") || character.equals("(") || character.equals("[");
            boolean isCloser = character.equals("}") || character.equals(")") || character.equals("]");
            if (!isOpener && !isCloser) return;

            int caretPos = codeField.getCaretPosition();
            String text = codeField.getText();

            if (isOpener) {
                event.consume();

                String closing = character.equals("{") ? "}" : (character.equals("(") ? ")" : "]");

                int selStart = codeField.getSelection().getStart();
                int selEnd = codeField.getSelection().getEnd();

                if (selStart != selEnd) {
                    // Выделенный текст оборачиваем в скобки
                    codeField.replaceText(selStart, selEnd, character + closing);
                    codeField.moveTo(selStart + 1);
                } else if (caretPos < text.length() && text.charAt(caretPos) == closing.charAt(0)) {
                    // Закрывающая скобка уже стоит после курсора — просто перепрыгиваем через неё,
                    // чтобы не получалось {}{, {{} и т.п.
                    codeField.moveTo(caretPos + 1);
                } else {
                    codeField.replaceText(caretPos, caretPos, character + closing);
                    codeField.moveTo(caretPos + 1);
                }
                return;
            }

            // --- 1б. НАБОР ЗАКРЫВАЮЩЕЙ СКОБКИ ПЕРЕД УЖЕ СУЩЕСТВУЮЩЕЙ ---
            if (codeField.getSelection().getLength() == 0
                    && caretPos < text.length()
                    && text.charAt(caretPos) == character.charAt(0)) {
                // Скобка уже после курсора — не вставляем дубликат, а переходим через неё
                event.consume();
                codeField.moveTo(caretPos + 1);
            }
            // Если скобки после курсора нет — НЕ гасим событие,
            // пусть RichTextFX вставит символ сам, как обычно.
        });

        codeField.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            int caretPos = codeField.getCaretPosition();
            String text = codeField.getText();

            // --- 1в. УДАЛЕНИЕ ПАРЫ СКОБОК (Backspace между пустыми скобками) ---
            if (event.getCode() == KeyCode.BACK_SPACE) {
                if (codeField.getSelection().getLength() == 0
                        && caretPos > 0 && caretPos < text.length()) {
                    char before = text.charAt(caretPos - 1);
                    char after = text.charAt(caretPos);
                    if ((before == '{' && after == '}') ||
                        (before == '(' && after == ')') ||
                        (before == '[' && after == ']')) {
                        event.consume();
                        codeField.replaceText(caretPos - 1, caretPos + 1, "");
                        codeField.moveTo(caretPos - 1);
                        return;
                    }
                }
            }

            // --- 2. ПЕРЕНОС СКОБОК НА НОВУЮ СТРОКУ ---
            if (event.getCode() == KeyCode.ENTER) {
                if (caretPos > 0 && caretPos < text.length()) {
                    char charBefore = text.charAt(caretPos - 1);
                    char charAfter = text.charAt(caretPos);

                    if ((charBefore == '{' && charAfter == '}') ||
                        (charBefore == '(' && charAfter == ')') ||
                        (charBefore == '[' && charAfter == ']')) {

                        event.consume(); // Полностью блокируем стандартный Enter

                        String indent = "    "; // 4 пробела
                        // Отступ только между скобками, перед закрывающей — ничего
                        String insertion = "\n" + indent + "\n";

                        codeField.replaceText(caretPos, caretPos, insertion);

                        // Курсор — на пустой строке с отступом между скобками
                        int targetPos = caretPos + 1 + indent.length();
                        codeField.moveTo(targetPos);
                        return;
                    }
                }
            }
        });

        IntFunction<Node> numberFactory = LineNumberFactory.get(codeField);
        codeField.setParagraphGraphicFactory(line -> {
            Node node = numberFactory.apply(line);
            node.setStyle(
                    "-fx-background-color: " + PANEL_COLOR + ";" +
                    "-fx-text-fill: #888888;" +
                    "-fx-padding: 0 10 0 10;"
            );
            return node;
        });

        return codeField;
    }

    private static final int LITERAL_STRING = 0;
    private static final int LITERAL_COMMENT = 1;

    private void applySyntaxHighlighting() {
        String text = codeField.getText();
        if (text.isEmpty()) return;

        codeField.setStyle(0, text.length(), "-fx-fill: #d4d4d4;");

        // Сначала находим строки и комментарии — внутри них код не подсвечиваем
        List<int[]> literals = findLiterals(text);

        // Порядок важен: более поздняя подсветка перекрывает предыдущую.
        // Сначала — функции и типы, затем ключевые слова и числа.
        highlightFunctions(text, literals);
        highlight(text, TYPES, "#4ec9b0", "normal", literals);
        highlight(text, KEYWORDS, "#569cd6", "bold", literals);
        highlight(text, NUMBERS, "#b5cea8", "normal", literals);

        // Строки и комментарии — поверх всего остального
        for (int[] range : literals) {
            codeField.setStyle(range[0], range[1],
                    range[2] == LITERAL_COMMENT
                            ? "-fx-fill: #6a9955; -fx-font-style: italic;"
                            : "-fx-fill: #ce9178;");
        }
    }

    /**
     * Находит диапазоны [start, end, тип] строк и комментариев.
     * Поддерживаются строки в кавычках (двойные, одинарные, обратные)
     * и комментарии: однострочные (// и #) и блочные.
     */
    private static List<int[]> findLiterals(String text) {
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

    private void highlightFunctions(String text, List<int[]> literals) {
        Matcher matcher = FUNCTIONS.matcher(text);
        while (matcher.find()) {
            int start = matcher.start(1);
            int end = matcher.end(1);
            if (overlaps(start, end, literals)) continue;
            codeField.setStyle(start, end, "-fx-fill: #dcdcaa;");
        }
    }

    private void highlight(String text, Pattern pattern, String color, String fontWeight, List<int[]> literals) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            if (overlaps(matcher.start(), matcher.end(), literals)) continue;
            codeField.setStyle(matcher.start(), matcher.end(),
                    "-fx-fill: " + color + "; -fx-font-weight: " + fontWeight + ";");
        }
    }

    private static Button createButton(String text) {
        Button button = new Button(text);
        button.setStyle(BUTTON_STYLE);
        return button;
    }

    private static Button createButton(String text, double width, double height) {
        Button button = createButton(text);
        button.setPrefSize(width, height);
        return button;
    }

    private static String backgroundStyle(String color) {
        return "-fx-background-color: " + color + ";";
    }
}