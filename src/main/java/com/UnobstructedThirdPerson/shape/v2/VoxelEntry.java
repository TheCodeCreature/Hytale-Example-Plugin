package com.UnobstructedThirdPerson.shape.v2;

import com.UnobstructedThirdPerson.records.BlockSnapshot;
import com.UnobstructedThirdPerson.shape.v2.fill.BlockFillTypeV2;
import com.UnobstructedThirdPerson.shape.v2.visual.DebugStyle;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class VoxelEntry {
    
    private BlockSnapshot original;
    private BlockFillTypeV2 fill;
    private String ownerId;
    private DebugStyle debugStyle;
    private boolean excluded;
    
    public VoxelEntry() {
        this.debugStyle = DebugStyle.NONE;
    }
    
    @Nullable
    public BlockSnapshot getOriginal() {
        return original;
    }
    
    public void setOriginal(@Nullable BlockSnapshot original) {
        this.original = original;
    }
    
    @Nullable
    public BlockFillTypeV2 getFill() {
        return fill;
    }
    
    public void setFill(@Nullable BlockFillTypeV2 fill) {
        this.fill = fill;
    }
    
    @Nullable
    public String getOwnerId() {
        return ownerId;
    }
    
    public void setOwnerId(@Nullable String ownerId) {
        this.ownerId = ownerId;
    }
    
    @Nonnull
    public DebugStyle getDebugStyle() {
        return debugStyle != null ? debugStyle : DebugStyle.NONE;
    }
    
    public void setDebugStyle(@Nonnull DebugStyle debugStyle) {
        this.debugStyle = debugStyle;
    }
    
    public boolean isExcluded() {
        return excluded;
    }
    
    public void setExcluded(boolean excluded) {
        this.excluded = excluded;
    }
    
    public boolean hasFill() {
        return fill != null;
    }
    
    public boolean isOwnedBy(@Nullable String operationId) {
        if (ownerId == null && operationId == null) {
            return true;
        }
        return ownerId != null && ownerId.equals(operationId);
    }
    
    public void clearFill() {
        this.fill = null;
        this.ownerId = null;
        this.debugStyle = DebugStyle.NONE;
    }
    
    public void write(@Nullable BlockSnapshot original, @Nullable BlockFillTypeV2 fill,
                      @Nullable String ownerId, @Nonnull DebugStyle debugStyle) {
        if (original != null) {
            this.original = original;
        }
        this.fill = fill;
        this.ownerId = ownerId;
        this.debugStyle = debugStyle;
    }
    
    @Nonnull
    public VoxelEntry copy() {
        VoxelEntry copy = new VoxelEntry();
        copy.original = this.original;
        copy.fill = this.fill;
        copy.ownerId = this.ownerId;
        copy.debugStyle = this.debugStyle;
        copy.excluded = this.excluded;
        return copy;
    }
}
