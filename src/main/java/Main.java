import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;
import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) {

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #2b2b2b;");

        VBox leftPanel = new VBox(10);
        leftPanel.setStyle("-fx-background-color: #1b1b1b;");
        leftPanel.setPrefWidth(200);
        leftPanel.setAlignment(Pos.BOTTOM_LEFT);

        HBox topPanel = new HBox();
        topPanel.setStyle("-fx-background-color: #2b2b2b;");

        HBox toolsPanel = new HBox(-3);
        toolsPanel.setStyle("-fx-background-color: #1b1b1b;");
        toolsPanel.setPrefWidth(200);
        toolsPanel.setAlignment(Pos.BOTTOM_CENTER);

        Button fileButton = new Button("File");
        fileButton.setStyle("-fx-background-color: #3b3b3b; -fx-text-fill: #FFFFFF");

        Button editButton = new Button("Edit");
        editButton.setStyle("-fx-background-color: #3b3b3b; -fx-text-fill: #FFFFFF");

        Button settingsButton = new Button("Settings");
        settingsButton.setStyle("-fx-background-color: #3b3b3b; -fx-text-fill: #FFFFFF");
        settingsButton.setPrefWidth(100);
        settingsButton.setPrefHeight(40);

        Button terminalButton = new Button("Terminal");
        terminalButton.setStyle("-fx-background-color: #3b3b3b; -fx-text-fill: #FFFFFF");
        terminalButton.setPrefWidth(100);
        terminalButton.setPrefHeight(40);

        TextArea codeField = new TextArea();
        codeField.setStyle("-fx-control-inner-background: #2b2b2b; -fx-font-size: 18px;");

        topPanel.getChildren().addAll(fileButton, editButton);
        toolsPanel.getChildren().addAll(settingsButton, terminalButton);
        leftPanel.getChildren().addAll(toolsPanel);

        root.setLeft(leftPanel);
        root.setCenter(codeField);
        root.setTop(topPanel);

        Scene scene = new Scene(root, 800, 600);
        primaryStage.setTitle("Iron IDE");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

