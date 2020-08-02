package com.helospark.ladspaplugin.ladspa;

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
public class LadspaPluginProvider {
    private final String ladspaPluginLocations;

    public LadspaPluginProvider(@Value("${ladspa.plugin.locations}") String ladspaPluginLocations) {
        this.ladspaPluginLocations = ladspaPluginLocations;
    }

    public Set<File> getLadspaPlugins() {
        String ladspaPath = System.getenv("LADSPA_PATH");

        Set<File> plugins = new LinkedHashSet<>();
        if (SystemUtils.IS_OS_LINUX) {
            //            Stream.of("/usr/lib/ladspa/", "/usr/local/lib/ladspa/")
            Stream.of("/usr/local/lib/ladspa/")
                    .flatMap(a -> searchPath(a).stream())
                    // distinct
                    .forEach(a -> plugins.add(a));

            if (ladspaPath != null) {
                Arrays.stream(ladspaPath.split(":"))
                        .forEach(a -> plugins.addAll(searchPath(a)));
            }
        } else if (SystemUtils.IS_OS_WINDOWS) {
            if (ladspaPath != null) {
                Arrays.stream(ladspaPath.split(";"))
                        .forEach(a -> plugins.addAll(searchPath(a)));
            }
        } else if (SystemUtils.IS_OS_MAC) {
            if (ladspaPath != null) {
                Arrays.stream(ladspaPath.split(":"))
                        .forEach(a -> plugins.addAll(searchPath(a)));
            }
        }

        Arrays.stream(ladspaPluginLocations.split(":"))
                .forEach(a -> plugins.addAll(searchPath(a)));

        return plugins;
    }

    private List<File> searchPath(String pathString) {
        List<File> result = new ArrayList<>();
        File path = new File(pathString);
        if (!path.exists()) {
            return Collections.emptyList();
        }
        if (path.isFile() && (path.getAbsolutePath().endsWith(".so") || path.getAbsolutePath().endsWith(".dll"))) {
            return List.of(path);
        }
        if (path.isDirectory()) {
            for (File childFile : path.listFiles()) {
                result.addAll(searchPath(childFile.getAbsolutePath()));
            }
        }
        return result;
    }

}
