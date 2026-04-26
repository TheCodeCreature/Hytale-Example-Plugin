---
topic: "InteractiveCustomUIPage API Reference"
category: "Plugin API"
updated: 2026-04-24
sources: ["decompiled OpenCustomUIInteraction.java", "decompiled InteractiveCustomUIPage.java", "decompiled CustomUIPage.java", "decompiled UICommandBuilder.java", "decompiled UIEventBuilder.java", "decompiled EventData.java", "decompiled ItemGridSlot.java", "decompiled CommandListPage.java", "decompiled EntitySpawnPage.java", "decompiled BarterPage.java", "decompiled CustomPageLifetime.java", "decompiled CustomUIEventBindingType.java"]
---

# InteractiveCustomUIPage — Complete API Reference (Raw Decompiled Code)

## 1. OpenCustomUIInteraction — Registration Methods

**Package:** `com.hypixel.hytale.server.core.modules.interaction.interaction.config.server`

### registerSimple()

```java
public static void registerSimple(
    @Nonnull PluginBase plugin,
    Class<?> tClass,
    String id,
    @Nonnull Function<PlayerRef, CustomUIPage> supplier
) {
    registerCustomPageSupplier(plugin, tClass, id, (ref, componentAccessor, playerRef, context) -> supplier.apply(playerRef));
}
```

- `plugin` — your plugin instance (`this`)
- `tClass` — any class used for codec identification (typically your page supplier class or the plugin class)
- `id` — unique string ID for the page type (must match asset JSON `"Page"` key)
- `supplier` — lambda `PlayerRef → CustomUIPage`. Only receives `playerRef`; the `Ref<EntityStore>`, `ComponentAccessor`, and `InteractionContext` are discarded.

### registerCustomPageSupplier() (what registerSimple delegates to)

```java
public static <S extends OpenCustomUIInteraction.CustomPageSupplier> void registerCustomPageSupplier(
    @Nonnull PluginBase plugin,
    Class<?> tClass,
    String id,
    @Nonnull S supplier
) {
    plugin.getCodecRegistry(PAGE_CODEC)
        .register(
            id,
            (Class<? extends OpenCustomUIInteraction.CustomPageSupplier>)supplier.getClass(),
            (Codec<? extends OpenCustomUIInteraction.CustomPageSupplier>)BuilderCodec.builder(tClass, () -> supplier).build()
        );
}
```

### registerBlockCustomPage() (Deprecated)

```java
@Deprecated
public static <T extends BlockState> void registerBlockCustomPage(
    @Nonnull PluginBase plugin,
    Class<?> tClass,
    String id,
    @Nonnull Class<T> stateClass,
    @Nonnull OpenCustomUIInteraction.BlockCustomPageSupplier<T> blockSupplier
) {
    registerBlockCustomPage(plugin, tClass, id, stateClass, blockSupplier, false);
}

@Deprecated
public static <T extends BlockState> void registerBlockCustomPage(
    @Nonnull PluginBase plugin,
    Class<?> tClass,
    String id,
    @Nonnull Class<T> stateClass,
    @Nonnull OpenCustomUIInteraction.BlockCustomPageSupplier<T> blockSupplier,
    boolean createState
) {
    OpenCustomUIInteraction.CustomPageSupplier supplier = (ref, componentAccessor, playerRef, context) -> {
        BlockPosition targetBlock = context.getTargetBlock();
        if (targetBlock == null) {
            return null;
        } else {
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData().getWorld();
            BlockState state = world.getState(targetBlock.x, targetBlock.y, targetBlock.z, true);
            if (state == null) {
                if (createState) {
                    WorldChunk chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(targetBlock.x, targetBlock.z));
                    state = BlockStateModule.get()
                        .createBlockState(
                            stateClass,
                            chunk,
                            new Vector3i(targetBlock.x, targetBlock.y, targetBlock.z),
                            chunk.getBlockType(targetBlock.x, targetBlock.y, targetBlock.z)
                        );
                    chunk.setState(targetBlock.x, targetBlock.y, targetBlock.z, state);
                }
                if (state == null) {
                    return null;
                }
            }
            return stateClass.isInstance(state) ? blockSupplier.tryCreate(playerRef, stateClass.cast(state)) : null;
        }
    };
    registerCustomPageSupplier(plugin, tClass, id, supplier);
}
```

### registerBlockEntityCustomPage()

