package com.helospark.ladspaplugin.lv2.parameter;

import com.helospark.tactview.core.timeline.effect.interpolation.provider.ValueListElement;

public class ScalePoint extends ValueListElement {
    private double value;

    public ScalePoint(String text, double value) {
        super(text, text);
    }

    public double getValue() {
        return value;
    }

}
