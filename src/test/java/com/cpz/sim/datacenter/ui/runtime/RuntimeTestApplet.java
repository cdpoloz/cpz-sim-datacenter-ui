package com.cpz.sim.datacenter.ui.runtime;

import processing.core.PApplet;
import processing.core.PFont;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads the dashboard configuration during tests without starting Processing's surface. */
final class RuntimeTestApplet extends PApplet {
    RuntimeTestApplet(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override public InputStream createInputRaw(String filename) {
        try {
            return Files.newInputStream(Path.of(filename));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override public PFont createFont(String name, float size) {
        return new PFont();
    }
}