```java
public static void registerBlockEntityCustomPage(
    @Nonnull PluginBase plugin,
    Class<?> tClass,
    String id,
    @Nonnull OpenCustomUIInteraction.BlockEntityCustomPageSupplier blockSupplier
) {
    OpenCustomUIInteraction.CustomPageSupplier supplier = (ref, componentAccessor, playerRef, context) -> {
        BlockPosition targetBlock = context.getTargetBlock();
        if (targetBlock == null) {
            return null;
        } else {
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData().getWorld();
            WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(targetBlock.x, targetBlock.z));
            if (chunk == null) {
                return null;
            } else {
                BlockPosition targetBaseBlock = world.getBaseBlock(targetBlock);
                Ref<ChunkStore> blockEntityRef = chunk.getBlockComponentEntity(targetBaseBlock.x, targetBaseBlock.y, targetBaseBlock.z);
                return blockEntityRef == null ? null : blockSupplier.tryCreate(playerRef, blockEntityRef);
            }
        }
    };
    registerCustomPageSupplier(plugin, tClass, id, supplier);
}
```

### Functional Interfaces

```java
@FunctionalInterface
public interface CustomPageSupplier {
    @Nullable
    CustomUIPage tryCreate(Ref<EntityStore> var1, ComponentAccessor<EntityStore> var2, PlayerRef var3, InteractionContext var4);
}

@FunctionalInterface
public interface BlockCustomPageSupplier<T extends BlockState> {
    CustomUIPage tryCreate(PlayerRef var1, T var2);
}

@FunctionalInterface
public interface BlockEntityCustomPageSupplier {
    CustomUIPage tryCreate(PlayerRef var1, Ref<ChunkStore> var2);
}
```

### How firstRun() opens the page

```java
@Override
protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
    Ref<EntityStore> ref = context.getEntity();
    CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
    Player playerComponent = commandBuffer.getComponent(ref, Player.getComponentType());
    if (playerComponent != null) {
        PageManager pageManager = playerComponent.getPageManager();
        if (pageManager.getCustomPage() == null) {
            PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
            assert playerRef != null;
            CustomUIPage page = this.customPageSupplier.tryCreate(ref, commandBuffer, playerRef, context);
            if (page != null) {
                Store<EntityStore> store = commandBuffer.getStore();
                pageManager.openCustomPage(ref, store, page);
            }
        }
    }
}
```

---

## 2. CustomUIPage (base class)

**Package:** `com.hypixel.hytale.server.core.entity.entities.player.pages`

```java
public abstract class CustomUIPage {
    @Nonnull
    protected final PlayerRef playerRef;
    @Nonnull
    protected CustomPageLifetime lifetime;

    public CustomUIPage(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime) {
        this.playerRef = playerRef;
        this.lifetime = lifetime;
    }

    public void setLifetime(@Nonnull CustomPageLifetime lifetime) {
        this.lifetime = lifetime;
    }

    @Nonnull
    public CustomPageLifetime getLifetime() {
        return this.lifetime;
    }

    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, String rawData) {
        throw new UnsupportedOperationException("CustomUIPage doesn't support events! " + this + ": " + rawData);
    }

    // THE KEY METHOD — subclasses implement this
    public abstract void build(
        @Nonnull Ref<EntityStore> var1,
        @Nonnull UICommandBuilder var2,
        @Nonnull UIEventBuilder var3,
        @Nonnull Store<EntityStore> var4
    );

    protected void rebuild() {
        Ref<EntityStore> ref = this.playerRef.getReference();
        if (ref != null) {
            Store<EntityStore> store = ref.getStore();
            Player playerComponent = store.getComponent(ref, Player.getComponentType());
            UICommandBuilder commandBuilder = new UICommandBuilder();
            UIEventBuilder eventBuilder = new UIEventBuilder();
            this.build(ref, commandBuilder, eventBuilder, ref.getStore());
            playerComponent.getPageManager()
                .updateCustomPage(new CustomPage(this.getClass().getName(), false, true, this.lifetime, commandBuilder.getCommands(), eventBuilder.getEvents()));
        }
    }

    protected void sendUpdate() {
        this.sendUpdate(null, false);
    }

    protected void sendUpdate(@Nullable UICommandBuilder commandBuilder) {
        this.sendUpdate(commandBuilder, false);
    }

    protected void sendUpdate(@Nullable UICommandBuilder commandBuilder, boolean clear) {
        Ref<EntityStore> ref = this.playerRef.getReference();
        if (ref != null) {
            Store<EntityStore> store = ref.getStore();
            Player playerComponent = store.getComponent(ref, Player.getComponentType());
            playerComponent.getPageManager()
                .updateCustomPage(
                    new CustomPage(
                        this.getClass().getName(),
                        false,
                        clear,
                        this.lifetime,
                        commandBuilder != null ? commandBuilder.getCommands() : UICommandBuilder.EMPTY_COMMAND_ARRAY,
                        UIEventBuilder.EMPTY_EVENT_BINDING_ARRAY
                    )
                );
        }
    }

    protected void close() {
        Ref<EntityStore> ref = this.playerRef.getReference();
        if (ref != null) {
            Store<EntityStore> store = ref.getStore();
            Player playerComponent = store.getComponent(ref, Player.getComponentType());
            playerComponent.getPageManager().setPage(ref, store, Page.None);
        }
    }

    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
    }
}
```

