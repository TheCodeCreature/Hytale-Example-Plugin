---
topic: "Commands"
category: "Plugins"
updated: 2026-04-16
sources: ["codebase analysis"]
---

# Commands

## Summary

Plugins register chat commands that players can execute in-game. Commands can be simple or organized into command groups with subcommands.

## Command Types

### AbstractPlayerCommand

For commands that require a player context:

```java
public class StartCommand extends AbstractPlayerCommand {
    private final OptionalArg<Integer> radiusArg;

    public StartCommand() {
        super("Start", "Starts the transparency system");
        this.radiusArg = withOptionalArg("Radius", "Set the radius size", 
            ArgTypes.INTEGER)
            .addValidator(Validators.min(0))
            .addValidator(Validators.max(20));
    }

    @Override
    protected void execute(@NonNull CommandContext context,
                           @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world) {
        int radius = this.radiusArg.provided(context) 
            ? this.radiusArg.get(context) 
            : 5;
        // Command logic here
    }
}
```

### AbstractCommandCollection

For grouping subcommands under a parent:

```java
public class MyCommandGroup extends AbstractCommandCollection {

    public MyCommandGroup() {
        super("MyCommand", "My command group description");
        this.setPermissionGroup(GameMode.Adventure);
        this.addAliases("MC", "MyCmd");
        this.addSubCommand(new StartCommand());
        this.addSubCommand(new StopCommand());
    }
}
```

## Registration

```java
@Override
protected void setup() {
    this.getCommandRegistry().registerCommand(new MyCommandGroup());
}
```

## Command Arguments

### Argument Types (ArgTypes)

| Type | Java Type | Description |
|------|-----------|-------------|
| `ArgTypes.INTEGER` | `Integer` | Whole number |
| `ArgTypes.FLOAT` | `Float` | Floating-point number |
| `ArgTypes.DOUBLE` | `Double` | Double-precision number |
| `ArgTypes.STRING` | `String` | Text input |
| `ArgTypes.BOOLEAN` | `Boolean` | true/false |
| `ArgTypes.PLAYER_REF` | `PlayerRef` | Online player |
| `ArgTypes.BLOCK_TYPE_ASSET` | `BlockType` | Block type reference |
| `ArgTypes.ITEM_ASSET` | `Item` | Item reference |
| `ArgTypes.GAME_MODE` | `GameMode` | Game mode |
| `ArgTypes.UUID` | `UUID` | UUID value |
| `ArgTypes.RELATIVE_POSITION` | `Relative` | 3D position with ~ support |

### Required vs Optional Arguments

```java
// Required argument
Arg<Float> distanceArg = withArg("Distance", "Camera distance", ArgTypes.FLOAT);

// Optional argument
OptionalArg<Integer> radiusArg = withOptionalArg("Radius", "Size", ArgTypes.INTEGER);

// Check if optional was provided
if (radiusArg.provided(context)) {
    int value = radiusArg.get(context);
}
```

### Validators

```java
withArg("Count", "Item count", ArgTypes.INTEGER)
    .addValidator(Validators.min(0))
    .addValidator(Validators.max(100));
```

## Permission Groups

Commands can be restricted by game mode:

```java
this.setPermissionGroup(GameMode.Adventure);  // Only in Adventure mode
this.setPermissionGroup(GameMode.Creative);   // Only in Creative mode
```

## Aliases

```java
this.addAliases("NoClipCamera", "UCamera", "UC");
// Player can type /UC instead of /UnobstructedCamera
```

## Sending Messages

```java
playerRef.sendMessage(Message.raw("§a[Plugin] Success!"));
playerRef.sendMessage(Message.raw("§cError: something went wrong"));
```

Color codes use `§` prefix:
- `§a` — green
- `§c` — red
- `§e` — yellow
- `§b` — aqua
- `§f` — white

## See Also

- [Plugin Lifecycle](./lifecycle.md)
- [ArgTypes Reference](https://hytalemodding.dev/en/docs/server/argtypes)
