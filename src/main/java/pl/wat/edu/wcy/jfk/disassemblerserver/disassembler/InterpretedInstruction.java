package pl.wat.edu.wcy.jfk.disassemblerserver.disassembler;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class InterpretedInstruction {
    private int addr;
    private String opcode;
    private String mnemo;
    private String op1;
    private String op2;
    private String op3;

    @Override
    public String toString() {
        StringBuilder output = new StringBuilder();

        output.append(String.format("%08X", addr)).append("   ");
        output.append(opcode).append("  ");
        output.append(mnemo).append(" ");
        if (!op1.contentEquals("")) {
            output.append(op1);
            if (!op2.contentEquals("")) {
                output.append(", ").append(op2);
                if (!op3.contentEquals("")) {
                    output.append(", ").append(op3);
                }
            }
        }

        return output.toString();
    }
}
