package com.helospark.ladspaplugin;

import java.util.ArrayList;
import java.util.List;

import com.helospark.ladspaplugin.nativelibrary.LadspaGetPluginDescriptionRequest;
import com.helospark.ladspaplugin.nativelibrary.LadspaNativeLibrary;
import com.helospark.lightdi.annotation.Component;
import com.sun.jna.Memory;
import com.sun.jna.Native;

@Component
public class LadspaParameterExtractor {
    private final LadspaNativeLibrary ladspaNativeLibrary;

    public LadspaParameterExtractor(LadspaNativeLibrary ladspaNativeLibrary) {
        this.ladspaNativeLibrary = ladspaNativeLibrary;
    }

    public LadspaDescriptorHolder extractDescriptor(int pluginIndex) {
        int parameterNumber = ladspaNativeLibrary.getParameterNumber(pluginIndex);

        if (parameterNumber <= 0) {
            System.out.println("Plugin load error " + pluginIndex);
            throw new RuntimeException("Cannot load");
        }

        LadspaGetPluginDescriptionRequest ladspaGetPluginDescriptionRequest = new LadspaGetPluginDescriptionRequest();
        ladspaGetPluginDescriptionRequest.index = pluginIndex;
        ladspaGetPluginDescriptionRequest.parameterLowerValues = new Memory(parameterNumber * Native.getNativeSize(Double.TYPE));
        ladspaGetPluginDescriptionRequest.parameterUpperValues = new Memory(parameterNumber * Native.getNativeSize(Double.TYPE));

        ladspaNativeLibrary.getPluginDescription(ladspaGetPluginDescriptionRequest);

        System.out.println(ladspaGetPluginDescriptionRequest.name + " " + ladspaGetPluginDescriptionRequest.label);

        String[] parameterNames = ladspaGetPluginDescriptionRequest.parameterNames.getStringArray(0, parameterNumber);
        int[] parameterTypes = ladspaGetPluginDescriptionRequest.parameterTypes.getIntArray(0, parameterNumber);
        double[] lowerValues = ladspaGetPluginDescriptionRequest.parameterLowerValues.getDoubleArray(0, parameterNumber);
        double[] upperValues = ladspaGetPluginDescriptionRequest.parameterUpperValues.getDoubleArray(0, parameterNumber);
        for (int j = 0; j < parameterNumber; ++j) {
            System.out.println("Parameter " + parameterNames[j] + " " + parameterTypes[j] + " [ " + lowerValues[j] + " - " + upperValues[j] + " ]");
        }

        List<LadspaParameterHolder> result = new ArrayList<>(parameterNumber);

        for (int i = 0; i < parameterNumber; ++i) {
            result.add(LadspaParameterHolder.builder()
                    .withIndex(i)
                    .withName(parameterNames[i])
                    .withLowerValue(lowerValues[i])
                    .withUpperValue(upperValues[i])
                    .withParameterTypes(resolveTypes(parameterTypes[i]))
                    .build());
        }

        return new LadspaDescriptorHolder(ladspaGetPluginDescriptionRequest.name, ladspaGetPluginDescriptionRequest.channelCount, result);
    }

    private List<LadspaParameterType> resolveTypes(int type) {
        List<LadspaParameterType> result = new ArrayList<>();

        for (var parameterType : LadspaParameterType.values()) {
            if ((type & parameterType.getLadspaBitIndex()) != 0) {
                result.add(parameterType);
            }
        }

        return result;
    }

}
