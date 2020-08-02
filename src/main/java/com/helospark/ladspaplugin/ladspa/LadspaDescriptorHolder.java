package com.helospark.ladspaplugin.ladspa;

import java.util.List;

public class LadspaDescriptorHolder {
    String name;
    int channelCount;
    List<LadspaParameterHolder> parameters;

    public LadspaDescriptorHolder(String name, int channelCount, List<LadspaParameterHolder> parameters) {
        this.name = name;
        this.channelCount = channelCount;
        this.parameters = parameters;
    }

}
