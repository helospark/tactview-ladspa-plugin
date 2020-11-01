package com.helospark.ladspaplugin.lv2;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.SimpleBNode;
import org.eclipse.rdf4j.model.impl.SimpleLiteral;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.UnsupportedRDFormatException;

import com.helospark.ladspaplugin.lv2.file.Lv2ManifestProvider;
import com.helospark.ladspaplugin.lv2.nativelibrary.Lv2NativeLibrary;
import com.helospark.ladspaplugin.lv2.parameter.ChannelDesignation;
import com.helospark.ladspaplugin.lv2.parameter.ParameterType;
import com.helospark.ladspaplugin.lv2.parameter.PluginParameter;
import com.helospark.ladspaplugin.lv2.parameter.PluginParameterChannel;
import com.helospark.ladspaplugin.lv2.parameter.PluginProperty;
import com.helospark.ladspaplugin.lv2.parameter.ScalePoint;
import com.helospark.ladspaplugin.lv2.plugin.Lv2AudioEffect;
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
public class Lv2PluginFactory {
    private final Lv2ManifestProvider lv2ManifestProvider;
    private final Lv2NativeLibrary lv2NativeLibrary;
    private final LightDiContext context;
    private final MemoryManager memoryManager;

    private final List<StandardEffectFactory> effectFactories = new ArrayList<>();

    public Lv2PluginFactory(Lv2ManifestProvider lv2ManifestProvider, Lv2NativeLibrary lv2NativeLibrary, LightDiContext context, MemoryManager memoryManager) {
        this.lv2ManifestProvider = lv2ManifestProvider;
        this.lv2NativeLibrary = lv2NativeLibrary;
        this.context = context;
        this.memoryManager = memoryManager;
    }

    @PostConstruct
    public void init() throws RDFParseException, UnsupportedRDFormatException, IOException {
        Set<String> manifests = lv2ManifestProvider.getLadspaPlugins();

        for (String manifest : manifests) {
            loadManifest(manifest);
        }
    }

    @Bean
    public List<StandardEffectFactory> getLv2Effects() {
        return effectFactories;
    }

    protected void loadManifest(String manifest) throws FileNotFoundException, IOException {
        try (InputStream is = getInputStream(manifest)) {
            Model rdfModel = Rio.parse(is, manifest, RDFFormat.TURTLE);

            for (Statement st : rdfModel) {
                IRI property = st.getPredicate();
                Value value = st.getObject();

                if (property.getLocalName().equals("type") && value.stringValue().matches("http://lv2plug.in/ns/lv2core#Plugin")) {
                    PluginProperty plugin = new PluginProperty();
                    plugin.uri = st.getSubject().stringValue();

                    populatePlugin(rdfModel, st.getSubject(), plugin);

                    StandardEffectFactory factory = StandardEffectFactory.builder()
                            .withFactory(request -> new Lv2AudioEffect(new TimelineInterval(request.getPosition(), TimelineLength.ofMillis(10000)), lv2NativeLibrary, memoryManager, plugin))
                            .withRestoreFactory((node, loadMetadata) -> new Lv2AudioEffect(node, loadMetadata, lv2NativeLibrary, memoryManager, plugin))
                            .withName(plugin.name + "-lv2")
                            .withSupportedEffectId(plugin.name)
                            .withSupportedClipTypes(List.of(TimelineClipType.AUDIO))
                            .withEffectType(TimelineEffectType.AUDIO_EFFECT)
                            .withIsFullWidth(true)
                            .build();
                    factory.setContext(context);
                    effectFactories.add(factory);

                    System.out.println(plugin);
                }
            }
        }
    }

    private InputStream getInputStream(String manifest) throws IOException {
        if (manifest.startsWith("file:")) {
            return new FileInputStream(new File(manifest.replaceFirst("file:", "")));
        } else if (manifest.startsWith("classpath")) {
            return this.getClass().getResource(manifest.replaceFirst("classpath:", "")).openStream();
        } else {
            throw new RuntimeException("Unknown protocol for " + manifest);
        }
    }

    protected void populatePlugin(Model rdfModel, Resource subject, PluginProperty plugin) throws RDFParseException, UnsupportedRDFormatException, IOException {
        Model pluginProperties = rdfModel.filter(subject, null, null);
        for (Statement st2 : pluginProperties) {
            IRI property2 = st2.getPredicate();
            Value value2 = st2.getObject();

            if (property2.getLocalName().equals("port")) {
                addPort(rdfModel, value2, plugin);
            } else if (property2.getLocalName().equals("name")) {
                plugin.name = value2.stringValue();
            } else if (property2.getLocalName().equals("seeAlso")) {
                String newFile = value2.stringValue();
                try (InputStream inputStream = getInputStream(newFile)) {
                    Model seeAlsoModel = Rio.parse(inputStream, newFile, RDFFormat.TURTLE);
                    populatePlugin(seeAlsoModel, subject, plugin);
                }
            } else if (property2.getLocalName().equals("binary")) {
                String binaryPath = value2.stringValue();
                plugin.binaryPath = binaryPath;
            }
        }
    }

