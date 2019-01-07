package pl.wat.edu.wcy.jfk.disassemblerserver.disassembler;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class PEFile {
    private static final Logger logger = LoggerFactory.getLogger(PEFile.class);

    private List<Byte> exe;

    private DosHeader dosHeader;
    private Code code;

    private List<List<Byte>> machineCode;
    private List<Code.SectionTable> codeTables;
    private int imageBase;
    private int addressOfEntryPoint;

    private boolean isPE;

    public PEFile(byte[] machineCode) {
        code = new Code(machineCode);
        codeTables = code.codeTables;
    }

    public PEFile(InputStream inputStream) {
        machineCode = new ArrayList<>();
        exe = new ArrayList<>();

        isPE = true;

        try {
            BufferedInputStream input = new BufferedInputStream(inputStream);

            boolean eof = false;
            while (true) {
                byte[] tmp = new byte[1];
                int in = input.read(tmp, 0, 1);
                if (in == -1) eof = true;
                if (eof) break;

                exe.add(tmp[0]);
            }
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        dosHeader = new DosHeader();
        code = new Code();

        codeTables = code.codeTables;
    }

    @Getter
    public class DosHeader {
        private char[] signature;
        private short lastsize;
        private short nblocks;
        private short nreloc;
        private short hdrsize;
        private short minalloc;
        private short maxalloc;
        private short ss;
        private short sp;
        private short checksum;
        private short ip;
        private short cs;
        private short relocpos;
        private short noverlay;
        private short[] reserved1;
        private short oem_id;
        private short oem_info;
        private short[] reserved2;
        private int e_lfanew;

        private int pos;

        private DosHeader() {
            this.signature = new char[2];
            this.reserved1 = new short[4];
            this.reserved2 = new short[10];
            this.pos = 0;

            signature[0] = getChar();
            signature[1] = getChar();
            lastsize = getShort();
            nblocks = getShort();
            nreloc = getShort();
            hdrsize = getShort();
            minalloc = getShort();
            maxalloc = getShort();
            ss = getShort();
            sp = getShort();
            checksum = getShort();
            ip = getShort();
            cs = getShort();
            relocpos = getShort();
            noverlay = getShort();
            for (int i = 0; i < reserved1.length; ++i) {
                reserved1[i] = getShort();
            }
            oem_id = getShort();
            oem_info = getShort();
            for (int i = 0; i < reserved2.length; ++i) {
                reserved2[i] = getShort();
            }
            e_lfanew = getInt();
        }

        private char getChar() {
            return (char) exe.get(pos++).byteValue();
        }

        private short getShort() {
            ByteBuffer byteBuffer = ByteBuffer.allocate(2);
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            byteBuffer.put(exe.get(pos));
            byteBuffer.put(exe.get(pos + 1));
            pos += 2;

            return byteBuffer.getShort(0);
        }

        private int getInt() {
            ByteBuffer byteBuffer = ByteBuffer.allocate(4);
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            byteBuffer.put(exe.get(pos));
            byteBuffer.put(exe.get(pos + 1));
            byteBuffer.put(exe.get(pos + 2));
            byteBuffer.put(exe.get(pos + 3));

            pos += 4;

            return byteBuffer.getInt(0);
        }

        @Override
        public String toString() {
            StringBuilder output = new StringBuilder();
            output.append("DOS_HEADER").append("\n");
            output.append("signature: ").append(signature).append("\n");
            output.append("lastsize:  ").append(String.format("%04X", lastsize)).append("\n");
            output.append("nblocks:   ").append(String.format("%04X", nblocks)).append("\n");
            output.append("nreloc:    ").append(String.format("%04X", nreloc)).append("\n");
            output.append("hdrsize:   ").append(String.format("%04X", hdrsize)).append("\n");
            output.append("minalloc:  ").append(String.format("%04X", minalloc)).append("\n");
            output.append("*ss:       ").append(String.format("%04X", ss)).append("\n");
            output.append("*sp:       ").append(String.format("%04X", sp)).append("\n");
            output.append("*checksum: ").append(String.format("%04X", checksum)).append("\n");
            output.append("*ip:       ").append(String.format("%04X", ip)).append("\n");
            output.append("*cs:       ").append(String.format("%04X", cs)).append("\n");
            output.append("relocpos:  ").append(String.format("%04X", relocpos)).append("\n");
            output.append("noverlay:  ").append(String.format("%04X", noverlay)).append("\n");

            output.append("reserved1: ");
            for (int i = 0; i < 4; ++i) {
                output.append(String.format("%04X", reserved1[i])).append(" ");
            }
            output.append("\n");

            output.append("oem_id:    ").append(String.format("%04X", oem_id)).append("\n");
            output.append("oem_info:  ").append(String.format("%04X", oem_info)).append("\n");

            output.append("reserved2: ");
            for (int i = 0; i < 10; ++i) {
                output.append(String.format("%04X", reserved2[i])).append(" ");
            }
            output.append("\n");

            output.append("e_lfanew:  ").append(String.format("%08X", e_lfanew)).append("\n");

            return output.toString();
        }
    }

    public class Code {
        private List<Byte> pe;
        private List<SectionTable> codeTables;

        public Code(byte[] machineCode) {
            codeTables = new ArrayList<>();
            codeTables.add(new SectionTable(".text".toCharArray(), machineCode.length, 0, 0, 0, 0, 0, (short) 0, (short) 0, 0, 0));
        }

        public Code() {
            pe = new ArrayList<>();
            codeTables = new ArrayList<>();

            for (int i = dosHeader.e_lfanew; i < exe.size(); ++i) {
                pe.add(exe.get(i));
            }

            if (pe.size() <= 4) {
                isPE = false;
                return;
            }

            ByteBuffer byteBuffer = ByteBuffer.allocate(4);
            byteBuffer.order(ByteOrder.BIG_ENDIAN);
            byteBuffer.put(pe.get(0x00));
            byteBuffer.put(pe.get(0x01));
            byteBuffer.put(pe.get(0x02));
            byteBuffer.put(pe.get(0x03));
            int signature = byteBuffer.getInt(0);

            if (signature != 0x50450000) {
                isPE = false;
                return;
            }

            byteBuffer = ByteBuffer.allocate(2);
            byteBuffer.put(pe.get(0x06));
            byteBuffer.put(pe.get(0x07));
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            short numberOfSections = byteBuffer.getShort(0);

            byteBuffer = ByteBuffer.allocate(2);
            byteBuffer.put(pe.get(0x14));
            byteBuffer.put(pe.get(0x15));
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            short sizeOfOptionalHeader = byteBuffer.getShort(0);

            byteBuffer = ByteBuffer.allocate(4);
            byteBuffer.put(pe.get(0x1C));
            byteBuffer.put(pe.get(0x1D));
            byteBuffer.put(pe.get(0x1E));
            byteBuffer.put(pe.get(0x1F));
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            int sizeOfCode = byteBuffer.getInt(0);

            int sizeOfCoffHeader = 0x18;

            int sectionTablesAdress = sizeOfCoffHeader + sizeOfOptionalHeader;

            int sizeOfSectionTable = 0x28;

            for (int i = sectionTablesAdress; i < sectionTablesAdress + numberOfSections * sizeOfSectionTable; i += sizeOfSectionTable) {
                SectionTable sectionTable = new SectionTable(i);

                // Checking if section contains executable code
                if ((sectionTable.characteristics & 0x00000020) == 0x00000020) {
                    if (sectionTable.sizeOfRawData > 0) {
                        codeTables.add(sectionTable);
                    }
                }
            }

            int size = 0;
            for (SectionTable i : codeTables) {
                size += i.sizeOfRawData;
            }
            if (size != sizeOfCode) {
                logger.warn("Not all code sections were found");
            }

            for (SectionTable table : codeTables) {
                List<Byte> code = new ArrayList<>();

                for(int i = table.pointerToRawData; i < table.pointerToRawData + table.sizeOfRawData; ++i) {
                    code.add(exe.get(i));
                }

                machineCode.add(code);
            }

            byteBuffer = ByteBuffer.allocate(4);
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            byteBuffer.put(pe.get(0x34));
            byteBuffer.put(pe.get(0x35));
            byteBuffer.put(pe.get(0x36));
            byteBuffer.put(pe.get(0x37));
            imageBase = byteBuffer.getInt(0);

            byteBuffer = ByteBuffer.allocate(4);
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            byteBuffer.put(pe.get(0x28));
            byteBuffer.put(pe.get(0x29));
            byteBuffer.put(pe.get(0x2A));
            byteBuffer.put(pe.get(0x2B));
            addressOfEntryPoint = byteBuffer.getInt(0) + imageBase;
        }

        @Getter
        @NoArgsConstructor
        @AllArgsConstructor
        public class SectionTable {
            private char[] name;
            private int misc;
            private int virtualAdress;
            private int sizeOfRawData;
            private int pointerToRawData;
            private int pointerToRelocations;
            private int pointerToLinenumbers;
            private short numberOfRelocations;
            private short numberOfLinenumbers;
            private int characteristics;

            private int pos;

            public SectionTable(int tableAddr) {
                name = new char[8];
                pos = tableAddr;

                for (int i = 0; i < 8; ++i) {
                    name[i] = getChar();
                }

                misc = getInt();
                virtualAdress = getInt();
                sizeOfRawData = getInt();
                pointerToRawData = getInt();
                pointerToRelocations = getInt();
                pointerToLinenumbers = getInt();
                numberOfRelocations = getShort();
                numberOfLinenumbers = getShort();
                characteristics = getInt();
            }

            @Override
            public String toString() {
                StringBuilder output = new StringBuilder();
                output.append("SECTION_TABLE").append("\n");
                output.append("name:                  ").append(name).append("\n");
                output.append("misc:                  ").append(String.format("%08X", misc)).append("\n");
                output.append("virtualAdress:         ").append(String.format("%08X", virtualAdress)).append("\n");
                output.append("sizeOfRawData:         ").append(String.format("%08X", sizeOfRawData)).append("\n");
                output.append("pointerToRawData:      ").append(String.format("%08X", pointerToRawData)).append("\n");
                output.append("pointerToRelocations:  ").append(String.format("%08X", pointerToRelocations)).append("\n");
                output.append("pointerToLinenumbers:  ").append(String.format("%08X", pointerToLinenumbers)).append("\n");
                output.append("numberOfRelocations:   ").append(String.format("%04X", numberOfRelocations)).append("\n");
                output.append("numberOfLinenumbers:   ").append(String.format("%04X", numberOfLinenumbers)).append("\n");
                output.append("characteristics:       ").append(String.format("%08X", characteristics)).append("\n");

                return output.toString();
            }

            private char getChar() {
                return (char) pe.get(pos++).byteValue();
            }

            private short getShort() {
                ByteBuffer byteBuffer = ByteBuffer.allocate(2);
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                byteBuffer.put(pe.get(pos));
                byteBuffer.put(pe.get(pos + 1));
                pos += 2;

                return byteBuffer.getShort(0);
            }

            private int getInt() {
                ByteBuffer byteBuffer = ByteBuffer.allocate(4);
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                byteBuffer.put(pe.get(pos));
                byteBuffer.put(pe.get(pos + 1));
                byteBuffer.put(pe.get(pos + 2));
                byteBuffer.put(pe.get(pos + 3));

                pos += 4;

                return byteBuffer.getInt(0);
            }
        }
    }

    public List<List<Byte>> getMachineCode() {
        return machineCode;
    }

    public List<Code.SectionTable> getCodeTables() {
        return codeTables;
    }

    public int getImageBase() {
        return imageBase;
    }

    public int getAddressOfEntryPoint() {
        return addressOfEntryPoint;
    }

    public boolean isPE() {
        return isPE;
    }
}
