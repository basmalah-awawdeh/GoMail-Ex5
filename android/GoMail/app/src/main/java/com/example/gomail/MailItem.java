package com.example.gomail;
//mail items shown in the inbox like mongoDB id, subject line, etc...
public class MailItem {
    public String id, subject, from, snippet, date, label;
    //constructor
    public MailItem(String id, String subject, String from, String snippet, String date, String label) {
        this.id = id;
        this.subject = subject;
        this.from = from;
        this.snippet = snippet;
        this.date = date;
        this.label = label;
    }
}
