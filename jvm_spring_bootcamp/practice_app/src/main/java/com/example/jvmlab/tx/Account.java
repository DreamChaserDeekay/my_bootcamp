package com.example.jvmlab.tx;

import jakarta.persistence.*;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String owner;
    private long balance;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOwner() { return owner; }
    public void setOwner(String o) { this.owner = o; }
    public long getBalance() { return balance; }
    public void setBalance(long b) { this.balance = b; }
}
