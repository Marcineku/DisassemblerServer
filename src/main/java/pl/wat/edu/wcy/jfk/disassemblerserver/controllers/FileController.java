package pl.wat.edu.wcy.jfk.disassemblerserver.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pl.wat.edu.wcy.jfk.disassemblerserver.App;
import pl.wat.edu.wcy.jfk.disassemblerserver.disassembler.InterpretedInstruction;
import pl.wat.edu.wcy.jfk.disassemblerserver.disassembler.PEFile;

import java.io.IOException;
import java.util.List;

@RestController
@CrossOrigin
public class FileController {
    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    @PostMapping("/file")
    public List<List<InterpretedInstruction>> disassembleFile(@RequestParam("file") MultipartFile file) {

        List<List<InterpretedInstruction>> interpretedInstructions = null;

        if (file == null) {
            logger.warn("File not found");
            return interpretedInstructions;
        }

        try {
            PEFile peFile = new PEFile(file.getInputStream());

            if (!peFile.isPE()) {
                logger.warn(file.getOriginalFilename() + " is not a PE file");
                return interpretedInstructions;
            }
            logger.info(file.getOriginalFilename() + " successfully uploaded!");

            interpretedInstructions = App.machineCodeInterpreter.interpret(peFile.getMachineCode(), peFile.getCodeTables(), peFile.getImageBase());

        } catch (IOException e) {
            logger.error(e.getMessage());
        }

        return interpretedInstructions;
    }
}
