package se233.chapter3;

import se233.chapter3.JarLauncher; // สมมติว่านี่คือคลาส JavaFX Application ของคุณ
import javafx.application.Application;

public class JarLauncher {
    public static void main(String[] args) {
        // ให้ Helper Class นี้เรียกใช้คลาส JavaFX Application จริงๆ
        // โดยใช้ HelloApplication แทน Launcher ตาม pom.xml
        Application.launch(Launcher.class, args);
    }
}