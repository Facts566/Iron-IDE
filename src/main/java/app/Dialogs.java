package app;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextInputDialog;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/** Диалоги приложения: файловые, ввода имени, ошибок и подтверждений. */
final class Dialogs {
    private Dialogs() {
    }

    /** Выбор файла с тёмным оформлением; стартовая папка — текущий файл или проект. */
    static FileChooser fileChooser(String title, Path currentFile, Path projectDir) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);

        if (currentFile != null && currentFile.getParent() != null) {
            File directory = currentFile.getParent().toFile();
            if (directory.isDirectory()) chooser.setInitialDirectory(directory);
        } else if (projectDir != null && Files.isDirectory(projectDir)) {
            chooser.setInitialDirectory(projectDir.toFile());
        }

        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Исходники и текст",
                        "*.java", "*.py", "*.js", "*.ts", "*.c", "*.cpp", "*.h", "*.hpp",
                        "*.cs", "*.go", "*.rs", "*.kt", "*.php", "*.rb", "*.sh", "*.sql",
                        "*.html", "*.css", "*.xml", "*.json", "*.md", "*.txt"),
                new FileChooser.ExtensionFilter("Все файлы", "*.*")
        );
        return chooser;
    }

    /** Alert с владельцем и тёмным оформлением (стили добавляются при показе). */
    static Alert createAlert(Alert.AlertType type, Window owner) {
        Alert alert = new Alert(type);
        if (owner != null) alert.initOwner(owner);

        // сцена диалога создаётся только при показе
        alert.setOnShowing(event -> styleDialog(alert.getDialogPane()));
        return alert;
    }

    static void showError(Window owner, String header, String content) {
        Alert alert = createAlert(Alert.AlertType.ERROR, owner);
        alert.setTitle("Ошибка");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    static Optional<ButtonType> confirm(Window owner, String title, String header, String content) {
        Alert alert = createAlert(Alert.AlertType.CONFIRMATION, owner);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        return alert.showAndWait();
    }

    /** Окно ввода имени файла (тёмное оформление как у остальных диалогов). */
    static Optional<String> askFileName(Window owner, String title, String header, String initial) {
        TextInputDialog dialog = new TextInputDialog(initial);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        if (owner != null) dialog.initOwner(owner);
        dialog.setOnShowing(event -> styleDialog(dialog.getDialogPane()));
        return dialog.showAndWait();
    }

    private static void styleDialog(DialogPane pane) {
        Scene scene = pane.getScene();
        if (scene == null) return;
        String css = Styles.cssUrl(Styles.DIALOG_CSS);
        if (!scene.getStylesheets().contains(css)) scene.getStylesheets().add(css);
    }
}
