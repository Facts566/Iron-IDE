package app;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Создание пунктов выпадающих меню (текст + подсказка горячей клавиши). */
final class MenuItems {
    private MenuItems() {
    }

    static Button item(String text, String shortcut, Runnable action) {
        Label nameLabel = new Label(text);
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        Label shortcutLabel = new Label(shortcut);
        shortcutLabel.getStyleClass().add("menu-shortcut");

        HBox content = new HBox(nameLabel, shortcutLabel);
        content.setAlignment(Pos.CENTER_LEFT);

        Button item = new Button();
        item.setGraphic(content);
        item.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        item.setMaxWidth(Double.MAX_VALUE);
        item.getStyleClass().add("menu-item-btn");
        item.setOnAction(event -> action.run());
        return item;
    }

    static Region separator() {
        Region separator = new Region();
        separator.getStyleClass().add("menu-separator");
        separator.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(separator, new Insets(4, 10, 4, 10));
        return separator;
    }
}
