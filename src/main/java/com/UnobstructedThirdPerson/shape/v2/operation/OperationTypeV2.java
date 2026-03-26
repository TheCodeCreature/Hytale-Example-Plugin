package com.UnobstructedThirdPerson.shape.v2.operation;

public enum OperationTypeV2 {
    
    DEFINE(10),
    FILL(20),
    CUT(20),
    INTERSECT(30),
    SUBTRACT(40),
    FILL_REMAINING(50),
    EXCLUDE(100);
    
    private final int defaultPriority;
    
    OperationTypeV2(int defaultPriority) {
        this.defaultPriority = defaultPriority;
    }
    
    public int getDefaultPriority() {
        return defaultPriority;
    }
}
