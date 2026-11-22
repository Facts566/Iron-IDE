import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.application.Platform;
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
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
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

    // --- верхняя панель и выпадающие меню ---
    private VBox topPanel;
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
            ".find-btn:hover { -fx-background-color: #4a4a4a; }";

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
            ".dialog-pane .button:hover { -fx-background-color: #4a4a4a; }";

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
        root.setTop(createTopPanel());
        root.setLeft(createLeftPanel());
        root.setBottom(createFindBar());

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

        fileMenu = buildFileMenu();
        editMenu = buildEditMenu();
        setMenuVisible(fileMenu, false);
        setMenuVisible(editMenu, false);

        HBox row = new HBox(fileMenuButton, editMenuButton);
        row.setStyle(backgroundStyle(BACKGROUND_COLOR));

        VBox menus = new VBox(fileMenu, editMenu);
        menus.setStyle(backgroundStyle(BACKGROUND_COLOR));

        topPanel = new VBox(row, menus);
        topPanel.setStyle(backgroundStyle(BACKGROUND_COLOR));

        fileMenuButton.setOnAction(event -> toggleMenu(fileMenu, fileMenuButton));
        editMenuButton.setOnAction(event -> toggleMenu(editMenu, editMenuButton));

        return topPanel;
    }

    private VBox buildFileMenu() {
        VBox menu = new VBox();
        menu.getStyleClass().add("menu-dropdown");

        saveMenuItem = createMenuItem("Save", "Ctrl+S", this::fileSave);

        menu.getChildren().addAll(
                createMenuItem("New", "Ctrl+N", this::fileNew),
                createMenuItem("Open…", "Ctrl+O", this::fileOpen),
                createSeparator(),
                saveMenuItem,
                createMenuItem("Save As…", "Ctrl+Shift+S", this::fileSaveAs),
                createSeparator(),
                createMenuItem("Exit", "Ctrl+Q", this::fileExit)
        );
        return menu;
    }

    private VBox buildEditMenu() {
        VBox menu = new VBox();
        menu.getStyleClass().add("menu-dropdown");

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
        menu.setManaged(visible);
    }

    private void toggleMenu(VBox menu, Button button) {
        boolean show = !menu.isVisible();
        closeMenus();
        if (show) {
            setMenuVisible(menu, true);
            button.getStyleClass().add("open");
            updateMenuStates();
        }
    }

    private void closeMenus() {
        setMenuVisible(fileMenu, false);
        setMenuVisible(editMenu, false);
        fileMenuButton.getStyleClass().remove("open");
        editMenuButton.getStyleClass().remove("open");
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
            if (event.getCode() == KeyCode.ESCAPE && (fileMenu.isVisible() || editMenu.isVisible())) {
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

    // ================= ДЕЙСТВИЯ МЕНЮ «FILE» =================

    private boolean isDirty() {
        return !codeField.getText().equals(savedText);
    }

    private Window window() {
        Scene scene = codeField.getScene();
        return scene == null ? null : scene.getWindow();
    }

    private void fileNew() {
        if (!confirmDiscardChanges()) return;
        closeMenus();
        currentFile = null;
        savedText = "";
        codeField.replaceText("");
        codeField.moveTo(0);
        updateTitle();
    }

    private void fileOpen() {
        closeMenus();
        File selected = createFileChooser("Открыть файл").showOpenDialog(window());
        if (selected == null) return;
        if (!confirmDiscardChanges()) return;

        Path path = selected.toPath();
        String content;
        try {
            content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            showError("Не удалось открыть файл", "Ошибка при чтении:\n" + e.getMessage());
            return;
        }

        currentFile = path;
        savedText = content;
        codeField.replaceText(content);
        codeField.moveTo(0);
        codeField.requestFollowCaret();
        updateTitle();
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
        return writeFile(currentFile);
    }

    private boolean writeFile(Path path) {
        try {
            Files.write(path, codeField.getText().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            showError("Не удалось сохранить файл", "Ошибка при записи:\n" + e.getMessage());
            return false;
        }
        savedText = codeField.getText();
        updateTitle();
        return true;
    }

    private void fileExit() {
        if (!confirmDiscardChanges()) return;
        closeMenus();
        Window window = window();
        if (window != null) window.hide();
        else Platform.exit();
    }

    private FileChooser createFileChooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);

        if (currentFile != null && currentFile.getParent() != null) {
            File directory = currentFile.getParent().toFile();
            if (directory.isDirectory()) chooser.setInitialDirectory(directory);
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

    /** Спрашивает про несохранённые изменения. true — можно продолжать. */
    private boolean confirmDiscardChanges() {
        if (!isDirty()) return true;

        ButtonType save = new ButtonType("Сохранить", ButtonData.YES);
        ButtonType discard = new ButtonType("Не сохранять", ButtonData.NO);
        ButtonType cancel = new ButtonType("Отмена", ButtonData.CANCEL_CLOSE);

        Alert alert = createAlert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Несохранённые изменения");
        alert.setHeaderText("В файле есть несохранённые изменения.");
        alert.setContentText("Сохранить изменения перед продолжением?");
        alert.getButtonTypes().setAll(save, discard, cancel);

        Optional<ButtonType> result = alert.showAndWait();
        if (!result.isPresent() || result.get() == cancel) return false;
        if (result.get() == save) return fileSave();
        return true;
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
                    new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN), this::fileSave);
            newScene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
                    this::fileSaveAs);
            newScene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN), this::showFind);
            newScene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN), this::fileExit);

            // заголовок окна появляется только после привязки сцены к Stage
            newScene.windowProperty().addListener((obs2, oldWindow, newWindow) -> updateTitle());
            updateTitle();
        });
    }

    private static String cssUrl(String css) {
        return "data:text/css;base64," + Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
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