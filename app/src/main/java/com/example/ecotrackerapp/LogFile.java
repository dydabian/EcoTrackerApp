package com.example.ecotrackerapp;

import android.util.Log;

import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class LogFile{

    private final File file;
    public LogFile(String folder) {
        String dateStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd"));
        Log.d("LOGFILE","name:" + dateStamp);
        String name = dateStamp + "_log.csv";
        this.file = new File(folder, name);
    }

    public String getName() {
        return file.getName();
    }

    public String getPath() {
        return file.getAbsolutePath();
    }
    private void writeNewLine(String lineText) {

        try {
            FileWriter fWriter = new FileWriter(file, true);
            fWriter.write(lineText);
            fWriter.flush();
            fWriter.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
    public void addEntry(String action) {
        String line = getTimestamp() + "," + action + "\n";
        writeNewLine(line);
    }

    public int writeTemperatureData(String sensorName, ArrayList<Float> data) {

        addEntry("temperature data downloaded from: " + sensorName);
        int written = 0;
        int failed = 0;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
                for (Float val : data) {
                    writer.write(val.toString());
                    written++;
                    writer.newLine();
                }
                writer.flush();
            } catch (IOException e) {
                failed ++;
                e.printStackTrace();
            }

            if (failed >0) {
                addEntry("failed to write " + failed + " temperature readings");
            }

            return written;
    }

    public File getFile() {
        return file;
    }
}
