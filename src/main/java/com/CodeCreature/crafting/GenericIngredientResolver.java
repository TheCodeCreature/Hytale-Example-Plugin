package com.CodeCreature.crafting;

import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * @node    GenericIngredientResolver
 * @wiki    docs/The Fractonomical System/_knowledge/_sources/Hytale/04010000_Crafting-Input-Resolution/Overview.md
 * @intent  Defines the typed generic ingredient resolution contract used by affordability and
 *          planner boundaries while preserving authored input ordering.
 * @wave    4 (generic icons + generic affordability boundary)
 * @status  Wave 4 - implemented typed resolver contract with default bulk adapter
 * @do-not  Introduce concrete removal-policy assumptions in this interface.
 *          Reorder authored inputs in resolveAll.
 */
public interface GenericIngredientResolver {

    /** @intent Resolve one authored material input onto the typed generic ingredient boundary.
     *  @wave   4 - implemented contract
     *  @status implemented
     *  @node   GenericIngredientResolver#resolve
     */
    @Nonnull
    GenericIngredientResolution resolve(@Nonnull MaterialQuantity input, boolean preferNatural);

    /** @intent Resolve authored material inputs in-order through the same generic boundary contract.
     *  @wave   4 - implemented default bulk adapter
     *  @status implemented
     *  @node   GenericIngredientResolver#resolveAll
     */
    @Nonnull
    default List<GenericIngredientResolution> resolveAll(@Nonnull List<MaterialQuantity> inputs,
                                                         boolean preferNatural) {
        List<GenericIngredientResolution> resolved = new ArrayList<>(inputs.size());
        for (MaterialQuantity input : inputs) {
            if (input != null) {
                resolved.add(resolve(input, preferNatural));
            }
        }
        return List.copyOf(resolved);
    }
}
