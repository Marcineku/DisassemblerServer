package pl.wat.edu.wcy.jfk.disassemblerserver.disassembler;

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import lombok.AllArgsConstructor;
import lombok.Getter;

import javax.xml.bind.annotation.adapters.HexBinaryAdapter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
public class InstructionPatternParser {
    private List<List<InstructionPattern>> oneByteInstructions;
    private List<List<InstructionPattern>> twoByteInstructions;

    public InstructionPatternParser(String i386path) {
        List<String> parsedPages = new ArrayList<>();
        List<String> parsedLines = new ArrayList<>();
        List<InstructionPattern> instructionPatterns = new ArrayList<>();

        try {
            PdfReader pdfReader = new PdfReader(i386path);
            String pattern = "(?<=((Opcode[ ]{1,20}Instruction[ ]{1,20}Clocks[ ]{1,20}Description)|(Translation)\\n))[ -~\\n≠←≤]+?(?=(Operation|─|Page|Description))";
            Pattern regex = Pattern.compile(pattern);

            for (int i = 256; i < 412; ++i) {
                String text = PdfTextExtractor.getTextFromPage(pdfReader, i);

                Matcher matcher = regex.matcher(text);

                int j = 0;
                while (matcher.find()) {
                    parsedPages.add(matcher.group(j));
                    ++j;
                }
            }

            pdfReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (String page : parsedPages) {
            String[] lines = page.split("\\r?\\n");
            Collections.addAll(parsedLines, lines);
        }

        // Only instruction w/o opcode in entire intel doc
        String dec = parsedLines.get(159);
        char[] decArray = dec.toCharArray();
        decArray[0] = 'F';
        decArray[1] = 'F';
        decArray[2] = ' ';
        decArray[3] = '/';
        decArray[4] = '1';
        parsedLines.set(159, new String(decArray));

        // Remove empty lines or ones with leftovers from description
        ListIterator<String> iterator = parsedLines.listIterator();
        while (iterator.hasNext()) {
            String s = iterator.next();
            char[] sArray = s.toCharArray();

            if (s.length() > 0) {
                if (sArray[0] == ' ' && sArray[1] == ' ' || sArray[0] == 'I' && sArray[1] == 'f' || sArray[0] == 'N' && sArray[1] == 'o') {
                    iterator.remove();
                }
            } else {
                iterator.remove();
            }
        }

        // Sorting instructionPatterns from 00 to FF
        Collections.sort(parsedLines);

        // Changing opcodes from OF to 0F
        String push = parsedLines.get(647);
        char[] pushArray = push.toCharArray();
        pushArray[0] = '0';
        parsedLines.set(647, new String(pushArray));

        String clts = parsedLines.get(648);
        char[] cltsArray = clts.toCharArray();
        cltsArray[0] = '0';
        parsedLines.set(648, new String(cltsArray));


        // Changing MOV r/m8,imm8 opcode from Ciiiiii to C6
        String mov = parsedLines.get(478);
        char[] movArray = mov.toCharArray();
        movArray[1] = '6';
        for (int i = 2; i < 7; ++i) {
            movArray[i] = ' ';
        }
        parsedLines.set(478, new String(movArray));

        // Changing STI opcode from F13 to FB
        String sti = parsedLines.get(564);
        char[] stiArray = sti.toCharArray();
        stiArray[1] = 'B';
        stiArray[2] = ' ';
        parsedLines.set(564, new String(stiArray));

        // Sorting again
        Collections.sort(parsedLines);

        // Splitting lines of instruction to sections
        for (int i = 0; i < parsedLines.size(); ++i) {
            List<String> tmp = new ArrayList<>();
            String[] test = parsedLines.get(i).split(" ");

            boolean foundMnemonic = false;
            for (int j = 0; j < test.length; ++j) {
                String s = test[j];

                if (s.length() > 0) {
                    tmp.add(s);

                    if (foundMnemonic) break;

                    // Only instruction with 2 word mnemo
                    if (s.matches("REP|REP.|REP..")) {
                        foundMnemonic = true;
                        tmp.remove(s);
                        tmp.add(test[j] + test[++j]);

                    } else if (s.matches("\\b([A-Z]+)\\b(?<!\\b((SS)|(DS)|(ES)|(AL)|(EAX)|(AX)|(CS)|(FS)|(GS)|(CR)|(DR)|(TR)|(CL)|(DX)|([A-F]{2,2}))\\b)")) {
                        foundMnemonic = true;
                    }
                }
            }
            instructionPatterns.add(new InstructionPattern(tmp));
        }

        // Clearing incorrect operands
        for (int i = 0; i < instructionPatterns.size(); ++i) {
            String operands = instructionPatterns.get(i).operands;

            if (operands.matches("[ -~]+(\\[*]+)?([=]+)[ -~]+")) {
                instructionPatterns.get(i).operands = "";
            }

            if (operands.matches("\\b[ts]+\\b")) {
                instructionPatterns.get(i).operands = "";
            }

            if (operands.matches("\\b[10+m]+\\b")) {
                instructionPatterns.get(i).operands = "";
            }

            if (operands.matches("[0-9]+")) {
                if (instructionPatterns.get(i).mnemo.matches("[INT]+") && operands.getBytes()[0] == '3') {
                } else {
                    instructionPatterns.get(i).operands = "";
                }
            }

            if (operands.matches("[ -~]*[,]")) {
                instructionPatterns.get(i).operands = operands + "DX";
            }
        }

        // Every instruction with same opcode lands in same list
        List<List<InstructionPattern>> lists = new ArrayList<>();
        for (int i = 0; i <= 0xFF; ++i) {
            lists.add(new ArrayList<>());
        }
        for (int i = 0; i <= 0xFF; ++i) {
            for (int j = 0; j < instructionPatterns.size(); ++j) {
                InstructionPattern instructionPattern = instructionPatterns.get(j);

                if (instructionPattern.priOpcode == (byte) i) {
                    lists.get(i).add(instructionPattern);
                }
            }
        }

        // Deleting 16 bit versions of instructionPatterns
        for (int i = 0; i <= 0xFF; ++i) {
            if (i == 0x0F) continue;
            List<InstructionPattern> list = lists.get(i);
            if (list.size() != 2) continue;
            ListIterator<InstructionPattern> listIterator = list.listIterator();
            while (listIterator.hasNext()) {
                InstructionPattern instructionPattern = listIterator.next();
                if (!instructionPattern.operands.contains("16") && !instructionPattern.operands.contains("32")) continue;
                if (instructionPattern.operands.contains("16")) listIterator.remove();
            }
        }

        // One-byte opcodes
        // Deleting some of a instructionPatterns with same opcode and mnemo and some 16 bit versions of 32 bit instr
        for (int i = 0; i <= 0xFF; ++i) {
            if (i == 0x0F) continue;
            List<InstructionPattern> list = lists.get(i);
            if (list.size() != 2) continue;
            if (list.get(0).opcode.equals(list.get(1).opcode) && list.get(0).mnemo.equals(list.get(1).mnemo)) {
                list.remove(0);
            }
        }
        for (int i = 0; i <= 0xFF; ++i) {
            if (i == 0x0F) continue;
            List<InstructionPattern> list = lists.get(i);
            if (list.size() != 2) continue;
            if (list.get(0).opcode.equals(list.get(1).opcode) && list.get(0).operands.equals(list.get(1).operands)) {
                list.remove(1);
            }
        }
        for (int i = 0; i <= 0xFF; ++i) {
            if (i == 0x0F || i == 0xFE) continue;
            List<InstructionPattern> list = lists.get(i);
            if (list.size() != 2) continue;
            list.remove(1);
        }
        for (int i = 0; i <= 0xFF; ++i) {
            if (i == 0x0F) continue;
            List<InstructionPattern> list = lists.get(i);
            if (list.size() != 3) continue;
            list.remove(0);
            list.remove(0);
        }
        for (int i = 0; i <= 0xFF; ++i) {
            if (i == 0x0F) continue;
            List<InstructionPattern> list = lists.get(i);
            if (list.size() != 4) continue;
            if (i == 0x6B) {
                list.remove(0);
                list.remove(0);
                list.remove(0);
            } else {
                list.remove(0);
                list.remove(1);
                list.remove(1);
            }
        }
        for (int i = 0; i <= 0xFF; ++i) {
            if (i == 0x0F || i == 0x90) continue;
            List<InstructionPattern> list = lists.get(i);
            if (list.size() != 5) continue;
            list.remove(0);
            list.remove(0);
            list.remove(0);
            list.remove(0);
        }
        for (int i = 0; i <= 0xFF; ++i) {
            if (i == 0x0F) continue;
            List<InstructionPattern> list = lists.get(i);
            if (list.size() != 5) continue;
            InstructionPattern xchg = list.get(2);
            xchg.opcode = xchg.opcode + "d";
            for (int j = 0x91; j <= 0x97; ++j) {
                lists.get(j).add(xchg);
            }
            list.remove(1);
            list.remove(1);
            list.remove(1);
            list.remove(1);
        }
        for (int i = 0; i <= 0xFF; ++i) {
            if (i == 0x0F || i == 0xF2 || i == 0xF6) continue;
            List<InstructionPattern> list = lists.get(i);
            if (list.size() != 7) continue;
            list.remove(1);
            list.remove(1);
            list.remove(1);
            list.remove(1);
            list.remove(1);
            list.remove(1);
        }
        for (int i = 0; i <= 0xFF; ++i) {
            if (i == 0x0F) continue;
            List<InstructionPattern> list = lists.get(i);
            if (list.size() != 6) continue;
            list.remove(1);
            list.remove(3);
        }
        for (int i = 0; i <= 0xFF; ++i) {
            if (i == 0x0F || i == 0x80 || i == 0xEA) continue;
            List<InstructionPattern> list = lists.get(i);
            if (list.size() != 8) continue;
            list.remove(0);
        }
        for (int i = 0; i <= 0xFF; ++i) {
            if (i == 0x0F || i == 0x80) continue;
            List<InstructionPattern> list = lists.get(i);
            if (list.size() != 8) continue;
            list.remove(0);
            list.remove(0);
            list.remove(0);
            list.remove(0);
            list.remove(1);
            list.remove(1);
            list.remove(1);
        }

        for (int i = 0; i <= 0xFF; ++i) {
            if (i == 0x0F) continue;
            List<InstructionPattern> list = lists.get(i);
            if (list.size() != 10) continue;
            list.remove(0);
            list.remove(0);
            list.remove(0);
            list.remove(0);
            list.remove(0);
            list.remove(1);
            list.remove(1);
            list.remove(1);
            list.remove(1);
        }

        for (int i = 0; i <= 0xFF; ++i) {
            if (i == 0x0F) continue;
            List<InstructionPattern> list = lists.get(i);
            if (list.size() != 14) continue;
            ListIterator<InstructionPattern> listIterator = list.listIterator();
            while (listIterator.hasNext()) {
                InstructionPattern instructionPattern = listIterator.next();
                if (instructionPattern.operands.contains("16")) {
                    listIterator.remove();
                }
            }
        }
        for (int i = 0; i <= 0xFF; ++i) {
            if (i == 0x0F) continue;
            List<InstructionPattern> list = lists.get(i);
            if (list.size() != 16) continue;
            ListIterator<InstructionPattern> listIterator = list.listIterator();
            while (listIterator.hasNext()) {
                InstructionPattern instructionPattern = listIterator.next();
                if (instructionPattern.operands.contains("16")) {
                    listIterator.remove();
                }
            }
        }
        for (int i = 0; i <= 0xFF; ++i) {
            if (i == 0x0F || i == 0x80 || i == 0x83 || i == 0x81) continue;
            List<InstructionPattern> list = lists.get(i);
            if (list.size() != 8) continue;
            list.remove(0);
        }
        for (int i = 0; i <= 0xFF; ++i) {
            if (i == 0x0F) continue;
            List<InstructionPattern> list = lists.get(i);
            if (list.size() != 18) continue;
            ListIterator<InstructionPattern> listIterator = list.listIterator();
            while (listIterator.hasNext()) {
                InstructionPattern instructionPattern = listIterator.next();
                if (instructionPattern.operands.contains("16")) {
                    listIterator.remove();
                }
            }
        }
        for (int i = 0; i <= 0xFF; ++i) {
            if (i == 0x0F) continue;
            List<InstructionPattern> list = lists.get(i);
            if (list.size() != 28) continue;
            list.get(2).operands = "r/m32";
        }
        for (int i = 0; i <= 0xFF; ++i) {
            if (i == 0x0F) continue;
            List<InstructionPattern> list = lists.get(i);
            if (list.size() != 28) continue;
            ListIterator<InstructionPattern> listIterator = list.listIterator();
            int count1 = 0;
            int count2 = 0;
            while (listIterator.hasNext()) {
                InstructionPattern instructionPattern = listIterator.next();
                if (instructionPattern.opcode.contains("/3")) {
                    if (count1 < 9)
                        listIterator.remove();
                    ++count1;
                }
                if (instructionPattern.opcode.contains("/5")) {
                    if (count2 < 7)
                        listIterator.remove();
                    ++count2;
                }
            }
        }
        for (int i = 0; i <= 0xFF; ++i) {
            if (i == 0x0F || i == 0xF3) continue;
            List<InstructionPattern> list = lists.get(i);
            if (list.size() != 12) continue;
            list.remove(0);
            list.remove(2);
            list.remove(4);
            list.remove(7);
        }
        for (int i = 0; i <= 0xFF; ++i) {
            if (i != 0xFF) continue;
            List<InstructionPattern> list = lists.get(i);
            if (list.size() != 8) continue;
            list.remove(6);
        }
        for (int i = 0; i <= 0xFF; ++i) {
            List<InstructionPattern> list = lists.get(i);

            if (list.size() > 0) {
                InstructionPattern instructionPattern = list.get(0);
                if (i == 0x68) {
                    instructionPattern.opcode = instructionPattern.opcode + "id";
                } else if (i == 0x6A) {
                    instructionPattern.opcode = instructionPattern.opcode + "ib";
                } else if (i == 0xB0) {
                    instructionPattern.opcode = instructionPattern.opcode + "ib";
                } else if (i == 0xB8) {
                    instructionPattern.opcode = instructionPattern.opcode + "id";
                } else if (i == 0xC6) {
                    instructionPattern.opcode = instructionPattern.opcode + "/rib";
                } else if (i == 0xC7) {
                    instructionPattern.opcode = instructionPattern.opcode + "/rid";
                } else if (i == 0xFF) {
                    instructionPattern.operands = "r/" + instructionPattern.operands;
                } else if (i == 0x50) {
                    instructionPattern.opcode = "50+rd";
                }
            }
        }
        for(int i = 0x41; i <= 0x47; ++i) {
            lists.set(i, lists.get(0x40));
        }
        for(int i = 0x49; i <= 0x4F; ++i) {
            lists.set(i, lists.get(0x48));
        }
        for(int i = 0x51; i <= 0x57; ++i) {
            lists.set(i, lists.get(0x50));
        }
        for(int i = 0x59; i <= 0x5F; ++i) {
            lists.set(i, lists.get(0x58));
        }
        for(int i = 0xB1; i <= 0xB7; ++i) {
            lists.set(i, lists.get(0xB0));
        }
        for(int i = 0xB9; i <= 0xBF; ++i) {
            lists.set(i, lists.get(0xB8));
        }


        // Each two-byte instruction lands in own list
        List<List<InstructionPattern>> twoBytes = new ArrayList<>();
        for (int i = 0; i <= 0xFF; ++i) {
            twoBytes.add(new ArrayList<>());
            List<InstructionPattern> twoByteCodes = lists.get(0x0F);
            for (int j = 0; j < twoByteCodes.size(); ++j) {
                InstructionPattern instructionPattern = twoByteCodes.get(j);
                String StringPriOpcode = instructionPattern.opcode.substring(2, 4);
                HexBinaryAdapter hexBinaryAdapter = new HexBinaryAdapter();
                byte priOpcode = hexBinaryAdapter.unmarshal(StringPriOpcode)[0];

                if (priOpcode == (byte) i) {
                    instructionPattern.priOpcode = priOpcode;
                    twoBytes.get(i).add(instructionPattern);
                }
            }
        }
        lists.get(0x0F).clear();

        for(int i = 0; i <= 0xFF; ++i) {
            List<InstructionPattern> list = twoBytes.get(i);
            for(int j = 0; j < list.size(); ++j) {
                InstructionPattern instructionPattern = list.get(j);
                if(instructionPattern.opcode.contains("cw/cd")) {
                    instructionPattern.opcode = instructionPattern.opcode.substring(0, 4) + "cd";
                    instructionPattern.operands = "rel32";
                }
            }

        }
        for(int i = 0; i <= 0xFF; ++i) {
            List<InstructionPattern> list = twoBytes.get(i);
            if (list.size() != 2) continue;
            ListIterator<InstructionPattern> listIterator = list.listIterator();
            while(listIterator.hasNext()) {
                InstructionPattern instructionPattern = listIterator.next();
                if (instructionPattern.operands.contains("16") || instructionPattern.operands.contains("32")) {
                    if(!instructionPattern.operands.contains("32")) {
                        listIterator.remove();
                    }
                }
            }
        }
        for(int i = 0; i <= 0xFF; ++i) {
            List<InstructionPattern> list = twoBytes.get(i);
            if (list.size() != 2) continue;
            for(int j = 0; j < list.size(); ++j) {
                list.remove(1);
            }
        }
        for(int i = 0; i <= 0xFF; ++i) {
            List<InstructionPattern> list = twoBytes.get(i);
            if (list.size() != 3) continue;
            for(int j = 0; j < list.size(); ++j) {
                list.remove(1);
                list.remove(1);
            }
        }
        for(int i = 0; i <= 0xFF; ++i) {
            List<InstructionPattern> list = twoBytes.get(i);
            if (list.size() != 4) continue;
            for(int j = 0; j < list.size(); ++j) {
                list.remove(0);
                list.remove(0);
                list.remove(0);
            }
        }
        for(int i = 0; i <= 0xFF; ++i) {
            List<InstructionPattern> list = twoBytes.get(i);
            if (list.size() != 8) continue;
            ListIterator<InstructionPattern> listIterator = list.listIterator();
            while(listIterator.hasNext()) {
                InstructionPattern instructionPattern = listIterator.next();
                if (instructionPattern.operands.contains("16")) {
                    listIterator.remove();
                }
            }
        }
        lists.get(0xA0).get(0).opcode = lists.get(0xA0).get(0).opcode + "mb";
        lists.get(0xA1).get(0).opcode = lists.get(0xA1).get(0).opcode + "md";
        lists.get(0xA2).get(0).opcode = lists.get(0xA2).get(0).opcode + "mb";
        lists.get(0xA3).get(0).opcode = lists.get(0xA3).get(0).opcode + "md";
        lists.get(0x8D).get(0).operands = "r32,r/m32";
        lists.get(0xAA).get(0).operands = "ES:[EDI],AL";
        lists.get(0xAB).get(0).operands = "ES:[EDI],EAX";
        lists.get(0x8F).get(0).operands = "r/m32";

        twoBytes.get(0x01).get(0).operands = "m16&32";
        twoBytes.get(0x01).get(1).operands = "m16&32";
        for(int i = 0x90; i <= 0x9F; ++i) {
            twoBytes.get(i).get(0).opcode = twoBytes.get(i).get(0).opcode + "/r";
        }
        for(int i = 0xBB; i <= 0xBD; ++i) {
            twoBytes.get(i).get(0).opcode = twoBytes.get(i).get(0).opcode + "/r";
        }
        twoBytes.get(0xA3).get(0).opcode = twoBytes.get(0xA3).get(0).opcode + "/r";
        twoBytes.get(0xA4).get(0).opcode = twoBytes.get(0xA4).get(0).opcode + "/rib";
        twoBytes.get(0xA5).get(0).opcode = twoBytes.get(0xA5).get(0).opcode + "/r";
        twoBytes.get(0xAB).get(0).opcode = twoBytes.get(0xAB).get(0).opcode + "/r";
        twoBytes.get(0xAC).get(0).opcode = twoBytes.get(0xAC).get(0).opcode + "/rib";
        twoBytes.get(0xAD).get(0).opcode = twoBytes.get(0xAD).get(0).opcode + "/r";
        twoBytes.get(0xB3).get(0).opcode = twoBytes.get(0xB3).get(0).opcode + "/r";

        for (List<InstructionPattern> list : lists) {
            for (InstructionPattern i : list) {
                i.length = i.calculateLengthBasedOnOpcode();
            }
        }
        for (List<InstructionPattern> list : twoBytes) {
            for (InstructionPattern i : list) {
                i.length = i.calculateLengthBasedOnOpcode();
            }
        }

        lists.get(0xDB).add(new InstructionPattern((byte)0xDB, "DBE2", "FNCLEX", "", 2));

        lists.get(0xD8).add(new InstructionPattern((byte)0xD8, "D8/0", "FADD", "m32fp", 2));
        lists.get(0xD8).add(new InstructionPattern((byte)0xD8, "D8/1", "FMUL", "m32fp", 2));
        lists.get(0xD8).add(new InstructionPattern((byte)0xD8, "D8/2", "FCOM", "m32fp", 2));
        lists.get(0xD8).add(new InstructionPattern((byte)0xD8, "D8/3", "FCOMP", "m32fp", 2));
        lists.get(0xD8).add(new InstructionPattern((byte)0xD8, "D8/4", "FSUB", "m32fp", 2));
        lists.get(0xD8).add(new InstructionPattern((byte)0xD8, "D8/5", "FSUBR", "m32fp", 2));
        lists.get(0xD8).add(new InstructionPattern((byte)0xD8, "D8/6", "FDIV", "m32fp", 2));
        lists.get(0xD8).add(new InstructionPattern((byte)0xD8, "D8/7", "FDIVR", "m32fp", 2));

        lists.get(0xD9).add(new InstructionPattern((byte)0xD9, "D9/0", "FLD", "m32fp", 2));
        lists.get(0xD9).add(new InstructionPattern((byte)0xD9, "D9/2", "FST", "m32fp", 2));
        lists.get(0xD9).add(new InstructionPattern((byte)0xD9, "D9/3", "FSTP", "m32fp", 2));
        lists.get(0xD9).add(new InstructionPattern((byte)0xD9, "D9/4", "FLDENV", "m14/28byte", 2));
        lists.get(0xD9).add(new InstructionPattern((byte)0xD9, "D9/5", "FLDCW", "m2byte", 2));
        lists.get(0xD9).add(new InstructionPattern((byte)0xD9, "D9/6", "FNSTENV", "m14/28byte", 2));
        lists.get(0xD9).add(new InstructionPattern((byte)0xD9, "D9/7", "FNSTCW", "m2byte", 2));

        lists.get(0xDE).add(new InstructionPattern((byte)0xDE, "DE/0", "FIADD", "m16int", 2));
        lists.get(0xDE).add(new InstructionPattern((byte)0xDE, "DE/1", "FIMUL", "m16int", 2));
        lists.get(0xDE).add(new InstructionPattern((byte)0xDE, "DE/2", "FICOM", "m16int", 2));
        lists.get(0xDE).add(new InstructionPattern((byte)0xDE, "DE/3", "FICOMP", "m16int", 2));
        lists.get(0xDE).add(new InstructionPattern((byte)0xDE, "DE/4", "FISUB", "m16int", 2));
        lists.get(0xDE).add(new InstructionPattern((byte)0xDE, "DE/5", "FISUBR", "m16int", 2));
        lists.get(0xDE).add(new InstructionPattern((byte)0xDE, "DE/6", "FIDIV", "m16int", 2));
        lists.get(0xDE).add(new InstructionPattern((byte)0xDE, "DE/7", "FIDIVR", "m16int", 2));

        lists.get(0xDD).add(new InstructionPattern((byte)0xDD, "DD/0", "FLD", "m64fp", 2));
        lists.get(0xDD).add(new InstructionPattern((byte)0xDD, "DD/1", "FISTTP", "m64int", 2));
        lists.get(0xDD).add(new InstructionPattern((byte)0xDD, "DD/2", "FST", "m64fp", 2));
        lists.get(0xDD).add(new InstructionPattern((byte)0xDD, "DD/3", "FSTP", "m64fp", 2));
        lists.get(0xDD).add(new InstructionPattern((byte)0xDD, "DD/4", "FRSTOR", "m94/108byte", 2));
        lists.get(0xDD).add(new InstructionPattern((byte)0xDD, "DD/6", "FNSAVE", "m94/108byte", 2));
        lists.get(0xDD).add(new InstructionPattern((byte)0xDD, "DD/7", "FNSTSW", "m2byte", 2));

        twoBytes.get(0x1B).add(new InstructionPattern((byte)0x1B, "0F1B/r", "BNDSTX", "mib,bnd", 3));
        twoBytes.get(0x1A).add(new InstructionPattern((byte)0x1A, "0F1A/r", "BNDLDX", "bnd,mib", 3));
        twoBytes.get(0xB0).add(new InstructionPattern((byte)0xB0, "0FB0/r", "CMPXCHG", "r/m8,r8", 3));
        twoBytes.get(0xB1).add(new InstructionPattern((byte)0xB1, "0FB1/r", "CMPXCHG", "r/m32,r32", 3));
        twoBytes.get(0xA2).add(new InstructionPattern((byte)0xA2, "0FA2", "CPUID", "", 2));
        twoBytes.get(0x01).add(0, new InstructionPattern((byte)0x01, "0F01D1", "XSETBV", "", 3));
        twoBytes.get(0x01).add(0, new InstructionPattern((byte)0x01, "0F01D0", "XGETBV", "", 3));

        twoBytes.get(0x57).add(new InstructionPattern((byte)0x57, "0F57/r", "XORPS", "xmm1,xmm2/m128", 3));
        twoBytes.get(0x13).add(new InstructionPattern((byte)0x13, "0F13/r", "MOVLPD", "m64,xmm1", 3));
        twoBytes.get(0x10).add(new InstructionPattern((byte)0x10, "0F10/r", "MOVUPS", "xmm1,xmm2/m128", 3));
        twoBytes.get(0x11).add(new InstructionPattern((byte)0x11, "0F11/r", "MOVUPS", "xmm2/m128,xmm1", 3));

        twoBytes.get(0x42).add(new InstructionPattern((byte)0x42, "0F42/r", "CMOVB", "r32,r/m32", 3));
        twoBytes.get(0x43).add(new InstructionPattern((byte)0x43, "0F43/r", "CMOVAE", "r32,r/m32", 3));
        twoBytes.get(0x44).add(new InstructionPattern((byte)0x44, "0F44/r", "CMOVE", "r32,r/m32", 3));
        twoBytes.get(0x45).add(new InstructionPattern((byte)0x45, "0F45/r", "CMOVNE", "r32,r/m32", 3));
        twoBytes.get(0x46).add(new InstructionPattern((byte)0x46, "0F46/r", "CMOVBE", "r32,r/m32", 3));
        twoBytes.get(0x47).add(new InstructionPattern((byte)0x47, "0F47/r", "CMOVA", "r32,r/m32", 3));
        twoBytes.get(0x48).add(new InstructionPattern((byte)0x48, "0F48/r", "CMOVS", "r32,r/m32", 3));
        twoBytes.get(0x4C).add(new InstructionPattern((byte)0x4C, "0F4C/r", "CMOVL", "r32,r/m32", 3));
        twoBytes.get(0x4D).add(new InstructionPattern((byte)0x4D, "0F4D/r", "CMOVGE", "r32,r/m32", 3));
        twoBytes.get(0x4E).add(new InstructionPattern((byte)0x4E, "0F4E/r", "CMOVLE", "r32,r/m32", 3));
        twoBytes.get(0x4F).add(new InstructionPattern((byte)0x4F, "0F4F/r", "CMOVG", "r32,r/m32", 3));

        twoBytes.get(0x6E).add(new InstructionPattern((byte)0x6E, "0F6E/r", "MOVD", "mm,r/m32", 3));
        twoBytes.get(0x7E).add(new InstructionPattern((byte)0x7E, "0F7E/r", "MOVD", "r/m32, mm", 3));

        twoBytes.get(0xC0).add(new InstructionPattern((byte)0xC0, "0FC0/r", "XADD", "r/m8,r8", 3));
        twoBytes.get(0xC1).add(new InstructionPattern((byte)0xC1, "0FC1/r", "XADD", "r/m32,r32", 3));

        twoBytes.get(0x5B).add(new InstructionPattern((byte)0x5B, "0F5B/r", "CVTDQ2PS", "xmm1,xmm2/m128", 3));
        twoBytes.get(0x2C).add(new InstructionPattern((byte)0x2C, "0F2C/r", "CVTTSS2SI", "r32,xmm1/m32", 3));
        twoBytes.get(0x5A).add(new InstructionPattern((byte)0x5A, "0F5A/r", "CVTPS2PD", "xmm1,xmm2/m64", 3));
        twoBytes.get(0xE6).add(new InstructionPattern((byte)0xE6, "0FE6/r", "CVTDQ2PD", "xmm1,xmm2/m64", 3));
        twoBytes.get(0x2E).add(new InstructionPattern((byte)0x2E, "0F2E/r", "UCOMISD", "xmm1,xmm2/m64", 3));
        twoBytes.get(0x28).add(new InstructionPattern((byte)0x28, "0F28/r", "MOVAPS", "xmm1,xmm2/m128", 3));
        twoBytes.get(0x29).add(new InstructionPattern((byte)0x29, "0F29/r", "MOVAPS", "xmm2/m128,xmm1", 3));

        twoBytes.get(0x5C).add(new InstructionPattern((byte)0x5C, "0F5C/r", "SUBSS", "xmm1,xmm2/m32", 3));
        twoBytes.get(0x2F).add(new InstructionPattern((byte)0x2F, "0F2F/r", "COMISS", "xmm1,xmm2/m32", 3));
        twoBytes.get(0x59).add(new InstructionPattern((byte)0x59, "0F59/r", "MULSS", "xmm1,xmm2/m32", 3));
        twoBytes.get(0x58).add(new InstructionPattern((byte)0x58, "0F58/r", "ADDPS", "xmm1,xmm2/m128", 3));
        twoBytes.get(0x5E).add(new InstructionPattern((byte)0x5E, "0F5E/r", "DIVPS", "xmm1,xmm2/m128", 3));
        twoBytes.get(0x54).add(new InstructionPattern((byte)0x54, "0F54/r", "ANDPS", "xmm1,xmm2/m128", 3));
        twoBytes.get(0x5F).add(new InstructionPattern((byte)0x5F, "0F5F/r", "MAXSS", "xmm1,xmm2/m32", 3));

        twoBytes.get(0x1F).add(new InstructionPattern((byte)0x1F, "0F1F/0", "NOP", "r/m32", 3));

        this.oneByteInstructions = lists;
        this.twoByteInstructions = twoBytes;

        String coder32path = "coder32.html";
        InstructionParser instructionParser = new InstructionParser(coder32path);
        List<InstructionParser.Instruction> instructions = instructionParser.getInstructions();

        List<InstructionPattern> ob = new ArrayList<>();
        List<InstructionPattern> tb = new ArrayList<>();
        for (InstructionParser.Instruction instruction : instructions) {
            if (instruction.getPrimaryOpcode().length() < 2) continue;

            HexBinaryAdapter hexBinaryAdapter = new HexBinaryAdapter();
            byte pri = hexBinaryAdapter.unmarshal(instruction.getPrimaryOpcode().substring(0, 2))[0];

            if (instruction.getPrefix0F().length() == 0) {
                if (oneByteInstructions.get(pri & 0xFF).size() == 0) {
                    String opcode = instruction.getPrefix() + instruction.getPrimaryOpcode() + instruction.getSecondaryOpcode();
                    if (instruction.getRegisterOpcodeField().length() > 0) {
                        opcode = opcode + "/" + instruction.getRegisterOpcodeField();
                    }

                    String operands = "";
                    if (instruction.getOperand1().length() > 0) {
                        operands = operands + instruction.getOperand1();
                    }
                    if (instruction.getOperand2().length() > 0) {
                        operands = operands + "," + instruction.getOperand2();
                    }
                    if (instruction.getOperand3().length() > 0) {
                        operands = operands + "," + instruction.getOperand3();
                    }
                    if (instruction.getOperand4().length() > 0) {
                        operands = operands + "," + instruction.getOperand4();
                    }

                    if (operands.contains("imm8")) opcode = opcode + "ib";
                    if (operands.contains("imm16/32")) opcode = opcode + "id";
                    else if (operands.contains("imm16")) opcode = opcode + "iw";
                    if (operands.contains("imm32")) opcode = opcode + "id";

                    if (operands.contains("rel8")) opcode = opcode + "cd";
                    if (operands.contains("rel16/32")) opcode = opcode + "cd";
                    else if (operands.contains("rel16")) opcode = opcode + "cw";
                    if (operands.contains("rel32")) opcode = opcode + "cd";

                    if (operands.contains("ptr16:16")) opcode = opcode + "cd";
                    if (operands.contains("ptr16:32")) opcode = opcode + "cp";
                    if (operands.contains("ptr32:32")) opcode = opcode + "cp";

                    if (operands.contains("moffs8")) opcode = opcode + "mb";
                    if (operands.contains("moffs32")) opcode = opcode + "md";

                    InstructionPattern instructionPattern = new InstructionPattern(pri, opcode, instruction.getMnemonic(), operands, -1);
                    instructionPattern.length = instructionPattern.calculateLengthBasedOnOpcode();
                    ob.add(instructionPattern);
                }
            } else {
                if (twoByteInstructions.get(pri & 0xFF).size() == 0) {
                    if (instruction.getPrimaryOpcode().contains("+")) {
                        for (int i = pri & 0xFF; i <= (pri & 0xFF) + 7; ++i) {
                            String opcode = instruction.getPrefix0F() + instruction.getPrimaryOpcode();

                            String operands = "";
                            if (instruction.getOperand1().length() > 0) {
                                operands = operands + instruction.getOperand1();
                            }
                            if (instruction.getOperand2().length() > 0) {
                                operands = operands + "," + instruction.getOperand2();
                            }
                            if (instruction.getOperand3().length() > 0) {
                                operands = operands + "," + instruction.getOperand3();
                            }
                            if (instruction.getOperand4().length() > 0) {
                                operands = operands + "," + instruction.getOperand4();
                            }

                            InstructionPattern instructionPattern = new InstructionPattern((byte) i, opcode, instruction.getMnemonic(), operands, -1);
                            instructionPattern.length = instructionPattern.calculateLengthBasedOnOpcode();
                            tb.add(instructionPattern);
                        }
                    } else {
                        String opcode = instruction.getPrefix() + instruction.getPrefix0F() + instruction.getPrimaryOpcode() + instruction.getSecondaryOpcode();
                        if (instruction.getRegisterOpcodeField().length() > 0) {
                            opcode = opcode + "/" + instruction.getRegisterOpcodeField();
                        }

                        String operands = "";
                        if (instruction.getOperand1().length() > 0) {
                            operands = operands + instruction.getOperand1();
                        }
                        if (instruction.getOperand2().length() > 0) {
                            operands = operands + "," + instruction.getOperand2();
                        }
                        if (instruction.getOperand3().length() > 0) {
                            operands = operands + "," + instruction.getOperand3();
                        }
                        if (instruction.getOperand4().length() > 0) {
                            operands = operands + "," + instruction.getOperand4();
                        }

                        if (operands.contains("imm8")) opcode = opcode + "ib";
                        if (operands.contains("imm16/32")) opcode = opcode + "id";
                        else if (operands.contains("imm16")) opcode = opcode + "iw";
                        if (operands.contains("imm32")) opcode = opcode + "id";

                        if (operands.contains("rel8")) opcode = opcode + "cd";
                        if (operands.contains("rel16/32")) opcode = opcode + "cd";
                        else if (operands.contains("rel16")) opcode = opcode + "cw";
                        if (operands.contains("rel32")) opcode = opcode + "cd";

                        if (operands.contains("ptr16:16")) opcode = opcode + "cd";
                        if (operands.contains("ptr16:32")) opcode = opcode + "cp";
                        if (operands.contains("ptr32:32")) opcode = opcode + "cp";

                        if (operands.contains("moffs8")) opcode = opcode + "mb";
                        if (operands.contains("moffs32")) opcode = opcode + "md";

                        int length = 2;
                        if (instruction.getPrefix().length() > 0) length += 1 ;
                        if (instruction.getSecondaryOpcode().length() > 0) length += 1;
                        if (opcode.contains("/")) length += 1;
                        if (opcode.contains("ib")) length += 1;
                        if (opcode.contains("iw")) length += 2;
                        if (opcode.contains("id")) length += 4;
                        if (opcode.contains("cb")) length += 1;
                        if (opcode.contains("cw")) length += 2;
                        if (opcode.contains("cd")) length += 4;
                        if (opcode.contains("cp")) length += 6;
                        if (opcode.contains("mb")) length += 1;
                        if (opcode.contains("md")) length += 4;

                        InstructionPattern instructionPattern = new InstructionPattern(pri, opcode, instruction.getMnemonic(), operands, length);
                        tb.add(instructionPattern);
                    }
                }
            }
        }

        for (InstructionPattern i : ob) {
            oneByteInstructions.get(i.priOpcode & 0xFF).add(i);
        }
        for (InstructionPattern i : tb) {
            twoByteInstructions.get(i.priOpcode & 0xFF).add(i);
        }

        for (int i = 0x00; i <= 0xFF; ++i) {
            if (oneByteInstructions.get(i).size() > 0) {
                for (InstructionPattern instructionPattern : oneByteInstructions.get(i)) {
                    if (instructionPattern.getOperands().contains("moffs8")) {
                        instructionPattern.opcode = instructionPattern.opcode.substring(0, 2) + "md";
                        instructionPattern.length = 5;

                        if (instructionPattern.priOpcode == (byte) 0xA0) {
                            instructionPattern.operands = "AL,moffs32";
                        } else if (instructionPattern.priOpcode == (byte) 0xA2) {
                            instructionPattern.operands = "moffs32,AL";
                        }
                    }
                }
            }
        }

        for (int i = 0x00; i <= 0xFF; ++i) {
            if (twoByteInstructions.get(i).size() == 0) continue;
            InstructionPattern instr = twoByteInstructions.get(i).get(0);
            if (!instr.getMnemo().contains("BSWAP")) continue;

            instr.opcode = instr.opcode + "d";
        }
    }

    @Getter
    @AllArgsConstructor
    public class InstructionPattern {
        private byte priOpcode;
        private String opcode;
        private String mnemo;
        private String operands;
        private int length;

        private InstructionPattern(List<String> attributes) {
            length = -1;
            operands = "";

            if (attributes.get(0).contains("+")) {
                String a1 = attributes.get(0);

                String hex = a1.substring(0, 2);
                String rest = a1.substring(2);

                attributes.add(1, rest);

                attributes.set(0, hex);
            }

            HexBinaryAdapter hexBinaryAdapter = new HexBinaryAdapter();
            priOpcode = hexBinaryAdapter.unmarshal(attributes.get(0))[0];

            int mnemoIndex = -1;
            for (int i = 0; i < attributes.size(); ++i) {
                String s = attributes.get(i);
                if (s.matches("\\b([A-Z]+)\\b(?<!\\b((SS)|(DS)|(ES)|(AL)|(EAX)|(AX)|(CS)|(FS)|(GS)|(CR)|(DR)|(TR)|(CL)|(DX)|([A-F]{2,2}))\\b)")) {
                    mnemoIndex = i;
                }
            }

            opcode = "";
            if (mnemoIndex > 1) {
                for (int i = 0; i < mnemoIndex; ++i) {
                    opcode += attributes.get(i);
                }
            } else {
                opcode = attributes.get(0);
            }

            mnemo = attributes.get(mnemoIndex);

            if (mnemoIndex + 1 < attributes.size()) {
                operands = attributes.get(mnemoIndex + 1);
            }
        }

        @Override
        public String toString() {
            StringBuilder output = new StringBuilder();

            output.append("Primary opcode: ").append(String.format("0x%02X", priOpcode)).append("\n");
            output.append("Full opcode:    ").append(opcode).append("\n");
            output.append("Mnemonics:      ").append(mnemo).append("\n");
            output.append("Operands:       ").append(operands).append("\n");
            output.append("Length:         ").append(length).append("\n");

            return output.toString();
        }

        private int calculateLengthBasedOnOpcode() {
            int l = 1;

            if (opcode.substring(0, 2).contains("0F")) l += 1;

            if (priOpcode == (byte) 0xF2 || priOpcode == (byte) 0xF3) l += 1;

            if (opcode.contains("/r")) l += 1;

            if (opcode.contains("ib")) l += 1;
            if (opcode.contains("iw")) l += 2;
            if (opcode.contains("id")) l += 4;

            if (opcode.contains("cb")) l += 1;
            if (opcode.contains("cw")) l += 2;
            if (opcode.contains("cd")) l += 4;
            if (opcode.contains("cp")) l += 6;

            if (opcode.contains("mb")) l += 1;
            if (opcode.contains("md")) l += 4;

            if (opcode.matches("[ -~]*[/][0-9][ -~]*")) l += 1;

            return l;
        }

        private int calculateLengthBasedOnOperands() {
            int l = 1;

            if (opcode.substring(0, 2).contains("0F")) l += 1;

            if (priOpcode == (byte) 0xF2 || priOpcode == (byte) 0xF3) l += 1;

            if (operands.contains("TR") || operands.contains("DR") || operands.contains("CR")) l += 1;
            if (operands.contains("imm8")) l += 1;
            if (operands.contains("imm16")) l += 2;
            if (operands.contains("imm32")) l += 4;

            if (operands.contains("r/m") && !operands.contains("DX")) l += 1;

            if (operands.contains("m16:16")) l += 1;
            if (operands.contains("m16:32")) l += 1;
            if (operands.contains("m16&16")) l += 1;
            if (operands.contains("m16&32")) l += 1;
            if (operands.contains("m32&32")) l += 1;

            if (priOpcode == (byte) 0x8F || priOpcode == (byte) 0xFF) {
                if (operands.matches("[m][0-9]{1,2}")) l += 1;
            }

            if (operands.contains("rel8")) l += 1;
            if (operands.contains("rel16")) l += 2;
            if (operands.contains("rel32")) l += 4;

            if (operands.contains("ptr16:16")) l += 4;
            if (operands.contains("ptr16:32")) l += 6;
            if (operands.contains("ptr32:32")) l += 6;

            if (operands.matches("\\br32,m\\b")) l += 1;

            if (operands.contains("moffs8")) l += 1;
            if (operands.contains("moffs32")) l += 4;

            return l;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof InstructionPattern)) return false;
            InstructionPattern instructionPattern = (InstructionPattern) obj;

            boolean isInstanceOf = true;

            if (priOpcode != instructionPattern.priOpcode) isInstanceOf = false;
            if (!opcode.equals(instructionPattern.opcode)) isInstanceOf = false;
            if (!operands.equals(instructionPattern.operands)) isInstanceOf = false;
            if (length != instructionPattern.length) isInstanceOf = false;

            return isInstanceOf;
        }
    }
}
