package com.helospark.ladspaplugin.lv2.plugin;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.helospark.ladspaplugin.lv2.nativelibrary.Lv2ChannelData;
import com.helospark.ladspaplugin.lv2.nativelibrary.Lv2IntatiateRequest;
import com.helospark.ladspaplugin.lv2.nativelibrary.Lv2NativeLibrary;
import com.helospark.ladspaplugin.lv2.nativelibrary.Lv2ParameterData;
import com.helospark.ladspaplugin.lv2.nativelibrary.Lv2RunRequest;
import com.helospark.ladspaplugin.lv2.parameter.ChannelDesignation;
import com.helospark.ladspaplugin.lv2.parameter.ParameterType;
import com.helospark.ladspaplugin.lv2.parameter.PluginParameter;
import com.helospark.ladspaplugin.lv2.parameter.PluginProperty;
import com.helospark.ladspaplugin.lv2.parameter.ScalePoint;
import com.helospark.ladspaplugin.util.ByteBufferBackedFloatBuffer;
import com.helospark.tactview.core.clone.CloneRequestMetadata;
import com.helospark.tactview.core.decoder.framecache.MemoryManager;
import com.helospark.tactview.core.timeline.AudioFrameResult;
import com.helospark.tactview.core.timeline.StatelessEffect;
import com.helospark.tactview.core.timeline.TimelineInterval;
import com.helospark.tactview.core.timeline.audioeffect.AudioEffectRequest;
import com.helospark.tactview.core.timeline.audioeffect.StatelessAudioEffect;
import com.helospark.tactview.core.timeline.effect.interpolation.KeyframeableEffect;
import com.helospark.tactview.core.timeline.effect.interpolation.ValueProviderDescriptor;
import com.helospark.tactview.core.timeline.effect.interpolation.interpolator.MultiKeyframeBasedDoubleInterpolator;
import com.helospark.tactview.core.timeline.effect.interpolation.interpolator.StepStringInterpolator;
import com.helospark.tactview.core.timeline.effect.interpolation.provider.BooleanProvider;
import com.helospark.tactview.core.timeline.effect.interpolation.provider.DoubleProvider;
import com.helospark.tactview.core.timeline.effect.interpolation.provider.ValueListProvider;

public class Lv2Plugin extends StatelessAudioEffect {
    private final Object lock = new Object();

    private final Lv2NativeLibrary library;
    private final MemoryManager memoryManager;
    private final PluginProperty pluginParameter;

    private final Map<Integer, KeyframeableEffect> idToProvider = new LinkedHashMap<>();
    private final Map<Integer, PluginParameter> idToParameter = new LinkedHashMap<>();

    private final Map<Integer, Integer> sampleRateToInstanceId = new ConcurrentHashMap<>();

    public Lv2Plugin(TimelineInterval interval, Lv2NativeLibrary library, MemoryManager memoryManager, PluginProperty pluginParameter) {
        super(interval);

        this.library = library;
        this.memoryManager = memoryManager;
        this.pluginParameter = pluginParameter;
    }

    @Override
    protected AudioFrameResult applyEffectInternal(AudioEffectRequest input) {
        int sampleRate = input.getInput().getSamplePerSecond();

        int instanceId = getInstanceIdForSampleRate(sampleRate);

        Lv2RunRequest runRequest = new Lv2RunRequest();

        Map<Integer, ByteBufferBackedFloatBuffer> channels = getChannels(input);
        runRequest.channels = new Lv2ChannelData();
        Lv2ChannelData[] channelsArray = (Lv2ChannelData[]) runRequest.channels.toArray(channels.size());
        copyChannelMapToArray(channels, channelsArray);
        runRequest.numberOfChannelData = channels.size();

        Map<Integer, Float> parameters = getParameters(input);
        runRequest.parameters = new Lv2ParameterData();
        Lv2ParameterData[] parameterArray = (Lv2ParameterData[]) runRequest.parameters.toArray(parameters.size());
        copyParameterMapToArray(parameters, parameterArray);
        runRequest.numberOfParameters = parameters.size();

        runRequest.instanceId = instanceId;
        runRequest.sampleCount = input.getInput().getNumberSamples();

        synchronized (lock) {
            library.run(runRequest);
        }

        AudioFrameResult result = convertToResult(input, channels);

        for (var entry : channels.entrySet()) {
            memoryManager.returnBuffer(entry.getValue().attachment);
        }

        return result;
    }

