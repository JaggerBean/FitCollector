package com.stepcraft;

public enum StepCraftPlayerAction {
    NONE("Open Menu"),
    BAN("Ban"),
    UNBAN("Unban"),
    DELETE("Delete"),
    CLAIM_REWARD("Claim Reward"),
    CLAIM_STATUS("Claim Status"),
    YESTERDAY_STEPS("Day Steps");

    private final String label;

    StepCraftPlayerAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
