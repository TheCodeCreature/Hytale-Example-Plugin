package com;

import com.hypixel.hytale.protocol.CameraInteraction;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.InteractionSettings;
import com.hypixel.hytale.protocol.io.ProtocolException;
import com.hypixel.hytale.protocol.io.ValidationResult;
import com.hypixel.hytale.protocol.io.VarInt;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class TestPlugin extends CameraInteraction {


    @Override
    public int serialize(@Nonnull ByteBuf buf) {
        int startPos = buf.writerIndex();

        return startPos;
    }
}
