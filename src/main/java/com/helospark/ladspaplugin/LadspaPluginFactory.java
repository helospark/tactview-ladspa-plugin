package com.helospark.ladspaplugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helospark.ladspaplugin.nativelibrary.LadspaLoadPluginRequest;
import com.helospark.ladspaplugin.nativelibrary.LadspaNativeLibrary;
import com.helospark.lightdi.LightDiContext;
import com.helospark.lightdi.annotation.Bean;
import com.helospark.lightdi.annotation.Configuration;
import com.helospark.tactview.core.decoder.framecache.MemoryManager;
import com.helospark.tactview.core.timeline.TimelineClipType;
import com.helospark.tactview.core.timeline.TimelineInterval;
import com.helospark.tactview.core.timeline.TimelineLength;
import com.helospark.tactview.core.timeline.effect.StandardEffectFactory;
import com.helospark.tactview.core.timeline.effect.TimelineEffectType;

@Configuration
public class LadspaPluginFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(LadspaPluginFactory.class);
    private final LadspaPluginProvider ladspaPluginProvider;
    private final LadspaNativeLibrary ladspaNativeLibrary;
    private final LadspaParameterExtractor ladspaParameterExtractor;
    private final MemoryManager memoryManager;
    private final LightDiContext context;

    private final List<StandardEffectFactory> effectFactories = new ArrayList<>();

    public LadspaPluginFactory(LadspaPluginProvider ladspaPluginProvider, LadspaNativeLibrary ladspaNativeLibrary, LadspaParameterExtractor ladspaParameterExtractor, MemoryManager memoryManager,
            LightDiContext context) {
        this.ladspaPluginProvider = ladspaPluginProvider;
        this.ladspaNativeLibrary = ladspaNativeLibrary;
        this.ladspaParameterExtractor = ladspaParameterExtractor;
        this.memoryManager = memoryManager;
        this.context = context;
    }

    @PostConstruct
    public void initialize() {
        Set<File> plugins = ladspaPluginProvider.getLadspaPlugins();

        for (File plugin : plugins) {
            LadspaLoadPluginRequest loadRequest = new LadspaLoadPluginRequest();
            loadRequest.file = plugin.getAbsolutePath();

            LOGGER.debug("Loading ladspa plugin " + plugin.getAbsolutePath());

            int resultCode = ladspaNativeLibrary.loadPlugin(loadRequest);

            if (resultCode == 0) {
                int[] loadedPlugins = loadRequest.loadedIndices.getIntArray(0, loadRequest.numberOfLoadedPlugins);
                for (int i = 0; i < loadRequest.numberOfLoadedPlugins; ++i) {
                    int pluginIndex = loadedPlugins[i];
                    System.out.println();

                    LadspaDescriptorHolder descriptor;
                    try {
                        descriptor = ladspaParameterExtractor.extractDescriptor(pluginIndex);
                    } catch (Exception e) {
                        e.printStackTrace();
                        continue;
                    }
                    StandardEffectFactory factory = StandardEffectFactory.builder()
                            .withFactory(request -> new LadspaAudioEffect(
                                    new TimelineInterval(request.getPosition(), TimelineLength.ofMillis(10000)), ladspaNativeLibrary, memoryManager, pluginIndex, descriptor))
                            .withRestoreFactory((node, loadMetadata) -> null)
                            .withName(descriptor.name)
                            .withSupportedEffectId(descriptor.name)
                            .withSupportedClipTypes(List.of(TimelineClipType.AUDIO))
                            .withEffectType(TimelineEffectType.AUDIO_EFFECT)
                            .withIsFullWidth(true)
                            .build();
                    factory.setContext(context);
                    effectFactories.add(factory);
                }
            }
        }
    }

    @Bean
    public List<StandardEffectFactory> ladspaEffects() {
        return effectFactories;
    }

}
