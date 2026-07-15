---
topic: "Server-Side Localization (i18n) System"
category: "Plugin API / I18n"
updated: 2026-06-03
sources:
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/modules/i18n/I18nModule.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/modules/i18n/parser/LangFileParser.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/Message.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/util/MessageUtil.java"
  - ".tmp_hytale_src/com/hypixel/hytale/protocol/packets/assets/UpdateTranslations.java"
  - "src/main/java/com/CodeCreature/stencil/StencilVisualManager.java"
  - "logs/2026-05-26_13-29-07_server.log"
---

# Server-Side Localization (i18n) System

## Summary

Hytale's localization system is built around `I18nModule`, a core plugin that loads `.lang` files, stores key→value translation maps per language, and sends them to clients via `UpdateTranslations` packets. The **client** performs the final key→text resolution — the server sends localization keys as raw strings in protocol fields, and the client looks them up in its local translation table.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│ SERVER                                                  │
│                                                         │
│  .lang files ──▶ LangFileParser ──▶ I18nModule          │
│  (Server/Languages/en-US/server.lang)   │               │
│                                          │              │
│  I18nModule.getMessage("en-US", key) ◀───┘              │
│        │                                                │
│        │  UpdateTranslations packet (Init)              │
│        ▼                                                │
├────────────────── network ──────────────────────────────┤
│                                                         │
│ CLIENT                                                  │
│                                                         │
│  Translation table: { key → text }                      │
│        │                                                │
│        ▼                                                │
│  ItemBase.translationProperties.name = "server.items.X" │
│  → client looks up key → displays resolved text         │
│  → if no match → displays raw string as fallback (name) │
└─────────────────────────────────────────────────────────┘
```

---

## Q1: How Does a Plugin Resolve a Lang Key to a String at Runtime?

### Server-Side Resolution

Use `I18nModule.get().getMessage(language, key)`:

```java
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;

// Resolve a key server-side
String text = I18nModule.get().getMessage("en-US", "server.items.StencilBook.name");
// Returns: "Stencil Journal"
// Returns null if key not found
```

**Method signature** (from `I18nModule.java`):
```java
@Nullable
public String getMessage(String language, @Nonnull String key)
```

Note: This method strips `[TMP]` prefixes unless `displayTmpTagsInStrings` is enabled in server config.

### Client-Side Resolution (Automatic)

The client resolves keys automatically for certain protocol fields:
- `ItemBase.translationProperties.name` — client looks up the string as a lang key; if not found, displays the raw string
- `ItemBase.translationProperties.description` — client looks up as a lang key
- `ItemQuality.localizationKey` — client looks up for quality label display

**You don't call an API for client-side resolution** — you just set the protocol field to a lang key string and the client does the lookup.

### Message API (for chat, notifications, UI text)

```java
import com.hypixel.hytale.server.core.Message;

// Raw string (no localization)
Message raw = Message.raw("Hello world");

// Translation key (client resolves)
Message translated = Message.translation("server.items.StencilBook.name");

// Translation with named parameters
Message parameterized = Message.translation("stencil.chat.givenStencil")
    .param("item", "Oak Planks")
    .param("count", 5);
```

**Key classes:**
| Class | Package | Purpose |
|-------|---------|---------|
| `I18nModule` | `com.hypixel.hytale.server.core.modules.i18n` | Core i18n plugin — loads .lang files, stores translations, sends to clients |
| `Message` | `com.hypixel.hytale.server.core` | Formatted message with `raw()` and `translation()` factory methods |
| `MessageUtil` | `com.hypixel.hytale.server.core.util` | Formats `{key}` placeholders in translation strings |
| `LangFileParser` | `com.hypixel.hytale.server.core.modules.i18n.parser` | Parses `.lang` files into key→value maps |
| `UpdateTranslations` | `com.hypixel.hytale.protocol.packets.assets` | Network packet that sends translations to clients |

---

## Q2: Does `cmd.set("...Text", value)` Support Lang Key References?

**Short answer: No direct lang key syntax.** The `UICommandBuilder.set(selector, String)` method sends a **raw string** — the client does NOT perform localization lookup on it.

### What works

```java
// Raw string — client displays verbatim
cmd.set("#Label.Text", "Hello World");

// Message object — supports both raw and i18n
cmd.set("#Label.Text", Message.raw("Hello"));
cmd.set("#Label.Text", Message.translation("server.ui.myLabel"));  // ← client resolves the key
```

The `set(selector, Message)` overload sends a `FormattedMessage` protocol object with either `rawText` or `messageId` set. When `messageId` is set, the **client** resolves the translation key from its local table.

### What does NOT work (speculative, no evidence found)

```java
// These patterns do NOT exist in the API:
cmd.set("#Label.Text", "@langKey");           // ❌ No @ prefix for lang keys
cmd.set("#Label.Text", Value.i18n("key"));    // ❌ Value.i18n() does not exist
```

### For item tooltips

Item tooltip text (name/description) uses a **different path** — the raw string in `ItemBase.translationProperties` is resolved by the client automatically. This does NOT go through `cmd.set()`.

---

## Q3: Does the `.lang` File Support Format Parameters?

**Yes — using ICU-style `{paramName}` syntax, NOT `{0}` or `%s`.**

### Syntax

```properties
# Named parameter (correct)
stencil.chat.givenStencil = Given stencil: {item}

