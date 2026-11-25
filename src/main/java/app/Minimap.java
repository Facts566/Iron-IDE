package app;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.animation.PauseTransition;
import javafx.beans.value.ObservableValue;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.fxmisc.richtext.InlineCssTextArea;

/**
 * Мини-карта кода справа от редактора (как в VS Code): уменьшенная копия
 * всего документа с подсветкой и рамкой, показывающей видимую область.
 * Клик по мини-карте прокручивает редактор, рамку можно перетаскивать.
 */
final class Minimap extends Region {
    private static final double PREF_WIDTH = 84;
    private static final double H_PADDING = 4;
    private static final double MAX_LINE_HEIGHT = 3.0;
    private static final double MIN_OVERLAY_HEIGHT = 18;

    private static final Color BACKGROUND = Color.web(Styles.BACKGROUND);
    private static final Color SEPARATOR = Color.web("#3a3a3a");
    private static final Color OVERLAY_FILL = Color.web("#c8c8c8", 0.13);
    private static final Color OVERLAY_BORDER = Color.web("#c8c8c8", 0.28);

    // категории символов для раскраски
    private static final byte DEFAULT = 0;
    private static final byte KEYWORD = 1;
    private static final byte TYPE = 2;
    private static final byte STRING = 3;
    private static final byte FUNCTION = 4;
    private static final byte COMMENT = 5;
    private static final byte NUMBER = 6;

    private final InlineCssTextArea area;
    private final Canvas codeCanvas = new Canvas();
    private final Canvas overlayCanvas = new Canvas();
    private final PauseTransition redrawDelay = new PauseTransition(Duration.millis(120));

    private double lineHeight = MAX_LINE_HEIGHT;
    private double contentHeight;
    private double overlayY;
    private double overlayHeight;
    private double dragOffset = Double.NaN;

    Minimap(InlineCssTextArea area) {
        this.area = area;

        getStyleClass().add("minimap");
        setPrefWidth(PREF_WIDTH);
        setMinWidth(PREF_WIDTH);
        setMaxWidth(PREF_WIDTH);
        setStyle(Styles.backgroundStyle(Styles.BACKGROUND));

        getChildren().addAll(codeCanvas, overlayCanvas);

        redrawDelay.setOnFinished(event -> rebuild());
        area.textProperty().addListener((obs, oldText, newText) -> scheduleRedraw());
        // свойства прокрутки объявлены в reactfx; слушаем их как обычные
        // javafx-observable, чтобы не тянуть reactfx в module-info
        ObservableValue<Double> scrollY = area.estimatedScrollYProperty();
        scrollY.addListener((obs, oldValue, newValue) -> drawOverlay());
        ObservableValue<Double> totalHeight = area.totalHeightEstimateProperty();
        totalHeight.addListener((obs, oldValue, newValue) -> drawOverlay());
        area.heightProperty().addListener((obs, oldValue, newValue) -> drawOverlay());

        addEventHandler(MouseEvent.MOUSE_PRESSED, this::onMousePressed);
        addEventHandler(MouseEvent.MOUSE_DRAGGED, this::onMouseDragged);
        addEventHandler(MouseEvent.MOUSE_RELEASED, event -> dragOffset = Double.NaN);
        setCursor(Cursor.DEFAULT);
    }

    private void scheduleRedraw() {
        redrawDelay.playFromStart();
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0) return;

        codeCanvas.relocate(0, 0);
        overlayCanvas.relocate(0, 0);

