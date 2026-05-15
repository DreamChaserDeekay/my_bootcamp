package com.example.dblab.web;

import com.example.dblab.domain.Account;
import com.example.dblab.domain.AccountRepository;
import com.example.dblab.service.TransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountRepository repo;
    private final TransferService transferService;

    @GetMapping
    @Transactional(readOnly = true)     // readOnly 트랜잭션
    public List<Account> list() {
        return repo.findAll();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public Account get(@PathVariable Integer id) {
        return repo.findById(id).orElseThrow();
    }

    @PostMapping("/transfer/pessimistic")
    public String transferP(@RequestParam Integer from, @RequestParam Integer to,
                            @RequestParam BigDecimal amount) {
        transferService.transferPessimistic(from, to, amount);
        return "OK";
    }

    @PostMapping("/transfer/optimistic")
    public String transferO(@RequestParam Integer from, @RequestParam Integer to,
                            @RequestParam BigDecimal amount) {
        transferService.transferOptimistic(from, to, amount);
        return "OK";
    }
}
