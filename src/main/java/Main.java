import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) {
        UI ui = new UI();

        Scene scene = new Scene(ui.getRoot(), 1000, 800);
        primaryStage.setTitle("Iron IDE");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
