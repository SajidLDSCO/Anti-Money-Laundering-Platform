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

/** Suspicious Activity Report (called STR in some jurisdictions) raised from an alert. */
@Entity
@Table(name = "sar_reports")
public class SarReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String sarNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alert_id")
    private Alert alert;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(nullable = false, length = 8000)
    private String narrative;

    @Column(nullable = false, length = 100)
    private String filedBy;

    private Instant filedAt;

    @PrePersist
    void onCreate() {
        filedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getSarNumber() { return sarNumber; }
    public void setSarNumber(String sarNumber) { this.sarNumber = sarNumber; }
    public Alert getAlert() { return alert; }
    public void setAlert(Alert alert) { this.alert = alert; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public String getNarrative() { return narrative; }
    public void setNarrative(String narrative) { this.narrative = narrative; }
    public String getFiledBy() { return filedBy; }
    public void setFiledBy(String filedBy) { this.filedBy = filedBy; }
    public Instant getFiledAt() { return filedAt; }
}
