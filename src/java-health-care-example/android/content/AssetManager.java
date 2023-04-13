package android.content;

import android.util.Log;

import java.io.*;
import java.nio.file.Paths;

/**
 * android.content.AssetManagerの擬似クラス
 * [res]ディレクトリのリクエスト用JSONファイルを読み込み、
 */
public class AssetManager {
    private static final String TAG = "AssetManager";
    // Development environ: Ubuntu18.04
    // ~/project/java-workspaces/java-health-care-example/resources
    private static final String HOME = System.getenv("HOME");
    private static final String RESOURCE_PATH = Paths.get(HOME,
            "project", "java-workspaces", "java-health-care-example", "resources").toString();
    public AssetManager() {
    }

    public InputStream open(String filename) throws IOException {
        File absFile = Paths.get(RESOURCE_PATH, filename).toFile();
        Log.d(TAG, "file: " + absFile);
        return new FileInputStream(absFile);
    }


    public void save(byte[] imageData, String filename) throws IOException {
        File absFile = Paths.get(RESOURCE_PATH, filename).toFile();
        Log.d(TAG, "saved: " + absFile);
        try (FileOutputStream out = new FileOutputStream(absFile)) {
            out.write(imageData);
        }
    }
}
