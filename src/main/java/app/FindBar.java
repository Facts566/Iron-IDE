package app;

import java.util.Locale;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import org.fxmisc.richtext.InlineCssTextArea;

/** Панель поиска по тексту (Ctrl+F) с переходом вперёд/назад. */
final class FindBar {
    private final InlineCssTextArea codeField;
    private final Runnable beforeShow;
    private final HBox bar;
    private final TextField findField;

    /**
     * @param beforeShow вызывается перед показом панели (закрыть меню)
     */
    FindBar(InlineCssTextArea codeField, Runnable beforeShow) {
        this.codeField = codeField;
        this.beforeShow = beforeShow;

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
                hide();
                event.consume();
            }
        });

        bar = new HBox(8,
                new Label("Найти:"),
                findField,
                createSmallButton("↑", this::findPrevious),
                createSmallButton("↓", this::findNext),
                createSmallButton("✕", this::hide)
        );
        bar.getStyleClass().add("find-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setVisible(false);
        bar.setManaged(false);
    }

    Node getNode() {
        return bar;
    }

    void show() {
        beforeShow.run();
        String selected = codeField.getSelectedText();
        if (selected != null && !selected.isEmpty() && selected.indexOf('\n') < 0) {
            findField.setText(selected);
        }
        bar.setVisible(true);
        bar.setManaged(true);
        findField.selectAll();
        findField.requestFocus();
    }

    void hide() {
        bar.setVisible(false);
        bar.setManaged(false);
        findField.getStyleClass().remove("not-found");
        codeField.requestFocus();
    }

    private static Button createSmallButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("find-btn");
        button.setOnAction(event -> action.run());
        return button;
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
}
