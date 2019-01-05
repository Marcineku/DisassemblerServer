package pl.wat.edu.wcy.jfk.disassemblerserver.disassembler;

import javax.xml.bind.annotation.adapters.HexBinaryAdapter;
import java.util.ArrayList;
import java.util.List;

public class CodeInterpreter {
    private List<InstructionParser.Instruction> instructions;

    public CodeInterpreter(List<InstructionParser.Instruction> instructions) {
        this.instructions = instructions;
    }

    public List<List<InterpretedInstruction>> interpret(List<List<Byte>> codeLists, List<PEFile.Code.SectionTable> codeTables, int imageBase) {
        List<List<InterpretedInstruction>> lists = new ArrayList<>();

        for (int l = 0; l < codeLists.size(); ++l) {
            List<Byte> code = codeLists.get(l);

            List<InterpretedInstruction> interpretedInstructions = new ArrayList<>();
            lists.add(interpretedInstructions);

            int p = 0;

            while (p < codeTables.get(l).getMisc()) {
                int b = code.get(p) & 0xFF;
                int length = 1;

                int cur = p;

                // Looking for prefix
                List<InstructionParser.Instruction> matchingInstructions = new ArrayList<>(instructions);
                if (isPrefix(b)) {
                    matchingInstructions.removeIf(i -> i.getPrefix().length() == 0);
                } else {
                    matchingInstructions.removeIf(i -> i.getPrefix().length() > 0);
                }

                System.out.println(p);
                matchingInstructions.forEach(System.out::println);

                if (matchingInstructions.size() == 1) {
                    InstructionParser.Instruction instruction = matchingInstructions.get(0);

                    int address = p + imageBase + codeTables.get(l).getVirtualAdress();
                    StringBuilder opcode = new StringBuilder();
                    if (!(p + length > code.size())) {
                        for (int i = p; i < p + length; ++i) {
                            opcode.append(String.format("%02X", code.get(i)));
                        }
                    }
                    String mnemo = instruction.getMnemonic();
                    String op1 = instruction.getOperand1();
                    String op2 = instruction.getOperand2();
                    String op3 = instruction.getOperand3();
                    interpretedInstructions.add(new InterpretedInstruction(address, opcode.toString(), mnemo, op1, op2, op3));

                    System.out.println(instruction);

                    p += length;
                    continue;
                }

                b = code.get(++cur) & 0xFF;
                ++length;

                p += length;
            }
        }

        return lists;
    }

    private boolean is0F(int b) {
        return b == 0x0F;
    }

    private boolean isPrefix(int b) {
        for (InstructionParser.Instruction instruction : instructions) {
            int prefix = hexStringToInt(instruction.getPrefix());

            if (b == prefix) {
                return true;
            }
        }

        return false;
    }

    private int hexStringToInt(String hex) {
        if (hex.length() < 2) return -1;

        HexBinaryAdapter hexBinaryAdapter = new HexBinaryAdapter();
        return hexBinaryAdapter.unmarshal(hex.substring(0, 2))[0] & 0xFF;
    }
}
