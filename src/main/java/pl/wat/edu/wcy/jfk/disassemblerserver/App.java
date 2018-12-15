package pl.wat.edu.wcy.jfk.disassemblerserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import pl.wat.edu.wcy.jfk.disassemblerserver.disassembler.InstructionPatternParser;
import pl.wat.edu.wcy.jfk.disassemblerserver.disassembler.MachineCodeInterpreter;

@SpringBootApplication
public class App {
    public static MachineCodeInterpreter machineCodeInterpreter;

    public static void main(String[] args) {
        String i386path = "src/main/resources/files/i386.pdf";
        InstructionPatternParser instructionPatternParser = new InstructionPatternParser(i386path);
        machineCodeInterpreter = new MachineCodeInterpreter(instructionPatternParser.getOneByteInstructions(), instructionPatternParser.getTwoByteInstructions());

        SpringApplication.run(App.class, args);
    }
}
