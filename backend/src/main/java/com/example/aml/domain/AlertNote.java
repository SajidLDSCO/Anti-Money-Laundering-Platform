package com.example.aml.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Append-only investigation trail for an alert: analyst comments and
 * system entries (status changes, assignment, SAR filing).
 */
@Entity
@Table(name = "alert_notes")
public class AlertNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alert_id")
    private Alert alert;

    @Column(nullable = false, length = 100)
    private String author;

    @Column(nullable = false, length = 4000)
    private String text;

    private boolean systemEntry;

    private Instant createdAt;

    protected AlertNote() {
    }

    public AlertNote(Alert alert, String author, String text, boolean systemEntry) {
        this.alert = alert;
        this.author = author;
        this.text = text;
        this.systemEntry = systemEntry;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Alert getAlert() { return alert; }
    public String getAuthor() { return author; }
    public String getText() { return text; }
    public boolean isSystemEntry() { return systemEntry; }
    public Instant getCreatedAt() { return createdAt; }
}
