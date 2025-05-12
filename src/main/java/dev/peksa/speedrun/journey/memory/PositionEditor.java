package dev.peksa.speedrun.journey.memory;

import com.sun.jna.Pointer;
import dev.peksa.speedrun.process.*;

public class PositionEditor {

    private final OpenedProcess process;
    private final Pointer zPointer;
    private final Pointer xPointer;
    private final Pointer yPointer;
    private final Pointer scarfPower;
    private final Pointer scarfLength;
    private final Pointer boostPointer;

    public PositionEditor(OpenedProcess process) {
        this.process = process;

        var resolver = new PointerPathResolver(process);

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
                "THREADSTACK0", -0x118, 0x50, 0x30, 0x1b8, 0xb38, 0x30, 0x5d0, 0xddc));
    }

    public SaveState getCurrentSaveState() {
        float x = process.readFloat(xPointer);
        float y = process.readFloat(yPointer);
        float z = process.readFloat(zPointer);

        int length = process.readInt(scarfLength);
        int power = process.readInt(scarfPower);
        float boost = process.readFloat(boostPointer);

        return new SaveState(x, y, z, length, power, boost);
    }

    public void restoreSaveState(SaveState saveState) {
        process.writeFloat(yPointer, saveState.y);
        process.writeFloat(zPointer, saveState.z);
        process.writeFloat(xPointer, saveState.x);

        process.writeInt(scarfLength, saveState.scarfLength);
        process.writeInt(scarfPower, saveState.scarfPower);
        process.writeFloat(boostPointer, saveState.boost);
    }

    public record SaveState(float x, float y, float z, int scarfLength, int scarfPower, float boost) {}
}
