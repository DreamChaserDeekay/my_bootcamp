package com.example.jvmlab.tx;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audits")
public class Audit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    String message;
    Instant at = Instant.now();

    public Audit() {}
    public Audit(String m) { this.message = m; }

    public Long getId() { return id; }
    public String getMessage() { return message; }
    public Instant getAt() { return at; }
}
