package com.helospark.ladspaplugin.lv2.parameter;

public class PluginParameterChannel {
    public int index;

    public PluginParameterChannel(int index) {
        this.index = index;
    }

    @Override
    public String toString() {
        return "PluginParameterChannel [index=" + index + "]";
    }

}