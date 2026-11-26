package app;

import java.util.List;
import java.util.Optional;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Bounds;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.InlineCssTextArea;

/**
 * Прозрачный слой поверх редактора, на котором рисуются рамки блоков:
 * сплошные серые — вокруг функций, пунктирные серые — вокруг ООП-конструкций
 * ({@code class}, {@code enum}, {@code struct} и т.п.).
 *
 * <p>Слой не перехватывает мышь и не вмешивается в разметку текста, поэтому
 * код не может «исчезнуть» из-за рамок.</p>
 */
final class BlockOverlay extends Canvas {

    private static final Color FUNCTION_STROKE = Color.web("#8a8a8a");
    private static final Color OOP_STROKE = Color.web("#8a8a8a");
    private static final double PAD = 5.0;
    private static final double LINE_WIDTH = 1.5;

    private final InlineCssTextArea area;
    private final VirtualizedScrollPane<InlineCssTextArea> scroll;

    private List<BlockFrames.Block> blocks = List.of();
    private boolean redrawScheduled;

    BlockOverlay(InlineCssTextArea area, VirtualizedScrollPane<InlineCssTextArea> scroll) {
        this.area = area;
        this.scroll = scroll;
        setMouseTransparent(true);
        installListeners();
        refresh();
    }

    private void installListeners() {
        area.textProperty().addListener((obs, oldText, newText) -> refresh());
        area.layoutBoundsProperty().addListener((obs, oldBounds, newBounds) -> scheduleRedraw());

        widthProperty().addListener((obs, oldValue, newValue) -> scheduleRedraw());
        heightProperty().addListener((obs, oldValue, newValue) -> scheduleRedraw());

        // Приводим к ObservableValue: метод addListener из reactfx-интерфейса
        // Var недоступен модулю, а javafx-версия — доступна.
        ((ObservableValue<Double>) scroll.estimatedScrollXProperty())
                .addListener((obs, oldValue, newValue) -> scheduleRedraw());
        ((ObservableValue<Double>) scroll.estimatedScrollYProperty())
                .addListener((obs, oldValue, newValue) -> scheduleRedraw());
    }

    /** Перестраивает список блоков и перерисовывает рамки. */
    void refresh() {
        blocks = BlockFrames.detect(area.getText());
        scheduleRedraw();
    }

    private void scheduleRedraw() {
        if (redrawScheduled) return;
        redrawScheduled = true;
        Platform.runLater(() -> {
            redrawScheduled = false;
            redraw();
        });
    }

    private void redraw() {
        double width = getWidth();
        double height = getHeight();
        GraphicsContext g = getGraphicsContext2D();
        g.clearRect(0, 0, width, height);

        if (width <= 0 || height <= 0 || blocks.isEmpty() || area.getScene() == null) {
            return;
        }

        Bounds areaScreen = area.localToScreen(area.getBoundsInLocal());
        if (areaScreen == null) return;
        Bounds areaLocal = screenToLocal(areaScreen);
        if (areaLocal == null) return;

        int firstVisible;
        int lastVisible;
        try {
            firstVisible = area.firstVisibleParToAllParIndex();
            lastVisible = area.lastVisibleParToAllParIndex();
        } catch (RuntimeException noVisibleParagraphs) {
            return;
        }
        if (firstVisible < 0 || lastVisible < firstVisible) return;

        String[] lines = area.getText().split("\n", -1);
        int[] starts = lineStarts(lines);

        for (BlockFrames.Block block : blocks) {
            if (block.endParagraph < firstVisible || block.startParagraph > lastVisible) {
                continue;
            }

            int visibleStart = Math.max(block.startParagraph, firstVisible);
            int visibleEnd = Math.min(block.endParagraph, lastVisible);
            if (visibleStart >= lines.length) continue;
            visibleEnd = Math.min(visibleEnd, lines.length - 1);

            double left = Double.MAX_VALUE;
            double right = -Double.MAX_VALUE;
            for (int p = visibleStart; p <= visibleEnd; p++) {
                double[] extent = lineExtent(lines[p], starts[p]);
                if (extent == null) continue;
                left = Math.min(left, extent[0]);
                right = Math.max(right, extent[1]);
            }
            if (right < left) continue;

            Optional<Bounds> startOnScreen = area.getParagraphBoundsOnScreen(visibleStart);
            Optional<Bounds> endOnScreen = area.getParagraphBoundsOnScreen(visibleEnd);
            if (startOnScreen.isEmpty() || endOnScreen.isEmpty()) continue;

            Bounds startLocal = screenToLocal(startOnScreen.get());
            Bounds endLocal = screenToLocal(endOnScreen.get());
            if (startLocal == null || endLocal == null) continue;

            double top = block.startParagraph < firstVisible
                    ? areaLocal.getMinY()
                    : startLocal.getMinY();
            double bottom = block.endParagraph > lastVisible
                    ? areaLocal.getMaxY()
                    : endLocal.getMaxY();

            left = Math.max(areaLocal.getMinX(), left - PAD);
            right = Math.min(areaLocal.getMaxX(), right + PAD);

            if (bottom <= top || right <= left) continue;

            g.setLineWidth(LINE_WIDTH);
            if (block.oop) {
                g.setStroke(OOP_STROKE);
                g.setLineDashes(7, 5);
            } else {
                g.setStroke(FUNCTION_STROKE);
                g.setLineDashes(null);
            }
            g.strokeRect(left, top, right - left, bottom - top);
        }

        g.setLineDashes(null);
    }

    private int[] lineStarts(String[] lines) {
        int[] starts = new int[lines.length];
        int offset = 0;
        for (int i = 0; i < lines.length; i++) {
            starts[i] = offset;
            offset += lines[i].length() + 1;
        }
        return starts;
    }

    /** Горизонтальные границы непустой части строки в локальных координатах. */
    private double[] lineExtent(String line, int lineStart) {
        int first = 0;
        while (first < line.length() && Character.isWhitespace(line.charAt(first))) {
            first++;
        }
        int last = line.length() - 1;
        while (last >= first && Character.isWhitespace(line.charAt(last))) {
            last--;
        }
        if (last < first) return null;

        int from = lineStart + first;
        int to = Math.min(lineStart + last + 1, area.getLength());
        if (to <= from) return null;

        try {
            Optional<Bounds> onScreen = area.getCharacterBoundsOnScreen(from, to);
            if (onScreen.isEmpty()) return null;
            Bounds local = screenToLocal(onScreen.get());
            if (local == null) return null;
            return new double[]{local.getMinX(), local.getMaxX()};
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