    private void addPort(Model rdfModel, Value value2, PluginProperty plugin) {
        Model props = rdfModel.filter((SimpleBNode) value2, null, null);
        //        System.out.println(" - Port:");

        Map<String, List<Value>> propertyMap = new HashMap<>();

        for (Statement st2 : props) {
            String localName = st2.getPredicate().getLocalName();
            List<Value> v = propertyMap.get(localName);

            if (v == null) {
                v = new ArrayList<>();
                propertyMap.put(localName, v);
            }
            v.add(st2.getObject());
        }

        int index = ((SimpleLiteral) propertyMap.get("index").get(0)).intValue();
        boolean isInput = containsValue(propertyMap, "type", "http://lv2plug.in/ns/lv2core#InputPort");

        if (containsValue(propertyMap, "type", "http://lv2plug.in/ns/lv2core#AudioPort")) {
            ChannelDesignation channel = extractChannel(propertyMap);
            if (isInput) {
                plugin.inputChannels.put(channel, new PluginParameterChannel(index));
            } else {
                plugin.outputChannels.put(channel, new PluginParameterChannel(index));
            }
        } else if (containsValue(propertyMap, "type", "http://lv2plug.in/ns/lv2core#ControlPort")) {

            if (containsValue(propertyMap, "portProperty", "http://lv2plug.in/ns/lv2core#enumeration")) {
                String name = getSinglePropertyOptional(propertyMap, "name").map(a -> a.stringValue()).orElse("Unknown");
                double defaultV = getSingleDoubleOr(propertyMap, "default", 0.0);

                List<Value> scalePointsElements = propertyMap.get("scalePoint");
                List<ScalePoint> scalePoints = new ArrayList<>();
                for (var value : scalePointsElements) {
                    scalePoints.add(readScalePoint(rdfModel, value));
                }

                PluginParameter pluginParameter = new PluginParameter();
                pluginParameter.defaultValue = defaultV;
                pluginParameter.index = index;
                pluginParameter.name = name;
                pluginParameter.type = ParameterType.ENUM;
                pluginParameter.scalePoints = scalePoints;

                plugin.parameter.add(pluginParameter);
            } else {
                String name = getSinglePropertyOptional(propertyMap, "name").map(a -> a.stringValue()).orElse("Unknown");
                double minimum = getSingleDoubleOr(propertyMap, "minimum", 0.0);
                double maximum = getSingleDoubleOr(propertyMap, "maximum", 0.0);
                double defaultV = getSingleDoubleOr(propertyMap, "default", minimum);
                ParameterType type = mapParameterType(propertyMap);

                PluginParameter pluginParameter = new PluginParameter();
                pluginParameter.defaultValue = defaultV;
                pluginParameter.max = maximum;
                pluginParameter.min = minimum;
                pluginParameter.index = index;
                pluginParameter.name = name;
                pluginParameter.type = type;

                plugin.parameter.add(pluginParameter);
            }
        }

    }

    private ScalePoint readScalePoint(Model rdfModel, Value filterValue) {
        Model props = rdfModel.filter((SimpleBNode) filterValue, null, null);

        String label = "";
        double value = 0.0;

        for (Statement st2 : props) {
            if (st2.getPredicate().getLocalName().equals("label")) {
                label = st2.getObject().stringValue();
            }
            if (st2.getPredicate().getLocalName().equals("value")) {
                value = Double.parseDouble(st2.getObject().stringValue());
            }
        }

        return new ScalePoint(label, value);
    }

    private ParameterType mapParameterType(Map<String, List<Value>> propertyMap) {
        List<Value> properties = propertyMap.get("portProperty");

        if (properties == null) {
            return ParameterType.DOUBLE;
        }

        return properties.stream()
                .map(a -> a.stringValue())
                .filter(a -> a.contains("http://lv2plug.in/ns/lv2core#toggled"))
                .findAny()
                .map(a -> ParameterType.BOOLEAN)
                .orElse(ParameterType.DOUBLE);
    }

    protected Double getSingleDoubleOr(Map<String, List<Value>> propertyMap, String property, double defaultV) {
        return getSinglePropertyOptional(propertyMap, property).map(a -> ((SimpleLiteral) a).doubleValue()).orElse(defaultV);
    }

    private boolean containsValue(Map<String, List<Value>> propertyMap, String property, String toFind) {
        List<Value> values = propertyMap.get(property);

        if (values == null) {
            return false;
        }

        return values.stream()
                .filter(a -> a.stringValue().equals(toFind))
                .findAny()
                .isPresent();
    }

    private ChannelDesignation extractChannel(Map<String, List<Value>> propertyMap) {
        Map<String, ChannelDesignation> MAPPING = Map.of("http://lv2plug.in/ns/ext/port-groups#left", ChannelDesignation.LEFT,
                "http://lv2plug.in/ns/ext/port-groups#right", ChannelDesignation.RIGHT);

        return getSinglePropertyOptional(propertyMap, "designation")
                .map(a -> MAPPING.get(a.stringValue()))
                .orElse(ChannelDesignation.LEFT);
    }

    private Optional<Value> getSinglePropertyOptional(Map<String, List<Value>> propertyMap, String string) {
        List<Value> values = propertyMap.get(string);

        return Optional.ofNullable(values)
                .filter(a -> !a.isEmpty())
                .map(a -> a.get(0));
    }

}
