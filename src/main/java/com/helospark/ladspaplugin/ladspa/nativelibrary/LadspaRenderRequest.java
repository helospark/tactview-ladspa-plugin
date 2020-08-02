package com.helospark.ladspaplugin.ladspa.nativelibrary;

import java.nio.FloatBuffer;
import java.util.List;

import com.sun.jna.Structure;

public class LadspaRenderRequest extends Structure implements Structure.ByReference {
    public int instanceId;
    public int sampleCount;

    public int numberOfParametersDefined;
    public LadspaParameter parameters;

    public FloatBuffer inputLeft;
    public FloatBuffer inputRight;

    public FloatBuffer outputLeft;
    public FloatBuffer outputRight;

    @Override
    protected List<String> getFieldOrder() {
        return List.of("instanceId", "sampleCount", "numberOfParametersDefined", "parameters", "inputLeft", "inputRight", "outputLeft", "outputRight");
    }
}