    private AudioFrameResult convertToResult(AudioEffectRequest input, Map<Integer, ByteBufferBackedFloatBuffer> channels) {
        int bytesPerSample = input.getInput().getBytesPerSample();

        AudioFrameResult result = AudioFrameResult.sameSizeAndFormatAs(input.getInput());

        for (int channelIndex = 0; channelIndex < channels.size(); ++channelIndex) {
            FloatBuffer outputBuffer = mapChannelToOutputBuffer(channelIndex, channels);
            if (outputBuffer != null) {
                for (int i = 0; i < input.getInput().getNumberSamples(); ++i) {
                    result.setSampleAt(channelIndex, i, (int) (outputBuffer.get(i) * (1 << (bytesPerSample * 8 - 1))));
                }
            }
        }

        return result;
    }

    private FloatBuffer mapChannelToOutputBuffer(int channelIndex, Map<Integer, ByteBufferBackedFloatBuffer> channels) {
        Map<Integer, ChannelDesignation> indexToDesignation = Map.of(0, ChannelDesignation.LEFT, 1, ChannelDesignation.RIGHT);

        return Optional.ofNullable(indexToDesignation.get(channelIndex))
                .map(channelDesignation -> pluginParameter.outputChannels.get(channelDesignation).index)
                .map(index -> channels.get(index))
                .map(result -> result.buffer)
                .orElse(null);
    }

    private void copyParameterMapToArray(Map<Integer, Float> parameters, Lv2ParameterData[] parameterArray) {
        int i = 0;
        for (var entry : parameters.entrySet()) {
            parameterArray[i].index = entry.getKey();
            parameterArray[i].data = entry.getValue();
            ++i;
        }
    }

    private Map<Integer, Float> getParameters(AudioEffectRequest input) {
        Map<Integer, Float> result = new HashMap<>();
        for (var entry : idToProvider.entrySet()) {
            KeyframeableEffect keyframeableEffect = entry.getValue();

            double value = 0.0;

            if (keyframeableEffect instanceof DoubleProvider) {
                value = ((DoubleProvider) keyframeableEffect).getValueAt(input.getEffectPosition());
            } else if (keyframeableEffect instanceof BooleanProvider) {
                value = ((BooleanProvider) keyframeableEffect).getValueAt(input.getEffectPosition()) ? 1.0 : 0.0;
            } else if (keyframeableEffect instanceof ValueListProvider) {
                value = ((ValueListProvider<ScalePoint>) keyframeableEffect).getValueAt(input.getEffectPosition()).getValue();
            } else {
                System.out.println("Unknown type " + keyframeableEffect.getClass());
            }

            result.put(entry.getKey(), (float) value);
        }
        return result;
    }

    protected void copyChannelMapToArray(Map<Integer, ByteBufferBackedFloatBuffer> channels, Lv2ChannelData[] channelsArray) {
        int i = 0;
        for (var entry : channels.entrySet()) {
            channelsArray[i].index = entry.getKey();
            channelsArray[i].buffer = entry.getValue().buffer;
            ++i;
        }
    }

    private Map<Integer, ByteBufferBackedFloatBuffer> getChannels(AudioEffectRequest input) {
        Map<Integer, ByteBufferBackedFloatBuffer> result = new HashMap<>();
        for (var entry : pluginParameter.inputChannels.entrySet()) {
            result.put(entry.getValue().index, getChannelFrom(input, entry.getKey()));
        }
        for (var entry : pluginParameter.outputChannels.entrySet()) {
            result.put(entry.getValue().index, createOutputBuffer(input.getInput().getSamplePerSecond()));
        }

        return result;
    }

