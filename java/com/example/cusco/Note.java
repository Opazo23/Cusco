package com.example.cusco;

public class Note {
    private String title;
    private String message;

    public Note(String title, String message) {
        this.title = title;
        this.message = message;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public int getContent() {
        return 0;
    }
}
