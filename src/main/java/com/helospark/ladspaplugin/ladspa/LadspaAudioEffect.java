package com.helospark.ladspaplugin.ladspa;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.helospark.ladspaplugin.ladspa.nativelibrary.LadspaNativeLibrary;
import com.helospark.ladspaplugin.ladspa.nativelibrary.LadspaParameter;
import com.helospark.ladspaplugin.ladspa.nativelibrary.LadspaRenderRequest;
import com.helospark.ladspaplugin.util.ByteBufferBackedFloatBuffer;
import com.helospark.tactview.core.clone.CloneRequestMetadata;
import com.helospark.tactview.core.decoder.framecache.MemoryManager;
import com.helospark.tactview.core.save.LoadMetadata;
import com.helospark.tactview.core.timeline.AudioFrameResult;
import com.helospark.tactview.core.timeline.StatelessEffect;
import com.helospark.tactview.core.timeline.TimelineInterval;
import com.helospark.tactview.core.timeline.TimelinePosition;
import com.helospark.tactview.core.timeline.audioeffect.AudioEffectRequest;
import com.helospark.tactview.core.timeline.audioeffect.StatelessAudioEffect;
import com.helospark.tactview.core.timeline.effect.interpolation.ValueProviderDescriptor;
import com.helospark.tactview.core.timeline.effect.interpolation.interpolator.MultiKeyframeBasedDoubleInterpolator;
import com.helospark.tactview.core.timeline.effect.interpolation.provider.DoubleProvider;
import com.helospark.tactview.core.timeline.effect.interpolation.provider.evaluator.EvaluationContext;
import com.helospark.tactview.core.timeline.threading.SingleThreadedRenderable;

public class LadspaAudioEffect extends StatelessAudioEffect implements SingleThreadedRenderable {
    private final Map<Integer, Integer> sampleRateToInstanceId = new ConcurrentHashMap<>();

    private final LadspaNativeLibrary library;
    private final MemoryManager memoryManager;

    private final int pluginId;
    private final LadspaDescriptorHolder descriptor;

    private final Map<Integer, DoubleProvider> providers = new LinkedHashMap<>();

    public LadspaAudioEffect(TimelineInterval interval, LadspaNativeLibrary library, MemoryManager memoryManager, int pluginId, LadspaDescriptorHolder descriptor) {
        super(interval);

        this.library = library;
        this.memoryManager = memoryManager;
        this.pluginId = pluginId;
        this.descriptor = descriptor;
    }

    public LadspaAudioEffect(JsonNode node, LoadMetadata loadMetadata, LadspaNativeLibrary library, MemoryManager memoryManager, int pluginId, LadspaDescriptorHolder descriptor) {
        super(node, loadMetadata);

        // TODO: load parameters

        this.library = library;
        this.memoryManager = memoryManager;
        this.pluginId = pluginId;
        this.descriptor = descriptor;
    }

    public LadspaAudioEffect(LadspaAudioEffect statelessAudioEffect, CloneRequestMetadata cloneRequestMetadata, LadspaNativeLibrary library, MemoryManager memoryManager, int pluginId,
            LadspaDescriptorHolder descriptor) {
        super(statelessAudioEffect, cloneRequestMetadata);

        for (var param : providers.entrySet()) {
            this.providers.put(param.getKey(), param.getValue().deepClone(cloneRequestMetadata));
        }

        this.library = library;
        this.memoryManager = memoryManager;
        this.pluginId = pluginId;
        this.descriptor = descriptor;
    }

    @Override
    protected AudioFrameResult applyEffectInternal(AudioEffectRequest input) {
        int sampleRate = input.getInput().getSamplePerSecond();
        Integer instanceId = sampleRateToInstanceId.get(sampleRate);

        if (instanceId == null) { // ladspa keeps sampleRate always the same, so let's instanitate multiple ones
            synchronized (this) {
                instanceId = sampleRateToInstanceId.get(sampleRate);
                if (instanceId == null) {
                    instanceId = library.instantiatePlugin(pluginId, sampleRate);
                    sampleRateToInstanceId.put(sampleRate, instanceId);
                }
            }
        }

        int sampleCount = input.getInput().getNumberSamples();

        ByteBufferBackedFloatBuffer outputLeftBuffer = requestFloatBuffer(sampleCount);
        ByteBufferBackedFloatBuffer inputLeftBuffer = convertToFloatBuffer(input, 0);

        ByteBufferBackedFloatBuffer outputRightBuffer = null;
        ByteBufferBackedFloatBuffer inputRightBuffer = null;

        boolean effectHasRightChannel = descriptor.channelCount > 1;
        boolean inputHasRightChannel = input.getInput().getChannels().size() > 1;

        if (inputHasRightChannel) {
            outputRightBuffer = requestFloatBuffer(sampleCount);
            inputRightBuffer = convertToFloatBuffer(input, 1);
        }

        LadspaRenderRequest renderRequest = new LadspaRenderRequest();
        renderRequest.instanceId = instanceId;
        renderRequest.inputLeft = inputLeftBuffer.buffer;
        renderRequest.outputLeft = outputLeftBuffer.buffer;
        renderRequest.sampleCount = sampleCount;

        renderRequest.parameters = new LadspaParameter();
        fillParameters(input.getEffectPosition(), renderRequest.parameters, input.getEvaluationContext());

        renderRequest.numberOfParametersDefined = providers.size();

        if (effectHasRightChannel) {
            renderRequest.outputRight = outputRightBuffer.buffer;
            renderRequest.inputRight = inputRightBuffer.buffer;
        }

        library.render(renderRequest);

        if (!effectHasRightChannel && inputHasRightChannel) {
            renderRequest.inputLeft = inputRightBuffer.buffer;
            renderRequest.outputLeft = outputRightBuffer.buffer;

            library.render(renderRequest);
        }

        AudioFrameResult result = convertToResult(input, outputLeftBuffer, outputRightBuffer);

        memoryManager.returnBuffer(outputLeftBuffer.attachment);
        memoryManager.returnBuffer(inputLeftBuffer.attachment);
        if (outputRightBuffer != null) {
            memoryManager.returnBuffer(outputRightBuffer.attachment);
        }
        if (inputRightBuffer != null) {
            memoryManager.returnBuffer(inputRightBuffer.attachment);
        }

        return result;
    }

