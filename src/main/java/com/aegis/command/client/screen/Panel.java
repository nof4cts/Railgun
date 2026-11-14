package com.aegis.command.client.screen;

/** One tab of the laptop terminal. Coordinates are in the terminal's virtual 520x300 canvas. */
public abstract class Panel {
    protected final LaptopScreen s;
    protected long shownMs;

    protected Panel(LaptopScreen s) {
        this.s = s;
    }

    public void show() { shownMs = System.currentTimeMillis(); }

    public float since() { return (System.currentTimeMillis() - shownMs) / 1000f; }

    public abstract void render(float mx, float my, float dt);

    public boolean mouseDown(float mx, float my, int button) { return false; }
    public boolean mouseUp(float mx, float my, int button) { return false; }
    public boolean drag(float mx, float my, float dx, float dy, int button) { return false; }
    public boolean scroll(float mx, float my, double delta) { return false; }
    public void close() {}
}
