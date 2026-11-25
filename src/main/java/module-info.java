module test {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.fxmisc.richtext;
    requires org.fxmisc.flowless;

    exports app;
    opens app to javafx.fxml;
}