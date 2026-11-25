package app;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.fxmisc.richtext.InlineCssTextArea;

/**
 * Поведение скобок в редакторе: автозакрытие пар, переход через уже
 * существующую закрывающую скобку, удаление пустой пары по Backspace
 * и перенос скобок на новую строку с отступом по Enter.
 */
final class BracketEditing {
    private BracketEditing() {
    }

    static void install(InlineCssTextArea codeField) {
        // --- 1. АВТОМАТИЧЕСКОЕ ЗАКРЫТИЕ СКОБОК ---
        // Важно: RichTextFX вставляет обычные символы на событии KEY_TYPED,
        // поэтому обрабатываем скобки именно там и гасим это событие.
        // Если вставлять пару на KEY_PRESSED, символ вставится второй раз
        // от обработчика RichTextFX (получалось (() вместо ()).
        codeField.addEventFilter(KeyEvent.KEY_TYPED, event -> {
            if (event.isConsumed()) return;
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
            if (event.isConsumed()) return;
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
    }
}
