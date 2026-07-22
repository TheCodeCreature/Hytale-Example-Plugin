package com.CodeCreature.ui.bench;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

import java.util.*;

public class StencilBookPrefs {
    public static final BuilderCodec<StencilBookPrefs> CODEC = BuilderCodec.builder(StencilBookPrefs.class, StencilBookPrefs::new)
            .append(new KeyedCodec<>("ActiveTab", Codec.STRING, true), (p, v) -> p.activeTab = v, p -> p.activeTab).add()
            .append(new KeyedCodec<>("ActiveSetFilters", new ArrayCodec<>(Codec.STRING, String[]::new), true),
                    (p, v) -> p.activeSetFilters = Arrays.asList(v), p -> p.activeSetFilters.toArray(new String[0])).add()
            .append(new KeyedCodec<>("IgnoredSetFilters", new ArrayCodec<>(Codec.STRING, String[]::new), true),
                    (p, v) -> p.ignoredSetFilters = Arrays.asList(v), p -> p.ignoredSetFilters.toArray(new String[0])).add()
            .append(new KeyedCodec<>("ActiveMaterialGroups", new ArrayCodec<>(Codec.STRING, String[]::new), true),
                    (p, v) -> p.activeMaterialGroups = Arrays.asList(v), p -> p.activeMaterialGroups.toArray(new String[0])).add()
            .append(new KeyedCodec<>("AffordabilityMode", Codec.STRING, true), (p, v) -> p.affordabilityMode = v, p -> p.affordabilityMode).add()
            // Migration: read legacy "AffordabilityEnabled" boolean and convert via AffordabilityMode.fromString()
            .append(new KeyedCodec<>("AffordabilityEnabled", Codec.BOOLEAN, true),
                    (p, v) -> { if (p.affordabilityMode == null) p.affordabilityMode = AffordabilityMode.fromString(String.valueOf(v)).name(); },
                    p -> null).add()
            .append(new KeyedCodec<>("ActiveResourceTypes", new ArrayCodec<>(Codec.STRING, String[]::new), true),
                    (p, v) -> p.activeResourceTypes = Arrays.asList(v), p -> p.activeResourceTypes.toArray(new String[0])).add()
            .append(new KeyedCodec<>("ResourceTypesExpanded", Codec.BOOLEAN, true), (p, v) -> p.resourceTypesExpanded = v, p -> p.resourceTypesExpanded).add()
            .append(new KeyedCodec<>("SearchQuery", Codec.STRING, true), (p, v) -> p.searchQuery = v, p -> p.searchQuery).add()
            .append(new KeyedCodec<>("SelectedRecipeId", Codec.STRING, true), (p, v) -> p.selectedRecipeId = v, p -> p.selectedRecipeId).add()
            .append(new KeyedCodec<>("SelectAllSets", Codec.BOOLEAN, true), (p, v) -> p.selectAllSets = v, p -> p.selectAllSets).add()
            .append(new KeyedCodec<>("SelectAllCategories", Codec.BOOLEAN, true), (p, v) -> p.selectAllCategories = v, p -> p.selectAllCategories).add()
            .append(new KeyedCodec<>("SelectedIngredientNodes", new ArrayCodec<>(Codec.STRING, String[]::new), true),
                    (p, v) -> p.selectedIngredientNodes = Arrays.asList(v), p -> p.selectedIngredientNodes.toArray(new String[0])).add()
            .build();

    String activeTab = "All";
    List<String> activeSetFilters = new ArrayList<>();
        List<String> ignoredSetFilters = new ArrayList<>();
    List<String> activeMaterialGroups = new ArrayList<>();
    String affordabilityMode = AffordabilityMode.INVENTORY_DRIVEN.name();
    List<String> activeResourceTypes = new ArrayList<>();
    boolean resourceTypesExpanded = true;
    String searchQuery = "";
    String selectedRecipeId;
    boolean selectAllSets = false;
    boolean selectAllCategories = false;
    List<String> selectedIngredientNodes = new ArrayList<>();
}
