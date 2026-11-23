module test {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.fxmisc.richtext;

    exports app;
    opens app to javafx.fxml;
}