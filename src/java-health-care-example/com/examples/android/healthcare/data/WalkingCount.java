package com.examples.android.healthcare.data;

public class WalkingCount {
    private final Integer counts;

    public WalkingCount(Integer counts) {
        this.counts = counts;
    }

    public Integer getCounts() { return counts; }

    @Override
    public String toString() {
        return "WalkingCount{counts=" + counts + '}';
    }
}
