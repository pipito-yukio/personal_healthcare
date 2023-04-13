package com.examples.android.healthcare.data;

public class NocturiaFactors {
    private final int midnightToiletVisits;
    private final boolean hasCoffee;
    private final boolean hasTea;
    private final boolean hasAlcohol;
    private final boolean hasNutritionDrink;
    private final boolean hasSportsDrink;
    private final boolean hasDiuretic;
    private final boolean takeMedicine;
    private final boolean takeBathing;
    private final String conditionMemo;

    public NocturiaFactors(int midnightToiletVisits,
           boolean hasCoffee, boolean hasTea, boolean hasAlcohol, boolean hasNutritionDrink,
           boolean hasSportsDrink, boolean hasDiuretic, boolean takeMedicine, boolean takeBathing,
           String conditionMemo) {
        this.midnightToiletVisits = midnightToiletVisits;
        this.hasCoffee = hasCoffee;
        this.hasTea = hasTea;
        this.hasAlcohol = hasAlcohol;
        this.hasNutritionDrink = hasNutritionDrink;
        this.hasSportsDrink = hasSportsDrink;
        this.hasDiuretic = hasDiuretic;
        this.takeMedicine = takeMedicine;
        this.takeBathing = takeBathing;
        this.conditionMemo = conditionMemo;
    }

    public int getMidnightToiletVisits() { return midnightToiletVisits; }
    public boolean hasCoffee() { return hasCoffee; }
    public boolean hasTea() { return hasTea; }
    public boolean hasAlcohol() { return hasAlcohol; }
    public boolean hasNutritionDrink() { return hasNutritionDrink; }
    public boolean hasSportsDrink() { return hasSportsDrink; }
    public boolean hasDiuretic() { return hasDiuretic; }
    public boolean isTakeMedicine() { return takeMedicine; }
    public boolean isTakeBathing() { return takeBathing; }
    public String getConditionMemo() { return conditionMemo; }

    @Override
    public String toString() {
        return "NocturiaFactors{" +
                "midnightToiletVisits=" + midnightToiletVisits +
                ", hasCoffee=" + hasCoffee +
                ", hasTea=" + hasTea +
                ", hasAlcohol=" + hasAlcohol +
                ", hasNutritionDrink=" + hasNutritionDrink +
                ", hasSportsDrink=" + hasSportsDrink +
                ", hasDiuretic=" + hasDiuretic +
                ", takeMedicine=" + takeMedicine +
                ", takeBathing=" + takeBathing +
                ", conditionMemo='" + conditionMemo + '\'' +
                '}';
    }
}
