package com.dku.priorify.model;

import org.springframework.data.mongodb.core.mapping.Document;

@Document
public class CategoryPriority {
    private String category;
    private int rank;  // 1,2,3
    private boolean isHighPriority;  // true=상위, false=하위

    public CategoryPriority() {}

    public CategoryPriority(String category, int rank, boolean isHighPriority) {
        this.category = category;
        this.rank = rank;
        this.isHighPriority = isHighPriority;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public boolean isHighPriority() {
        return isHighPriority;
    }

    public void setHighPriority(boolean highPriority) {
        isHighPriority = highPriority;
    }
} 