package com.helospark.ladspaplugin.lv2.nativelibrary;

import java.util.List;

import com.sun.jna.Structure;

public class Lv2RunRequest extends Structure implements Structure.ByReference {
    public int instanceId;
    public int numberOfChannelData;
    public int sampleCount;
    public Lv2ChannelData channels; // array

    public int numberOfParameters;
    public Lv2ParameterData parameters; // array

    @Override
    protected List<String> getFieldOrder() {
        return List.of("instanceId", "numberOfChannelData", "sampleCount", "channels", "numberOfParameters", "parameters");
    }
}
