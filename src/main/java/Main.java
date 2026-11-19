import java.util.function.IntFunction;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.fxmisc.richtext.InlineCssTextArea;
import org.fxmisc.richtext.LineNumberFactory;

public class Main extends Application {
    private static final String BACKGROUND_COLOR = "#2b2b2b";
    private static final String PANEL_COLOR = "#1b1b1b";

    private static final String BUTTON_STYLE =
            "-fx-background-color: #3b3b3b; -fx-text-fill: #ffffff;";

    private static final int SIDEBAR_WIDTH = 200;
    private static final int TOOL_BUTTON_WIDTH = 100;
    private static final int TOOL_BUTTON_HEIGHT = 40;

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();
        root.setStyle(backgroundStyle(BACKGROUND_COLOR));

        // Верхняя панель с кнопками меню.
        HBox topPanel = new HBox(
                createButton("File"),
                createButton("Edit")
        );
        topPanel.setStyle(backgroundStyle(BACKGROUND_COLOR));

        // Панель инструментов внизу боковой панели.
        HBox toolsPanel = new HBox(
                -3,
                createButton("Settings", TOOL_BUTTON_WIDTH, TOOL_BUTTON_HEIGHT),
                createButton("Terminal", TOOL_BUTTON_WIDTH, TOOL_BUTTON_HEIGHT)
        );
        toolsPanel.setStyle(backgroundStyle(PANEL_COLOR));
        toolsPanel.setPrefWidth(SIDEBAR_WIDTH);
        toolsPanel.setAlignment(Pos.BOTTOM_CENTER);

        // Левая боковая панель.
        VBox leftPanel = new VBox(10, toolsPanel);
        leftPanel.setStyle(backgroundStyle(PANEL_COLOR));
        leftPanel.setPrefWidth(SIDEBAR_WIDTH);
        leftPanel.setAlignment(Pos.BOTTOM_LEFT);

        // Область редактирования кода.
        InlineCssTextArea codeField = new InlineCssTextArea();
        codeField.setStyle(
                "-fx-background-color: " + BACKGROUND_COLOR + ";" +
                "-fx-font-size: 18px;"
        );
        codeField.textProperty().addListener((obs, oldText, newText) ->
                codeField.setStyle(0, codeField.getLength(), "-fx-fill: #ffffff;")
        );
        codeField.setWrapText(false);

        // Настройка тёмной колонки с номерами строк.
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

        root.setLeft(leftPanel);
        root.setCenter(codeField);
        root.setTop(topPanel);

        // Главное окно приложения.
        Scene scene = new Scene(root, 1000, 800);
        primaryStage.setTitle("Iron IDE");
        primaryStage.setScene(scene);
        primaryStage.show();
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

    public static void main(String[] args) {
        launch(args);
    }
}