    private ByteBufferBackedFloatBuffer getChannelFrom(AudioEffectRequest input, ChannelDesignation key) {
        AudioFrameResult audio = input.getInput();

        Map<ChannelDesignation, Integer> designationToIndex = Map.of(ChannelDesignation.LEFT, 0, ChannelDesignation.RIGHT, 1);

        Integer channelIndex = designationToIndex.get(key);

        if (channelIndex >= audio.getChannels().size()) {
            channelIndex = audio.getChannels().size() - 1;
        }

        ByteBuffer byteBuffer = memoryManager.requestBuffer(audio.getNumberSamples() * 4);
        FloatBuffer result = byteBuffer.asFloatBuffer();

        for (int i = 0; i < audio.getNumberSamples(); ++i) {
            result.put(i, audio.getNormalizedSampleAt(channelIndex, i));
        }

        return new ByteBufferBackedFloatBuffer(byteBuffer, result);
    }

    private ByteBufferBackedFloatBuffer createOutputBuffer(int sampleCount) {
        ByteBuffer byteBuffer = memoryManager.requestBuffer(sampleCount * 4);
        FloatBuffer floatBuffer = byteBuffer.asFloatBuffer();

        return new ByteBufferBackedFloatBuffer(byteBuffer, floatBuffer);
    }

    protected int getInstanceIdForSampleRate(int sampleRate) {
        Integer instanceId = sampleRateToInstanceId.get(sampleRate);
        if (instanceId == null) {
            synchronized (lock) {
                instanceId = sampleRateToInstanceId.get(sampleRate);
                if (instanceId == null) {
                    Lv2IntatiateRequest instantiateRequest = new Lv2IntatiateRequest();
                    instantiateRequest.binaryPath = cleanProtocol(pluginParameter.binaryPath);
                    instantiateRequest.sampleRate = sampleRate;
                    instantiateRequest.uri = pluginParameter.uri;
                    instanceId = library.instantiate(instantiateRequest);

                    if (instanceId < 0) {
                        throw new RuntimeException("Unable to instantiate plugin");
                    }

                    sampleRateToInstanceId.put(sampleRate, instanceId);
                }
            }
        }
        return instanceId;
    }

    private String cleanProtocol(String binaryPath) {
        // TODO: how could shared library read classpath file?
        return binaryPath.replaceFirst("file:", "").replaceFirst("classpath:", "");
    }

    @Override
    protected void initializeValueProviderInternal() {
        for (PluginParameter pluginParameter : pluginParameter.parameter) {
            KeyframeableEffect effect;
            if (pluginParameter.type.equals(ParameterType.BOOLEAN)) {
                effect = new BooleanProvider(new MultiKeyframeBasedDoubleInterpolator(pluginParameter.defaultValue));
            } else if (pluginParameter.type.equals(ParameterType.DOUBLE)) {
                effect = new DoubleProvider(pluginParameter.min, pluginParameter.max, new MultiKeyframeBasedDoubleInterpolator(pluginParameter.defaultValue));
            } else if (pluginParameter.type.equals(ParameterType.ENUM)) {
                double defaultValue = pluginParameter.defaultValue;
                String defaultString = pluginParameter.scalePoints.get(0).getId();
                for (var p : pluginParameter.scalePoints) {
                    if (Math.abs(p.getValue() - defaultValue) < 0.0001) {
                        defaultString = p.getId();
                    }
                }
                effect = new ValueListProvider<>(pluginParameter.scalePoints, new StepStringInterpolator(defaultString));
            } else {
                throw new RuntimeException("Unknown type " + pluginParameter.type);
            }

            idToProvider.put(pluginParameter.index, effect);
            idToParameter.put(pluginParameter.index, pluginParameter);
        }
    }

    @Override
    protected List<ValueProviderDescriptor> getValueProvidersInternal() {
        List<ValueProviderDescriptor> result = new ArrayList<>();

        for (var entry : idToProvider.entrySet()) {
            result.add(ValueProviderDescriptor.builder()
                    .withKeyframeableEffect(entry.getValue())
                    .withName(idToParameter.get(entry.getKey()).name)
                    .build());
        }

        return result;
    }

    @Override
    public StatelessEffect cloneEffect(CloneRequestMetadata cloneRequestMetadata) {
        // TODO Auto-generated method stub
        return null;
    }
}
