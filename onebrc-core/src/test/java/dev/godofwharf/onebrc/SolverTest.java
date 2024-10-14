package dev.godofwharf.onebrc;

import dev.godofwharf.onebrc.models.AggregationKey;
import dev.godofwharf.onebrc.models.AggregationResult;
import dev.godofwharf.onebrc.models.AggregationState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static dev.godofwharf.onebrc.models.AggregationKey.from;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SolverTest {
    private static final String inputFilePath = "src/test/resources/test_measurements.txt";
    private Solver solver;

    public SolverTest() {
        this.solver = new Solver(Path.of(inputFilePath), 2);
    }

    @Test
    void testSplitFileIntoChunks() throws Exception {
        Solver.MemoryMappedFile file = solver.mmapFile();
        List<Solver.Chunk> chunks = solver.splitFileIntoChunks(file);
        assertNotNull(chunks);
        assertEquals(2, chunks.size());
        assertEquals(Files.size(Paths.get(inputFilePath)),
                chunks.stream().map(Solver.Chunk::length).reduce((a, b) -> a + b).get());
    }

    @Test
    void testSolve() throws Exception {
        solver.solve();
    }

    @Test
    void testParseChunks() throws Exception {
        AggregationState state = new AggregationState();
        LinearProbedMap<AggregationKey, AggregationResult> map = state.getOrCreateState();
        Solver.MemoryMappedFile file = solver.mmapFile();
        solver.parseChunkVectorized(file.fileSegment(), map);
        solver.print(state.merge(), System.out);
    }

    @Test
    void testPrint() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
        solver.print(
                Map.of(
                        from("Nairobi"), new AggregationResult(-103, 207, 6577, 30),
                        from("Aachen"), new AggregationResult(-154, 168, 4389, 25),
                        from("Melbourne"), new AggregationResult(102, 384, 10281, 30)),
                new PrintStream(baos));
        String result = baos.toString(StandardCharsets.UTF_8).replace("\n", "");
        Assertions.assertEquals(
                "{Aachen=-15.4/17.6/16.8,Melbourne=10.2/34.3/38.4,Nairobi=-10.3/21.9/20.7}",
                        result);
    }
}