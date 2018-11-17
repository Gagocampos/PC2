package br.ufsc.ine5410.input;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

public class InputBuilder {

    public static void main(String[] args) throws Exception {
        new InputBuilder().run(args);
    }

    private void run(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.printf("Usage: java -cp t2-1.0-SNAPSHOT.jar %s cotacoes output\n",
                    InputBuilder.class.getName());
        }

        Set<String> selected = loadSelected();
        try (PrintStream out = new PrintStream(new FileOutputStream(args[1]))) {
            for (String code : selected)
                writeCodeLine(out, code, args[0]);
        }
    }

    private void writeCodeLine(PrintStream out, String code, String historyFilePath) throws Exception {
        out.printf("%s", code);
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(new FileInputStream(historyFilePath)))) {
            String line = in.readLine(); //header
            while ((line = in.readLine()) != null) {
                String bdi = line.substring(10, 12);
                if (!bdi.equals("02")) continue;
                String actualCode = line.substring(12, 24).trim();
                if (!actualCode.equals(code)) continue;

                double closePrice = Double.parseDouble(line.substring(108, 119))
                        + Double.parseDouble(line.substring(119, 121))/100;
                int negs = Integer.parseInt(line.substring(147, 152));
                out.printf(", %s, %d", Double.toString(closePrice), negs);
            }
        }
        out.println();
    }

    private Set<String> loadSelected() throws IOException {
        String filename = "/selected.csv";
        Set<String> set = new HashSet<>();
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(InputBuilder.class.getResourceAsStream(filename)))) {
            String line = in.readLine(); //header
            while ((line = in.readLine()) != null) {
                set.add(line);
            }
        }
        return set;
    }
}
