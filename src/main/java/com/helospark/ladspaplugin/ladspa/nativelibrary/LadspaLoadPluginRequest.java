package com.helospark.ladspaplugin.ladspa.nativelibrary;

import java.util.List;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;

public class LadspaLoadPluginRequest extends Structure implements Structure.ByReference {
    public String file;

    // output
    public int numberOfLoadedPlugins;
    public Pointer loadedIndices; // int pointer

    @Override
    protected List<String> getFieldOrder() {
        return List.of("file", "numberOfLoadedPlugins", "loadedIndices");
    }
}
