package com.examples.android.healthcare.functions;

import android.content.Context;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ファイルユーティリィティ
 */
public class FileManager {

    public static String checkFileNamesInContextDir(Context context) {
        File rootPath = context.getFilesDir();
        File[] fileArray = rootPath.listFiles();
        if (fileArray == null || fileArray.length == 0) {
            return null;
        }
        return Arrays.stream(fileArray)
                .map(File::toString)
                .collect(Collectors.joining(",")
                );

    }

    public static boolean isFileExist(Context context, String fileName) {
        File rootPath = context.getFilesDir();
        if (rootPath.exists()) {
            File file = new File(rootPath, fileName);
            return file.exists();
        }
        return false;
    }

    public static void saveText(Context context, String fileName, String data) throws IOException {
        FileOutputStream fos = context.openFileOutput(fileName, Context.MODE_PRIVATE);
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(fos, StandardCharsets.UTF_8))) {
             writer.write(data);
             writer.newLine();
        }
    }

    public static String readText(Context context, String fileName) throws IOException {
        FileInputStream fis = context.openFileInput(fileName);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(fis, StandardCharsets.UTF_8)) ) {
            return reader.readLine();
        }
    }

    public static void saveLines(Context context, String fileName, List<String> lines) throws IOException {
        FileOutputStream fos = context.openFileOutput(fileName, Context.MODE_PRIVATE);
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
        try (BufferedWriter writer = new BufferedWriter(outputStreamWriter)) {
            for (String line : lines) {
                writer.append(line).append('\n');
            }
        }
    }

    public List<String> readLines(Context context, String fileName) throws IOException {
        List<String> result = new ArrayList<>();
        FileInputStream fis = context.openFileInput(fileName);
        InputStreamReader inputStreamReader = new InputStreamReader(fis, StandardCharsets.UTF_8);
        try (BufferedReader reader = new BufferedReader(inputStreamReader)) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.add(line + '\n');
            }
        }
        return result;
    }
}
