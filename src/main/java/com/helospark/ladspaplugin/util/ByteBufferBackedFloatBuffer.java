package com.helospark.ladspaplugin.util;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public class ByteBufferBackedFloatBuffer {
    public ByteBuffer attachment;
    public FloatBuffer buffer;

    public ByteBufferBackedFloatBuffer(ByteBuffer attachment, FloatBuffer buffer) {
        this.attachment = attachment;
        this.buffer = buffer;
    }
}
