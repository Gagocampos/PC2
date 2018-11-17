package br.ufsc.ine5410.bank;

import javax.annotation.Nonnull;

public class NoSuchAccountException extends Exception {
    private final @Nonnull Investor investor;

    public NoSuchAccountException(@Nonnull Investor investor) {
        this.investor = investor;
    }

    @Override
    public String toString() {
        return String.format("NoSuchAccountException(%s)", investor);
    }
}
