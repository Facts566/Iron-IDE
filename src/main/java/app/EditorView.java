package app;

import java.util.function.IntFunction;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.InlineCssTextArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.util.UndoUtils;

/**
 * Область редактирования кода: само поле, его оформление, номера строк,
 * подсветка, скобки, полоса прокрутки и мини-карта в стиле VS Code.
 */
final class EditorView {
    final InlineCssTextArea area;
    final VirtualizedScrollPane<InlineCssTextArea> scroll;
    final Minimap minimap;
    /** Дополнительные курсоры (Alt+Click, Ctrl+Alt+Up/Down). */
    final MultiCursor multiCursor;
    /** Всё вместе: редактор + мини-карта справа. */
    final BorderPane view;

    private EditorView(InlineCssTextArea area, VirtualizedScrollPane<InlineCssTextArea> scroll,
                       Minimap minimap, MultiCursor multiCursor, BorderPane view) {
        this.area = area;
        this.scroll = scroll;
        this.minimap = minimap;
        this.multiCursor = multiCursor;
        this.view = view;
    }

    /**
     * Создаёт редактор.
     *
     * @param onTextChanged вызывается после любого изменения текста
     */
    static EditorView create(Runnable onTextChanged) {
        InlineCssTextArea codeField = new InlineCssTextArea();
        codeField.setStyle(
                "-fx-background-color: " + Styles.BACKGROUND + ";" +
                "-fx-font-size: 18px;"
        );

        String caretCss = ".styled-text-area .caret { -fx-stroke: #ffffff; }";
        codeField.getStylesheets().add(Styles.cssUrl(caretCss));

        codeField.setWrapText(false);

        // История Undo/Redo — только по тексту.
        // У InlineCssTextArea preserveStyle = true, из-за чего в историю попадают
        // и стильовые изменения: подсветка синтаксиса засоряла историю
        // и сбрасывала Redo сразу после Undo.
        codeField.setUndoManager(UndoUtils.plainTextUndoManager(codeField));

        codeField.textProperty().addListener((obs, oldText, newText) -> onTextChanged.run());

        // Мультикурсор ставим ДО обработки скобок: его фильтр должен первым
        // увидеть ввод, когда активны дополнительные курсоры.
        MultiCursor multiCursor = MultiCursor.install(codeField);
        BracketEditing.install(codeField);

        IntFunction<Node> numberFactory = LineNumberFactory.get(codeField);
        codeField.setParagraphGraphicFactory(line -> {
            Node node = numberFactory.apply(line);
            node.setStyle(
                    "-fx-background-color: " + Styles.PANEL + ";" +
                    "-fx-text-fill: #888888;" +
                    "-fx-padding: 0 10 0 10;"
            );
            return node;
        });

        // Оболочка со скроллбарами: ползунок справа показывает,
        // в каком месте документа находится видимая область (как в VS Code).
        // Оформление полосы прокрутки задаётся в Styles.MENU_CSS.
        VirtualizedScrollPane<InlineCssTextArea> scroll = new VirtualizedScrollPane<>(codeField);

        // Рамки блоков рисуются на прозрачном слое поверх текста.
        // Слой не влияет на разметку и не перехватывает мышь.
        BlockOverlay blockOverlay = new BlockOverlay(codeField, scroll);
        StackPane editorStack = new StackPane(scroll, blockOverlay);
        blockOverlay.widthProperty().bind(editorStack.widthProperty());
        blockOverlay.heightProperty().bind(editorStack.heightProperty());

        Minimap minimap = new Minimap(codeField);
        BorderPane view = new BorderPane(editorStack);
        view.setRight(minimap);

        return new EditorView(codeField, scroll, minimap, multiCursor, view);
    }
}
