package app;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Цвета и CSS-стили приложения.
 *
 * <p>Стили выпадающих меню, вкладок, дерева проекта, панели поиска и
 * полосы прокрутки редактора применяются к основной сцене; стили диалогов —
 * к сцене конкретного диалога.</p>
 */
final class Styles {
    static final String BACKGROUND = "#2b2b2b";
    static final String PANEL = "#1b1b1b";

    static final String BUTTON_STYLE =
            "-fx-background-color: #3b3b3b; -fx-text-fill: #ffffff;";

    private Styles() {
    }

    /** CSS, применямый к основной сцене. */
    static final String MENU_CSS =
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
            // --- ручка изменения ширины левой панели ---
            ".sidebar-handle { -fx-background-color: " + PANEL + "; }" +
            ".sidebar-handle:hover { -fx-background-color: #4a90d9; }" +
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
            "    -fx-background-color: " + BACKGROUND + ";" +
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
            ".project-header-row { -fx-background-color: " + PANEL + "; -fx-padding: 7 8 7 8; }" +
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
            "    -fx-background-color: " + PANEL + ";" +
            "    -fx-background: " + PANEL + ";" +
            "    -fx-border-color: transparent;" +
            "    -fx-padding: 0;" +
            "}" +
            ".project-scroll > .viewport { -fx-background-color: " + PANEL + "; }" +
            ".project-scroll:focused { -fx-border-color: transparent; }" +
            // --- дерево файлов ---
            ".tree-view {" +
            "    -fx-background-color: " + PANEL + ";" +
            "    -fx-border-color: transparent;" +
            "    -fx-control-inner-background: " + PANEL + ";" +
            "    -fx-control-inner-background-alt: " + PANEL + ";" +
            "    -fx-selection-bar: #37373d;" +
            "    -fx-selection-bar-non-focused: #2f2f2f;" +
            "    -fx-text-background-color: #ffffff;" +
            "}" +
            ".tree-view:focused { -fx-border-color: transparent; }" +
            ".tree-cell {" +
            "    -fx-background-color: " + PANEL + ";" +
            "    -fx-background: " + PANEL + ";" +
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
            "}" +
            // --- полоса прокрутки редактора (как в VS Code) ---
            // Тонкий ползунок справа: его размер и положение показывают,
            // какая часть кода видна в данный момент.
            ".virtualized-scroll-pane { -fx-background-color: transparent; }" +
            ".virtualized-scroll-pane > .scroll-bar:vertical {" +
            "    -fx-pref-width: 14px;" +
            "    -fx-min-width: 14px;" +
            "    -fx-background-color: transparent;" +
            "    -fx-padding: 0;" +
            "}" +
            ".virtualized-scroll-pane > .scroll-bar:horizontal {" +
            "    -fx-pref-height: 14px;" +
            "    -fx-min-height: 14px;" +
            "    -fx-background-color: transparent;" +
            "    -fx-padding: 0;" +
            "}" +
            ".virtualized-scroll-pane > .scroll-bar:focused {" +
            "    -fx-background-color: transparent;" +
            "    -fx-background-insets: 0;" +
            "}" +
            ".virtualized-scroll-pane > .scroll-bar > .track {" +
            "    -fx-background-color: transparent;" +
            "}" +
            ".virtualized-scroll-pane .thumb {" +
            "    -fx-background-color: rgba(121, 121, 121, 0.4);" +
            "    -fx-background-radius: 6px;" +
            "    -fx-background-insets: 2px;" +
            "}" +
            ".virtualized-scroll-pane .scroll-bar:hover .thumb {" +
            "    -fx-background-color: rgba(121, 121, 121, 0.75);" +
            "}" +
            ".virtualized-scroll-pane .scroll-bar:pressed .thumb {" +
            "    -fx-background-color: rgba(199, 199, 199, 0.5);" +
            "}" +
            // у VS Code нет кнопок со стрелками — прячем их
            ".virtualized-scroll-pane > .scroll-bar > .increment-button," +
            ".virtualized-scroll-pane > .scroll-bar > .decrement-button {" +
            "    -fx-padding: 0;" +
            "    -fx-pref-width: 0;" +
            "    -fx-pref-height: 0;" +
            "    -fx-min-width: 0;" +
            "    -fx-min-height: 0;" +
            "    -fx-background-color: transparent;" +
            "    -fx-border-color: transparent;" +
            "}" +
            ".virtualized-scroll-pane > .scroll-bar > .increment-button > .increment-arrow," +
            ".virtualized-scroll-pane > .scroll-bar > .decrement-button > .decrement-arrow {" +
            "    -fx-shape: \"M 0 0 L 0 0 Z\";" +
            "    -fx-background-color: transparent;" +
            "}";

    /** CSS, применяемый к сцене системных диалогов. */
    static final String DIALOG_CSS =
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

    /** Превращает CSS в data-URL, который можно добавить в stylesheets сцены. */
    static String cssUrl(String css) {
        return "data:text/css;base64,"
                + Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
    }

    static String backgroundStyle(String color) {
        return "-fx-background-color: " + color + ";";
    }
}
