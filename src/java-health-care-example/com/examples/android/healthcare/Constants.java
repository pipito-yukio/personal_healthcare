package com.examples.android.healthcare;

import java.nio.file.Paths;

/**
 * ユーザDocumentsディレクトリの入出力先
 * for linux ~/Documents
 */
public class Constants {
    public static final String USER_HOME = System.getProperty("user.home");
    public static final String DOCUMENTS = Paths.get(USER_HOME, "Documents").toString();
    // 出力先パス
    public static final String OUTPUT_DATA_PATH = Paths.get(DOCUMENTS, "java", "output").toString();
    // 入力データパス
    public static final String INPUT_DATA_PATH =  Paths.get(DOCUMENTS, "java", "input").toString();
}