---

## 3. InteractiveCustomUIPage\<T\> (typed event handling subclass)

**Package:** `com.hypixel.hytale.server.core.entity.entities.player.pages`

```java
public abstract class InteractiveCustomUIPage<T> extends CustomUIPage {
    @Nonnull
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    @Nonnull
    protected final BuilderCodec<T> eventDataCodec;

    public InteractiveCustomUIPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull CustomPageLifetime lifetime,
        @Nonnull BuilderCodec<T> eventDataCodec
    ) {
        super(playerRef, lifetime);
        this.eventDataCodec = eventDataCodec;
    }

    // Override this — receives deserialized typed event data
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull T data) {
    }

    // 3-arg sendUpdate with eventBuilder support (InteractiveCustomUIPage adds this)
    protected void sendUpdate(@Nullable UICommandBuilder commandBuilder, @Nullable UIEventBuilder eventBuilder, boolean clear) {
        Ref<EntityStore> ref = this.playerRef.getReference();
        if (ref != null) {
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData().getWorld();
            world.execute(
                () -> {
                    if (ref.isValid()) {
                        Player playerComponent = store.getComponent(ref, Player.getComponentType());
                        assert playerComponent != null;
                        playerComponent.getPageManager()
                            .updateCustomPage(
                                new CustomPage(
                                    this.getClass().getName(),
                                    false,
                                    clear,
                                    this.lifetime,
                                    commandBuilder != null ? commandBuilder.getCommands() : UICommandBuilder.EMPTY_COMMAND_ARRAY,
                                    eventBuilder != null ? eventBuilder.getEvents() : UIEventBuilder.EMPTY_EVENT_BINDING_ARRAY
                                )
                            );
                    }
                }
            );
        }
    }

    // Raw JSON → typed T deserialization bridge
    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, String rawData) {
        ExtraInfo extraInfo = ExtraInfo.THREAD_LOCAL.get();
        T data;
        try {
            data = this.eventDataCodec.decodeJson(new RawJsonReader(rawData.toCharArray()), extraInfo);
        } catch (IOException var7) {
            throw new RuntimeException(var7);
        }
        extraInfo.getValidationResults().logOrThrowValidatorExceptions(LOGGER);
        this.handleDataEvent(ref, store, data);
    }

    @Override
    protected void sendUpdate(@Nullable UICommandBuilder commandBuilder, boolean clear) {
        this.sendUpdate(commandBuilder, null, clear);
    }
}
```

**Key difference from base:** `InteractiveCustomUIPage.sendUpdate()` wraps the update in `world.execute()` for thread safety, while `CustomUIPage.sendUpdate()` does NOT.

---

## 4. UICommandBuilder — Full Class

**Package:** `com.hypixel.hytale.server.core.ui.builder`

