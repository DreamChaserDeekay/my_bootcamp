package com.example.jvmlab.tx;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tx/checked")
public class CheckedTxController {

    @Autowired CheckedTxService svc;
    @Autowired AccountRepo accounts;

    @GetMapping("/setup")
    public String setup() {
        Account a = new Account();
        a.setOwner("alice");
        a.setBalance(1000);
        return "created id=" + accounts.save(a).getId();
    }

    @GetMapping("/{id}/{rollback}")
    public String run(@PathVariable Long id, @PathVariable boolean rollback) {
        try {
            if (rollback) {
                svc.withRollback(id);
            } else {
                svc.noRollback(id);
            }
        } catch (Exception e) {
            // 의도된 throw
        }
        return "balance=" + accounts.findById(id).map(Account::getBalance).orElse(-1L);
    }
}

@Service
class CheckedTxService {

    @Autowired AccountRepo accounts;

    /** ❌ rollbackFor 명시 안 함 → checked 던져도 commit */
    @Transactional
    public void noRollback(Long id) throws Exception {
        Account a = accounts.findById(id).orElseThrow();
        a.setBalance(a.getBalance() - 100);
        throw new Exception("checked exception");
    }

    /** ✅ rollbackFor 명시 → 롤백됨 */
    @Transactional(rollbackFor = Exception.class)
    public void withRollback(Long id) throws Exception {
        Account a = accounts.findById(id).orElseThrow();
        a.setBalance(a.getBalance() - 100);
        throw new Exception("checked exception");
    }
}
