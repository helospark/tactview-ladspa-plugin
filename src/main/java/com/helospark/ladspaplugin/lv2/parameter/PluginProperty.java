package com.helospark.ladspaplugin.lv2.parameter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PluginProperty {
    public String uri;
    public String name;
    public String binaryPath;

    public Map<ChannelDesignation, PluginParameterChannel> inputChannels = new HashMap<>();
    public Map<ChannelDesignation, PluginParameterChannel> outputChannels = new HashMap<>();

    public List<PluginParameter> parameter = new ArrayList<>();

    @Override
    public String toString() {
        return "PluginProperty [uri=" + uri + ", name=" + name + ", binaryPath=" + binaryPath + ", inputChannels=" + inputChannels + ", outputChannels=" + outputChannels + ", parameter=" + parameter
                + "]";
    }

}
