package app;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.IndexRange;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.fxmisc.richtext.InlineCssTextArea;

/**
 * Главное окно Iron IDE: редактор с вкладками, меню, панель проекта
 * и связывание всех частей приложения.
 */
public class UI implements ProjectPanel.Host {
    private static final double SIDEBAR_WIDTH = 200;
    private static final double SIDEBAR_MIN_WIDTH = 140;
    private static final double SIDEBAR_MAX_WIDTH = 460;
    private static final double SIDEBAR_COLLAPSE_WIDTH = 80;
    private static final double SIDEBAR_HANDLE_WIDTH = 6;

    private final BorderPane root;
    private InlineCssTextArea codeField;
    private EditorView editorView;

    // --- состояние файлов ---
    private Path currentFile;
    private String savedText = "";

    // --- вкладки (каждая вкладка хранит свой текст) ---
    private final List<Tab> tabs = new ArrayList<>();
    private Tab activeTab;
    private HBox tabBar;

    // --- левая панель проекта и панель поиска ---
    private ProjectPanel projectPanel;
    private FindBar findBar;

    // --- левая панель: ширина и сворачивание мышью за правый край ---
    private HBox sidebar;
    private Region sidebarHandle;
    private boolean sidebarCollapsed;
    private double lastSidebarWidth = SIDEBAR_WIDTH;
    private double dragStartX = Double.NaN;
    private double dragStartWidth;

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

    public UI() {
        root = new BorderPane();
        root.setStyle(Styles.backgroundStyle(Styles.BACKGROUND));
        root.getStylesheets().add(Styles.cssUrl(Styles.MENU_CSS));

        // сначала редактор, затем панели (они ссылаются на codeField)
        EditorView editor = EditorView.create(this::onEditorTextChanged);
        editorView = editor;
        codeField = editor.area;
        root.setCenter(editor.view);

        projectPanel = new ProjectPanel(this, () -> topPanel);
        createSidebar();
        root.setLeft(sidebar);

        findBar = new FindBar(codeField, this::closeMenus);
        root.setBottom(findBar.getNode());

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

    private void onEditorTextChanged() {
        SyntaxHighlighter.apply(codeField);
        updateTitle();
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

        menuRow = new HBox(fileMenuButton, editMenuButton);
        menuRow.setStyle(Styles.backgroundStyle(Styles.BACKGROUND));

        tabBar = createTabBar();

        // Меню — НЕупраляемые дети: при открытии они накладываются поверх
        // содержимого окна и ничего не сдвигают вниз.
        topPanel = new VBox(menuRow, tabBar, fileMenu, editMenu);
        topPanel.setStyle(Styles.backgroundStyle(Styles.BACKGROUND));

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
                MenuItems.separator(),
                saveMenuItem,
                createMenuItem("Save As…", "Ctrl+Shift+S", this::fileSaveAs),
                MenuItems.separator(),
                createMenuItem("Close Tab", "Ctrl+W", this::closeActiveTab),
                MenuItems.separator(),
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
                MenuItems.separator(),
                cutMenuItem,
                copyMenuItem,
                pasteMenuItem,
                createMenuItem("Delete", "Del", this::deleteAtCaret),
                MenuItems.separator(),
                createMenuItem("Select All", "Ctrl+A", () -> codeField.selectAll()),
                MenuItems.separator(),
                createMenuItem("Find…", "Ctrl+F", this::showFind)
        );
        return menu;
    }