```java
public class UICommandBuilder {
    private static final Map<Class, Codec> CODEC_MAP = new Object2ObjectOpenHashMap<>();
    public static final CustomUICommand[] EMPTY_COMMAND_ARRAY = new CustomUICommand[0];
    @Nonnull
    private final List<CustomUICommand> commands = new ObjectArrayList<>();

    // DOM manipulation
    @Nonnull public UICommandBuilder clear(String selector)
    @Nonnull public UICommandBuilder remove(String selector)

    // Append a .ui template document
    @Nonnull public UICommandBuilder append(String documentPath)
    @Nonnull public UICommandBuilder append(String selector, String documentPath)

    // Append inline UI markup (raw document string, not a path)
    @Nonnull public UICommandBuilder appendInline(String selector, String document)

    // Insert before
    @Nonnull public UICommandBuilder insertBefore(String selector, String documentPath)
    @Nonnull public UICommandBuilder insertBeforeInline(String selector, String document)

    // Set property values
    @Nonnull public <T> UICommandBuilder set(String selector, @Nonnull Value<T> ref)  // reference-only
    @Nonnull public UICommandBuilder setNull(String selector)
    @Nonnull public UICommandBuilder set(String selector, @Nonnull String str)
    @Nonnull public UICommandBuilder set(String selector, @Nonnull Message message)
    @Nonnull public UICommandBuilder set(String selector, boolean b)
    @Nonnull public UICommandBuilder set(String selector, float n)
    @Nonnull public UICommandBuilder set(String selector, int n)
    @Nonnull public UICommandBuilder set(String selector, double n)
    @Nonnull public UICommandBuilder setObject(String selector, @Nonnull Object data)  // uses CODEC_MAP
    @Nonnull public <T> UICommandBuilder set(String selector, @Nonnull T[] data)       // array via CODEC_MAP
    @Nonnull public <T> UICommandBuilder set(String selector, @Nonnull List<T> data)   // list via CODEC_MAP

    @Nonnull public CustomUICommand[] getCommands()

    // Registered codec types (what setObject/set(array/list) supports):
    static {
        CODEC_MAP.put(Area.class, Area.CODEC);
        CODEC_MAP.put(ItemGridSlot.class, ItemGridSlot.CODEC);
        CODEC_MAP.put(ItemStack.class, ItemStack.CODEC);
        CODEC_MAP.put(LocalizableString.class, LocalizableString.CODEC);
        CODEC_MAP.put(PatchStyle.class, PatchStyle.CODEC);
        CODEC_MAP.put(DropdownEntryInfo.class, DropdownEntryInfo.CODEC);
        CODEC_MAP.put(Anchor.class, Anchor.CODEC);
    }
}
```

### Selector syntax patterns (from engine examples)

| Selector | Meaning |
|----------|---------|
| `"#CommandList"` | Element with id `CommandList` |
| `"#CommandList[0]"` | First child of `#CommandList` |
| `"#CommandList[0] #Button.Text"` | `.Text` property on `#Button` inside first child |
| `"#CommandList[0].Style"` | `.Style` property on first child |
| `"#NPCContent.Visible"` | `.Visible` property on `#NPCContent` |
| `"#ScaleSlider.Value"` | `.Value` property on `#ScaleSlider` |

---

## 5. UIEventBuilder — Full Class

**Package:** `com.hypixel.hytale.server.core.ui.builder`

```java
public class UIEventBuilder {
    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    public static final CustomUIEventBinding[] EMPTY_EVENT_BINDING_ARRAY = new CustomUIEventBinding[0];
    @Nonnull
    private final List<CustomUIEventBinding> events = new ObjectArrayList<>();

    @Nonnull
    public UIEventBuilder addEventBinding(CustomUIEventBindingType type, String selector) {
        return this.addEventBinding(type, selector, null);
    }

    @Nonnull
    public UIEventBuilder addEventBinding(CustomUIEventBindingType type, String selector, boolean locksInterface) {
        return this.addEventBinding(type, selector, null, locksInterface);
    }

    @Nonnull
    public UIEventBuilder addEventBinding(CustomUIEventBindingType type, String selector, EventData data) {
        return this.addEventBinding(type, selector, data, true);  // locksInterface defaults TRUE
    }

    @Nonnull
    public UIEventBuilder addEventBinding(
        CustomUIEventBindingType type,
        String selector,
        @Nullable EventData data,
        boolean locksInterface
    ) {
        String dataString = null;
        if (data != null) {
            ExtraInfo extraInfo = ExtraInfo.THREAD_LOCAL.get();
            dataString = MapCodec.STRING_HASH_MAP_CODEC.encode(data.events(), extraInfo).asDocument().toJson();
            extraInfo.getValidationResults().logOrThrowValidatorExceptions(LOGGER);
        }
        this.events.add(new CustomUIEventBinding(type, selector, dataString, locksInterface));
        return this;
    }

    @Nonnull
    public CustomUIEventBinding[] getEvents() {
        return this.events.toArray(CustomUIEventBinding[]::new);
    }
}
```

