package com.helospark.ladspaplugin.ladspa;

public enum LadspaParameterType {
    LADSPA_PORT_INPUT(1),
    LADSPA_PORT_OUTPUT(2),
    LADSPA_PORT_CONTROL(4),
    LADSPA_PORT_AUDIO(8);

    int ladspaBitIndex;

    private LadspaParameterType(int ladspaBitIndex) {
        this.ladspaBitIndex = ladspaBitIndex;
    }

    public int getLadspaBitIndex() {
        return ladspaBitIndex;
    }

}
