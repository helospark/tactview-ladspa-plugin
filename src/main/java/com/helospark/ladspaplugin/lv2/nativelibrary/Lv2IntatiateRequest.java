package com.helospark.ladspaplugin.lv2.nativelibrary;

import java.util.List;

import com.sun.jna.Structure;

public class Lv2IntatiateRequest extends Structure implements Structure.ByReference {
    public String binaryPath;
    public String uri;
    public int sampleRate;

    @Override
    protected List<String> getFieldOrder() {
        return List.of("binaryPath", "uri", "sampleRate");
    }
}
