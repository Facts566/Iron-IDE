package app;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javafx.geometry.Point2D;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.fxmisc.richtext.CaretNode;
import org.fxmisc.richtext.InlineCssTextArea;
import org.fxmisc.richtext.model.TwoDimensional;
import org.fxmisc.richtext.model.TwoDimensional.Bias;

/**
 * Мультикурсор редактора в духе VS Code.
 *
 * <p>Главный курсор — обычный курсор RichTextFX. Дополнительные курсоры —
 * {@link CaretNode}, которые редактор сам рисует и мигает. Правки для них
 * выполняются здесь: символы, Backspace/Delete, Enter, Tab и вставка
 * применяются сразу ко всем курсорам.</p>
 *
 * <p>Управление:</p>
 * <ul>
 *   <li>{@code Alt+Click} — добавить курсор;</li>
 *   <li>{@code Ctrl+Alt+Up/Down} — добавить курсор строкой выше/ниже;</li>
 *   <li>{@code Esc} или обычный клик — убрать дополнительные курсоры.</li>
 * </ul>
 */
final class MultiCursor {
    private static final String OPENERS = "{([";
    private static final String CLOSERS = "})]";
    private static final String INDENT = "    ";

    private final InlineCssTextArea area;
    private final List<CaretNode> carets = new ArrayList<>();

    /** Пока true, изменения текста сделаны нами и не должны сбрасывать курсоры. */
    private boolean suppressClear;
    private int sequence;

    private MultiCursor(InlineCssTextArea area) {
        this.area = area;
    }

    static MultiCursor install(InlineCssTextArea area) {
        MultiCursor multi = new MultiCursor(area);
        multi.installHandlers();
        return multi;
    }

    /** Количество дополнительных курсоров (без главного). */
    int extraCaretCount() {
        return carets.size();
    }

    /** Позиции дополнительных курсоров — для проверок и отладки. */
    List<Integer> extraCaretPositions() {
        List<Integer> positions = new ArrayList<>();
        for (CaretNode caret : carets) positions.add(caret.getPosition());
        positions.sort(Integer::compareTo);
        return positions;
    }

    /** Дополнительные курсоры (нужны для визуальных проверок). */
    List<CaretNode> extraCaretNodes() {
        return new ArrayList<>(carets);
    }

    private void installHandlers() {
        area.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
        area.addEventFilter(KeyEvent.KEY_TYPED, this::onKeyTyped);
        area.addEventFilter(MouseEvent.MOUSE_PRESSED, this::onMousePressed);

        // Любое «внешнее» изменение текста (undo/redo, переключение вкладки,
        // открытие файла, поиск с заменой) делает старые позиции курсоров
        // неверными — убираем их.
        area.textProperty().addListener((obs, oldText, newText) -> {
            if (!suppressClear) clearCarets();
        });
    }

    // ============================ КЛАВИАТУРА ============================

    private void onKeyPressed(KeyEvent event) {
        // Ctrl+Alt+Up/Down — новый курсор строкой выше/ниже.
        if (event.isControlDown() && event.isAltDown() && !event.isShiftDown()
                && (event.getCode() == KeyCode.UP || event.getCode() == KeyCode.DOWN)) {
            addCaretVertically(event.getCode() == KeyCode.DOWN ? 1 : -1);
            event.consume();
            return;
        }

        if (carets.isEmpty()) return;

        if (event.getCode() == KeyCode.ESCAPE) {
            clearCarets();
            event.consume();
            return;
        }

        // Вставка из буфера — во все курсоры.
        if (event.isShortcutDown() && event.getCode() == KeyCode.V) {
            String text = clipboardText();
            if (!text.isEmpty()) {
                event.consume();
                insertAtAll(text);
            }
            return;
        }

        // Остальные сочетания с Ctrl/Alt не трогаем.
        if (event.isShortcutDown() || event.isAltDown() || event.isMetaDown()) {
            return;
        }

        // С выделением работают обычные механизмы RichTextFX.
        if (area.getSelection().getLength() > 0) {
            clearCarets();
            return;
        }

        switch (event.getCode()) {
            case BACK_SPACE:
                event.consume();
                deleteBackward();
                return;
            case DELETE:
                event.consume();
                deleteForward();
                return;
            case ENTER:
                event.consume();
                insertNewline();
                return;
            case TAB:
                event.consume();
                insertAtAll(INDENT);
                return;
            case LEFT:
                if (!event.isShiftDown()) {
                    event.consume();
                    moveAll(position -> Math.max(0, position - 1));
                }
                return;
            case RIGHT:
                if (!event.isShiftDown()) {
                    event.consume();
                    moveAll(position -> Math.min(area.getLength(), position + 1));
                }
                return;
            case UP:
                if (!event.isShiftDown()) {
                    event.consume();
                    moveAll(position -> moveVertically(position, -1));
                }
                return;
            case DOWN:
                if (!event.isShiftDown()) {
                    event.consume();
                    moveAll(position -> moveVertically(position, 1));
                }
                return;
            case HOME:
                if (!event.isShiftDown()) {
                    event.consume();
                    moveAll(this::lineStart);
                }
                return;
            case END:
                if (!event.isShiftDown()) {
                    event.consume();
                    moveAll(this::lineEnd);
                }
                return;
            default:
                // Прочие клавиши — обычная обработка главного курсора;
                // если текст изменится, listener выше уберёт дополнительные.
        }
    }

