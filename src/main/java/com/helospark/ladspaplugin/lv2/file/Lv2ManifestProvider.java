package com.helospark.ladspaplugin.lv2.file;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.lang3.SystemUtils;

import com.helospark.lightdi.annotation.Component;
import com.helospark.lightdi.annotation.Value;

@Component
public class Lv2ManifestProvider {
    private final String lv2PluginLocations;

    public Lv2ManifestProvider(@Value("${lv2.plugin.locations}") String lv2PluginLocations) {
        this.lv2PluginLocations = lv2PluginLocations;
    }

    public Set<String> getLadspaPlugins() {
        String lv2Path = System.getenv("LV2_PATH");

        Set<String> plugins = new LinkedHashSet<>();
        if (SystemUtils.IS_OS_LINUX) {
            Stream.of("/usr/lib/lv2/", "/usr/local/lib/lv2/")
                    .flatMap(a -> searchPath(a).stream())
                    // distinct
                    .forEach(a -> plugins.add(a));

            if (lv2Path != null) {
                Arrays.stream(lv2Path.split(":"))
                        .forEach(a -> plugins.addAll(searchPath(a)));
            }
        } else if (SystemUtils.IS_OS_WINDOWS) {
            if (lv2Path != null) {
                Arrays.stream(lv2Path.split(";"))
                        .forEach(a -> plugins.addAll(searchPath(a)));
            }
        } else if (SystemUtils.IS_OS_MAC) {
            if (lv2Path != null) {
                Arrays.stream(lv2Path.split(":"))
                        .forEach(a -> plugins.addAll(searchPath(a)));
            }
        }

        Arrays.stream(lv2PluginLocations.split(":"))
                .forEach(a -> plugins.addAll(searchPath(a)));

        return plugins;
    }

    private List<String> searchPath(String pathString) {
        List<String> result = new ArrayList<>();
        File path = new File(pathString);
        if (!path.exists()) {
            return Collections.emptyList();
        }
        if (path.isFile() && (path.getAbsolutePath().endsWith("manifest.ttl"))) {
            return List.of("file:" + path.getAbsolutePath());
        }
        if (path.isDirectory()) {
            for (File childFile : path.listFiles()) {
                result.addAll(searchPath(childFile.getAbsolutePath()));
            }
        }
        return result;
    }

}