---

## 6. EventData — Full Class

**Package:** `com.hypixel.hytale.server.core.ui.builder`

```java
public record EventData(Map<String, String> events) {
    public EventData() {
        this(new Object2ObjectOpenHashMap<>());
    }

    @Nonnull
    public EventData append(String key, String value) {
        return this.put(key, value);
    }

    @Nonnull
    public <T extends Enum<T>> EventData append(String key, @Nonnull T enumValue) {
        return this.put(key, enumValue.name());
    }

    @Nonnull
    public EventData put(String key, String value) {
        this.events.put(key, value);
        return this;
    }

    @Nonnull
    public static EventData of(@Nonnull String key, @Nonnull String value) {
        HashMap<String, String> map = new HashMap<>();
        map.put(key, value);
        return new EventData(map);
    }
}
```

### The `@` prefix convention for live values

Keys prefixed with `@` capture **live UI element values** at the moment the event fires. The value string uses UI selector syntax:

```java
// When search input changes, capture its current value and send as "@SearchQuery"
EventData.of("@SearchQuery", "#SearchInput.Value")

// Multiple live values captured together:
new EventData()
    .append("Type", "Spawn")           // static string
    .append("@Count", "#Count.Value")  // live value from #Count element
    .append("@Scale", "#ScaleSlider.Value")  // live value from slider
```

When deserialized into the event data class, keys with `@` map to fields with `@` in their codec key:
```java
.addField(new KeyedCodec<>("@SearchQuery", Codec.STRING), (entry, s) -> entry.searchQuery = s, entry -> entry.searchQuery)
.addField(new KeyedCodec<>("@Count", Codec.INTEGER), (entry, s) -> entry.count = s, entry -> entry.count)
```

---

## 7. CustomPageLifetime Enum

```java
public enum CustomPageLifetime {
    CantClose(0),                          // Player cannot dismiss
    CanDismiss(1),                         // Player can press Esc to dismiss
    CanDismissOrCloseThroughInteraction(2); // Can dismiss OR auto-close when interaction ends
}
```

---

## 8. CustomUIEventBindingType Enum (all event types)

```java
public enum CustomUIEventBindingType {
    Activating(0),              // Button click / activation
    RightClicking(1),
    DoubleClicking(2),
    MouseEntered(3),
    MouseExited(4),
    ValueChanged(5),            // Input/slider value changed
    ElementReordered(6),
    Validating(7),
    Dismissing(8),
    FocusGained(9),
    FocusLost(10),
    KeyDown(11),
    MouseButtonReleased(12),
    SlotClicking(13),
    SlotDoubleClicking(14),
    SlotMouseEntered(15),
    SlotMouseExited(16),
    DragCancelled(17),
    Dropped(18),                // Drag-and-drop completed
    SlotMouseDragCompleted(19),
    SlotMouseDragExited(20),
    SlotClickReleaseWhileDragging(21),
    SlotClickPressWhileDragging(22),
    SelectedTabChanged(23);
}
```

---

## 9. ItemGridSlot — Full Class

**Package:** `com.hypixel.hytale.server.core.ui`

