package app;

import java.nio.file.Path;
import javafx.scene.control.Button;

/** Открытая вкладка: свой файл, свой текст и своё состояние сохранности. */
final class Tab {
    String title;
    Path path;          // null — безымянный (не сохранённый) файл
    String text = "";
    String savedText = "";
    int caret;
    Button button;      // кнопка на панели вкладок (маркер «•»)

    Tab(String title, Path path) {
        this.title = title;
        this.path = path;
    }
}