    private void fillParameters(TimelinePosition position, LadspaParameter parameters, EvaluationContext evaluationContext) {
        int size = providers.size();

        if (size <= 0) {
            size = 1;
        }

        LadspaParameter[] result = (LadspaParameter[]) parameters.toArray(size);

        int index = 0;
        for (var entry : providers.entrySet()) {
            result[index].index = entry.getKey();
            result[index].value = entry.getValue().getValueAt(position, evaluationContext).floatValue();

            ++index;
        }

    }

    protected ByteBufferBackedFloatBuffer requestFloatBuffer(int sampleCount) {
        ByteBuffer byteBuffer = memoryManager.requestBuffer(sampleCount * 4);
        FloatBuffer floatBuffer = byteBuffer.asFloatBuffer();

        return new ByteBufferBackedFloatBuffer(byteBuffer, floatBuffer);
    }

    private AudioFrameResult convertToResult(AudioEffectRequest input, ByteBufferBackedFloatBuffer outputLeftBuffer, ByteBufferBackedFloatBuffer outputRightBuffer) {
        AudioFrameResult result = AudioFrameResult.sameSizeAndFormatAs(input.getInput());

        int numberOfSamples = input.getInput().getNumberSamples();
        for (int i = 0; i < numberOfSamples; ++i) {
            result.setNormalizedSampleAt(0, i, outputLeftBuffer.buffer.get(i));
        }
        if (outputRightBuffer != null) {
            for (int i = 0; i < numberOfSamples; ++i) {
                result.setNormalizedSampleAt(1, i, outputRightBuffer.buffer.get(i));
            }
        }

        return result;
    }

    protected ByteBufferBackedFloatBuffer convertToFloatBuffer(AudioEffectRequest input, int channelIndex) {
        AudioFrameResult audio = input.getInput();

        if (input.getInput().getChannels().size() <= channelIndex) {
            channelIndex = input.getInput().getChannels().size() - 1;
        }

        ByteBuffer byteBuffer = memoryManager.requestBuffer(audio.getNumberSamples() * 4);
        FloatBuffer result = byteBuffer.asFloatBuffer();

        for (int i = 0; i < audio.getNumberSamples(); ++i) {
            result.put(i, audio.getNormalizedSampleAt(channelIndex, i));
        }

        return new ByteBufferBackedFloatBuffer(byteBuffer, result);
    }

    @Override
    protected void initializeValueProviderInternal() {
        if (providers.isEmpty()) {
            int index = 0;
            for (var parameter : descriptor.parameters) {

                // input and output automatically appended
                if (!parameter.parameterTypes.contains(LadspaParameterType.LADSPA_PORT_AUDIO)) {
                    DoubleProvider provider = new DoubleProvider(parameter.lowerValue, parameter.upperValue,
                            new MultiKeyframeBasedDoubleInterpolator((parameter.lowerValue + parameter.upperValue) / 2.0));
                    providers.put(index, provider);
                }

                ++index;
            }
        }
    }

    @Override
    protected List<ValueProviderDescriptor> getValueProvidersInternal() {
        List<ValueProviderDescriptor> result = new ArrayList<>();

        for (var entry : providers.entrySet()) {
            result.add(ValueProviderDescriptor.builder()
                    .withKeyframeableEffect(entry.getValue())
                    .withName(descriptor.parameters.get(entry.getKey()).name)
                    .build());
        }

        return result;
    }

    @Override
    public StatelessEffect cloneEffect(CloneRequestMetadata cloneRequestMetadata) {
        return new LadspaAudioEffect(this, cloneRequestMetadata, library, memoryManager, pluginId, descriptor);
    }

    @Override
    public void onStartRender() {
        // todo: clear buffer and reActivate
    }

}
