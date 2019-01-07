package pl.wat.edu.wcy.jfk.disassemblerserver.disassembler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.annotation.adapters.HexBinaryAdapter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class MachineCodeInterpreter {
    private static final Logger logger = LoggerFactory.getLogger(MachineCodeInterpreter.class);

    private List<List<InstructionPatternParser.InstructionPattern>> oneByteInstructions;
    private List<List<InstructionPatternParser.InstructionPattern>> twoByteInstructions;

    private List<String> slashDigit;
    private List<String> rb;
    private List<String> rw;
    private List<String> rd;
    private List<String> r8;
    private List<String> r16;
    private List<String> r32;
    private List<String> effectiveAddress;
    private List<String> scaledIndex;
    private List<String> sibr32;
    private List<String> sreg;
    private List<Integer> slashRSize;

    public MachineCodeInterpreter(List<List<InstructionPatternParser.InstructionPattern>> oneByteInstructions, List<List<InstructionPatternParser.InstructionPattern>> twoByteInstructions) {
        this.oneByteInstructions = oneByteInstructions;
        this.twoByteInstructions = twoByteInstructions;

        List<String> digits = new ArrayList<>();
        for (int i = 0; i <= 7; ++i) {
            digits.add("/" + i);
        }
        int currentDigit = 0;
        int digitCount = 0;
        slashDigit = new ArrayList<>();
        for (int i = 0; i <= 0xFF; ++i) {
            slashDigit.add(digits.get(currentDigit));
            ++digitCount;
            if (digitCount > 7) {
                digitCount = 0;
                ++currentDigit;
            }
            if (currentDigit > 7) {
                currentDigit = 0;
            }
        }

        slashRSize = new ArrayList<>();
        for (int i = 0; i <= 0xFF; ++i) {
            int size = 0;

            for (int j = 0x05; j <= 0x3D; j += 8) {
                if (i == j) {
                    size += 4;
                }
            }

            for (int j = 0x04; j <= 0x3C; j += 8) {
                if (i == j) {
                    size += 1;
                }
            }

            for (int j = 0x44; j <= 0x7C; j += 8) {
                if (i == j) {
                    size += 1;
                }
            }

            for (int j = 0x84; j <= 0xBC; j += 8) {
                if (i == j) {
                    size += 1;
                }
            }

            if (i >= 0x40 && i <= 0x7F) {
                size += 1;
            }
            if (i >= 0x80 && i <= 0xBF) {
                size += 4;
            }

            slashRSize.add(size);
        }

        rb = new ArrayList<>();
        rb.add("AL");
        rb.add("CL");
        rb.add("DL");
        rb.add("BL");
        rb.add("AH");
        rb.add("CH");
        rb.add("DH");
        rb.add("BH");

        rw = new ArrayList<>();
        rw.add("AX");
        rw.add("CX");
        rw.add("DX");
        rw.add("BX");
        rw.add("SP");
        rw.add("BP");
        rw.add("SI");
        rw.add("DI");

        rd = new ArrayList<>();
        rd.add("EAX");
        rd.add("ECX");
        rd.add("EDX");
        rd.add("EBX");
        rd.add("ESP");
        rd.add("EBP");
        rd.add("ESI");
        rd.add("EDI");

        int regCounter = 0;
        int regSwap = 0;
        r8 = new ArrayList<>();
        r16 = new ArrayList<>();
        r32 = new ArrayList<>();
        for (int i = 0; i <= 0xFF; ++i) {
            r8.add(rb.get(regCounter));
            r16.add(rw.get(regCounter));
            r32.add(rd.get(regCounter));

            ++regSwap;
            if (regSwap > 7) {
                ++regCounter;
                regSwap = 0;
            }
            if (regCounter > 7) {
                regCounter = 0;
            }
        }

        regCounter = 0;
        effectiveAddress = new ArrayList<>();
        for (int i = 0; i <= 0xFF; ++i) {
            if (i >= 0x00 && i <= 0x3F) {
                effectiveAddress.add("[" + rd.get(regCounter) + "]");
                ++regCounter;

                if (regCounter > 7) {
                    regCounter = 0;
                }

                for (int j = 0x04; j <= 0x3C; j += 8) {
                    if (i == j) {
                        effectiveAddress.set(j, "[--]");
                    }
                }
                for (int j = 0x05; j <= 0x3D; j += 8) {
                    if (i == j) {
                        effectiveAddress.set(j, "disp32");
                    }
                }
            }
            if (i >= 0x40 && i <= 0x7F) {
                effectiveAddress.add("disp8[" + rd.get(regCounter) + "]");
                ++regCounter;

                if (regCounter > 7) {
                    regCounter = 0;
                }

                for (int j = 0x44; j <= 0x7C; j += 8) {
                    if (i == j) {
                        effectiveAddress.set(j, "disp8[--]");
                    }
                }
            }
            if (i >= 0x80 && i <= 0xBF) {
                effectiveAddress.add("disp32[" + rd.get(regCounter) + "]");
                ++regCounter;

                if (regCounter > 7) {
                    regCounter = 0;
                }

                for (int j = 0x84; j <= 0xBC; j += 8) {
                    if (i == j) {
                        effectiveAddress.set(j, "disp32[--]");
                    }
                }
            }
            if (i >= 0xC0 && i <= 0xFF) {
                effectiveAddress.add("r" + regCounter);
                ++regCounter;

                if (regCounter > 7) {
                    regCounter = 0;
                }
            }
        }

        regCounter = 0;
        regSwap = 0;
        scaledIndex = new ArrayList<>();
        for (int i = 0; i <= 0xFF; ++i) {
            if (i >= 0x00 && i <= 0x3F) {
                scaledIndex.add(rd.get(regCounter));
                ++regSwap;

                if (regSwap > 7) {
                    ++regCounter;
                    regSwap = 0;
                }
                if (regCounter > 7) {
                    regCounter = 0;
                }

                for (int j = 0x20; j <= 0x27; ++j) {
                    if (i == j) {
                        scaledIndex.set(j, "");
                    }
                }
            }
            if (i >= 0x40 && i <= 0x7F) {
                scaledIndex.add(rd.get(regCounter) + "*2");
                ++regSwap;

                if (regSwap > 7) {
                    ++regCounter;
                    regSwap = 0;
                }
                if (regCounter > 7) {
                    regCounter = 0;
                }

                for (int j = 0x60; j <= 0x67; ++j) {
                    if (i == j) {
                        scaledIndex.set(j, "");
                    }
                }
            }
            if (i >= 0x80 && i <= 0xBF) {
                scaledIndex.add(rd.get(regCounter) + "*4");
                ++regSwap;

                if (regSwap > 7) {
                    ++regCounter;
                    regSwap = 0;
                }
                if (regCounter > 7) {
                    regCounter = 0;
                }

                for (int j = 0xA0; j <= 0xA7; ++j) {
                    if (i == j) {
                        scaledIndex.set(j, "");
                    }
                }
            }
            if (i >= 0xC0 && i <= 0xFF) {
                scaledIndex.add(rd.get(regCounter) + "*8");
                ++regSwap;

                if (regSwap > 7) {
                    ++regCounter;
                    regSwap = 0;
                }
                if (regCounter > 7) {
                    regCounter = 0;
                }

                for (int j = 0xE0; j <= 0xE7; ++j) {
                    if (i == j) {
                        scaledIndex.set(j, "");
                    }
                }
            }
        }

        regCounter = 0;
        sibr32 = new ArrayList<>();
        for (int i = 0; i <= 0xFF; ++i) {
            sibr32.add(rd.get(regCounter));
            ++regCounter;

            if (regCounter > 7) {
                regCounter = 0;
            }
        }

        List<String> sregS = new ArrayList<>();
        sregS.add("ES");
        sregS.add("CS");
        sregS.add("SS");
        sregS.add("DS");
        sregS.add("FS");
        sregS.add("GS");
        sregS.add("");
        sregS.add("");

        regCounter = 0;
        regSwap = 0;
        sreg = new ArrayList<>();
        for (int i = 0; i <= 0xFF; ++i) {
            sreg.add(sregS.get(regCounter));

            ++regSwap;
            if (regSwap > 7) {
                ++regCounter;
                regSwap = 0;
            }
            if (regCounter > 7) {
                regCounter = 0;
            }
        }
    }

    public List<List<InterpretedInstruction>> interpret(List<List<Byte>> machineCode, List<PEFile.Code.SectionTable> codeTables, int imageBase) {
        List<List<InterpretedInstruction>> interpretedInstructions = new ArrayList<>();

        for (int l = 0; l < machineCode.size(); ++l) {
            List<Byte> list = machineCode.get(l);

            int p = 0;

            List<InterpretedInstruction> interpretedInstructionList = new ArrayList<>();
            interpretedInstructions.add(interpretedInstructionList);

            while (p < codeTables.get(l).getMisc()) {
                int b = list.get(p) & 0xFF;

                int pf = 0;

                List<List<InstructionPatternParser.InstructionPattern>> instructions;
                int priOpcodeSize = 0;
                int length = 0;
                String mnemoAddition = "";
                String segmentOverridePrefix = "";

                // Checking for prefixes
                int pfTmp;
                do {
                    pfTmp = pf;
                    if (b == 0xF2) {
                        int tmpB = list.get(p + 1 + pf) & 0xFF;
                        if (tmpB == 0xA6 || tmpB == 0xA7 || tmpB == 0xAE || tmpB == 0xAF) {
                        } else {
                            b = tmpB;
                            priOpcodeSize += 1;
                            length += 1;
                            mnemoAddition = "BND";
                            pf += 1;
                        }
                    } else if (b == 0xF3) {
                        b = list.get(p + 1 + pf) & 0xFF;
                        priOpcodeSize += 1;
                        length += 1;
                        mnemoAddition = "REP";
                        pf += 1;
                    } else if (b == 0xF0) {
                        b = list.get(p + 1 + pf) & 0xFF;
                        priOpcodeSize += 1;
                        length += 1;
                        mnemoAddition = "LOCK";
                        pf += 1;
                    } else if (b == 0x66) {
                        b = list.get(p + 1 + pf) & 0xFF;
                        priOpcodeSize += 1;
                        length += 1;
                        mnemoAddition = "data16";
                        pf += 1;
                    } else if (b == 0x67) {
                        b = list.get(p + 1 + pf) & 0xFF;
                        priOpcodeSize += 1;
                        length += 1;
                        mnemoAddition = "addr16";
                        pf += 1;
                    } else if (b == 0x2E) {
                        b = list.get(p + 1 + pf) & 0xFF;
                        priOpcodeSize += 1;
                        length += 1;
                        segmentOverridePrefix = "CS";
                        pf += 1;
                    } else if (b == 0x36) {
                        b = list.get(p + 1 + pf) & 0xFF;
                        priOpcodeSize += 1;
                        length += 1;
                        segmentOverridePrefix = "SS";
                        pf += 1;
                    } else if (b == 0x3E) {
                        b = list.get(p + 1 + pf) & 0xFF;
                        priOpcodeSize += 1;
                        length += 1;
                        segmentOverridePrefix = "DS";
                        pf += 1;
                    } else if (b == 0x26) {
                        b = list.get(p + 1 + pf) & 0xFF;
                        priOpcodeSize += 1;
                        length += 1;
                        segmentOverridePrefix = "ES";
                        pf += 1;
                    } else if (b == 0x64) {
                        b = list.get(p + 1 + pf) & 0xFF;
                        priOpcodeSize += 1;
                        length += 1;
                        segmentOverridePrefix = "FS";
                        pf += 1;
                    } else if (b == 0x65) {
                        b = list.get(p + 1 + pf) & 0xFF;
                        priOpcodeSize += 1;
                        length += 1;
                        segmentOverridePrefix = "GS";
                        pf += 1;
                    }
                } while (pfTmp != pf);

                // Checking for instruction type
                if (b == 0x0F) {
                    b = list.get(p + 1) & 0xFF;
                    if (b == 0x0F) b = list.get(p + 2) & 0xFF;

                    instructions = twoByteInstructions;
                    priOpcodeSize += 2;
                } else {
                    instructions = oneByteInstructions;
                    priOpcodeSize += 1;
                }

                InstructionPatternParser.InstructionPattern instr;

                if (instructions.get(b).size() > 0) {
                    instr = instructions.get(b).get(0);
                }
                else {
                    String whichInstrSet;
                    if (instructions.equals(oneByteInstructions)) {
                        whichInstrSet = "One Byte";
                    } else {
                        whichInstrSet = "Two Byte";
                    }

                    logger.error(whichInstrSet + ": " + String.format("%08X ", p + imageBase + codeTables.get(l).getVirtualAdress()) + String.format("%02X ", list.get(p)) + String.format("%02X", b));
                    p += 1;

                    continue;
                }

                // Trying to get second byte in one-byte instr or third byte in two-byte instr
                if (p + priOpcodeSize < list.size()) {
                    int b2 = list.get(p + priOpcodeSize) & 0xFF;

                    if (instr.getOpcode().contains("/r")) {
                        length += slashRSize.get(b2);

                        boolean sibByte = false;
                        for (int j = 0x04; j <= 0x3C; j += 8) {
                            if (b2 == j) {
                                sibByte = true;
                            }
                        }

                        // Checking for disp32 following SIB Byte
                        if (sibByte) {
                            int b3 = list.get(p + priOpcodeSize + 1) & 0xFF;
                            for (int j = 0x05; j <= 0xFD; j += 8) {
                                if (b3 == j) {
                                    length += 4;
                                }
                            }
                        }
                    } else if (instr.getOpcode().matches("[ -~]*[/][0-9][ -~]*")) {
                        String digit = slashDigit.get(b2);
                        for (int i = 0; i < instructions.get(b).size(); ++i) {
                            InstructionPatternParser.InstructionPattern instruction = instructions.get(b).get(i);
                            if (instruction.getOpcode().contains(digit)) {
                                instr = instruction;
                            }
                        }
                        length += slashRSize.get(b2);

                        boolean sibByte = false;
                        for (int j = 0x04; j <= 0x3C; j += 8) {
                            if (b2 == j) {
                                sibByte = true;
                            }
                        }

                        // Checking for disp32 following SIB Byte
                        if (sibByte) {
                            int b3 = list.get(p + priOpcodeSize + 1) & 0xFF;
                            for (int j = 0x05; j <= 0xFD; j += 8) {
                                if (b3 == j) {
                                    length += 4;
                                }
                            }
                        }
                    } else if (instr.getOpcode().contains("0F01")) {
                        int thirdByte = list.get(p + 2) & 0xFF;

                        if (thirdByte == 0xD0) {
                            instr = instructions.get(0x01).get(0);
                        } else if (thirdByte == 0xD1) {
                            instr = instructions.get(0x01).get(1);
                        } else {
                            instr = instructions.get(0x01).get(2);
                        }
                    }
                }

                length += instr.getLength();

                int addr = p + imageBase + codeTables.get(l).getVirtualAdress();
                StringBuilder opcode = new StringBuilder();

                if (!(p + length > list.size())) {
                    for (int i = p; i < p + length; ++i) {
                        opcode.append(String.format("%02X", list.get(i)));
                    }
                }

                String[] operands = instr.getOperands().split(",");
                String[] tmp = new String[3];
                if (operands.length == 3) {
                    tmp[0] = operands[0];
                    tmp[1] = operands[1];
                    tmp[2] = operands[2];
                } else if (operands.length == 2) {
                    tmp[0] = operands[0];
                    tmp[1] = operands[1];
                    tmp[2] = "";
                } else if (operands.length == 1) {
                    tmp[0] = operands[0];
                    tmp[1] = "";
                    tmp[2] = "";
                }
                operands = tmp;

                for (int i = 0; i < instr.getOpcode().length(); i += 2) {
                    String opByte = instr.getOpcode().substring(i, i + 2);
                    if (opByte.equals("+r")) {
                        opByte = instr.getOpcode().substring(i, i + 3);
                        i += 1;
                    }

                    if (opByte.equals("cb")) {
                        byte cb = list.get(p + priOpcodeSize);

                        for (int j = 0; j < operands.length; ++j) {
                            if (operands[j].equals("rel8")) {
                                operands[j] = String.format("%02X", cb);
                            }
                        }
                    } else if (opByte.equals("cd")) {
                        ByteBuffer byteBuffer = ByteBuffer.allocate(4);
                        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                        for (int j = p + priOpcodeSize; j < p + priOpcodeSize + 4; ++j) {
                            byteBuffer.put(list.get(j));
                        }
                        int cd = byteBuffer.getInt(0);

                        for (int j = 0; j < operands.length; ++j) {
                            if (operands[j].equals("rel32")) {
                                operands[j] = String.format("%08X", cd);
                            }
                        }
                    }

                    if (opByte.equals("+rb")) {
                        HexBinaryAdapter hexBinaryAdapter = new HexBinaryAdapter();
                        byte base = hexBinaryAdapter.unmarshal(instr.getOpcode().substring(0, 2))[0];
                        int rByte = list.get(p + priOpcodeSize - 1) - base;

                        for (int j = 0; j < operands.length; ++j) {
                            if (operands[j].equals("reg8") || operands[j].equals("r8")) {
                                operands[j] = rb.get(rByte);
                            }
                        }
                    } else if (opByte.equals("+rw") || opByte.equals("+rd")) {
                        HexBinaryAdapter hexBinaryAdapter = new HexBinaryAdapter();
                        byte base = hexBinaryAdapter.unmarshal(instr.getOpcode().substring(0, 2))[0];
                        int rByte = list.get(p + priOpcodeSize - 1) - base;

                        for (int j = 0; j < operands.length; ++j) {
                            if (operands[j].equals("r32") || operands[j].equals("reg32")) {
                                operands[j] = rd.get(rByte);
                            }
                        }
                    }

                    if ((opByte.equals("/r") || opByte.matches("[ -~]*[/][0-9][ -~]*")) && p + priOpcodeSize < list.size()) {
                        int slashRByte = list.get(p + priOpcodeSize) & 0xFF;

                        for (int j = 0; j < operands.length; ++j) {
                            if (operands[j].equals("r/m8")) {
                                operands[j] = effectiveAddress.get(slashRByte);
                                if (operands[j].contains("r")) {
                                    operands[j] = rb.get(operands[j].toCharArray()[1] - '0');
                                }
                            } else if (operands[j].equals("r/m16")) {
                                operands[j] = effectiveAddress.get(slashRByte);
                                if (operands[j].contains("r")) {
                                    operands[j] = rw.get(operands[j].toCharArray()[1] - '0');
                                }
                            } else if (operands[j].equals("r/m32")) {
                                operands[j] = effectiveAddress.get(slashRByte);
                                if (operands[j].contains("r")) {
                                    operands[j] = rd.get(operands[j].toCharArray()[1] - '0');
                                }
                            } else if (operands[j].equals("r8")) {
                                operands[j] = r8.get(slashRByte);
                            } else if (operands[j].equals("r16")) {
                                operands[j] = r16.get(slashRByte);
                            } else if (operands[j].equals("r32")) {
                                operands[j] = r32.get(slashRByte);
                            }

                            if (operands[j].equals("disp8[--]")) {
                                int sib = list.get(p + priOpcodeSize + 1) & 0xFF;
                                String sibScaledIndex = scaledIndex.get(sib);

                                int disp8 = list.get(p + priOpcodeSize + 2) & 0xFF;

                                if (sibScaledIndex.equals("")) {
                                    operands[j] = "[" + sibr32.get(sib) + " + " + String.format("%02X]", disp8);
                                } else {
                                    operands[j] = "[" + sibr32.get(sib) + " + " + scaledIndex.get(sib) + " + " + String.format("%02X]", disp8);
                                }
                            } else if (operands[j].contains("disp8[")) {
                                String reg = operands[j].substring(6, 9);
                                int disp8 = list.get(p + priOpcodeSize + 1) & 0xFF;
                                operands[j] = "[" + reg + " + " + String.format("%02X]", disp8);
                            }

                            if (operands[j].equals("disp32[--]")) {
                                int sib = list.get(p + priOpcodeSize + 1) & 0xFF;
                                String sibScaledIndex = scaledIndex.get(sib);

                                ByteBuffer byteBuffer = ByteBuffer.allocate(4);
                                byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                                for (int k = p + priOpcodeSize + 2; k < p + priOpcodeSize + 2 + 4; ++k) {
                                    byteBuffer.put(list.get(k));
                                }
                                int disp32 = byteBuffer.getInt(0);

                                if (sibScaledIndex.equals("")) {
                                    operands[j] = "[" + sibr32.get(sib) + " + " + String.format("%08X]", disp32);
                                } else {
                                    operands[j] = "[" + sibr32.get(sib) + " + " + scaledIndex.get(sib) + " + " + String.format("%08X]", disp32);
                                }
                            } else if (operands[j].contains("disp32[")) {
                                String reg = operands[j].substring(7, 10);

                                ByteBuffer byteBuffer = ByteBuffer.allocate(4);
                                byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                                for (int k = p + priOpcodeSize + 1; k < p + priOpcodeSize + 1 + 4; ++k) {
                                    byteBuffer.put(list.get(k));
                                }
                                int disp32 = byteBuffer.getInt(0);
                                operands[j] = "[" + reg + " + " + String.format("%08X]", disp32);
                            } else if (operands[j].contains("disp32")) {
                                ByteBuffer byteBuffer = ByteBuffer.allocate(4);
                                byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                                for (int k = p + priOpcodeSize + 1; k < p + priOpcodeSize + 1 + 4; ++k) {
                                    byteBuffer.put(list.get(k));
                                }
                                int disp32 = byteBuffer.getInt(0);
                                operands[j] = String.format("%08X", disp32);
                            }

                            if (operands[j].contains("[--]")) {
                                int sib = list.get(p + priOpcodeSize + 1) & 0xFF;
                                String sibScaledIndex = scaledIndex.get(sib);

                                boolean sibByte = false;
                                for (int k = 0x04; k <= 0x3C; k += 8) {
                                    if (slashRByte == k) {
                                        sibByte = true;
                                    }
                                }

                                boolean isDisp32 = false;
                                // Checking for disp32 following SIB Byte
                                if (sibByte) {
                                    for (int k = 0x05; k <= 0xFD; k += 8) {
                                        if (sib == k) {
                                            isDisp32 = true;
                                        }
                                    }
                                }

                                if (sibScaledIndex.equals("")) {
                                    operands[j] = "[" + sibr32.get(sib) + "]";
                                } else {
                                    operands[j] = "[" + sibr32.get(sib) + " + " + scaledIndex.get(sib) + "]";
                                }

                                if (isDisp32) {
                                    ByteBuffer byteBuffer = ByteBuffer.allocate(4);
                                    byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                                    for (int k = p + priOpcodeSize + 2; k < p + priOpcodeSize + 2 + 4; ++k) {
                                        byteBuffer.put(list.get(k));
                                    }
                                    int disp32 = byteBuffer.getInt(0);

                                    operands[j] = "[" + scaledIndex.get(sib)  + " + " + String.format("%08X]", disp32);
                                }
                            }

                            if (operands[j].contains("Sreg")) {
                                operands[j] = sreg.get(slashRByte);
                            }
                        }
                    }

                    int slashRLength = 0;
                    if ((instr.getOpcode().contains("/r") || instr.getOpcode().matches("[ -~]*[/][0-9][ -~]*")) && p + priOpcodeSize < list.size()) {
                        slashRLength = slashRSize.get(list.get(p + priOpcodeSize) & 0xFF) + 1;
                    }

                    if (opByte.equals("ib") && p + priOpcodeSize + slashRLength < list.size()) {
                        int imm8Byte = list.get(p + priOpcodeSize + slashRLength) & 0xFF;

                        for (int j = 0; j < operands.length; ++j) {
                            if (operands[j].equals("imm8")) {
                                operands[j] = String.format("%02X", imm8Byte);
                            }
                        }
                    } else if (opByte.equals("iw")) {
                        ByteBuffer byteBuffer = ByteBuffer.allocate(2);
                        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                        for (int j = p + priOpcodeSize + slashRLength; j < p + priOpcodeSize + slashRLength + 2; ++j) {
                            byteBuffer.put(list.get(j));
                        }
                        int iw = byteBuffer.getShort(0);

                        for (int j = 0; j < operands.length; ++j) {
                            if (operands[j].equals("imm16")) {
                                operands[j] = String.format("%04X", iw);
                            }
                        }
                    } else if (opByte.equals("id")) {
                        ByteBuffer byteBuffer = ByteBuffer.allocate(4);
                        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                        for (int j = p + priOpcodeSize + slashRLength; j < p + priOpcodeSize + slashRLength + 4; ++j) {
                            byteBuffer.put(list.get(j));
                        }
                        int id = byteBuffer.getInt(0);

                        for (int j = 0; j < operands.length; ++j) {
                            if (operands[j].equals("imm32")) {
                                operands[j] = String.format("%08X", id);
                            }
                        }
                    }

                    if (opByte.equals("mb") && p + priOpcodeSize + slashRLength < list.size()) {
                        int moffs8 = list.get(p + priOpcodeSize + slashRLength) & 0xFF;

                        for (int j = 0; j < operands.length; ++j) {
                            if (operands[j].equals("moffs8")) {
                                if (segmentOverridePrefix.length() == 0) segmentOverridePrefix = "DS";
                                operands[j] = segmentOverridePrefix + ":" + String.format("%02X", moffs8);
                            }
                        }
                    } else if (opByte.equals("md")) {
                        ByteBuffer byteBuffer = ByteBuffer.allocate(4);
                        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                        for (int j = p + priOpcodeSize + slashRLength; j < p + priOpcodeSize + slashRLength + 4; ++j) {
                            byteBuffer.put(list.get(j));
                        }
                        int md = byteBuffer.getInt(0);

                        for (int j = 0; j < operands.length; ++j) {
                            if (operands[j].equals("moffs32")) {
                                if (segmentOverridePrefix.length() == 0) segmentOverridePrefix = "DS";
                                operands[j] = segmentOverridePrefix + ":" + String.format("%08X", md);
                            }
                        }
                    }
                }

                String mnemo = mnemoAddition;

                if (!operands[0].contains(":") && !operands[1].contains(":") && !operands[2].contains(":") && !instr.getOperands().contains("moffs")) {
                    mnemo = mnemo + " " + segmentOverridePrefix;
                }
                mnemo = mnemo + " " + instr.getMnemo();

                interpretedInstructionList.add(new InterpretedInstruction(addr, opcode.toString(), mnemo, operands[0], operands[1], operands[2]));

                p += length;
            }
        }

        return interpretedInstructions;
    }
}
