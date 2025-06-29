package com.example.ecotrackerapp;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;

public class BluetoothCommand {

    private final String text;
    private int status;
    private ArrayList<String> response;
    private boolean sent;
    private boolean success;
    private boolean done;
    //private Map<String, Integer> status_values  = new HashMap<>();


    public BluetoothCommand(String cmd) {
        this.text = cmd;
        this.sent = false;
        this.done = false;
        this.success = false;
        this.response = new ArrayList<>();
    }

    public byte[] asBytes() {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    public String getText() {
        return text;
    }

    public void appendToResponse(byte[] data) {
        String txt = new String(data, java.nio.charset.StandardCharsets.UTF_8).trim();
        response.add(txt);
    }

    public int responseLength() {
        return response.size();
    }
    public String getResponse() {
        StringBuilder sb = new StringBuilder();
        for (String val: response) {
            sb.append(val).append("\n");
        }
        return sb.toString();
    }

    public boolean sent() {
        return this.sent;
    }
    public boolean succeeded() {
        return this.success;
    }
    public boolean done() {
        return this.done;
    }

    public void setSent(boolean val) {
        this.sent = val;
    }

    public void setFail(boolean val) {
        this.done = true;
    }
    public void setSuccess(boolean val) {
        this.success = val;
        this.done = true;
    }
}
