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
    private final Pointer scarfPower;
    private final Pointer scarfLength;
    private final Pointer boostPointer;

    public PositionHook(HookedProcess process) {
        this.process = process;
        this.resolver = new PointerPathResolver(process);

        this.xPointer = resolver.resolvePointerPath(new PointerPath(
            "Journey.exe", 0x03C47B18, 0x60, 0x28, 0xD0, 0x100, 0x30, 0x370, 0xC0));
        this.yPointer = resolver.resolvePointerPath(new PointerPath(
            "Journey.exe", 0x03C47B18, 0x70, 0x178, 0x78, 0xD0, 0x108, 0x3A8, 0xC4));
        this.zPointer = resolver.resolvePointerPath(new PointerPath(
            "Journey.exe", 0x03C47B18, 0x70, 0x28, 0xD0, 0x108, 0x30, 0x370, 0xC8));

        this.scarfPower = resolver.resolvePointerPath(new PointerPath(
           "Journey.exe", 0x03C47B18, 0x60, 0x178, 0xd0, 0x100, 0x3a8, 0x118));
        this.scarfLength = resolver.resolvePointerPath(new PointerPath(
                "Journey.exe", 0x03CFCA80, 0x70, 0x28, 0xd0, 0x130, 0x370, 0x11c));

        this.boostPointer = resolver.resolvePointerPath(new PointerPath(
                "Journey.exe", 0x3CFCA88, 0x40, 0x98, 0x20, 0x30, 0x5d0, 0xddc));

    }

    public SaveState getCurrentSaveState() {
        float x = process.readMemory(xPointer, 4).getFloat(0);
        float y = process.readMemory(yPointer, 4).getFloat(0);
        float z = process.readMemory(zPointer, 4).getFloat(0);

        int length = process.readMemory(scarfLength, 4).getInt(0);
        int power = process.readMemory(scarfPower, 4).getInt(0);
        float boost = process.readMemory(boostPointer, 4).getFloat(0);

        return new SaveState(x, y, z, length, power, boost);
    }

    public void restoreSaveState(SaveState saveState) {
        setFloat(yPointer, saveState.y);
        setFloat(zPointer, saveState.z);
        setFloat(xPointer, saveState.x);

        setInt(scarfLength, saveState.scarfLength);
        setInt(scarfPower, saveState.scarfPower);
        setFloat(boostPointer, saveState.boost);
    }

    private void setFloat(Pointer p, float value) {
        var mem = new Memory(4);
        mem.setFloat(0, value);
        process.writeMemory(p,  mem, 4);
    }

    private void setInt(Pointer p, int value) {
        var mem = new Memory(4);
        mem.setInt(0, value);
        process.writeMemory(p,  mem, 4);
    }

    public record SaveState(float x, float y, float z, int scarfLength, int scarfPower, float boost) {}

}
