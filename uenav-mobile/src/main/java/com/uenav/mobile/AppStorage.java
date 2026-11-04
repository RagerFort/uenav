package com.uenav.mobile;

import com.gluonhq.attach.storage.StorageService;
import java.io.File;

/** Finds a folder the app may write to: the phone's private app storage, or a folder in the user's home on a PC. */
final class AppStorage {

    private AppStorage() { }

    static File directory() {
        File dir = privateStorage();
        if (dir == null) {
            dir = new File(System.getProperty("user.home", "."), ".uenav");
        }
        dir.mkdirs();
        return dir;
    }

    private static File privateStorage() {
        try {
            return StorageService.create().flatMap(StorageService::getPrivateStorage).orElse(null);
        } catch (Throwable notAvailable) {
            return null;
        }
    }
}
