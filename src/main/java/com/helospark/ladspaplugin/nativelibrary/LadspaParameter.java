package com.helospark.ladspaplugin.nativelibrary;

import java.util.List;

import com.sun.jna.Structure;

public class LadspaParameter extends Structure implements Structure.ByReference {
    public int index;
    public float value;

    @Override
    protected List<String> getFieldOrder() {
        return List.of("index", "value");
    }
}
