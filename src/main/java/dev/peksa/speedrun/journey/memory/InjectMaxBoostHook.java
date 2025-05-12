package dev.peksa.speedrun.journey.memory;

import com.sun.jna.Pointer;
import dev.peksa.speedrun.process.OpenedProcess;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HexFormat;

public class InjectMaxBoostHook {

    private static final System.Logger LOGGER = System.getLogger(InjectMaxBoostHook.class.getSimpleName());

    private final OpenedProcess process;

    private Pointer codeCave;
    private Pointer maxBoostPointer;

    public InjectMaxBoostHook(OpenedProcess process) {
        this.process = process;
    }

    public void restoreOriginalCode() {
        var originalCode = new byte[] { 0x0F, 0x2F, (byte) 0xC8, 0x73, 0x0D };

        Pointer moduleBase = process.getModuleBase("Journey.exe");
        Pointer injectionPointAddr = moduleBase.share(0x241F2F);
        process.writeBytes(injectionPointAddr, originalCode);

        if (codeCave != null) {
            process.freeMemoryAt(codeCave);
        }
        if (maxBoostPointer != null) {
            process.freeMemoryAt(maxBoostPointer);
        }
        LOGGER.log(System.Logger.Level.INFO, "Successfully restored the original code, and deallocated previously allocated memory");
    }

    public Pointer injectMaxBoostCode() {
        Pointer moduleBase = process.getModuleBase("Journey.exe");
        Pointer injectionPointAddr = moduleBase.share(0x241F2F);

        LOGGER.log(System.Logger.Level.INFO, "Checking if code to be able to read maximum possible boost is already injected...");
        byte[] bytes = process.readBytes(injectionPointAddr, 1);

        if (bytes[0] == (byte) 0xE9) {
            LOGGER.log(System.Logger.Level.WARNING, "Code is already injected into Journey.exe, going to overwrite...");
        } else if (bytes[0] == (byte) 0x0F) {
            LOGGER.log(System.Logger.Level.INFO, "Code not injected into Journey.exe yet, will inject...");
        } else {
            LOGGER.log(System.Logger.Level.WARNING, "Unexpected instruction found in Journey.exe: " + HexFormat.of().formatHex(bytes));
        }

        // allocate memory for code, hopefully near (+- 2GB) of where we want to inject
        this.codeCave = process.allocateExecutableMemoryNear(64, injectionPointAddr);
        LOGGER.log(System.Logger.Level.INFO, "Allocated 64 bytes of executable memory in Journey.exe at " + codeCave);

        // allocate memory for a single float near our injected code, used to store the max boost possible
        this.maxBoostPointer = process.allocateWriteableMemoryNear(4, codeCave);
        LOGGER.log(System.Logger.Level.INFO, "Allocated 4 bytes of writeable memory in Journey.exe at " + maxBoostPointer);

        if (!isWithinRel32Range(injectionPointAddr, codeCave)) {
            throw new IllegalStateException("Code cave is not within +-2GB of injection point. JMP will not work.");
        }

        long codeCaveAddr = Pointer.nativeValue(codeCave);
        long maxBoostAddr = Pointer.nativeValue(maxBoostPointer);
        long targetAddress = Pointer.nativeValue(injectionPointAddr);

        Pointer skipTargetAddr = moduleBase.share(0x241F41);
        Pointer returnNormalAddr = moduleBase.share(0x241F34);

        byte[] patch = buildPatchCode(
                codeCaveAddr,
                maxBoostAddr,
                Pointer.nativeValue(skipTargetAddr),
                Pointer.nativeValue(returnNormalAddr)
        );

        process.writeBytes(codeCave, patch);
        LOGGER.log(System.Logger.Level.INFO, "Injected patch into code cave at " + codeCave);

        byte[] jumpInstruction = createJumpInstruction(targetAddress, codeCaveAddr);
        process.writeBytes(injectionPointAddr, jumpInstruction);

        LOGGER.log(System.Logger.Level.INFO, "Injected hook into max boost calculation code at " + injectionPointAddr);

        return maxBoostPointer;
    }

    public static boolean isWithinRel32Range(Pointer from, Pointer to) {
        long distance = Pointer.nativeValue(to) - (Pointer.nativeValue(from) + 5); // +5 to account for size of JMP
        return distance >= Integer.MIN_VALUE && distance <= Integer.MAX_VALUE;
    }

    private static byte[] buildPatchCode(long newmem, long storedMaxValue, long skipReturn, long normalReturn) {
        ByteBuffer buf = ByteBuffer.allocate(64);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        // 0x000: comiss xmm1, xmm0
        buf.put((byte) 0x0F).put((byte) 0x2F).put((byte) 0xC8);

        // 0x003: jae skip (fixed relative offset)
        buf.put((byte) 0x0F).put((byte) 0x83).putInt(0x0D);

        // 0x009: movss [storedMaxValue], xmm0 (RIP-relative)
        int disp1 = (int)(storedMaxValue - (newmem + buf.position() + 8));
        buf.put((byte) 0xF3).put((byte) 0x0F).put((byte) 0x11).put((byte) 0x05).putInt(disp1);

        // 0x011: jmp returnNormal (fixed relative offset)
        buf.put((byte) 0xE9).putInt(0x0D);

        // 0x016: movss [storedMaxValue], xmm0 (RIP-relative)
        int disp2 = (int)(storedMaxValue - (newmem + buf.position() + 8));
        buf.put((byte) 0xF3).put((byte) 0x0F).put((byte) 0x11).put((byte) 0x05).putInt(disp2);

        // 0x01E: jmp Journey.exe+241F41
        int relSkip = (int)(skipReturn - (newmem + buf.position() + 5));
        buf.put((byte) 0xE9).putInt(relSkip);

        // 0x023: jmp Journey.exe+241F34
        int relNormal = (int)(normalReturn - (newmem + buf.position() + 5));
        buf.put((byte) 0xE9).putInt(relNormal);

        byte[] result = new byte[buf.position()];
        buf.rewind();
        buf.get(result);
        return result;
    }


    private byte[] createJumpInstruction(long fromAddr, long toAddr) {
        // JMP rel32 instruction (E9 followed by 4-byte relative offset)
        long offset = toAddr - fromAddr - 5;

        return new byte[] {
                (byte)0xE9,
                (byte)(offset & 0xFF),
                (byte)((offset >> 8) & 0xFF),
                (byte)((offset >> 16) & 0xFF),
                (byte)((offset >> 24) & 0xFF)
        };
    }
}