        if (codeCanvas.getWidth() != w || codeCanvas.getHeight() != h) {
            codeCanvas.setWidth(w);
            codeCanvas.setHeight(h);
            overlayCanvas.setWidth(w);
            overlayCanvas.setHeight(h);
            rebuild();
        } else {
            drawOverlay();
        }
    }

    // ================= ОТРИСОВКА КОДА =================

    private void rebuild() {
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0) return;

        String text = area.getText();
        String[] lines = text.split("\n", -1);
        int lineCount = Math.max(lines.length, 1);

        lineHeight = Math.min(MAX_LINE_HEIGHT, h / lineCount);
        contentHeight = lineCount * lineHeight;

        double usableWidth = Math.max(1, w - H_PADDING * 2);
        double charWidth = Math.max(0.45, lineHeight * 0.5);
        int maxChars = Math.max(1, (int) Math.floor(usableWidth / charWidth));
        int step = lineHeight >= 1 ? 1 : (int) Math.ceil(1 / Math.max(lineHeight, 0.01));
        double glyphHeight = Math.max(1, lineHeight * step * 0.8);

        GraphicsContext gc = codeCanvas.getGraphicsContext2D();
        gc.setFill(BACKGROUND);
        gc.fillRect(0, 0, w, h);

        // тонкая граница между редактором и мини-картой
        gc.setFill(SEPARATOR);
        gc.fillRect(0, 0, 1, h);

        List<int[]> literals = SyntaxHighlighter.findLiterals(text);
        int literalIndex = 0;
        int offset = 0;

        for (int i = 0; i < lineCount; i++) {
            String line = lines[Math.min(i, lines.length - 1)];

            // пропускаем литералы, которые закончились до начала строки
            while (literalIndex < literals.size() && literals.get(literalIndex)[1] <= offset) {
                literalIndex++;
            }

            if (i % step == 0) {
                int visibleLength = Math.min(line.length(), maxChars);
                if (visibleLength > 0) {
                    byte[] colors = classify(line, visibleLength, offset, literals, literalIndex);
                    double y = i * lineHeight;
                    drawLine(gc, line, colors, visibleLength, y, charWidth, glyphHeight);
                }
            }

            offset += line.length() + 1;
        }

        drawOverlay();
    }

    /** Определяет цвет каждого символа строки (не дальше visibleLength). */
    private static byte[] classify(String line, int visibleLength, int offset,
                                   List<int[]> literals, int literalIndex) {
        byte[] colors = new byte[visibleLength];
        String visible = line.substring(0, visibleLength);

        // порядок как в основной подсветке: функции, типы, ключевые слова, числа
        mark(colors, visible, SyntaxHighlighter.FUNCTIONS, FUNCTION, 1);
        mark(colors, visible, SyntaxHighlighter.TYPES, TYPE, 0);
        mark(colors, visible, SyntaxHighlighter.KEYWORDS, KEYWORD, 0);
        mark(colors, visible, SyntaxHighlighter.NUMBERS, NUMBER, 0);

        // строки и комментарии — поверх остального
        for (int i = literalIndex; i < literals.size(); i++) {
            int[] range = literals.get(i);
            if (range[0] >= offset + visibleLength) break;
            int start = Math.max(range[0], offset) - offset;
            int end = Math.min(range[1], offset + visibleLength) - offset;
            byte color = range[2] == SyntaxHighlighter.LITERAL_COMMENT ? COMMENT : STRING;
            for (int k = Math.max(0, start); k < end; k++) colors[k] = color;
        }
        return colors;
    }

    private static void mark(byte[] colors, String line, Pattern pattern, byte color, int group) {
        Matcher matcher = pattern.matcher(line);
        while (matcher.find()) {
            int end = Math.min(matcher.end(group), colors.length);
            for (int k = Math.max(0, matcher.start(group)); k < end; k++) colors[k] = color;
        }
    }

    private static void drawLine(GraphicsContext gc, String line, byte[] colors,
                                 int length, double y, double charWidth, double glyphHeight) {
        int col = 0;
        while (col < length) {
            // пробелы не рисуем — иначе строка превращается в сплошную полосу
            if (line.charAt(col) <= ' ') {
                col++;
                continue;
            }
            byte color = colors[col];
            int start = col;
            while (col < length && line.charAt(col) > ' ' && colors[col] == color) col++;

            gc.setFill(colorFor(color));
            gc.fillRect(H_PADDING + start * charWidth, y,
                    (col - start) * charWidth, glyphHeight);
        }
    }

    private static Color colorFor(byte code) {
        return switch (code) {
            case KEYWORD -> Color.web("#569cd6", 0.85);
            case TYPE -> Color.web("#4ec9b0", 0.85);
            case STRING -> Color.web("#ce9178", 0.85);
            case FUNCTION -> Color.web("#dcdcaa", 0.85);
            case COMMENT -> Color.web("#6a9955", 0.85);
            case NUMBER -> Color.web("#b5cea8", 0.85);
            default -> Color.web("#a8a8a8", 0.55);
        };
    }

    // ================= РАМКА ВИДИМОЙ ОБЛАСТИ =================

    private void drawOverlay() {
        double w = overlayCanvas.getWidth();
        double h = overlayCanvas.getHeight();
        if (w <= 0 || h <= 0) return;

        GraphicsContext gc = overlayCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);

        double total = area.getTotalHeightEstimate();
        double viewport = area.getViewportHeight();
        if (total <= 0 || contentHeight <= 0) {
            overlayY = 0;
            overlayHeight = 0;
            return;
        }

        double scroll = Math.max(0, area.getEstimatedScrollY());
        overlayHeight = Math.min(contentHeight, Math.max(MIN_OVERLAY_HEIGHT,
                contentHeight * (viewport / total)));
        overlayY = contentHeight * (scroll / total);
        if (viewport >= total) overlayY = 0;
        overlayY = Math.max(0, Math.min(overlayY, contentHeight - overlayHeight));

        gc.setFill(OVERLAY_FILL);
        gc.fillRect(0, overlayY, w, overlayHeight);
        gc.setStroke(OVERLAY_BORDER);
        gc.setLineWidth(1);
        gc.strokeLine(0.5, overlayY + 0.5, w, overlayY + 0.5);
        gc.strokeLine(0.5, overlayY + overlayHeight - 0.5, w, overlayY + overlayHeight - 0.5);
    }

    // ================= ПРОКРУТКА МЫШЬЮ =================

    private void onMousePressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY || contentHeight <= 0) return;

        double y = clamp(event.getY(), 0, contentHeight);
        if (y >= overlayY && y <= overlayY + overlayHeight) {
            dragOffset = y - overlayY; // тянем рамку за собой
        } else {
            dragOffset = overlayHeight / 2; // клик — центрируем область под курсором
            scrollToMinimapY(y - dragOffset);
        }
        event.consume();
    }

    private void onMouseDragged(MouseEvent event) {
        if (Double.isNaN(dragOffset) || contentHeight <= 0) return;
        double y = clamp(event.getY(), 0, contentHeight);
        scrollToMinimapY(y - dragOffset);
        event.consume();
    }

    /** Переводит положение рамки на мини-карте в позицию прокрутки редактора. */
    private void scrollToMinimapY(double minimapY) {
        double total = area.getTotalHeightEstimate();
        double viewport = area.getViewportHeight();
        if (total <= 0 || contentHeight <= 0) return;

        double scroll = minimapY * total / contentHeight;
        double max = Math.max(0, total - viewport);
        area.scrollYToPixel(Math.max(0, Math.min(max, scroll)));
        drawOverlay();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
