package com.helospark.ladspaplugin;

import java.util.Collections;
import java.util.List;

import javax.annotation.Generated;

public class LadspaParameterHolder {
    public final String name;
    public final int index;
    public final List<LadspaParameterType> parameterTypes;
    public final double lowerValue, upperValue;

    @Generated("SparkTools")
    private LadspaParameterHolder(Builder builder) {
        this.name = builder.name;
        this.index = builder.index;
        this.parameterTypes = builder.parameterTypes;
        this.lowerValue = builder.lowerValue;
        this.upperValue = builder.upperValue;
    }

    @Generated("SparkTools")
    public static Builder builder() {
        return new Builder();
    }

    @Generated("SparkTools")
    public static final class Builder {
        private String name;
        private int index;
        private List<LadspaParameterType> parameterTypes = Collections.emptyList();
        private double lowerValue;
        private double upperValue;

        private Builder() {
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withIndex(int index) {
            this.index = index;
            return this;
        }

        public Builder withParameterTypes(List<LadspaParameterType> parameterTypes) {
            this.parameterTypes = parameterTypes;
            return this;
        }

        public Builder withLowerValue(double lowerValue) {
            this.lowerValue = lowerValue;
            return this;
        }

        public Builder withUpperValue(double upperValue) {
            this.upperValue = upperValue;
            return this;
        }

        public LadspaParameterHolder build() {
            return new LadspaParameterHolder(this);
        }
    }

}
