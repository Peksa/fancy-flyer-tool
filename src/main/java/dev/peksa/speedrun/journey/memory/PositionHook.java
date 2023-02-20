package dev.peksa.speedrun.journey.memory;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import dev.peksa.speedrun.process.*;

public class PositionHook {

    private final PointerPathResolver resolver;
    private final HookedProcess process;
    private final Pointer zPointer;
    private final Pointer xPointer;
    private final Pointer yPointer;

    public PositionHook(HookedProcess process) {
        this.process = process;
        this.resolver = new PointerPathResolver(process);
        this.xPointer = resolver.resolvePointerPath(new PointerPath(
                "Journey.exe", 0x03C47B18, 0x60, 0x28, 0xD0, 0x100, 0x30, 0x370, 0xC0));
        this.yPointer = resolver.resolvePointerPath(new PointerPath(
                "Journey.exe", 0x03C47B18, 0x70, 0x178, 0x78, 0xD0, 0x108, 0x3A8, 0xC4));
        this.zPointer = resolver.resolvePointerPath(new PointerPath(
                "Journey.exe", 0x03C47B18, 0x70, 0x28, 0xD0, 0x108, 0x30, 0x370, 0xC8));
    }

    public Coordinates getCurrentCoordinates() {
        float x = process.readMemory(xPointer, 4).getFloat(0);
        float y = process.readMemory(yPointer, 4).getFloat(0);
        float z = process.readMemory(zPointer, 4).getFloat(0);
        return new Coordinates(x, y, z);
    }

    public void setCurrentCoordinates(Coordinates coordinates) {
        setValue(yPointer, coordinates.y);
        setValue(zPointer, coordinates.z);
        setValue(xPointer, coordinates.x);
    }

    private void setValue(Pointer p, float value) {
        var mem = new Memory(4);
        mem.setFloat(0, value);
        process.writeMemory(p,  mem, 4);
    }

    public record Coordinates(float x, float y, float z) {}

}
