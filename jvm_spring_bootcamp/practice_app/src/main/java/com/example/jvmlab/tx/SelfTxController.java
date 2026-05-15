package com.example.jvmlab.tx;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tx/self")
public class SelfTxController {

    @Autowired SelfTxService svc;

    @GetMapping
    public String run() {
        return svc.outer();
    }

    @GetMapping("/direct")
    public String direct() {
        // 직접 inner 호출 — 프록시 경유
        return svc.inner();
    }
}

@Service
class SelfTxService {

    public String outer() {
        // ❌ self-call → AOP 프록시 우회 → @Transactional 무력화
        return "outer-tx:" + TransactionSynchronizationManager.isActualTransactionActive()
             + " | " + inner();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String inner() {
        boolean active = TransactionSynchronizationManager.isActualTransactionActive();
        return "inner-tx:" + active;
    }
}