```java
public class ItemGridSlot {
    public static final BuilderCodec<ItemGridSlot> CODEC = BuilderCodec.builder(ItemGridSlot.class, ItemGridSlot::new)
        .addField(new KeyedCodec<>("ItemStack", ItemStack.CODEC), (p, t) -> p.itemStack = t, p -> p.itemStack)
        .addField(new KeyedCodec<>("Background", ValueCodec.PATCH_STYLE), (p, t) -> p.background = t, p -> p.background)
        .addField(new KeyedCodec<>("Overlay", ValueCodec.PATCH_STYLE), (p, t) -> p.overlay = t, p -> p.overlay)
        .addField(new KeyedCodec<>("Icon", ValueCodec.PATCH_STYLE), (p, t) -> p.icon = t, p -> p.icon)
        .addField(new KeyedCodec<>("IsItemIncompatible", Codec.BOOLEAN), (p, t) -> p.isItemIncompatible = t, p -> p.isItemIncompatible)
        .addField(new KeyedCodec<>("Name", Codec.STRING), (p, t) -> p.name = t, p -> p.name)
        .addField(new KeyedCodec<>("Description", Codec.STRING), (p, t) -> p.description = t, p -> p.description)
        .addField(new KeyedCodec<>("SkipItemQualityBackground", Codec.BOOLEAN), (p, t) -> p.skipItemQualityBackground = t, p -> p.skipItemQualityBackground)
        .addField(new KeyedCodec<>("IsActivatable", Codec.BOOLEAN), (p, t) -> p.isActivatable = t, p -> p.isActivatable)
        .addField(new KeyedCodec<>("IsItemUncraftable", Codec.BOOLEAN), (p, t) -> p.isItemUncraftable = t, p -> p.isItemUncraftable)
        .build();

    private ItemStack itemStack;
    private Value<PatchStyle> background;
    private Value<PatchStyle> overlay;
    private Value<PatchStyle> icon;
    private boolean isItemIncompatible;
    private String name;
    private String description;
    private boolean skipItemQualityBackground;
    private boolean isActivatable;
    private boolean isItemUncraftable;

    public ItemGridSlot() {}

    public ItemGridSlot(ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    @Nonnull public ItemGridSlot setItemStack(ItemStack itemStack)
    @Nonnull public ItemGridSlot setBackground(Value<PatchStyle> background)
    @Nonnull public ItemGridSlot setOverlay(Value<PatchStyle> overlay)
    @Nonnull public ItemGridSlot setIcon(Value<PatchStyle> icon)
    @Nonnull public ItemGridSlot setItemIncompatible(boolean itemIncompatible)
    @Nonnull public ItemGridSlot setName(String name)
    @Nonnull public ItemGridSlot setDescription(String description)
    public void setItemUncraftable(boolean itemUncraftable)
    public void setActivatable(boolean activatable)
    public void setSkipItemQualityBackground(boolean skipItemQualityBackground)

    // Getters
    public boolean isItemUncraftable()
    public boolean isActivatable()
    public boolean isSkipItemQualityBackground()
}
```

### How to display an item icon

```java
// Simple — just an item in a slot
new ItemGridSlot(new ItemStack("Blocks/Wood/Oak_Wood_Plank", 1))

// With name & description overlay
new ItemGridSlot(new ItemStack("Blocks/Wood/Oak_Wood_Plank", 4))
    .setName("Oak Wood Plank")
    .setDescription("A plank of oak wood")

// Set into a UI element
commandBuilder.set("#ItemMaterialSlot.Slots", new ItemGridSlot[]{
    new ItemGridSlot(new ItemStack(itemId, 1))
});

// Empty slot
new ItemGridSlot()
```

---

## 10. How Pages Access the Player Component / Inventory

Pages get `PlayerRef` in the constructor. To get `Player` (for inventory), use the `Ref<EntityStore>` from `playerRef.getReference()`:

### Pattern from BarterPage (the definitive example):

```java
// In build() — ref is provided as parameter
Player playerComponent = store.getComponent(ref, Player.getComponentType());
ItemContainer playerInventory = playerComponent.getInventory().getCombinedHotbarFirst();

// In handleDataEvent() or any other method — resolve ref from playerRef
Ref<EntityStore> playerEntityRef = this.playerRef.getReference();
Player playerComponent = playerEntityRef != null
    ? store.getComponent(playerEntityRef, Player.getComponentType())
    : null;
if (playerComponent != null) {
    ItemContainer playerInventory = playerComponent.getInventory().getCombinedHotbarFirst();
    // count items, check inventory, etc.
}
```

### Pattern from PortalDevicePageSupplier (checking item in hand):

```java
ItemStack inHand = playerComponent.getInventory().getItemInHand();
```

### Key methods on Player.getInventory():

- `getInventory().getCombinedHotbarFirst()` → `ItemContainer` (combined hotbar + inventory)
- `getInventory().getCombinedArmorHotbarUtilityStorage()` → `ItemContainer` (armor + hotbar + utility + storage)
- `getInventory().getItemInHand()` → `ItemStack`

### In build() vs handleDataEvent()

| Context | How to get Ref | How to get Store |
|---------|---------------|-----------------|
| `build(ref, cmd, evt, store)` | `ref` parameter | `store` parameter |
| `handleDataEvent(ref, store, data)` | `ref` parameter | `store` parameter |
| Any other method | `this.playerRef.getReference()` | `ref.getStore()` |

---

## 11. Complete Engine Example: CommandListPage

This is the shorter of the two complete examples. Key patterns highlighted.