    private Button createMenuItem(String text, String shortcut, Runnable action) {
        return MenuItems.item(text, shortcut, () -> {
            closeMenus();
            codeField.requestFocus();
            action.run();
        });
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

    @Override
    public void closeMenus() {
        setMenuVisible(fileMenu, false);
        setMenuVisible(editMenu, false);
        fileMenuButton.getStyleClass().remove("open");
        editMenuButton.getStyleClass().remove("open");
        if (projectPanel != null) projectPanel.hideContextMenu();
    }

    // ================= ЛЕВАЯ ПАНЕЛЬ: ШИРИНА И СВОРАЧИВАНИЕ =================

    /**
     * Левая панель + тонкая ручка у её правого края. Панель можно сузить
     * или свернуть перетаскиванием ручки мышью; двойной клик по ручке
     * сворачивает/разворачивает её. Ctrl+B делает то же с клавиатуры.
     */
    private void createSidebar() {
        Region panel = projectPanel.getNode();
        panel.setMinWidth(0);
        panel.setMaxWidth(Double.MAX_VALUE);

        // при сужении панели её содержимое не должно вылезать на редактор
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(panel.widthProperty());
        clip.heightProperty().bind(panel.heightProperty());
        panel.setClip(clip);

        sidebarHandle = new Region();
        sidebarHandle.getStyleClass().add("sidebar-handle");
        sidebarHandle.setMinWidth(SIDEBAR_HANDLE_WIDTH);
        sidebarHandle.setPrefWidth(SIDEBAR_HANDLE_WIDTH);
        sidebarHandle.setMaxWidth(SIDEBAR_HANDLE_WIDTH);
        sidebarHandle.setMaxHeight(Double.MAX_VALUE);
        sidebarHandle.setCursor(Cursor.H_RESIZE);

        sidebarHandle.setOnMousePressed(event -> {
            if (event.getButton() != MouseButton.PRIMARY) return;
            dragStartX = event.getScreenX();
            dragStartWidth = sidebar.getWidth();
            event.consume();
        });
        sidebarHandle.setOnMouseDragged(event -> {
            if (Double.isNaN(dragStartX)) return;
            double total = dragStartWidth + (event.getScreenX() - dragStartX);
            double panelWidth = total - SIDEBAR_HANDLE_WIDTH;
            if (panelWidth < SIDEBAR_COLLAPSE_WIDTH) {
                collapseSidebar();
            } else {
                expandSidebar();
                setSidebarWidth(panelWidth);
            }
            event.consume();
        });
        sidebarHandle.setOnMouseReleased(event -> dragStartX = Double.NaN);
        sidebarHandle.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                if (sidebarCollapsed) expandSidebar();
                else collapseSidebar();
                event.consume();
            }
        });

        sidebar = new HBox(panel, sidebarHandle);
        HBox.setHgrow(panel, Priority.ALWAYS);
        sidebar.setMinWidth(0);
        sidebar.setPrefWidth(SIDEBAR_WIDTH + SIDEBAR_HANDLE_WIDTH);
        sidebar.setMaxWidth(Region.USE_PREF_SIZE);
    }

    private void setSidebarWidth(double width) {
        double clamped = Math.max(SIDEBAR_MIN_WIDTH, Math.min(SIDEBAR_MAX_WIDTH, width));
        lastSidebarWidth = clamped;
        sidebar.setPrefWidth(clamped + SIDEBAR_HANDLE_WIDTH);
    }

    private void collapseSidebar() {
        if (sidebarCollapsed) return;
        sidebarCollapsed = true;

        Node panel = projectPanel.getNode();
        panel.setVisible(false);
        panel.setManaged(false);

        // остаётся только тонкая ручка, за которую панель можно вернуть
        sidebar.setPrefWidth(SIDEBAR_HANDLE_WIDTH);
    }

    private void expandSidebar() {
        if (!sidebarCollapsed) return;
        sidebarCollapsed = false;

        Node panel = projectPanel.getNode();
        panel.setVisible(true);
        panel.setManaged(true);

        setSidebarWidth(lastSidebarWidth);
    }

    /** Скрывает/показывает левую панель с файлами проекта (Ctrl+B). */
    private void toggleSidebar() {
        closeMenus();
        if (sidebarCollapsed) expandSidebar();
        else collapseSidebar();
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
                    || projectPanel.isContextMenuVisible();
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
        File chosen = Dialogs.fileChooser("Открыть файл", currentFile, projectPanel.getProjectDir())
                .showOpenDialog(window());
        if (chosen == null) return;
        openFileInTab(chosen.toPath());
    }

    private boolean fileSave() {
        if (currentFile == null) return fileSaveAs();
        return writeFile(currentFile);
    }

    private boolean fileSaveAs() {
        closeMenus();
        FileChooser chooser = Dialogs.fileChooser("Сохранить файл", currentFile,
                projectPanel.getProjectDir());
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

        Alert alert = Dialogs.createAlert(Alert.AlertType.CONFIRMATION, window());
        alert.setTitle("Несохранённые изменения");
        alert.setHeaderText("В файле есть несохранённые изменения.");
        alert.setContentText("Сохранить изменения перед продолжением?");
        alert.getButtonTypes().setAll(save, discard, cancel);

        Optional<ButtonType> result = alert.showAndWait();
        if (!result.isPresent() || result.get() == cancel) return CHOICE_CANCEL;
        if (result.get() == save) return CHOICE_SAVE;
        return CHOICE_DISCARD;
    }

    private void showError(String header, String content) {
        Dialogs.showError(window(), header, content);
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

    private void showFind() {
        findBar.show();
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

    // ================= ПАНЕЛЬ ПРОЕКТА =================

    private void openProjectFolder() {
        projectPanel.openProjectFolder();
    }

    // Реализация ProjectPanel.Host (действия, которые запрашивает панель проекта)

    @Override
    public void openFile(Path path) {
        openFileInTab(path);
    }

    @Override
    public void fileRenamed(Path oldPath, Path newPath) {
        // обновляем вкладку, если этот файл открыт
        for (Tab tab : tabs) {
            if (oldPath.equals(tab.path)) {
                tab.path = newPath;
                if (newPath.getFileName() != null) tab.title = newPath.getFileName().toString();
            }
        }
        if (oldPath.equals(currentFile)) currentFile = newPath;

        rebuildTabBar();
        updateTitle();
    }

    @Override
    public boolean closeTabForFile(Path path) {
        for (Tab tab : new ArrayList<>(tabs)) {
            if (path.equals(tab.path)) return closeTab(tab);
        }
        return true;
    }

    @Override
    public void filesRemoved(Set<Path> removedPaths) {
        // файл или папка удалены снаружи — закрываем их вкладки
        for (Tab tab : new ArrayList<>(tabs)) {
            if (tab.path == null) continue;
            for (Path gone : removedPaths) {
                if (tab.path.equals(gone) || tab.path.startsWith(gone)) {
                    closeTab(tab); // чистая — тихо, с правками — с вопросом
                    break;
                }
            }
        }
    }

    @Override
    public void filesModified(Set<Path> modifiedFiles) {
        // содержимое изменилось снаружи — обновляем чистые вкладки
        for (Path file : modifiedFiles) {
            for (Tab tab : tabs) {
                if (file.equals(tab.path)) reloadIfChanged(tab);
            }
        }
    }

    @Override
    public void focusEditor() {
        codeField.requestFocus();
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
}
