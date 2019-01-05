package pl.wat.edu.wcy.jfk.disassemblerserver.disassembler;

import lombok.Getter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Getter
public class InstructionParser {
    private List<Instruction> instructions;

    public InstructionParser(String coder32path) {
        this.instructions = new ArrayList<>();

        try {
            File html = new File(coder32path);
            Document doc = Jsoup.parse(html, "UTF-8");

            Element refTable = doc.getElementsByClass("ref_table notpublic").first();
            Elements tbodys = refTable.getElementsByTag("tbody");

            for (Element tbody : tbodys) {
                Element tr = tbody.children().first();
                List<String> text = new ArrayList<>();
                for (Element td : tr.children()) {
                    text.add(td.wholeText());
                }
                instructions.add(new Instruction(text));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (Instruction instruction : instructions) {
            System.out.println(instruction);
        }
    }

    @Getter
    public class Instruction {
        private String mnemonic;
        private String operand1;
        private String operand2;
        private String operand3;
        private String operand4;
        private String instructionExtensionGroup;
        private String prefix;
        private String prefix0F;
        private String primaryOpcode;
        private String secondaryOpcode;
        private String registerOpcodeField;

        private Instruction(List<String> text) {
            int i = 0;
            this.mnemonic = text.get(i).trim();
            this.operand1 = text.get(++i).trim();
            this.operand2 = text.get(++i).trim();
            this.operand3 = text.get(++i).trim();
            this.operand4 = text.get(++i).trim();
            this.instructionExtensionGroup = text.get(++i).trim();
            this.prefix = text.get(++i).trim();
            this.prefix0F = text.get(++i).trim();
            this.primaryOpcode = text.get(++i).trim();
            this.secondaryOpcode = text.get(++i).trim();
            this.registerOpcodeField = text.get(++i).trim();
        }

        @Override
        public String toString() {
            return String.format("%1$-12s", this.mnemonic)
                    + String.format("%1$-12s", this.operand1)
                    + String.format("%1$-12s", this.operand2)
                    + String.format("%1$-12s", this.operand3)
                    + String.format("%1$-12s", this.operand4)
                    + String.format("%1$-12s", this.instructionExtensionGroup)
                    + String.format("%1$-12s", this.prefix)
                    + String.format("%1$-12s", this.prefix0F)
                    + String.format("%1$-12s", this.primaryOpcode)
                    + String.format("%1$-12s", this.secondaryOpcode)
                    + String.format("%1$-12s", this.registerOpcodeField);
        }
    }
}
