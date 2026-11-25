package app;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

/**
 * Левая панель проекта: дерево файлов, контекстное меню (создание,
 * переименование, удаление) и наблюдение за изменениями на диске.
 */
final class ProjectPanel {
    /** Действия, которые панель запрашивает у главного окна. */
    interface Host {
        void openFile(Path path);

        /** Обновить вкладку после переименования файла. */
        void fileRenamed(Path oldPath, Path newPath);

        /** Закрыть вкладку файла; false — пользователь отказался. */
        boolean closeTabForFile(Path path);

        /** Файлы/папки удалены с диска — закрыть их вкладки. */
        void filesRemoved(Set<Path> removedPaths);

        /** Файлы изменены на диске — перечитать чистые вкладки. */
        void filesModified(Set<Path> modifiedFiles);

        void closeMenus();

        void focusEditor();
    }

    private static final int SIDEBAR_WIDTH = 200;
    private static final int TOOL_BUTTON_WIDTH = 100;
    private static final int TOOL_BUTTON_HEIGHT = 40;

    private final Host host;
    private final Supplier<Pane> overlaySupplier; // topPanel: контекстное меню поверх всего

    private final VBox node;
    private Path projectDir;
    private final TreeView<Path> projectTree;
    private final ScrollPane projectScroll;
    private final Label projectHeaderLabel;
    private final Label projectPlaceholder;

    // --- контекстное меню дерева (создать/переименовать/удалить) ---
    private VBox contextMenu;

    // --- наблюдатель изменений файлов на диске (WatchService) ---
    private WatchService watchService;
    private Thread watchThread;
    private final Object watchLock = new Object();
    private final Set<Path> pendingChanged = new HashSet<>();
    private final Set<Path> pendingRemoved = new HashSet<>();
    private final Set<Path> pendingModified = new HashSet<>();
    private boolean flushQueued;

    ProjectPanel(Host host, Supplier<Pane> overlaySupplier) {
        this.host = host;
        this.overlaySupplier = overlaySupplier;

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

        projectScroll = new ScrollPane(projectTree);
        projectScroll.getStyleClass().add("project-scroll");
        projectScroll.setFitToWidth(true);
        projectScroll.setFocusTraversable(false);
        projectScroll.setPannable(true);
        VBox.setVgrow(projectScroll, Priority.ALWAYS);
        projectScroll.setVisible(false);
        projectScroll.setManaged(false);

        HBox toolsPanel = new HBox(
                -3,
                createButton("Settings", TOOL_BUTTON_WIDTH, TOOL_BUTTON_HEIGHT),
                createButton("Terminal", TOOL_BUTTON_WIDTH, TOOL_BUTTON_HEIGHT)
        );
        toolsPanel.setStyle(Styles.backgroundStyle(Styles.PANEL));
        toolsPanel.setPrefWidth(SIDEBAR_WIDTH);
        toolsPanel.setAlignment(Pos.BOTTOM_CENTER);

        node = new VBox(header, projectPlaceholder, projectScroll, toolsPanel);
        node.setStyle(Styles.backgroundStyle(Styles.PANEL));
        node.setPrefWidth(SIDEBAR_WIDTH);
        node.setMinWidth(0); // панель можно сузить/свернуть за правый край
    }

    VBox getNode() {
        return node;
    }

    Path getProjectDir() {
        return projectDir;
    }

    void hideContextMenu() {
        if (contextMenu != null) contextMenu.setVisible(false);
    }

    boolean isContextMenuVisible() {
        return contextMenu != null && contextMenu.isVisible();
    }

