package dev.godofwharf.onebrc;

import java.nio.file.Paths;

public class CalculateAverage {

    public static void main(String[] args) throws Exception {
        final int cpuCores = Integer.parseInt(
                System.getProperty("cpuCores", "" + Runtime.getRuntime().availableProcessors()));
        final String inputFile = System.getProperty("inputFile", "./measurements.txt");
        Solver solver = new Solver(Paths.get(inputFile), cpuCores);
        solver.solve();
    }
}
