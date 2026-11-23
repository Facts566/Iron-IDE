import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.IndexRange;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.fxmisc.richtext.InlineCssTextArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.util.UndoUtils;

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

    // --- состояние файлов ---
    private Path currentFile;
    private String savedText = "";

    // --- вкладки (каждая вкладка хранит свой текст) ---
    private final List<Tab> tabs = new ArrayList<>();
    private Tab activeTab;
    private HBox tabBar;

    // --- дерево проекта в левой панели ---
    private Path projectDir;
    private TreeView<Path> projectTree;
    private ScrollPane projectScroll;
    private Label projectHeaderLabel;
    private Label projectPlaceholder;
    private VBox leftPanel;          // можно скрыть/показать (Ctrl+B)

    // --- наблюдатель изменений файлов на диске (WatchService) ---
    private WatchService watchService;
    private Thread watchThread;
    private final Object watchLock = new Object();
    private final Set<Path> pendingChanged = new HashSet<>();
    private final Set<Path> pendingRemoved = new HashSet<>();
    private final Set<Path> pendingModified = new HashSet<>();
    private boolean flushQueued;

    // --- верхняя панель и выпадающие меню ---
    private VBox topPanel;
    private HBox menuRow;
    private VBox fileMenu;
    private VBox editMenu;
    private Button fileMenuButton;
    private Button editMenuButton;
    private Button saveMenuItem;
    private Button undoMenuItem;
    private Button redoMenuItem;
    private Button cutMenuItem;
    private Button copyMenuItem;
    private Button pasteMenuItem;

    // --- контекстное меню дерева (создать/переименовать/удалить) ---
    private VBox contextMenu;

    // --- панель поиска ---
    private HBox findBar;
    private TextField findField;

    // Стили выпадающих меню и панели поиска (применяются к основной сцене)
    private static final String MENU_CSS =
            ".top-menu-btn {" +
            "    -fx-background-color: #3b3b3b;" +
            "    -fx-background-radius: 0;" +
            "    -fx-background-insets: 0;" +
            "    -fx-text-fill: #ffffff;" +
            "    -fx-padding: 6 16 6 16;" +
            "    -fx-cursor: hand;" +
            "}" +
            ".top-menu-btn:focused {" +
            "    -fx-background-color: #3b3b3b;" +
            "    -fx-background-insets: 0;" +
            "    -fx-border-color: transparent;" +
            "}" +
            ".top-menu-btn:hover { -fx-background-color: #4a4a4a; }" +
            ".top-menu-btn.open { -fx-background-color: #4a4a4a; }" +
            ".sidebar-toggle { -fx-padding: 6 10 6 10; -fx-alignment: center; }" +
            ".menu-dropdown {" +
            "    -fx-background-color: #2d2d2d;" +
            "    -fx-background-radius: 0;" +
            "    -fx-border-color: #454545;" +
            "    -fx-border-radius: 0;" +
            "    -fx-padding: 4 0 4 0;" +
            "    -fx-pref-width: 270;" +
            "}" +
            ".menu-item-btn {" +
            "    -fx-background-color: transparent;" +
            "    -fx-background-radius: 0;" +
            "    -fx-background-insets: 0;" +
            "    -fx-text-fill: #ffffff;" +
            "    -fx-alignment: CENTER_LEFT;" +
            "    -fx-padding: 5 12 5 12;" +
            "    -fx-cursor: hand;" +
            "}" +
            ".menu-item-btn:focused {" +
            "    -fx-background-color: transparent;" +
            "    -fx-background-insets: 0;" +
            "    -fx-border-color: transparent;" +
            "}" +
            ".menu-item-btn:hover { -fx-background-color: #3b3b3b; }" +
            ".menu-item-btn:armed { -fx-background-color: #4e5254; }" +
            ".menu-item-btn .label { -fx-text-fill: #ffffff; }" +
            ".menu-item-btn .menu-shortcut { -fx-text-fill: #9a9a9a; }" +
            ".menu-item-btn:disabled .label { -fx-text-fill: #777777; }" +
            ".menu-item-btn:disabled { -fx-cursor: default; }" +
            ".menu-separator { -fx-background-color: #454545; -fx-pref-height: 1; }" +
            ".find-bar { -fx-background-color: #1b1b1b; -fx-padding: 6 10 6 10; }" +
            ".find-bar .label { -fx-text-fill: #ffffff; }" +
            ".find-field {" +
            "    -fx-background-color: #3b3b3b;" +
            "    -fx-background-radius: 0;" +
            "    -fx-border-color: #555555;" +
            "    -fx-border-radius: 0;" +
            "    -fx-text-fill: #ffffff;" +
            "    -fx-prompt-text-fill: #999999;" +
            "}" +
            ".find-field:focused { -fx-border-color: #569cd6; }" +
            ".find-field.not-found { -fx-border-color: #f44747; }" +
            ".find-btn {" +
            "    -fx-background-color: #3b3b3b;" +
            "    -fx-background-radius: 0;" +
            "    -fx-text-fill: #ffffff;" +
            "    -fx-cursor: hand;" +
            "}" +
            ".find-btn:hover { -fx-background-color: #4a4a4a; }" +
            // --- панель вкладок ---
            ".tab-bar { -fx-background-color: #1e1e1e; -fx-padding: 0; }" +
            ".tab {" +
            "    -fx-background-color: #1e1e1e;" +
            "    -fx-background-radius: 0;" +
            "    -fx-border-color: transparent #333333 transparent transparent;" +
            "    -fx-border-radius: 0;" +
            "    -fx-padding: 0;" +
            "}" +
            ".tab.active {" +
            "    -fx-background-color: " + BACKGROUND_COLOR + ";" +
            "    -fx-border-color: #4a90d9 transparent transparent transparent;" +
            "}" +
            ".tab-btn {" +
            "    -fx-background-color: transparent;" +
            "    -fx-background-radius: 0;" +
            "    -fx-background-insets: 0;" +
            "    -fx-border-color: transparent;" +
            "    -fx-text-fill: #9a9a9a;" +
            "    -fx-alignment: CENTER_LEFT;" +
            "    -fx-padding: 6 4 6 12;" +
            "    -fx-cursor: hand;" +
            "}" +
            ".tab-btn:hover { -fx-text-fill: #dddddd; }" +
            ".tab-btn:focused { -fx-border-color: transparent; }" +
            ".tab.active .tab-btn { -fx-text-fill: #ffffff; }" +
            ".tab-close {" +
            "    -fx-background-color: transparent;" +
            "    -fx-background-radius: 0;" +
            "    -fx-background-insets: 0;" +
            "    -fx-border-color: transparent;" +
            "    -fx-text-fill: #9a9a9a;" +
            "    -fx-padding: 0 10 0 4;" +
            "    -fx-font-size: 12px;" +
            "    -fx-cursor: hand;" +
            "}" +
            ".tab-close:hover { -fx-background-color: #3b3b3b; -fx-text-fill: #ffffff; }" +
            ".tab-close:focused { -fx-border-color: transparent; }" +
            // --- левая панель: папка проекта ---
            ".project-header-row { -fx-background-color: " + PANEL_COLOR + "; -fx-padding: 7 8 7 8; }" +
            ".project-header {" +
            "    -fx-text-fill: #9a9a9a;" +
            "    -fx-font-size: 11px;" +
            "    -fx-font-weight: bold;" +
            "}" +
            ".project-header-row .find-btn { -fx-padding: 1 7 1 7; -fx-font-size: 11px; }" +
            ".project-placeholder {" +
            "    -fx-text-fill: #777777;" +
            "    -fx-font-size: 12px;" +
            "    -fx-text-alignment: center;" +
            "}" +
            ".project-scroll {" +
            "    -fx-background-color: " + PANEL_COLOR + ";" +
            "    -fx-background: " + PANEL_COLOR + ";" +
            "    -fx-border-color: transparent;" +
            "    -fx-padding: 0;" +
            "}" +
            ".project-scroll > .viewport { -fx-background-color: " + PANEL_COLOR + "; }" +
            ".project-scroll:focused { -fx-border-color: transparent; }" +
            // --- дерево файлов ---
            ".tree-view {" +
            "    -fx-background-color: " + PANEL_COLOR + ";" +
            "    -fx-border-color: transparent;" +
            "    -fx-control-inner-background: " + PANEL_COLOR + ";" +
            "    -fx-control-inner-background-alt: " + PANEL_COLOR + ";" +
            "    -fx-selection-bar: #37373d;" +
            "    -fx-selection-bar-non-focused: #2f2f2f;" +
            "    -fx-text-background-color: #ffffff;" +
            "}" +
            ".tree-view:focused { -fx-border-color: transparent; }" +
            ".tree-cell {" +
            "    -fx-background-color: " + PANEL_COLOR + ";" +
            "    -fx-background: " + PANEL_COLOR + ";" +
            "    -fx-text-fill: #cfcfcf;" +
            "    -fx-padding: 2 4 2 4;" +
            "    -fx-font-size: 12px;" +
            "}" +
            ".tree-cell:hover { -fx-background-color: #2a2a2a; -fx-background: #2a2a2a; }" +
            ".tree-cell.dir { -fx-text-fill: #dcb67a; }" +
            ".tree-cell.file { -fx-text-fill: #cfcfcf; }" +
            ".tree-cell:filled:selected {" +
            "    -fx-background: #37373d;" +
            "    -fx-background-color: #37373d;" +
            "    -fx-text-fill: #ffffff;" +
            "}" +
            ".tree-cell > .tree-disclosure-node > .arrow {" +
            "    -fx-background-color: #cccccc;" +
            "}";

    // Стили системных диалогов (открытие/сохранение/подтверждения)
    private static final String DIALOG_CSS =
            ".dialog-pane { -fx-background-color: #2b2b2b; }" +
            ".dialog-pane > .header-panel { -fx-background-color: #2b2b2b; }" +
            ".dialog-pane .label { -fx-text-fill: #ffffff; }" +
            ".dialog-pane .button {" +
            "    -fx-background-color: #3b3b3b;" +
            "    -fx-background-radius: 0;" +
            "    -fx-text-fill: #ffffff;" +
            "}" +
            ".dialog-pane .button:hover { -fx-background-color: #4a4a4a; }" +
            ".dialog-pane .text-field {" +
            "    -fx-background-color: #3b3b3b;" +
            "    -fx-background-radius: 0;" +
            "    -fx-border-color: #555555;" +
            "    -fx-border-radius: 0;" +
            "    -fx-text-fill: #ffffff;" +
            "    -fx-prompt-text-fill: #999999;" +
            "}" +
            ".dialog-pane .text-field:focused { -fx-border-color: #569cd6; }";

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
        root.getStylesheets().add(cssUrl(MENU_CSS));

        // сначала редактор, затем панели (они ссылаются на codeField)
        root.setCenter(createCodeField());
        leftPanel = createLeftPanel();
        root.setLeft(leftPanel);
        root.setBottom(createFindBar());
        // setTop последним: выпадающие меню должны рисоваться поверх
        // боковой панели и кодового поля, а не под ними
        root.setTop(createTopPanel());

        installCloseHandlers();
        installAccelerators();
    }

    public Parent getRoot() {
        return root;
    }

    public InlineCssTextArea getCodeField() {
        return codeField;
    }

    // ================= ВЕРХНЯЯ ПАНЕЛЬ И МЕНЮ =================

    private VBox createTopPanel() {
        fileMenuButton = new Button("File");
        editMenuButton = new Button("Edit");
        fileMenuButton.getStyleClass().add("top-menu-btn");
        editMenuButton.getStyleClass().add("top-menu-btn");

        // кнопка скрытия/показа левой панели (как в VS Code — Ctrl+B)
        Button sidebarToggle = new Button("☰");
        sidebarToggle.getStyleClass().add("top-menu-btn");
        sidebarToggle.getStyleClass().add("sidebar-toggle");
        sidebarToggle.setOnAction(event -> toggleSidebar());

        fileMenu = buildFileMenu();
        editMenu = buildEditMenu();
        setMenuVisible(fileMenu, false);
        setMenuVisible(editMenu, false);

        menuRow = new HBox(sidebarToggle, fileMenuButton, editMenuButton);
        menuRow.setStyle(backgroundStyle(BACKGROUND_COLOR));

        tabBar = createTabBar();

        // Меню — НЕупраляемые дети: при открытии они накладываются поверх
        // содержимого окна и ничего не сдвигают вниз.
        topPanel = new VBox(menuRow, tabBar, fileMenu, editMenu);
        topPanel.setStyle(backgroundStyle(BACKGROUND_COLOR));

        fileMenuButton.setOnAction(event -> toggleMenu(fileMenu, fileMenuButton));
        editMenuButton.setOnAction(event -> toggleMenu(editMenu, editMenuButton));

        // первая (пустая) вкладка
        Tab first = createUntitledTab();
        tabs.add(first);
        activateTab(first);

        return topPanel;
    }

    private VBox buildFileMenu() {
        VBox menu = new VBox();
        menu.getStyleClass().add("menu-dropdown");
        // не растягивать меню на всю ширину окна
        menu.setMaxWidth(Region.USE_PREF_SIZE);

        saveMenuItem = createMenuItem("Save", "Ctrl+S", this::fileSave);

        menu.getChildren().addAll(
                createMenuItem("New", "Ctrl+N", this::fileNew),
                createMenuItem("Open…", "Ctrl+O", this::fileOpen),
                createMenuItem("Open Folder…", "Ctrl+K", this::openProjectFolder),
                createSeparator(),
                saveMenuItem,
                createMenuItem("Save As…", "Ctrl+Shift+S", this::fileSaveAs),
                createSeparator(),
                createMenuItem("Close Tab", "Ctrl+W", this::closeActiveTab),
                createSeparator(),
                createMenuItem("Exit", "Ctrl+Q", this::fileExit)
        );
        return menu;
    }

    private VBox buildEditMenu() {
        VBox menu = new VBox();
        menu.getStyleClass().add("menu-dropdown");
        // не растягивать меню на всю ширину окна
        menu.setMaxWidth(Region.USE_PREF_SIZE);

        undoMenuItem = createMenuItem("Undo", "Ctrl+Z", () -> codeField.undo());
        redoMenuItem = createMenuItem("Redo", "Ctrl+Y", () -> codeField.redo());
        cutMenuItem = createMenuItem("Cut", "Ctrl+X", () -> codeField.cut());
        copyMenuItem = createMenuItem("Copy", "Ctrl+C", () -> codeField.copy());
        pasteMenuItem = createMenuItem("Paste", "Ctrl+V", () -> codeField.paste());

        menu.getChildren().addAll(
                undoMenuItem,
                redoMenuItem,
                createSeparator(),
                cutMenuItem,
                copyMenuItem,
                pasteMenuItem,
                createMenuItem("Delete", "Del", this::deleteAtCaret),
                createSeparator(),
                createMenuItem("Select All", "Ctrl+A", () -> codeField.selectAll()),
                createSeparator(),
                createMenuItem("Find…", "Ctrl+F", this::showFind)
        );
        return menu;
    }

    private Button createMenuItem(String text, String shortcut, Runnable action) {
        Label nameLabel = new Label(text);
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        Label shortcutLabel = new Label(shortcut);
        shortcutLabel.getStyleClass().add("menu-shortcut");

        HBox content = new HBox(nameLabel, shortcutLabel);
        content.setAlignment(Pos.CENTER_LEFT);

        Button item = new Button();
        item.setGraphic(content);
        item.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        item.setMaxWidth(Double.MAX_VALUE);
        item.getStyleClass().add("menu-item-btn");
        item.setOnAction(event -> {
            closeMenus();
            codeField.requestFocus();
            action.run();
        });
        return item;
    }

    private static Region createSeparator() {
        Region separator = new Region();
        separator.getStyleClass().add("menu-separator");
        separator.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(separator, new Insets(4, 10, 4, 10));
        return separator;
    }

    private static void setMenuVisible(VBox menu, boolean visible) {
        menu.setVisible(visible);
        // меню никогда не участвует в раскладке — оно не занимает места
        menu.setManaged(false);
    }

    private void toggleMenu(VBox menu, Button button) {
        boolean show = !menu.isVisible();
        closeMenus();
        if (show) {
            showMenu(menu);
            button.getStyleClass().add("open");
            updateMenuStates();
        }
    }

    /** Показывает меню поверх содержимого окна, не сдвигая его. */
    private void showMenu(VBox menu) {
        menu.setVisible(true);
        menu.applyCss();
        menu.autosize(); // размер строго по содержимому (270px из CSS)

        double y = menuRow.getHeight();
        if (y <= 0) y = menuRow.prefHeight(-1); // первый пульс мог ещё не пройти
        menu.setLayoutX(0);
        menu.setLayoutY(y);

        menu.requestLayout();
        menu.layout(); // раскладываем пункты внутри самого меню
    }

    private void closeMenus() {
        setMenuVisible(fileMenu, false);
        setMenuVisible(editMenu, false);
        fileMenuButton.getStyleClass().remove("open");
        editMenuButton.getStyleClass().remove("open");
        if (contextMenu != null) contextMenu.setVisible(false);
    }

    /** Скрывает/показывает левую панель с файлами проекта (Ctrl+B). */
    private void toggleSidebar() {
        closeMenus();
        boolean show = !leftPanel.isVisible();
        leftPanel.setVisible(show);
        leftPanel.setManaged(show); // редактор сразу занимает освободившуюся ширину
    }

    /** Обновляет доступность пунктов меню в момент открытия. */
    private void updateMenuStates() {
        updateSelectionStates(codeField.getSelection());
        updateUndoRedoStates();

        Clipboard clipboard = Clipboard.getSystemClipboard();
        boolean clipboardHasContent = clipboard.hasString() || clipboard.hasImage()
                || clipboard.hasUrl() || clipboard.hasFiles();
        pasteMenuItem.setDisable(!clipboardHasContent);

        saveMenuItem.setDisable(currentFile == null && !isDirty());
    }

    private void updateSelectionStates(IndexRange selection) {
        boolean empty = selection == null || selection.getLength() == 0;
        cutMenuItem.setDisable(empty);
        copyMenuItem.setDisable(empty);
    }

    private void updateUndoRedoStates() {
        undoMenuItem.setDisable(!codeField.isUndoAvailable());
        redoMenuItem.setDisable(!codeField.isRedoAvailable());
    }

    /** Закрывает меню по клику вне панели и по Escape. */
    private void installCloseHandlers() {
        root.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            Node target = event.getTarget() instanceof Node ? (Node) event.getTarget() : null;
            if (target == null || !isInside(target, topPanel)) {
                closeMenus();
            }
        });

        root.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            boolean anyMenuVisible = fileMenu.isVisible() || editMenu.isVisible()
                    || (contextMenu != null && contextMenu.isVisible());
            if (event.getCode() == KeyCode.ESCAPE && anyMenuVisible) {
                closeMenus();
                event.consume();
            }
        });
    }

    private static boolean isInside(Node node, Node ancestor) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current == ancestor) return true;
        }
        return false;
    }

    // ================= ВКЛАДКИ =================

    /** Открытая вкладка: свой файл, свой текст и своё состояние сохранности. */
    private static final class Tab {
        String title;
        Path path;          // null — безымянный (не сохранённый) файл
        String text = "";
        String savedText = "";
        int caret;
        Button button;      // кнопка на панели вкладок (маркер «•»)

        Tab(String title, Path path) {
            this.title = title;
            this.path = path;
        }
    }

    private static Tab createUntitledTab() {
        return new Tab("Untitled", null);
    }

    private HBox createTabBar() {
        HBox bar = new HBox();
        bar.getStyleClass().add("tab-bar");
        return bar;
    }

    private void rebuildTabBar() {
        tabBar.getChildren().clear();
        for (Tab tab : tabs) {
            tabBar.getChildren().add(createTabNode(tab));
        }
        updateTabBarVisibility();
    }

    /**
     * Панель вкладок показывается только когда есть хоть один реальный файл
     * (или в пустой вкладке есть текст). Одна пустая «Untitled» — это лишняя
     * пустая кнопка файла: её не должно быть видно.
     */
    private void updateTabBarVisibility() {
        if (tabBar == null) return;
        boolean show = tabs.size() > 1
                || activeTab == null
                || activeTab.path != null
                || !codeField.getText().isEmpty();
        tabBar.setVisible(show);
        tabBar.setManaged(show);
    }

    private HBox createTabNode(Tab tab) {
        Button tabButton = new Button(tabTitle(tab));
        tabButton.getStyleClass().add("tab-btn");
        tabButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(tabButton, Priority.ALWAYS);
        tabButton.setOnAction(event -> activateTab(tab));
        tab.button = tabButton;

        Button closeButton = new Button("×");
        closeButton.getStyleClass().add("tab-close");
        closeButton.setOnAction(event -> closeTab(tab));

        HBox node = new HBox(tabButton, closeButton);
        node.getStyleClass().add("tab");
        node.setAlignment(Pos.CENTER_LEFT);
        if (tab == activeTab) node.getStyleClass().add("active");
        return node;
    }

    /** Название вкладки с маркером «•», если есть несохранённые правки. */
    private String tabTitle(Tab tab) {
        boolean dirty = tab == activeTab
                ? isDirty()
                : !Objects.equals(tab.text, tab.savedText);
        return tab.title + (dirty ? " •" : "");
    }

    private void activateTab(Tab tab) {
        if (tab == null || tab == activeTab) return;

        // запоминаем состояние покидаемой вкладки
        if (activeTab != null) {
            activeTab.text = codeField.getText();
            activeTab.savedText = savedText;
            activeTab.path = currentFile;
            activeTab.caret = codeField.getCaretPosition();
        }

        activeTab = tab;
        currentFile = tab.path;
        savedText = tab.savedText;

        codeField.replaceText(tab.text);
        codeField.moveTo(Math.min(tab.caret, tab.text.length()));
        codeField.requestFollowCaret();

        // История отмены своя для каждой активации: после переключения
        // Ctrl+Z не должен возвращать текст чужого файла.
        codeField.getUndoManager().forgetHistory();

        rebuildTabBar();
        updateTitle();
    }

    private void closeActiveTab() {
        closeMenus();
        if (activeTab != null) closeTab(activeTab);
    }

    /** Закрывает вкладку. false — пользователь отказался от закрытия. */
    private boolean closeTab(Tab tab) {
        closeMenus();
        activateTab(tab); // закрываемая вкладка становится активной
        if (!confirmDiscardChanges()) return false;

        int index = tabs.indexOf(tab);
        tabs.remove(tab);

        if (tabs.isEmpty()) {
            Tab fresh = createUntitledTab();
            tabs.add(fresh);
            activeTab = null;
            activateTab(fresh);
        } else {
            activeTab = null;
            activateTab(tabs.get(Math.min(index, tabs.size() - 1)));
        }
        rebuildTabBar();
        codeField.requestFocus();
        return true;
    }

    /** Открывает файл в вкладке (или активирует уже открытую). */
    private void openFileInTab(Path path) {
        closeMenus();

        for (Tab tab : tabs) {
            if (path.equals(tab.path)) {
                activateTab(tab);
                return;
            }
        }

        String content;
        try {
            content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            showError("Не удалось открыть файл", "Ошибка при чтении:\n" + e.getMessage());
            return;
        }

        String title = path.getFileName() == null ? path.toString() : path.getFileName().toString();

        // первым файлом занимаем пустую безымянную вкладку
        if (activeTab != null && activeTab.path == null && codeField.getText().isEmpty()) {
            activeTab.path = path;
            activeTab.title = title;
            activeTab.text = content;
            activeTab.savedText = content;
            activeTab.caret = 0;
            Tab reuse = activeTab;
            activeTab = null;
            activateTab(reuse);
            return;
        }

        Tab tab = new Tab(title, path);
        tab.text = content;
        tab.savedText = content;
        tabs.add(tab);
        activateTab(tab);
    }

    // ================= ДЕЙСТВИЯ МЕНЮ «FILE» =================

    private boolean isDirty() {
        return !codeField.getText().equals(savedText);
    }

    /** Есть ли несохранённые правки хоть в одной вкладке. */
    private boolean anyDirty() {
        if (isDirty()) return true;
        for (Tab tab : tabs) {
            if (tab == activeTab) continue;
            if (!Objects.equals(tab.text, tab.savedText)) return true;
        }
        return false;
    }

    private Window window() {
        Scene scene = codeField.getScene();
        return scene == null ? null : scene.getWindow();
    }

    private void fileNew() {
        closeMenus();
        // повторный New на пустой безымянной вкладке — просто остаёмся на ней
        if (activeTab != null && activeTab.path == null && codeField.getText().isEmpty()) {
            codeField.requestFocus();
            return;
        }
        // старая вкладка никуда не девается — правки в ней сохранены
        Tab tab = createUntitledTab();
        tabs.add(tab);
        activateTab(tab);
    }

    private void fileOpen() {
        closeMenus();
        File selected = createFileChooser("Открыть файл").showOpenDialog(window());
        if (selected == null) return;
        openFileInTab(selected.toPath());
    }

    private boolean fileSave() {
        if (currentFile == null) return fileSaveAs();
        return writeFile(currentFile);
    }

    private boolean fileSaveAs() {
        closeMenus();
        FileChooser chooser = createFileChooser("Сохранить файл");
        if (currentFile != null && currentFile.getFileName() != null) {
            chooser.setInitialFileName(currentFile.getFileName().toString());
        }

        File selected = chooser.showSaveDialog(window());
        if (selected == null) return false;

        currentFile = selected.toPath();
        if (activeTab != null) {
            activeTab.path = currentFile;
            if (currentFile.getFileName() != null) {
                activeTab.title = currentFile.getFileName().toString();
            }
        }
        boolean saved = writeFile(currentFile);
        rebuildTabBar();
        return saved;
    }

    private boolean writeFile(Path path) {
        try {
            Files.write(path, codeField.getText().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            showError("Не удалось сохранить файл", "Ошибка при записи:\n" + e.getMessage());
            return false;
        }
        savedText = codeField.getText();
        if (activeTab != null) activeTab.savedText = savedText;
        updateTitle();
        return true;
    }

    private void fileExit() {
        if (anyDirty()) {
            int choice = askDiscardChanges();
            if (choice == CHOICE_CANCEL) return;
            if (choice == CHOICE_SAVE && !saveAllDirtyTabs()) return;
        }
        closeMenus();
        Window window = window();
        if (window != null) window.hide();
        else Platform.exit();
    }

    /** Сохраняет все вкладки с правками. false — пользователь отменил. */
    private boolean saveAllDirtyTabs() {
        for (Tab tab : new ArrayList<>(tabs)) {
            boolean dirty = tab == activeTab
                    ? isDirty()
                    : !Objects.equals(tab.text, tab.savedText);
            if (!dirty) continue;
            activateTab(tab);
            if (!fileSave()) return false;
        }
        return true;
    }

    private FileChooser createFileChooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);

        if (currentFile != null && currentFile.getParent() != null) {
            File directory = currentFile.getParent().toFile();
            if (directory.isDirectory()) chooser.setInitialDirectory(directory);
        } else if (projectDir != null && Files.isDirectory(projectDir)) {
            chooser.setInitialDirectory(projectDir.toFile());
        }

        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Исходники и текст",
                        "*.java", "*.py", "*.js", "*.ts", "*.c", "*.cpp", "*.h", "*.hpp",
                        "*.cs", "*.go", "*.rs", "*.kt", "*.php", "*.rb", "*.sh", "*.sql",
                        "*.html", "*.css", "*.xml", "*.json", "*.md", "*.txt"),
                new FileChooser.ExtensionFilter("Все файлы", "*.*")
        );
        return chooser;
    }

    private static final int CHOICE_CANCEL = 0;
    private static final int CHOICE_SAVE = 1;
    private static final int CHOICE_DISCARD = 2;

    /** Спрашивает про несохранённые изменения активной вкладки. true — можно продолжать. */
    private boolean confirmDiscardChanges() {
        if (!isDirty()) return true;

        int choice = askDiscardChanges();
        if (choice == CHOICE_CANCEL) return false;
        if (choice == CHOICE_SAVE) return fileSave();
        return true;
    }

    /** Диалог «есть несохранённые изменения». CHOICE_SAVE / CHOICE_DISCARD / CHOICE_CANCEL. */
    private int askDiscardChanges() {
        ButtonType save = new ButtonType("Сохранить", ButtonData.YES);
        ButtonType discard = new ButtonType("Не сохранять", ButtonData.NO);
        ButtonType cancel = new ButtonType("Отмена", ButtonData.CANCEL_CLOSE);

        Alert alert = createAlert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Несохранённые изменения");
        alert.setHeaderText("В файле есть несохранённые изменения.");
        alert.setContentText("Сохранить изменения перед продолжением?");
        alert.getButtonTypes().setAll(save, discard, cancel);

        Optional<ButtonType> result = alert.showAndWait();
        if (!result.isPresent() || result.get() == cancel) return CHOICE_CANCEL;
        if (result.get() == save) return CHOICE_SAVE;
        return CHOICE_DISCARD;
    }

    private Alert createAlert(Alert.AlertType type) {
        Alert alert = new Alert(type);
        Window owner = window();
        if (owner != null) alert.initOwner(owner);

        // тёмное оформление диалога (сцена диалога создаётся при показе)
        alert.setOnShowing(event -> {
            Scene scene = alert.getDialogPane().getScene();
            if (scene != null && !scene.getStylesheets().contains(cssUrl(DIALOG_CSS))) {
                scene.getStylesheets().add(cssUrl(DIALOG_CSS));
            }
        });
        return alert;
    }

    private void showError(String header, String content) {
        Alert alert = createAlert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void updateTitle() {
        updateTabBarVisibility(); // текст появился/исчез — показать/скрыть вкладки
        // имя и маркер «•» на активной вкладке
        if (activeTab != null) {
            if (currentFile != null && currentFile.getFileName() != null) {
                activeTab.title = currentFile.getFileName().toString();
            }
            if (activeTab.button != null) {
                activeTab.button.setText(tabTitle(activeTab));
            }
        }

        Scene scene = codeField.getScene();
        if (scene == null || !(scene.getWindow() instanceof Stage)) return;

        StringBuilder title = new StringBuilder("Iron IDE");
        if (currentFile != null) title.append(" — ").append(currentFile.getFileName());
        if (isDirty()) title.append(" •");
        ((Stage) scene.getWindow()).setTitle(title.toString());
    }

    // ================= ДЕЙСТВИЯ МЕНЮ «EDIT» И ПОИСК =================

    private void deleteAtCaret() {
        if (codeField.getSelection().getLength() > 0) {
            codeField.replaceSelection("");
        } else {
            codeField.deleteNextChar();
        }
    }

    private HBox createFindBar() {
        findField = new TextField();
        findField.setPromptText("Поиск…");
        findField.getStyleClass().add("find-field");
        findField.setPrefWidth(260);
        findField.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                if (event.isShiftDown()) findPrevious();
                else findNext();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                hideFind();
                event.consume();
            }
        });

        HBox bar = new HBox(8,
                new Label("Найти:"),
                findField,
                createSmallButton("↑", this::findPrevious),
                createSmallButton("↓", this::findNext),
                createSmallButton("✕", this::hideFind)
        );
        bar.getStyleClass().add("find-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setVisible(false);
        bar.setManaged(false);

        findBar = bar;
        return bar;
    }

    private static Button createSmallButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("find-btn");
        button.setOnAction(event -> action.run());
        return button;
    }

    private void showFind() {
        closeMenus();
        String selected = codeField.getSelectedText();
        if (selected != null && !selected.isEmpty() && selected.indexOf('\n') < 0) {
            findField.setText(selected);
        }
        findBar.setVisible(true);
        findBar.setManaged(true);
        findField.selectAll();
        findField.requestFocus();
    }

    private void hideFind() {
        findBar.setVisible(false);
        findBar.setManaged(false);
        findField.getStyleClass().remove("not-found");
        codeField.requestFocus();
    }

    private void findNext() {
        find(true);
    }

    private void findPrevious() {
        find(false);
    }

    private void find(boolean forward) {
        String query = findField.getText();
        if (query.isEmpty()) {
            findField.getStyleClass().remove("not-found");
            return;
        }

        String text = codeField.getText().toLowerCase(Locale.ROOT);
        String needle = query.toLowerCase(Locale.ROOT);
        int index;

        if (forward) {
            int from = Math.min(codeField.getSelection().getEnd(), text.length());
            index = text.indexOf(needle, from);
            if (index < 0) index = text.indexOf(needle, 0); // поиск с начала
        } else {
            int start = codeField.getSelection().getStart();
            // ищем строго до начала текущего выделения, иначе — с конца файла
            index = start > 0 ? text.lastIndexOf(needle, start - 1) : -1;
            if (index < 0) index = text.lastIndexOf(needle);
        }

        if (index < 0) {
            findField.getStyleClass().add("not-found");
            return;
        }

        findField.getStyleClass().remove("not-found");
        codeField.selectRange(index, index + needle.length());
        codeField.requestFollowCaret();
    }

    // ================= ГОРЯЧИЕ КЛАВИШИ =================

    private void installAccelerators() {
        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) return;

            newScene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN), this::fileNew);
            newScene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN), this::fileOpen);
            newScene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN), this::openProjectFolder);
            newScene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN), this::closeActiveTab);
            newScene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN), this::fileSave);
            newScene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
                    this::fileSaveAs);
            newScene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN), this::showFind);
            newScene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN), this::fileExit);
            newScene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.B, KeyCombination.SHORTCUT_DOWN), this::toggleSidebar);

            // заголовок окна появляется только после привязки сцены к Stage
            newScene.windowProperty().addListener((obs2, oldWindow, newWindow) -> updateTitle());
            updateTitle();
        });
    }

    private static String cssUrl(String css) {
        return "data:text/css;base64," + Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
    }

    private VBox createLeftPanel() {
        projectHeaderLabel = new Label("PROJECT");
        projectHeaderLabel.getStyleClass().add("project-header");
        projectHeaderLabel.setMaxWidth(Double.MAX_VALUE);

        Button openFolderButton = new Button("Open…");
        openFolderButton.getStyleClass().add("find-btn");
        openFolderButton.setOnAction(event -> openProjectFolder());

        HBox header = new HBox(6, projectHeaderLabel, openFolderButton);
        header.getStyleClass().add("project-header-row");
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(projectHeaderLabel, Priority.ALWAYS);

        projectPlaceholder = new Label("Папка проекта не открыта\n\nФайл → Open Folder…\nили Ctrl+K");
        projectPlaceholder.getStyleClass().add("project-placeholder");
        projectPlaceholder.setMaxWidth(Double.MAX_VALUE);
        // у Control'ов вычисляемый maxHeight равен pref — без явного
        // Double.MAX_VALUE подсказка не растягивается и кнопки снизу «сползают»
        projectPlaceholder.setMaxHeight(Double.MAX_VALUE);
        projectPlaceholder.setAlignment(Pos.CENTER);
        VBox.setVgrow(projectPlaceholder, Priority.ALWAYS);

        projectTree = new TreeView<>();
        projectTree.setShowRoot(false);
        projectTree.setMaxWidth(Double.MAX_VALUE);
        projectTree.setPrefWidth(SIDEBAR_WIDTH);
        projectTree.setCellFactory(view -> new FileTreeCell());
        projectTree.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) openSelectedTreeFile();
        });
        projectTree.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                openSelectedTreeFile();
                event.consume();
            }
        });
        projectTree.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, this::showTreeContextMenu);

        ScrollPane treeScroll = new ScrollPane(projectTree);
        treeScroll.getStyleClass().add("project-scroll");
        treeScroll.setFitToWidth(true);
        treeScroll.setFocusTraversable(false);
        treeScroll.setPannable(true);
        VBox.setVgrow(treeScroll, Priority.ALWAYS);
        treeScroll.setVisible(false);
        treeScroll.setManaged(false);
        projectScroll = treeScroll;

        HBox toolsPanel = new HBox(
                -3,
                createButton("Settings", TOOL_BUTTON_WIDTH, TOOL_BUTTON_HEIGHT),
                createButton("Terminal", TOOL_BUTTON_WIDTH, TOOL_BUTTON_HEIGHT)
        );
        toolsPanel.setStyle(backgroundStyle(PANEL_COLOR));
        toolsPanel.setPrefWidth(SIDEBAR_WIDTH);
        toolsPanel.setAlignment(Pos.BOTTOM_CENTER);

        VBox leftPanel = new VBox(header, projectPlaceholder, treeScroll, toolsPanel);
        leftPanel.setStyle(backgroundStyle(PANEL_COLOR));
        leftPanel.setPrefWidth(SIDEBAR_WIDTH);
        return leftPanel;
    }

    /** Ячейка дерева: папки отдельным цветом, файлы — обычным. */
    private static final class FileTreeCell extends TreeCell<Path> {
        @Override
        protected void updateItem(Path item, boolean empty) {
            super.updateItem(item, empty);

            getStyleClass().remove("dir");
            getStyleClass().remove("file");

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            boolean directory = Files.isDirectory(item);
            Path name = item.getFileName();
            setText(name == null ? item.toString() : name.toString());
            getStyleClass().add(directory ? "dir" : "file");
        }
    }

    // ================= ДЕРЕВО ПРОЕКТА =================

    /** Открывает папку проекта и строит по ней дерево файлов. */
    private void openProjectFolder() {
        closeMenus();

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Открыть папку проекта");

        Path initial = projectDir != null ? projectDir
                : (currentFile != null ? currentFile.getParent() : null);
        if (initial != null && Files.isDirectory(initial)) {
            chooser.setInitialDirectory(initial.toFile());
        }

        File dir = chooser.showDialog(window());
        if (dir == null) return;
        setProjectDir(dir.toPath());
    }

    /** Строит дерево файлов по указанной папке (null — очищает дерево). */
    private void setProjectDir(Path dir) {
        projectDir = dir;

        if (dir == null || !Files.isDirectory(dir)) {
            projectDir = null;
            projectTree.setRoot(null);
            projectHeaderLabel.setText("PROJECT");
            showProjectTree(false);
            startWatching(null);
            return;
        }

        showProjectTree(true);
        String name = dir.getFileName() == null ? dir.toString() : dir.getFileName().toString();
        projectHeaderLabel.setText(name);

        TreeItem<Path> root = createFileItem(dir);
        root.setExpanded(true); // раскрытие подгружает содержимое папки
        projectTree.setRoot(root);
        projectTree.getSelectionModel().clearSelection();
        startWatching(projectDir); // изменения на диске подхватываются в реальном времени
    }

    private void showProjectTree(boolean show) {
        projectPlaceholder.setVisible(!show);
        projectPlaceholder.setManaged(!show);
        projectScroll.setVisible(show);
        projectScroll.setManaged(show);
    }

    /** Создаёт узел дерева; содержимое папки читается при первом раскрытии. */
    private TreeItem<Path> createFileItem(Path path) {
        boolean directory = Files.isDirectory(path);
        TreeItem<Path> item = new TreeItem<>(path) {
            @Override
            public boolean isLeaf() {
                return !directory;
            }
        };
        if (directory) {
            item.expandedProperty().addListener((obs, wasExpanded, expanded) -> {
                if (expanded && item.getChildren().isEmpty()) loadDirectory(item);
            });
        }
        return item;
    }

    private void loadDirectory(TreeItem<Path> item) {
        Path dir = item.getValue();
        List<Path> children = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) children.add(child);
        } catch (IOException e) {
            return; // папку не удалось прочитать — оставляем пустой узел
        }

        children.sort(Comparator
                .comparing((Path p) -> !Files.isDirectory(p)) // сначала папки
                .thenComparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));

        for (Path child : children) {
            item.getChildren().add(createFileItem(child));
        }
    }

    private void openSelectedTreeFile() {
        TreeItem<Path> selected = projectTree.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        Path path = selected.getValue();
        if (path != null && !Files.isDirectory(path)) openFileInTab(path);
    }

    // ================= КОНТЕКТНОЕ МЕНЮ ДЕРЕВА: СОЗДАНИЕ/ПЕРЕИМЕНОВАНИЕ/УДАЛЕНИЕ =================

    /**
     * Показывает контекстное меню дерева в точке вызова (правая кнопка мыши):
     * «New File…», а для файла — ещё «Rename…» и «Delete».
     */
    private void showTreeContextMenu(ContextMenuEvent event) {
        closeMenus();

        if (contextMenu == null) {
            contextMenu = new VBox();
            contextMenu.getStyleClass().add("menu-dropdown");
            contextMenu.setMaxWidth(Region.USE_PREF_SIZE); // ширина строго из CSS (270px)
            contextMenu.setVisible(false);
            contextMenu.setManaged(false); // не участвует в раскладке
            topPanel.getChildren().add(contextMenu); // последним — рисуется поверх
        }

        TreeItem<Path> clicked = itemAtPosition(event.getSceneX(), event.getSceneY());
        if (clicked != null) projectTree.getSelectionModel().select(clicked);

        List<Node> items = new ArrayList<>();
        if (projectDir == null) {
            // папка не открыта — единственный осмысленный пункт
            items.add(createMenuItem("Open Folder…", "Ctrl+K", this::openProjectFolder));
        } else {
            Path clickedPath = clicked == null ? null : clicked.getValue();
            boolean clickedDir = clickedPath != null && Files.isDirectory(clickedPath);
            Path dir = clickedDir ? clickedPath
                    : (clickedPath != null ? clickedPath.getParent() : projectDir);
            Path file = clickedPath != null && !clickedDir ? clickedPath : null;

            items.add(createMenuItem("New File…", "", () -> createFileInteractive(dir)));
            if (file != null) {
                items.add(createSeparator());
                items.add(createMenuItem("Rename…", "", () -> renameInteractive(file)));
                items.add(createMenuItem("Delete", "", () -> deleteInteractive(file)));
            }
        }

        contextMenu.getChildren().setAll(items);
        contextMenu.setVisible(true);
        contextMenu.applyCss();
        contextMenu.autosize(); // размер строго по содержимому

        javafx.geometry.Point2D local = topPanel.sceneToLocal(event.getSceneX(), event.getSceneY());
        double x = local.getX();
        double y = local.getY();
        double limitY = root.getHeight() - topPanel.getLayoutY();
        if (x + contextMenu.getWidth() > root.getWidth()) {
            x = Math.max(0, root.getWidth() - contextMenu.getWidth());
        }
        if (y + contextMenu.getHeight() > limitY) {
            y = Math.max(0, limitY - contextMenu.getHeight());
        }
        contextMenu.setLayoutX(x);
        contextMenu.setLayoutY(y);
        contextMenu.requestLayout();
        contextMenu.layout();
    }

    /** Ячейка дерева (её узел), находящаяся в заданной точке сцены; null — мимо строк. */
    @SuppressWarnings("unchecked")
    private TreeItem<Path> itemAtPosition(double sceneX, double sceneY) {
        for (Node node : projectTree.lookupAll(".tree-cell")) {
            if (!(node instanceof TreeCell)) continue;
            TreeCell<Path> cell = (TreeCell<Path>) node;
            if (cell.isEmpty() || cell.getTreeItem() == null) continue;
            Bounds bounds = node.localToScene(node.getBoundsInLocal());
            if (bounds.contains(sceneX, sceneY)) return cell.getTreeItem();
        }
        return null;
    }

    /** Проверка имени файла. null — имя допустимо, иначе текст ошибки. */
    private static String validateFileName(String name) {
        if (name == null || name.trim().isEmpty()) return "Имя файла не может быть пустым.";
        String trimmed = name.trim();
        if (trimmed.equals(".") || trimmed.equals("..")) return "Недопустимое имя файла.";
        if (trimmed.indexOf('/') >= 0 || trimmed.indexOf('\\') >= 0 || trimmed.indexOf('\0') >= 0) {
            return "Имя не может содержать «/», «\\» и спецсимволы.";
        }
        return null;
    }

    /**
     * Создаёт файл в папке и открывает его во вкладке.
     * Возвращает null при успехе, иначе текст ошибки (без диалога).
     */
    private String createFile(Path dir, String name) {
        String error = validateFileName(name);
        if (error != null) return error;
        if (dir == null || !Files.isDirectory(dir)) return "Папка назначения не найдена.";

        Path file = dir.resolve(name.trim());
        if (Files.exists(file)) return "«" + name.trim() + "» уже существует в этой папке.";

        try {
            Files.createFile(file);
        } catch (IOException e) {
            return "Не удалось создать файл: " + e.getMessage();
        }

        refreshTree(dir);
        openFileInTab(file);
        return null;
    }

    /**
     * Переименовывает файл и обновляет его вкладку в дереве.
     * Возвращает null при успехе (или если имя не изменилось), иначе текст ошибки.
     */
    private String renameFile(Path path, String newName) {
        String error = validateFileName(newName);
        if (error != null) return error;

        Path oldName = path.getFileName();
        if (oldName == null) return "Не удалось определить имя файла.";
        if (newName.trim().equals(oldName.toString())) return null;

        Path parent = path.getParent();
        if (parent == null) return "Не удалось определить папку файла.";
        Path target = parent.resolve(newName.trim());
        if (Files.exists(target)) return "«" + newName.trim() + "» уже существует.";

        try {
            Files.move(path, target);
        } catch (IOException e) {
            return "Не удалось переименовать: " + e.getMessage();
        }

        // обновляем вкладку, если этот файл открыт
        for (Tab tab : tabs) {
            if (path.equals(tab.path)) {
                tab.path = target;
                tab.title = newName.trim();
            }
        }
        if (path.equals(currentFile)) currentFile = target;

        refreshTree(parent);
        rebuildTabBar();
        updateTitle();
        return null;
    }

    /**
     * Удаляет файл (сначала закрывая его вкладку, при необходимости — с вопросом).
     * Возвращает null при успехе или при отмене пользователем, иначе текст ошибки.
     */
    private String deleteFile(Path path) {
        if (path == null || Files.isDirectory(path)) return "Удаление папок не поддерживается.";

        // сначала закрываем вкладку с этим файлом
        for (Tab tab : new ArrayList<>(tabs)) {
            if (path.equals(tab.path) && !closeTab(tab)) return null; // пользователь отказался
        }

        try {
            Files.delete(path);
        } catch (IOException e) {
            return "Не удалось удалить файл: " + e.getMessage();
        }

        refreshTree(path.getParent());
        return null;
    }

    /** Диалоговая обёртка: создать файл (имя вводит пользователь). */
    private void createFileInteractive(Path dir) {
        Optional<String> name = askFileName("Новый файл", "Имя нового файла:", "newfile.txt");
        if (!name.isPresent()) return;
        String error = createFile(dir, name.get());
        if (error != null) showError("Не удалось создать файл", error);
    }

    /** Диалоговая обёртка: переименовать файл. */
    private void renameInteractive(Path path) {
        Optional<String> name = askFileName("Переименовать файл", "Новое имя:",
                path.getFileName() == null ? "" : path.getFileName().toString());
        if (!name.isPresent()) return;
        String error = renameFile(path, name.get());
        if (error != null) showError("Не удалось переименовать файл", error);
    }

    /** Диалоговая обёртка: удалить файл (с подтверждением). */
    private void deleteInteractive(Path path) {
        Alert confirm = createAlert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Удалить файл");
        confirm.setHeaderText("Удалить файл «" + path.getFileName() + "»?");
        confirm.setContentText("Файл будет удалён с диска без возможности вернуть.");
        Optional<ButtonType> answer = confirm.showAndWait();
        if (!answer.isPresent() || answer.get() != ButtonType.OK) return;

        String error = deleteFile(path);
        if (error != null) showError("Не удалось удалить файл", error);
    }

    /** Окно ввода имени файла (тёмное оформление как у остальных диалогов). */
    private Optional<String> askFileName(String title, String header, String initial) {
        TextInputDialog dialog = new TextInputDialog(initial);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        Window owner = window();
        if (owner != null) dialog.initOwner(owner);
        dialog.setOnShowing(event -> {
            Scene scene = dialog.getDialogPane().getScene();
            if (scene != null && !scene.getStylesheets().contains(cssUrl(DIALOG_CSS))) {
                scene.getStylesheets().add(cssUrl(DIALOG_CSS));
            }
        });
        return dialog.showAndWait();
    }

    /** Обновляет содержимое папки в дереве после создания/переименования/удаления. */
    private void refreshTree(Path dir) {
        if (projectDir == null || dir == null || projectTree.getRoot() == null) return;
        TreeItem<Path> item = findTreeItem(projectTree.getRoot(), dir);
        if (item == null) return; // папка ещё не подгружена — подгрузится при раскрытии
        item.getChildren().clear();
        if (item.isExpanded()) loadDirectory(item); // иначе подхватит раскрытие
    }

    private static TreeItem<Path> findTreeItem(TreeItem<Path> item, Path dir) {
        if (item == null || dir == null) return null;
        if (dir.equals(item.getValue())) return item;
        for (TreeItem<Path> child : item.getChildren()) {
            TreeItem<Path> found = findTreeItem(child, dir);
            if (found != null) return found;
        }
        return null;
    }

    // ================= НАБЛЮДЕНИЕ ЗА ФАЙЛАМИ НА ДИСКЕ =================

    /**
     * Следит за папкой проекта в фоне: дерево обновляется в реальном времени,
     * а вкладка удалённого снаружи файла закрывается (dir == null — останавливает).
     */
    private void startWatching(Path dir) {
        stopWatching();
        if (dir == null) return;

        WatchService service;
        try {
            service = FileSystems.getDefault().newWatchService();
            registerAll(dir, service);
        } catch (IOException e) {
            return; // без наблюдателя — просто не будет автообновления
        }

        watchService = service;
        Thread thread = new Thread(() -> watchLoop(service), "iron-ide-project-watch");
        thread.setDaemon(true);
        watchThread = thread;
        thread.start();
    }

    private void stopWatching() {
        WatchService service;
        Thread thread;
        synchronized (watchLock) {
            service = watchService;
            thread = watchThread;
            watchService = null;
            watchThread = null;
            pendingChanged.clear();
            pendingRemoved.clear();
            pendingModified.clear();
            flushQueued = false;
        }
        if (service != null) {
            try {
                service.close(); // поток наблюдения завершится сам
            } catch (IOException ignored) {
                // уже закрыт
            }
        }
        if (thread != null) thread.interrupt();
    }

    /** Регистрирует наблюдение за папкой и всеми её подпапками. */
    private static void registerAll(Path root, WatchService service) {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isDirectory).forEach(p -> {
                try {
                    p.register(service,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_DELETE,
                            StandardWatchEventKinds.ENTRY_MODIFY);
                } catch (IOException | RuntimeException ignored) {
                    // папка исчезла или недоступна
                }
            });
        } catch (IOException | UncheckedIOException ignored) {
            // не удалось обойти дерево — наблюдается только уже зарегистрированное
        }
    }

    /** Цикл фонового потока: ждёт события и объединяет их в одну отрисовку. */
    private void watchLoop(WatchService service) {
        for (;;) {
            WatchKey key;
            try {
                key = service.take();
            } catch (InterruptedException e) {
                return;
            } catch (RuntimeException e) { // ClosedWatchServiceException
                return;
            }

            Set<Path> changed = new HashSet<>();
            Set<Path> removed = new HashSet<>();
            Set<Path> modified = new HashSet<>();

            for (;;) {
                collectEvents(key, service, changed, removed, modified);
                WatchKey next = service.poll(); // всплеск событий — обрабатываем сразу
                if (next == null) break;
                key = next;
            }

            synchronized (watchLock) {
                if (watchService != service) return; // наблюдатель уже заменён
                pendingChanged.addAll(changed);
                pendingRemoved.addAll(removed);
                pendingModified.addAll(modified);
                if (!flushQueued) {
                    flushQueued = true;
                    try {
                        Platform.runLater(this::flushPending);
                    } catch (RuntimeException ignored) {
                        // приложение уже завершается
                    }
                }
            }
        }
    }

    private void collectEvents(WatchKey key, WatchService service,
                               Set<Path> changed, Set<Path> removed, Set<Path> modified) {
        Path watched = (Path) key.watchable();

        for (WatchEvent<?> event : key.pollEvents()) {
            if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;
            if (!(event.context() instanceof Path)) continue;

            Path child = watched.resolve((Path) event.context());
            WatchEvent.Kind<?> kind = event.kind();

            if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                removed.add(child);
            } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                modified.add(child);
            } else if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                // переименование сюда приходит как CREATE (а уход — как DELETE)
                if (Files.isDirectory(child)) registerAll(child, service);
            }
            changed.add(watched);
        }

        if (!key.reset()) {
            removed.add(watched); // сама папка удалена или недоступна
            if (watched.getParent() != null) changed.add(watched.getParent());
        }
    }

    private void flushPending() {
        Set<Path> changed;
        Set<Path> removed;
        Set<Path> modified;
        synchronized (watchLock) {
            changed = new HashSet<>(pendingChanged);
            removed = new HashSet<>(pendingRemoved);
            modified = new HashSet<>(pendingModified);
            pendingChanged.clear();
            pendingRemoved.clear();
            pendingModified.clear();
            flushQueued = false;
        }
        applyFileChanges(changed, removed, modified);
    }

    /**
     * Применяет накопленные изменения диска: закрывает вкладки удалённых
     * файлов, подхватывает внешние правки в чистых вкладках, обновляет дерево.
     */
    private void applyFileChanges(Set<Path> changedDirs, Set<Path> removedPaths,
                                  Set<Path> modifiedFiles) {
        if (projectDir == null) return;

        // 1. файл или папка удалены снаружи — закрываем их вкладки
        for (Tab tab : new ArrayList<>(tabs)) {
            if (tab.path == null) continue;
            for (Path gone : removedPaths) {
                if (tab.path.equals(gone) || tab.path.startsWith(gone)) {
                    closeTab(tab); // чистая — тихо, с правками — с вопросом
                    break;
                }
            }
        }

        // 2. содержимое изменилось снаружи — обновляем чистые вкладки
        for (Path file : modifiedFiles) {
            for (Tab tab : tabs) {
                if (file.equals(tab.path)) reloadIfChanged(tab);
            }
        }

        // 3. дерево файлов — всегда по свежему состоянию диска
        for (Path dir : changedDirs) refreshTree(dir);
    }

    /** Перечитывает файл во вкладке, если он изменился снаружи (и вкладка чистая). */
    private void reloadIfChanged(Tab tab) {
        if (tab.path == null || !Files.isRegularFile(tab.path)) return;

        String content;
        try {
            content = new String(Files.readAllBytes(tab.path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return; // файл исчез — это обработает событие удаления
        }

        if (tab == activeTab) {
            if (content.equals(codeField.getText()) || isDirty()) return; // свои правки не трогаем
            int caret = codeField.getCaretPosition();
            codeField.replaceText(content);
            codeField.moveTo(Math.min(caret, content.length()));
            codeField.requestFollowCaret();
            savedText = content;
            tab.text = content;
            tab.savedText = content;
            updateTitle();
        } else {
            if (content.equals(tab.text) || !Objects.equals(tab.text, tab.savedText)) return;
            tab.text = content;
            tab.savedText = content;
        }
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

        // История Undo/Redo — только по тексту.
        // У InlineCssTextArea preserveStyle = true, из-за чего в историю попадают
        // и стильовые изменения: подсветка синтаксиса засоряла историю
        // и сбрасывала Redo сразу после Undo.
        codeField.setUndoManager(UndoUtils.plainTextUndoManager(codeField));

        codeField.textProperty().addListener((obs, oldText, newText) -> {
            applySyntaxHighlighting();
            updateTitle();
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