    /** Открывает папку проекта и строит по ней дерево файлов. */
    void openProjectFolder() {
        host.closeMenus();

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Открыть папку проекта");

        Path initial = projectDir;
        if (initial != null && Files.isDirectory(initial)) {
            chooser.setInitialDirectory(initial.toFile());
        }

        File dir = chooser.showDialog(getWindow());
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

    private Window getWindow() {
        if (node.getScene() == null) return null;
        return node.getScene().getWindow();
    }

    // ================= ДЕРЕВО =================

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
        if (path != null && !Files.isDirectory(path)) host.openFile(path);
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

    // ================= КОНТЕКСТНОЕ МЕНЮ ДЕРЕВА =================

    /**
     * Показывает контекстное меню дерева в точке вызова (правая кнопка мыши):
     * «New File…», а для файла — ещё «Rename…» и «Delete».
     */
    private void showTreeContextMenu(ContextMenuEvent event) {
        host.closeMenus();

        if (contextMenu == null) {
            contextMenu = new VBox();
            contextMenu.getStyleClass().add("menu-dropdown");
            contextMenu.setMaxWidth(Region.USE_PREF_SIZE); // ширина строго из CSS (270px)
            contextMenu.setVisible(false);
            contextMenu.setManaged(false); // не участвует в раскладке
        }
        Pane overlay = overlaySupplier.get();
        if (contextMenu.getParent() == null && overlay != null) {
            overlay.getChildren().add(contextMenu); // последним — рисуется поверх
        }

        TreeItem<Path> clicked = itemAtPosition(event.getSceneX(), event.getSceneY());
        if (clicked != null) projectTree.getSelectionModel().select(clicked);

        List<Node> items = new ArrayList<>();
        if (projectDir == null) {
            // папка не открыта — единственный осмысленный пункт
            items.add(contextItem("Open Folder…", "Ctrl+K", this::openProjectFolder));
        } else {
            Path clickedPath = clicked == null ? null : clicked.getValue();
            boolean clickedDir = clickedPath != null && Files.isDirectory(clickedPath);
            Path dir = clickedDir ? clickedPath
                    : (clickedPath != null ? clickedPath.getParent() : projectDir);
            Path file = clickedPath != null && !clickedDir ? clickedPath : null;

            items.add(contextItem("New File…", "", () -> createFileInteractive(dir)));
            if (file != null) {
                items.add(MenuItems.separator());
                items.add(contextItem("Rename…", "", () -> renameInteractive(file)));
                items.add(contextItem("Delete", "", () -> deleteInteractive(file)));
            }
        }

        contextMenu.getChildren().setAll(items);
        contextMenu.setVisible(true);
        contextMenu.applyCss();
        contextMenu.autosize(); // размер строго по содержимому

        Parent sceneRoot = node.getScene() == null ? null : node.getScene().getRoot();
        if (!(sceneRoot instanceof Region) || overlay == null) return;
        Region root = (Region) sceneRoot;

        javafx.geometry.Point2D local = overlay.sceneToLocal(event.getSceneX(), event.getSceneY());
        double x = local.getX();
        double y = local.getY();
        double limitY = root.getHeight() - overlay.getLayoutY();
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

    private Button contextItem(String text, String shortcut, Runnable action) {
        return MenuItems.item(text, shortcut, () -> {
            host.closeMenus();
            host.focusEditor();
            action.run();
        });
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

    // ================= ФАЙЛОВЫЕ ОПЕРАЦИИ =================

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
        host.openFile(file);
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

        host.fileRenamed(path, target);
        refreshTree(parent);
        return null;
    }

    /**
     * Удаляет файл (сначала закрывая его вкладку, при необходимости — с вопросом).
     * Возвращает null при успехе или при отмене пользователем, иначе текст ошибки.
     */
    private String deleteFile(Path path) {
        if (path == null || Files.isDirectory(path)) return "Удаление папок не поддерживается.";

        // сначала закрываем вкладку с этим файлом
        if (!host.closeTabForFile(path)) return null; // пользователь отказался

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
        Optional<String> name = Dialogs.askFileName(getWindow(),
                "Новый файл", "Имя нового файла:", "newfile.txt");
        if (!name.isPresent()) return;
        String error = createFile(dir, name.get());
        if (error != null) Dialogs.showError(getWindow(), "Не удалось создать файл", error);
    }

    /** Диалоговая обёртка: переименовать файл. */
    private void renameInteractive(Path path) {
        Optional<String> name = Dialogs.askFileName(getWindow(), "Переименовать файл",
                "Новое имя:", path.getFileName() == null ? "" : path.getFileName().toString());
        if (!name.isPresent()) return;
        String error = renameFile(path, name.get());
        if (error != null) Dialogs.showError(getWindow(), "Не удалось переименовать файл", error);
    }

    /** Диалоговая обёртка: удалить файл (с подтверждением). */
    private void deleteInteractive(Path path) {
        Optional<ButtonType> answer = Dialogs.confirm(getWindow(), "Удалить файл",
                "Удалить файл «" + path.getFileName() + "»?",
                "Файл будет удалён с диска без возможности вернуть.");
        if (!answer.isPresent() || answer.get() != ButtonType.OK) return;

        String error = deleteFile(path);
        if (error != null) Dialogs.showError(getWindow(), "Не удалось удалить файл", error);
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
        if (!removedPaths.isEmpty()) host.filesRemoved(removedPaths);

        // 2. содержимое изменилось снаружи — обновляем чистые вкладки
        if (!modifiedFiles.isEmpty()) host.filesModified(modifiedFiles);

        // 3. дерево файлов — всегда по свежему состоянию диска
        for (Path dir : changedDirs) refreshTree(dir);
    }

    // ================= КНОПКИ НИЖНЕЙ ПАНЕЛИ =================

    private static Button createButton(String text, double width, double height) {
        Button button = new Button(text);
        button.setStyle(Styles.BUTTON_STYLE);
        button.setPrefSize(width, height);
        return button;
    }
}
