package com.futo.platformplayer.sabr.parser.ump;

import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;

import java.io.IOException;
import java.io.InputStream;

@UnstableApi
public class UMPInputStream extends InputStream {
    private final UMPPart part;
    private int position = 0; // bytes read so far

    public UMPInputStream(UMPPart part) {
        this.part = part;
    }

    @Override
    public int read() throws IOException {
        if (position >= part.size) return -1;

        byte[] buffer = new byte[1];
        int read;
        read = part.data.read(buffer, 0, 1);

        if (read == C.RESULT_END_OF_INPUT) return -1;
        position += read;
        return buffer[0] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (position >= part.size) return -1;

        int toRead = Math.min(len, part.size - position);
        int read;
        read = part.data.read(b, off, toRead);

        if (read == C.RESULT_END_OF_INPUT) return -1;
        position += read;
        return read;
    }

    @Override
    public long skip(long n) throws IOException {
        int toSkip = (int) Math.min(n, part.size - position);
        int skipped;
        skipped = part.data.skip(toSkip);
        position += skipped;
        return skipped;
    }

    @Override
    public int available() {
        return part.size - position;
    }
}

