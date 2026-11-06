package dev.kollegen.client.emotes;

public enum EmoteType {
    ONE_TIME("⚡"),
    IDLE("⏸"),
    CONTINUOUS("↻");

    public final String symbol;

    EmoteType(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return this.symbol;
    }
}
