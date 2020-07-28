package com.helospark.ladspaplugin.nativelibrary;

import java.util.List;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;

public class LadspaGetPluginDescriptionRequest extends Structure implements Structure.ByReference {
    public int index;

    // output
    public int parameterCount;
    public Pointer parameterNames; // string
    public Pointer parameterTypes; // int
    public Pointer parameterLowerValues; // double
    public Pointer parameterUpperValues; // double

    public int channelCount;

    public String name;
    public String label;

    @Override
    protected List<String> getFieldOrder() {
        return List.of("index", "parameterCount", "parameterNames", "parameterTypes", "parameterLowerValues", "parameterUpperValues", "channelCount", "name", "label");
    }
}