# Plural form (ICU MessageFormat)
items.count = You have {count, plural, one {# item} other {# items}}

# Select form
ui.greeting = {gender, select, male {Mr.} female {Ms.} other {Mx.}} {name}
```

### How parameters are passed

From `MessageUtil.formatText()`: parameters use named keys in `{braces}`, supporting:
- `{key}` — simple substitution
- `{key, format}` — formatted substitution (supports `upper`, `lower`, `number`, `plural`, `select`)
- `{key, plural, one {# item} other {# items}}` — ICU plural rules
- `{{` — escaped literal brace

```java
// Server-side: send a parameterized message
Message msg = Message.translation("stencil.chat.givenStencil")
    .param("item", "Oak Planks");
context.sendMessage(msg);
```

### What does NOT work

```properties
# ❌ Positional parameters are NOT supported
stencil.chat.givenStencil = Given stencil: {0}
stencil.chat.givenStencil = Given stencil: %s
```

---

## Q4: How Does the `.lang` File Get Loaded?

### Plugin lang files are auto-loaded — IF `IncludesAssetPack` is `true`

The `I18nModule` registers event handlers for both `LoadAssetEvent` and `AssetPackRegisterEvent`. When a plugin JAR is registered as an asset pack (via `IncludesAssetPack: true` in `manifest.json`), the engine:

1. Opens the JAR as a virtual filesystem
2. `AssetPackRegisterEvent` is dispatched
3. `I18nModule` handles the event and calls `loadMessagesFromPack(pack)`
4. Walks `<JAR_ROOT>/Server/Languages/` for language subdirectories
5. For each subdirectory (e.g., `en-US/`), recursively finds all `.lang` files
6. Parses each file via `LangFileParser.parse()`
7. Computes a **prefix** from the directory structure and filename

### Key prefix computation

The prefix is computed from the file path relative to the language directory:

```
File: Server/Languages/en-US/server.lang
  → languagePath = Server/Languages/en-US/
  → path =         Server/Languages/en-US/server.lang
  → directory = parent = Server/Languages/en-US/  (equals languagePath → no directory prefix)
  → filename = "server.lang" → prefix = "server"

File: Server/Languages/en-US/ui/crafting.lang
  → directory prefix = "ui."
  → filename prefix = "crafting"
  → full prefix = "ui.crafting"
```

So for your file at `Server/Languages/en-US/server.lang` with entry:
```
items.StencilBook.name = Stencil Journal
```

The resolved key becomes: **`server.items.StencilBook.name`**
(prefix `server` + `.` + key `items.StencilBook.name`)

### Loading sequence from logs

```
[I18nModule|P] Loaded 9968 bundled default translations from /bundledDefaults/server.lang
[I18nModule|P] Loaded 9992 entries for 'en-US' from /Server/Languages     ← base game
[I18nModule|P] Loaded 51 entries from /Server/Languages/fallback.lang
                                                                          ← plugin pack loaded later via AssetPackRegisterEvent
```

### Live reloading

For **non-JAR** asset packs (directory-based), `I18nModule` registers an `AssetMonitor` that watches `.lang` files for changes. Modified translations are broadcast to all connected players via `UpdateTranslations(AddOrUpdate)`. JAR-based packs are marked `isImmutable` and are NOT watched.

### Manual translation injection (alternative)

If the lang file isn't loading (e.g., `IncludesAssetPack` is false), you can send translations programmatically via `UpdateTranslations`:

```java
// From StencilVisualManager.sendStencilTranslations()
Map<String, String> translations = new HashMap<>();
translations.put("server.items.stencil.description",
    "A stencil stencil for a specific block.");
UpdateTranslations packet = new UpdateTranslations(UpdateType.AddOrUpdate, translations);
playerRef.getPacketHandler().writeNoCache(packet);
```

This sends the translation directly to a specific player's client. Use `Universe.get().broadcastPacketNoCache(packet)` to send to all players.

**Important**: This only populates the client's translation table — it does NOT add entries to `I18nModule`'s server-side map. For server-side resolution to also work, call `I18nModule.get().getMessage()` won't find manually-sent entries.

---

## Q5: Lang Key Usage in the Existing Codebase

### StencilVisualManager.java

The primary i18n usage in the plugin:

1. **Description key constant** ([StencilVisualManager.java](../../../src/main/java/com/CodeCreature/stencil/StencilVisualManager.java#L88)):
   ```java
   private static final String STENCIL_DESCRIPTION_KEY = "server.items.stencil.description";
   ```

2. **Sending translations to client** ([StencilVisualManager.java](../../../src/main/java/com/CodeCreature/stencil/StencilVisualManager.java#L147-L156)):
   ```java
   private static void sendStencilTranslations(@Nonnull PlayerRef playerRef) {
       Map<String, String> translations = new HashMap<>();
       translations.put(STENCIL_DESCRIPTION_KEY, "A stencil stencil for...");
       UpdateTranslations packet = new UpdateTranslations(UpdateType.AddOrUpdate, translations);
       playerRef.getPacketHandler().writeNoCache(packet);
   }
   ```

3. **Setting description as lang key** ([StencilVisualManager.java](../../../src/main/java/com/CodeCreature/stencil/StencilVisualManager.java#L368)):
   ```java
   packet.translationProperties.description = STENCIL_DESCRIPTION_KEY;
   // Client receives "server.items.stencil.description", looks it up,
   // finds the text sent via UpdateTranslations
   ```

4. **Setting name as raw string** ([StencilVisualManager.java](../../../src/main/java/com/CodeCreature/stencil/StencilVisualManager.java#L378-L381)):
   ```java
   private static String resolveDisplayName(String itemId) {
       return STENCIL_NAME_PREFIX + itemId.replace('_', ' ');
       // Returns "[Stencil] Oak Planks" — raw string, NOT a lang key
   }
   ```

### server.lang file

The lang file at `src/main/resources/Server/Languages/en-US/server.lang` contains:
```properties
items.StencilBook.name = Stencil Journal
items.StencilBook.description = A journal of stencils and stencils for crafting.\n[F] Use — Open the stencil menu.\n[Middle Click] Select — Obtain a stencil from the selected stencil.
items.stencil.description = A stencil stencil for a specific block.\n[F] Use — Place the block using materials from your inventory.\n[Middle Click] Select — Quick-select this stencil in your hotbar.\n[G] Drop — Discard the stencil (it will be destroyed).
```

After prefix resolution, these become:
- `server.items.StencilBook.name`
- `server.items.StencilBook.description`
- `server.items.stencil.description`

### Dual-path pattern

`StencilVisualManager` uses BOTH approaches redundantly:
1. The `.lang` file in the JAR (auto-loaded via `IncludesAssetPack`)
2. Manual `UpdateTranslations` packet per player (in `sendStencilTranslations()`)

This is a **belt-and-suspenders** approach — the manual packet ensures the client has the translation even if the asset pack loading order caused a race condition.

---

## .lang File Format Reference

### Syntax

```properties
# Comments start with #
key = value
key = "quoted value"

# Multiline: end line with backslash
key = first line \
second line \
third line

# Escape sequences
key = Line one\nLine two\tTabbed
```

### Rules (from `LangFileParser`)

| Rule | Details |
|------|---------|
| Comments | Lines starting with `#` are ignored |
| Empty lines | Ignored |
| Key-value separator | `=` (first occurrence) |
| Quoting | Values optionally wrapped in `"double quotes"` — quotes are stripped |
| Multiline | Trailing `\` continues to next line |
| Escape sequences | `\n` → newline, `\t` → tab |
| Duplicate keys | **Error** — `TranslationParseException` thrown |

---

## Gotchas

1. **Keys are prefixed by filename**: A key `foo.bar` in `server.lang` becomes `server.foo.bar`. In `mymod.lang` it becomes `mymod.foo.bar`. Plan your key hierarchy accordingly.

2. **Client does resolution, not server**: `cmd.set("#Label.Text", "server.my.key")` sends the literal string — the client does NOT look it up. Use `cmd.set("#Label.Text", Message.translation("server.my.key"))` instead.

3. **Item tooltip keys are magic**: `ItemBase.translationProperties.name/description` are special — the client automatically resolves these strings as lang keys. This is different from `cmd.set()` which requires `Message.translation()`.

4. **Missing key fallback for Name**: If the client can't resolve a name key, it displays the raw string. This is why `"[Stencil] Oak Planks"` works as a raw name.

5. **Missing key fallback for Description**: Uncertain — all vanilla descriptions use lang keys. Raw string fallback behavior for descriptions is unverified.

6. **`IncludesAssetPack` required**: The `.lang` file in the JAR is only loaded if `manifest.json` has `"IncludesAssetPack": true`. Without this, the `Server/Languages/` directory is ignored.

7. **Validation warnings**: The engine validates item `TranslationProperties` values against loaded translations. Missing keys produce `[LOC] Key 'X' does not exist in server.lang!` warnings (visible in logs).

8. **No `%s` or `{0}` format**: Use ICU-style `{paramName}` syntax only. Positional parameters are not supported.

## See Also

- [Tooltip System](../items/tooltip-system.md) — how item name/description keys are used
- [UI Data Binding](../ui/ui-data-binding.md) — `cmd.set()` and `Message` usage
- [Plugin Asset Loading](../assets/plugin-asset-loading.md) — how `IncludesAssetPack` triggers lang loading
