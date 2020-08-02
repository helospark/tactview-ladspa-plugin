package com.helospark.ladspaplugin.lv2.nativelibrary;

import java.nio.FloatBuffer;
import java.util.List;

import com.sun.jna.Structure;

public class Lv2ChannelData extends Structure implements Structure.ByReference {
    public int index;
    public FloatBuffer buffer;

    @Override
    protected List<String> getFieldOrder() {
        return List.of("index", "buffer");
    }
}
