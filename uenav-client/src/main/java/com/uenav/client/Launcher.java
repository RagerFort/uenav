package com.uenav.client;

/**
 * Starts the admin app. UENav Admin.exe uses this as its main class: JavaFX refuses to start from a class that
 * extends Application when JavaFX is on the class path, so this plain class calls UenavApp.
 */
public final class Launcher {

    private Launcher() { }

    public static void main(String[] args) {
        UenavApp.main(args);
    }
}
