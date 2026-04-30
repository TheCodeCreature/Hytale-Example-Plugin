package com.UnobstructedThirdPerson.placeblock.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

import java.util.*;

public class BlueprintBenchPrefs {
    public static final BuilderCodec<BlueprintBenchPrefs> CODEC = BuilderCodec.builder(BlueprintBenchPrefs.class, BlueprintBenchPrefs::new)
            .append(new KeyedCodec<>("ActiveTab", Codec.STRING, true), (p, v) -> p.activeTab = v, p -> p.activeTab).add()
            .append(new KeyedCodec<>("ActiveSetFilters", new ArrayCodec<>(Codec.STRING, String[]::new), true),
                    (p, v) -> p.activeSetFilters = Arrays.asList(v), p -> p.activeSetFilters.toArray(new String[0])).add()
            .append(new KeyedCodec<>("AffordabilityEnabled", Codec.BOOLEAN, true), (p, v) -> p.affordabilityEnabled = v, p -> p.affordabilityEnabled).add()
            .append(new KeyedCodec<>("ShowUncategorized", Codec.BOOLEAN, true), (p, v) -> p.showUncategorized = v, p -> p.showUncategorized).add()
            .append(new KeyedCodec<>("SearchQuery", Codec.STRING, true), (p, v) -> p.searchQuery = v, p -> p.searchQuery).add()
            .append(new KeyedCodec<>("SelectedRecipeId", Codec.STRING, true), (p, v) -> p.selectedRecipeId = v, p -> p.selectedRecipeId).add()
            .build();

    String activeTab = "All";
    List<String> activeSetFilters = new ArrayList<>();
    boolean affordabilityEnabled = true;
    boolean showUncategorized = false;
    String searchQuery = "";
    String selectedRecipeId;
}
