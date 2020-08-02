package com.helospark.ladspaplugin.lv2.parameter;

import java.util.ArrayList;
import java.util.List;

public class PluginParameter {
    public String name;

    public double min;
    public double max;
    public double defaultValue;

    public int index;

    public List<String> extraProperties = new ArrayList<>();

    public ParameterType type;

    public List<ScalePoint> scalePoints;

    @Override
    public String toString() {
        return "PluginParameter [name=" + name + ", min=" + min + ", max=" + max + ", defaultValue=" + defaultValue + ", index=" + index + ", extraProperties=" + extraProperties + ", type=" + type
                + "]";
    }

}