```java
public class CommandListPage extends InteractiveCustomUIPage<CommandListPage.CommandListPageEventData> {

    // --- Style references ---
    private static final Value<String> BUTTON_LABEL_STYLE = Value.ref("Pages/BasicTextButton.ui", "LabelStyle");
    private static final Value<String> BUTTON_LABEL_STYLE_SELECTED = Value.ref("Pages/BasicTextButton.ui", "SelectedLabelStyle");

    // --- Page state ---
    private final List<String> visibleCommands = new ObjectArrayList<>();
    @Nonnull private String searchQuery = "";
    private String selectedCommand;
    // ... more state fields ...

    // --- Constructor ---
    public CommandListPage(@Nonnull PlayerRef playerRef) {
        this(playerRef, null);
    }

    public CommandListPage(@Nonnull PlayerRef playerRef, @Nullable String initialCommand) {
        super(playerRef, CustomPageLifetime.CanDismiss, CommandListPageEventData.CODEC);
        this.initialCommand = initialCommand;
    }

    // --- build() — initial UI construction ---
    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        // 1. Load the page template
        commandBuilder.append("Pages/CommandListPage.ui");

        // 2. Register event bindings
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#SearchInput",
            EventData.of("@SearchQuery", "#SearchInput.Value"),
            false  // locksInterface=false for search (don't lock on every keystroke)
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#BackButton",
            EventData.of("NavigateUp", "true")
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#SendToChatButton",
            EventData.of("SendToChat", "true")
        );

        // 3. Populate initial content
        this.buildCommandList(ref, commandBuilder, eventBuilder, store);

        // 4. Select initial command
        String commandToSelect = this.visibleCommands.getFirst();
        if (this.initialCommand != null && this.visibleCommands.contains(this.initialCommand)) {
            commandToSelect = this.initialCommand;
        }
        this.selectCommand(ref, commandToSelect, commandBuilder, eventBuilder, store);
    }

    // --- handleDataEvent() — process typed events ---
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandListPageEventData data
    ) {
        if (data.searchQuery != null) {
            this.searchQuery = data.searchQuery.trim().toLowerCase();
            UICommandBuilder commandBuilder = new UICommandBuilder();
            UIEventBuilder eventBuilder = new UIEventBuilder();
            this.buildCommandList(ref, commandBuilder, eventBuilder, store);
            this.sendUpdate(commandBuilder, eventBuilder, false);
        } else if (data.command != null) {
            UICommandBuilder commandBuilder = new UICommandBuilder();
            UIEventBuilder eventBuilder = new UIEventBuilder();
            this.selectCommand(ref, data.command, commandBuilder, eventBuilder, store);
            this.sendUpdate(commandBuilder, eventBuilder, false);
        }
        // ... more event handlers for navigateUp, subcommand, variant, sendToChat
    }

    // --- Building list items dynamically ---
    private void buildCommandList(...) {
        commandBuilder.clear("#CommandList");
        // ... filter/sort commands ...
        for (int i = 0; i < this.visibleCommands.size(); i++) {
            String name = this.visibleCommands.get(i);
            commandBuilder.append("#CommandList", "Pages/BasicTextButton.ui");
            commandBuilder.set("#CommandList[" + i + "].TextSpans", Message.raw(name));
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CommandList[" + i + "]",
                EventData.of("Command", name)
            );
            if (name.equals(this.selectedCommand)) {
                commandBuilder.set("#CommandList[" + i + "].Style", BUTTON_LABEL_STYLE_SELECTED);
            }
        }
    }

    // --- Inline UI construction example ---
    // From buildSubcommandTabs():
    commandBuilder.appendInline("#SubcommandCards", "Group { LayoutMode: Left; Anchor: (Bottom: 0); }");

    // --- Event Data class with BuilderCodec ---
    public static class CommandListPageEventData {
        public static final BuilderCodec<CommandListPageEventData> CODEC = BuilderCodec.builder(
                CommandListPageEventData.class, CommandListPageEventData::new
            )
            .addField(new KeyedCodec<>("Command", Codec.STRING), (e, s) -> e.command = s, e -> e.command)
            .addField(new KeyedCodec<>("Subcommand", Codec.STRING), (e, s) -> e.subcommand = s, e -> e.subcommand)
            .addField(new KeyedCodec<>("@SearchQuery", Codec.STRING), (e, s) -> e.searchQuery = s, e -> e.searchQuery)
            .addField(new KeyedCodec<>("NavigateUp", Codec.STRING), (e, s) -> e.navigateUp = s, e -> e.navigateUp)
            .addField(new KeyedCodec<>("Variant", Codec.STRING), (e, s) -> e.variantIndex = s, e -> e.variantIndex)
            .addField(new KeyedCodec<>("SendToChat", Codec.STRING), (e, s) -> e.sendToChat = s, e -> e.sendToChat)
            .build();

        private String command;
        private String subcommand;
        private String searchQuery;
        private String navigateUp;
        private String variantIndex;
        private String sendToChat;
    }
}
```

