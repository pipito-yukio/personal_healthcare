package com.examples.android.healthcare.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class FileUtil {
    public static void saveText(String fileName, String data) throws IOException {
        FileOutputStream fos = new FileOutputStream(fileName);
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(fos, StandardCharsets.UTF_8))) {
            writer.write(data);
            writer.newLine();
        }
    }

    public static String readText(String fileName) throws IOException {
        FileInputStream fis = new FileInputStream(fileName);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(fis, StandardCharsets.UTF_8)) ) {
            return reader.readLine();
        }
    }

    public static void saveLines(String fileName, List<String> lines) throws IOException {
        FileOutputStream fos =  new FileOutputStream(fileName);
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
        try (BufferedWriter writer = new BufferedWriter(outputStreamWriter)) {
            for (String line : lines) {
                writer.append(line).append('\n');
            }
        }
    }

    public static List<String> readLines(String fileName) throws IOException {
        List<String> result = new ArrayList<>();
        FileInputStream fis = new FileInputStream(fileName);
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
