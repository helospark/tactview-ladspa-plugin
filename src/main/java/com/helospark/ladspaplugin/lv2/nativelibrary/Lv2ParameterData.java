package com.helospark.ladspaplugin.lv2.nativelibrary;

import java.util.List;

import com.sun.jna.Structure;

public class Lv2ParameterData extends Structure implements Structure.ByReference {
    public int index;
    public float data;

    @Override
    protected List<String> getFieldOrder() {
        return List.of("index", "data");
    }
}
