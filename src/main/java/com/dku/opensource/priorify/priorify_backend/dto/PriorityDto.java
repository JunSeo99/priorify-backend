package com.dku.opensource.priorify.priorify_backend.dto;

import java.util.ArrayList;
import java.util.List;

import com.dku.opensource.priorify.priorify_backend.model.CategoryPriority;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriorityDto {
    private ArrayList<CategoryPriority> highPriorities;
    private ArrayList<CategoryPriority> lowPriorities;
}