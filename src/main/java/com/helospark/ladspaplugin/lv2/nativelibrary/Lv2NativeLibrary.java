package com.helospark.ladspaplugin.lv2.nativelibrary;

import com.helospark.tactview.core.util.jpaplugin.NativeImplementation;
import com.sun.jna.Library;

@NativeImplementation("lv2plugin")
public interface Lv2NativeLibrary extends Library {

    int instantiate(Lv2IntatiateRequest request);

    int run(Lv2RunRequest runRequest);

    int cleanup(int instanceId);
}