    private void onKeyTyped(KeyEvent event) {
        if (carets.isEmpty()) return;

        String character = event.getCharacter();
        if (character == null || character.isEmpty()) return;

        char ch = character.charAt(0);
        if (ch < 0x20 || ch == 0x7f) return; // Enter, Tab, Backspace и т.п.

        if (area.getSelection().getLength() > 0) {
            clearCarets();
            return;
        }

        event.consume();
        insertTyped(character);
    }

    // ============================== МЫШЬ ===============================

    private void onMousePressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) return;

        if (event.isAltDown() && !event.isControlDown() && !event.isMetaDown()) {
            Point2D local = area.screenToLocal(event.getScreenX(), event.getScreenY());
            int position = area.hit(local.getX(), local.getY()).getInsertionIndex();
            addCaret(position);
            area.requestFocus();
            event.consume();
            return;
        }

        // Обычный клик оставляет только главный курсор.
        if (!carets.isEmpty()) clearCarets();
    }

    // ============================ ОПЕРАЦИИ =============================

    private void insertTyped(String character) {
        char ch = character.charAt(0);
        int openerIndex = OPENERS.indexOf(ch);
        boolean opener = openerIndex >= 0;
        boolean closer = CLOSERS.indexOf(ch) >= 0;
        String text = area.getText();

        List<Target> targets = collectTargets();
        for (Target target : targets) {
            if (closer && target.oldPos < text.length() && text.charAt(target.oldPos) == ch) {
                // Закрывающая скобка уже стоит после курсора — перепрыгиваем её.
                target.skipForward = true;
            } else if (opener) {
                target.edit = new Edit(target.oldPos, target.oldPos,
                        character + CLOSERS.charAt(openerIndex));
                target.offset = 1;
            } else {
                target.edit = new Edit(target.oldPos, target.oldPos, character);
                target.offset = character.length();
            }
        }
        apply(targets);
    }

    private void insertAtAll(String text) {
        List<Target> targets = collectTargets();
        for (Target target : targets) {
            target.edit = new Edit(target.oldPos, target.oldPos, text);
            target.offset = text.length();
        }
        apply(targets);
    }

    private void insertNewline() {
        String text = area.getText();
        List<Target> targets = collectTargets();
        for (Target target : targets) {
            int position = target.oldPos;
            int start = lineStart(position);
            String indent = leadingWhitespace(text, start, position);

            String insertion;
            if (position > 0 && position < text.length()
                    && isPair(text.charAt(position - 1), text.charAt(position))) {
                // Enter между { } — как в одиночном курсоре: две строки с отступом.
                insertion = "\n" + indent + INDENT + "\n" + indent;
                target.offset = 1 + indent.length() + INDENT.length();
            } else {
                insertion = "\n" + indent;
                target.offset = insertion.length();
            }
            target.edit = new Edit(position, position, insertion);
        }
        apply(targets);
    }

    private void deleteBackward() {
        String text = area.getText();
        List<Target> targets = collectTargets();
        for (Target target : targets) {
            int position = target.oldPos;
            if (position <= 0) continue; // курсору в начале удалять нечего

            int start = position - 1;
            int end = position;
            if (position < text.length()
                    && isPair(text.charAt(position - 1), text.charAt(position))) {
                end = position + 1; // удаляем пустую пару целиком
            }
            target.edit = new Edit(start, end, "");
            target.offset = 0;
        }
        apply(targets);
    }

    private void deleteForward() {
        String text = area.getText();
        List<Target> targets = collectTargets();
        for (Target target : targets) {
            int position = target.oldPos;
            if (position >= text.length()) continue;

            int end = position + 1;
            if (position + 1 < text.length()
                    && isPair(text.charAt(position), text.charAt(position + 1))) {
                end = position + 2;
            }
            target.edit = new Edit(position, end, "");
            target.offset = 0;
        }
        apply(targets);
    }

    private void moveAll(java.util.function.IntUnaryOperator mover) {
        int mainPosition = clamp(mover.applyAsInt(area.getCaretPosition()));
        Set<Integer> used = new HashSet<>();
        used.add(mainPosition);

        List<CaretNode> kept = new ArrayList<>();
        for (CaretNode caret : carets) {
            int position = clamp(mover.applyAsInt(caret.getPosition()));
            if (used.add(position)) {
                caret.moveTo(position);
                kept.add(caret);
            } else {
                area.removeCaret(caret); // курсоры сошлись — оставляем один
            }
        }
        carets.clear();
        carets.addAll(kept);
        area.moveTo(mainPosition);
    }

    private void addCaretVertically(int direction) {
        // Повторное нажатие должно добавлять курсор ниже/выше последнего
        // добавленного, а не всё время ниже главного.
        int source = area.getCaretPosition();
        for (CaretNode caret : carets) {
            if (direction > 0 && caret.getPosition() > source) source = caret.getPosition();
            if (direction < 0 && caret.getPosition() < source) source = caret.getPosition();
        }
        addCaret(moveVertically(source, direction));
    }

    private void addCaret(int position) {
        int target = clamp(position);
        if (target == area.getCaretPosition()) return;
        for (CaretNode caret : carets) {
            if (caret.getPosition() == target) return;
        }
        CaretNode caret = new CaretNode("extra-caret-" + (++sequence), area, target);
        if (area.addCaret(caret)) carets.add(caret);
    }

    private void clearCarets() {
        if (carets.isEmpty()) return;
        List<CaretNode> copy = new ArrayList<>(carets);
        carets.clear();
        for (CaretNode caret : copy) area.removeCaret(caret);
    }

    // ========================= ПРИМЕНЕНИЕ ПРАВОК ========================

    /**
     * Применяет правки, привязанные к курсорам, и расставляет курсоры
     * в их новые позиции. Правки применяются от конца к началу, чтобы
     * смещения не ломали позиции ещё не обработанных курсоров.
     */
    private void apply(List<Target> targets) {
        List<Edit> base = new ArrayList<>();
        for (Target target : targets) {
            if (target.edit != null) base.add(target.edit);
        }

        List<Edit> edits = mergeDeletions(base);

        // Если удаления соседних курсоров слились в один диапазон,
        // все эти курсоры должны встать в его начало.
        for (Target target : targets) {
            if (target.edit != null && target.edit.replacement.isEmpty()
                    && target.edit.end > target.edit.start) {
                Edit merged = covering(edits, target.edit);
                if (merged != null && merged != target.edit) {
                    target.edit = merged;
                    target.offset = 0;
                }
            }
        }

        if (!edits.isEmpty()) {
            List<Edit> descending = new ArrayList<>(edits);
            descending.sort(Comparator.comparingInt((Edit edit) -> edit.start).reversed());

            suppressClear = true;
            try {
                for (Edit edit : descending) {
                    area.replaceText(edit.start, edit.end, edit.replacement);
                }
            } finally {
                suppressClear = false;
            }
        }

        placeCarets(targets, edits);
    }

    private void placeCarets(List<Target> targets, List<Edit> edits) {
        List<Edit> sorted = new ArrayList<>(edits);
        sorted.sort(Comparator.comparingInt(edit -> edit.start));

        Target main = null;
        for (Target target : targets) {
            if (target.main) main = target;
        }

        int mainPosition = mapPosition(main, sorted);
        Set<Integer> used = new HashSet<>();
        used.add(mainPosition);

        List<CaretNode> kept = new ArrayList<>();
        for (Target target : targets) {
            if (target.main) continue;
            int position = mapPosition(target, sorted);
            if (used.add(position)) {
                target.caret.moveTo(position);
                kept.add(target.caret);
            } else {
                area.removeCaret(target.caret); // слился с другим курсором
            }
        }

        carets.clear();
        carets.addAll(kept);
        area.moveTo(mainPosition);
    }

    /**
     * Куда переедет курсор после всех правок.
     *
     * <p>Для курсора с собственной вставкой позиция — начало вставки плюс
     * {@link Target#offset} (например, внутрь пары скобок). Для удаления
     * {@code offset == 0}, то есть начало удалённого диапазона. Правки
     * левее курсора сдвигают его на свою разницу длин.</p>
     */
    private int mapPosition(Target target, List<Edit> sortedEdits) {
        int shift = 0;
        for (Edit edit : sortedEdits) {
            if (edit == target.edit) {
                return clamp(edit.start + shift + target.offset);
            }
            if (edit.start < target.oldPos) {
                shift += edit.replacement.length() - (edit.end - edit.start);
            } else {
                break;
            }
        }
        int extra = target.skipForward ? 1 : 0;
        return clamp(target.oldPos + shift + extra);
    }

    // ============================ ВСПОМОГАТЕЛЬНОЕ =======================

    private List<Target> collectTargets() {
        List<Target> targets = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();

        int mainPosition = area.getCaretPosition();
        targets.add(new Target(null, mainPosition, true));
        seen.add(mainPosition);

        for (Iterator<CaretNode> iterator = carets.iterator(); iterator.hasNext(); ) {
            CaretNode caret = iterator.next();
            int position = caret.getPosition();
            if (seen.add(position)) {
                targets.add(new Target(caret, position, false));
            } else {
                area.removeCaret(caret); // дубликат — не нужен
                iterator.remove();
            }
        }

        targets.sort(Comparator.comparingInt(target -> target.oldPos));
        return targets;
    }

    /** Сливает пересекающиеся и соседние удаления в общие диапазоны. */
    private static List<Edit> mergeDeletions(List<Edit> edits) {
        List<Edit> deletions = new ArrayList<>();
        List<Edit> others = new ArrayList<>();
        for (Edit edit : edits) {
            if (edit.replacement.isEmpty() && edit.end > edit.start) deletions.add(edit);
            else others.add(edit);
        }

        deletions.sort(Comparator.comparingInt(edit -> edit.start));
        List<Edit> merged = new ArrayList<>();
        for (Edit deletion : deletions) {
            if (!merged.isEmpty()
                    && deletion.start <= merged.get(merged.size() - 1).end) {
                Edit last = merged.remove(merged.size() - 1);
                merged.add(new Edit(last.start, Math.max(last.end, deletion.end), ""));
            } else {
                merged.add(deletion);
            }
        }

        merged.addAll(others);
        merged.sort(Comparator.comparingInt(edit -> edit.start));
        return merged;
    }

    private static Edit covering(List<Edit> edits, Edit range) {
        for (Edit edit : edits) {
            if (edit.replacement.isEmpty()
                    && edit.start <= range.start && edit.end >= range.end) {
                return edit;
            }
        }
        return null;
    }

    private int moveVertically(int position, int direction) {
        TwoDimensional.Position current =
                area.offsetToPosition(clamp(position), Bias.Forward);
        int paragraph = current.getMajor() + direction;
        int paragraphCount = area.offsetToPosition(area.getLength(), Bias.Forward).getMajor() + 1;
        if (paragraph < 0 || paragraph >= paragraphCount) return position;

        int column = Math.min(current.getMinor(), area.getParagraphLength(paragraph));
        return area.getAbsolutePosition(paragraph, column);
    }

    private int lineStart(int position) {
        int paragraph = area.offsetToPosition(clamp(position), Bias.Forward).getMajor();
        return area.getAbsolutePosition(paragraph, 0);
    }

    private int lineEnd(int position) {
        int paragraph = area.offsetToPosition(clamp(position), Bias.Forward).getMajor();
        return area.getAbsolutePosition(paragraph, area.getParagraphLength(paragraph));
    }

    private static String leadingWhitespace(String text, int start, int end) {
        int index = start;
        while (index < end && (text.charAt(index) == ' ' || text.charAt(index) == '\t')) {
            index++;
        }
        return text.substring(start, index);
    }

    private static boolean isPair(char open, char close) {
        return (open == '{' && close == '}') ||
               (open == '(' && close == ')') ||
               (open == '[' && close == ']');
    }

    private int clamp(int position) {
        return Math.max(0, Math.min(area.getLength(), position));
    }

    private static String clipboardText() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (!clipboard.hasString()) return "";
        String text = clipboard.getString();
        return text == null ? "" : text;
    }

    private static final class Edit {
        final int start;
        final int end;
        final String replacement;

        Edit(int start, int end, String replacement) {
            this.start = start;
            this.end = end;
            this.replacement = replacement;
        }
    }

    private static final class Target {
        final CaretNode caret; // null у главного курсора
        final int oldPos;
        final boolean main;

        Edit edit;
        int offset;
        boolean skipForward;

        Target(CaretNode caret, int oldPos, boolean main) {
            this.caret = caret;
            this.oldPos = oldPos;
            this.main = main;
        }
    }
}
