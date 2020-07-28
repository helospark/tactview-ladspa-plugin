package com.helospark.ladspaplugin;

import com.helospark.lightdi.annotation.ComponentScan;
import com.helospark.lightdi.annotation.Configuration;
import com.helospark.lightdi.annotation.PropertySource;

@Configuration
@ComponentScan
@PropertySource("classpath:ladspa-plugin.properties")
public class LadspaPluginConfiguration {

}
