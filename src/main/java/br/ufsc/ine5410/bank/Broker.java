package br.ufsc.ine5410.bank;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;

public class Broker {
    private HashMap<Investor, Account> accounts = new HashMap<>();

    @Nullable
    public Account getAccount(@Nonnull Investor investor) {
        return accounts.get(investor);
    }

    @Nonnull
    public Account createAccount(@Nonnull Investor investor) {
        Account acc = accounts.get(investor);
        if (acc == null) {
            int id = (hashCode() % 100) * 1000 + accounts.size();
            acc = new Account(this, id, investor);
            accounts.put(investor, acc);
        }
        return acc;
    }
}
