package pl.wat.edu.wcy.jfk.disassemblerserver.controllers;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.wat.edu.wcy.jfk.disassemblerserver.App;
import pl.wat.edu.wcy.jfk.disassemblerserver.disassembler.InterpretedInstruction;
import pl.wat.edu.wcy.jfk.disassemblerserver.disassembler.PEFile;

import javax.xml.bind.annotation.adapters.HexBinaryAdapter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RestController
@CrossOrigin
public class TextController {
    private static final Logger logger = LoggerFactory.getLogger(TextController.class);

    @PostMapping("/text")
    public List<List<InterpretedInstruction>> disassembleText(@RequestParam("text") String text) {
        List<List<InterpretedInstruction>> interpretedInstructions = null;

        if (text == null) {
            logger.warn("Text not found");
            return interpretedInstructions;
        }

        text = text.replaceAll("\\s+", "");

        if (!isEven(text.length())) {
            logger.warn("Text length need to be even");
            return interpretedInstructions;
        }

        if (!text.matches("[0-9a-fA-F]+")) {
            logger.warn("Text need to contain only hex numbers");
            return interpretedInstructions;
        }

        logger.info("Text successfully uploaded!");

        HexBinaryAdapter hexBinaryAdapter = new HexBinaryAdapter();
        byte[] machineCode = hexBinaryAdapter.unmarshal(text);
        List<List<Byte>> lists = new ArrayList<>();
        List<Byte> bytes = Arrays.asList(ArrayUtils.toObject(machineCode));
        lists.add(bytes);

        PEFile peFile = new PEFile(machineCode);

        interpretedInstructions = App.machineCodeInterpreter.interpret(lists, peFile.getCodeTables(), 0);

        return interpretedInstructions;
    }

    private boolean isEven(int n) {
        return (n & 1) == 0;
    }
}
