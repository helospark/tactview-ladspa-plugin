package com.helospark.ladspaplugin.ladspa.nativelibrary;

import com.helospark.tactview.core.util.jpaplugin.NativeImplementation;
import com.sun.jna.Library;

@NativeImplementation("ladspaplugin")
public interface LadspaNativeLibrary extends Library {
    public int loadPlugin(LadspaLoadPluginRequest loadRequest);

    public int getParameterNumber(int id);

    public int getPluginDescription(LadspaGetPluginDescriptionRequest request);

    public int instantiatePlugin(int pluginId, int sampleRate);

    public int render(LadspaRenderRequest request);
}