---

## 12. EntitySpawnPage EventData (for comparison — uses .append().add() pattern)

```java
public static class EntitySpawnPageEventData {
    public static final BuilderCodec<EntitySpawnPageEventData> CODEC = BuilderCodec.builder(
            EntitySpawnPageEventData.class, EntitySpawnPageEventData::new
        )
        .append(new KeyedCodec<>("NPCRole", Codec.STRING), (e, s) -> e.npcRole = s, e -> e.npcRole).add()
        .append(new KeyedCodec<>("ModelId", Codec.STRING), (e, s) -> e.modelId = s, e -> e.modelId).add()
        .append(new KeyedCodec<>("ItemId", Codec.STRING), (e, s) -> e.itemId = s, e -> e.itemId).add()
        .append(new KeyedCodec<>("ItemStackId", Codec.STRING), (e, s) -> e.itemStackId = s, e -> e.itemStackId).add()
        .append(new KeyedCodec<>("Type", Codec.STRING), (e, s) -> e.type = s, e -> e.type).add()
        .append(new KeyedCodec<>("Tab", Codec.STRING), (e, s) -> e.tab = s, e -> e.tab).add()
        .append(new KeyedCodec<>("@SearchQuery", Codec.STRING), (e, s) -> e.searchQuery = s, e -> e.searchQuery).add()
        .append(new KeyedCodec<>("@Count", Codec.INTEGER), (e, s) -> e.count = s, e -> e.count).add()
        .append(new KeyedCodec<>("@RotationOffset", Codec.FLOAT), (e, s) -> e.rotationOffset = s, e -> e.rotationOffset).add()
        .append(new KeyedCodec<>("@Scale", Codec.FLOAT), (e, s) -> e.scale = s, e -> e.scale).add()
        .build();

    private String npcRole;
    private String modelId;
    private String itemId;
    private String itemStackId;
    private String type;         // discriminator: "Select", "TabSwitch", "Spawn", etc.
    private String tab;
    private String searchQuery;
    private int count;
    private float rotationOffset;
    private Float scale;         // nullable Float for optional
}
```

**Key difference:** `CommandListPage` uses `.addField()` (all fields always present), while `EntitySpawnPage` uses `.append().add()` (fields optional, only populated when the specific event sends them). The `.append().add()` pattern is better for pages with many different event types sharing one data class.

---

## Quick Reference Summary

| What | Class | Key Method |
|------|-------|------------|
| Register page supplier | `OpenCustomUIInteraction` | `registerSimple(plugin, class, id, playerRef -> page)` |
| Page base with typed events | `InteractiveCustomUIPage<T>` | extend, implement `build()` + `handleDataEvent()` |
| Construct UI | `UICommandBuilder` | `append()`, `appendInline()`, `set()`, `clear()` |
| Bind events | `UIEventBuilder` | `addEventBinding(type, selector, eventData, locksInterface)` |
| Event payload | `EventData` | `EventData.of(key, value)` or `new EventData().append(k,v).append(k2,v2)` |
| Live UI values | `@`-prefixed keys | `"@SearchQuery"` → `"#SearchInput.Value"` |
| Push updates | `InteractiveCustomUIPage` | `sendUpdate(commandBuilder, eventBuilder, clear)` |
| Display items | `ItemGridSlot` | `new ItemGridSlot(new ItemStack(itemId, qty))` |
| Access inventory | `Player` via `store.getComponent(ref, Player.getComponentType())` | `.getInventory().getCombinedHotbarFirst()` |
| Close page | `CustomUIPage` | `close()` or `playerComponent.getPageManager().setPage(ref, store, Page.None)` |
| Cleanup on dismiss | `CustomUIPage` | override `onDismiss(ref, store)` |
