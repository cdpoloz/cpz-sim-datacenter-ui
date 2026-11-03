package com.cpz.sim.datacenter.ui.dataset;

import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import com.cpz.sim.datacenter.dataset.*;
import com.cpz.sim.datacenter.example.SyntheticDatasetGenerator;
import com.cpz.sim.datacenter.input.DatacenterDataInputMode;
import com.cpz.sim.datacenter.model.ServerLocation;
import com.cpz.sim.foundation.engine.SimulationEngine;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.cpz.sim.datacenter.ui.dataset.DatasetReplayController.State.*;

class DatasetReplayControllerTest {
    static final Path BACKEND = Path.of(System.getProperty("replay.backend", "../cpz-sim-datacenter"));
    static final Path CONFIG = BACKEND.resolve("data/config/synthetic-dataset-pilot.json");
    static final Path TEMPLATE = BACKEND.resolve("docs/templates/datacenter-customer-dataset-template.xlsx");
    static final Instant START = SyntheticDatasetGenerator.START;
    static final Duration STEP = Duration.ofMinutes(15);
    @TempDir static Path temporary;
    static Path workbook;
    static byte[] workbookBefore, configBefore, templateBefore;
    @BeforeAll static void generate() throws Exception {
        templateBefore = Files.readAllBytes(TEMPLATE); configBefore = Files.readAllBytes(CONFIG);
        workbook = temporary.resolve("new-ui-pilot.xlsx");
        SyntheticDatasetGenerator.generate(TEMPLATE, workbook);
        workbookBefore = Files.readAllBytes(workbook);
    }
    @AfterAll static void inputsAreByteIdentical() throws Exception {
        assertArrayEquals(templateBefore, Files.readAllBytes(TEMPLATE));
        assertArrayEquals(configBefore, Files.readAllBytes(CONFIG));
        assertArrayEquals(workbookBefore, Files.readAllBytes(workbook));
    }
    DatasetReplaySession session(DatacenterDataInputMode mode) {
        return DatasetReplaySession.load(CONFIG, workbook, "synthetic-dataset-pilot", "1",
                new DatasetReplayPlan(mode, START, 3, STEP, STEP));
    }
    @ParameterizedTest @EnumSource(value=DatacenterDataInputMode.class, names={"UTILIZATION_DRIVEN", "POWER_DRIVEN"})
    void realSessionPreparationPlaybackPauseCompletionRestartAndSelection(DatacenterDataInputMode mode) {
        var session = session(mode);
        var c = new DatasetReplayController(() -> session);
        assertEquals(READY, c.state()); assertTrue(c.history().isEmpty()); assertTrue(c.selectedResult().isEmpty());
        assertEquals(DatasetReplaySession.State.PREPARED, session.state());
        assertEquals(Duration.ZERO, session.engineElapsed()); assertEquals(0, session.completedPoints());
        assertEquals(20, c.racks().size());
        for (var rack : c.racks()) { c.selectRack(rack.location()); assertEquals(10, c.servers().size()); }
        c.selectRack(c.racks().getFirst().location());
        c.pulse(10_000_000_000L); assertEquals(0, session.completedPoints());
        c.play(0); c.pulse(999_999_999L); assertTrue(c.history().isEmpty());
        c.pulse(1_000_000_000L); assertEquals(1, session.completedPoints());
        var first = c.lastFrame().orElseThrow();
        c.pause(); c.pulse(20_000_000_000L); assertEquals(1, session.completedPoints());
        var secondServer = c.servers().get(1).location();
        c.selectServer(secondServer);
        assertEquals(secondServer, c.selectedResult().orElseThrow().location());
        assertEquals(first, c.lastFrame().orElseThrow()); assertEquals(1, session.completedPoints());
        assertThrows(IllegalArgumentException.class, () -> c.selectServer(new ServerLocation("C02", "SYN-R01", "U01")));
        c.selectRack(c.racks().get(10).location());
        assertEquals("C02", c.selectedResult().orElseThrow().location().column());
        assertEquals(1, session.completedPoints());
        c.selectRack(c.racks().getFirst().location());
        c.play(20_000_000_000L); c.pulse(21_000_000_000L);
        assertEquals(2, session.completedPoints());
        c.pulse(100_000_000_000L); assertEquals(COMPLETED, c.state());
        c.play(100_000_000_000L); c.pulse(200_000_000_000L); assertEquals(3, session.completedPoints());
        assertEquals(3, c.history().size());
        for (int i=0; i<3; i++) {
            var frame=c.history().get(i); var r=frame.servers().getFirst();
            assertEquals(START.plus(STEP.multipliedBy(i)), frame.replayUtc());
            assertEquals(STEP.multipliedBy(i), frame.replayOffset());
            assertEquals(STEP.multipliedBy(i+1), frame.engineElapsed());
            assertEquals(.2+.2*i, r.original().number("utilizationFraction"), 1e-9);
            assertEquals(150+40*i, r.original().number("electricalPowerWatts"));
            assertEquals(.2+.2*i+(mode == DatacenterDataInputMode.POWER_DRIVEN ? .05 : 0), r.resultUtilizationFraction(), 1e-9);
            assertEquals((mode == DatacenterDataInputMode.POWER_DRIVEN ? 150 : 140)+40*i, r.resultPowerWatts(), 1e-9);
        }
        var sequence=c.history(); c.restart();
        assertEquals(READY,c.state()); assertTrue(c.history().isEmpty()); assertTrue(c.selectedResult().isEmpty());
        c.pulse(300_000_000_000L); assertEquals(0,session.completedPoints());
        c.play(0); for(int i=1;i<=3;i++) c.pulse(i*1_000_000_000L);
        assertEquals(sequence,c.history());
    }
    @Test void speedOnlyChangesCadenceAndLatePulsesNeverCatchUp() {
        var slow=new DatasetReplayController(() -> session(DatacenterDataInputMode.POWER_DRIVEN));
        var fast=new DatasetReplayController(() -> session(DatacenterDataInputMode.POWER_DRIVEN));
        fast.speed(4,0); slow.play(0); fast.play(0);
        fast.pulse(249_999_999); assertTrue(fast.history().isEmpty());
        fast.pulse(250_000_000); slow.pulse(250_000_000); assertTrue(slow.history().isEmpty());
        slow.pulse(1_000_000_000); assertEquals(slow.history(),fast.history());
        fast.speed(.5,250_000_000); fast.pulse(2_249_999_999L); assertEquals(1,fast.history().size());
        fast.pulse(2_250_000_000L); slow.pulse(2_000_000_000L); assertEquals(slow.history(),fast.history());
        fast.pulse(100_000_000_000L); assertEquals(3,fast.history().size()); assertEquals(COMPLETED,fast.state());
        for(double invalid : new double[]{0,9,Double.NaN}) assertThrows(IllegalArgumentException.class,()->fast.speed(invalid,0));
    }
    @Test void restartDuringPlaybackCancelsPendingPulseAndDoesNotReadFiles() throws Exception {
        var config=temporary.resolve("captured.json"); var source=temporary.resolve("captured.xlsx");
        Files.copy(CONFIG,config); Files.copy(workbook,source);
        var c=new DatasetReplayController(()-> DatasetReplaySession.load(config,source,"synthetic-dataset-pilot","1",
                new DatasetReplayPlan(DatacenterDataInputMode.POWER_DRIVEN,START,3,STEP,STEP)));
        c.play(0); c.pulse(1_000_000_000L); var first=c.lastFrame();
        Files.delete(config); Files.delete(source);
        c.restart(); c.pulse(2_000_000_000L);
        assertEquals(READY,c.state()); assertTrue(c.history().isEmpty());
        c.play(2_000_000_000L); c.pulse(3_000_000_000L); assertEquals(first,c.lastFrame());
    }
    @Test void diagnosticsRemainStructuredAndBlockPlay() {
        var configuration=new JsonDatacenterConfigLoader().load(CONFIG);
        var dataset=new XlsxCustomerDatasetLoader().load(workbook);
        var rows=new ArrayList<>(dataset.rows("ServerSamples"));
        var row=rows.getFirst(); var values=new LinkedHashMap<>(row.values()); values.put("quality","SUSPECT");
        rows.set(0,new DatasetRow(row.sheet(),row.excelRow(),values));
        var sheets=new LinkedHashMap<>(dataset.sheets()); sheets.put("ServerSamples",rows);
        var c=new DatasetReplayController(()->DatasetReplaySession.prepare(configuration,new CustomerDataset(dataset.metadata(),sheets),
                "synthetic-dataset-pilot","1",new DatasetReplayPlan(DatacenterDataInputMode.POWER_DRIVEN,START,3,STEP,STEP)));
        assertEquals(INVALID,c.state());
        assertTrue(c.errorLines().stream().anyMatch(s->s.contains("DATASET_DIAGNOSTICS")));
        assertTrue(c.errorLines().stream().anyMatch(s->s.contains("WARNING | ServerSamples | row 7 | quality |")));
        c.play(0); c.pulse(1_000_000_000L); assertTrue(c.history().isEmpty()); assertEquals(INVALID,c.state());
    }
    @Test void missingOptionalObservationIsNotReplacedByRuntimeValue() {
        var configuration=new JsonDatacenterConfigLoader().load(CONFIG);
        var dataset=new XlsxCustomerDatasetLoader().load(workbook);
        var rows=new ArrayList<>(dataset.rows("ServerSamples"));
        var row=rows.getFirst(); var values=new LinkedHashMap<>(row.values()); values.remove("utilizationFraction");
        rows.set(0,new DatasetRow(row.sheet(),row.excelRow(),values));
        var sheets=new LinkedHashMap<>(dataset.sheets()); sheets.put("ServerSamples",rows);
        var c=new DatasetReplayController(()->DatasetReplaySession.prepare(configuration,new CustomerDataset(dataset.metadata(),sheets),
                "synthetic-dataset-pilot","1",new DatasetReplayPlan(DatacenterDataInputMode.POWER_DRIVEN,START,3,STEP,STEP)));
        assertEquals(READY,c.state()); c.play(0); c.pulse(1_000_000_000L);
        var result=c.selectedResult().orElseThrow();
        assertNull(result.original().number("utilizationFraction"));
        assertEquals(.25,result.resultUtilizationFraction(),1e-9);
    }
    @Test void readFailureIncludesCategoryAndCauseAndCannotStart() {
        var c=new DatasetReplayController(()->DatasetReplaySession.load(temporary.resolve("absent.json"),workbook,
                "synthetic-dataset-pilot","1",new DatasetReplayPlan(DatacenterDataInputMode.POWER_DRIVEN,START,3,STEP,STEP)));
        assertEquals(INVALID,c.state());
        assertTrue(c.errorLines().stream().anyMatch(line->line.contains("CONFIGURATION_READ")));
        assertTrue(c.errorLines().stream().anyMatch(line->line.contains("absent.json")));
        c.play(0); c.restart(); c.pulse(1_000_000_000L);
        assertEquals(INVALID,c.state()); assertTrue(c.lastFrame().isEmpty());
    }
    @Test void executionFailureKeepsPreviousFrameClocksAndBlocksPlayUntilRestart() throws Exception {
        var session=session(DatacenterDataInputMode.POWER_DRIVEN); var c=new DatasetReplayController(()->session);
        c.play(0); c.pulse(1_000_000_000L); var first=c.lastFrame();
        var field=DatasetReplaySession.class.getDeclaredField("engine"); field.setAccessible(true);
        ((SimulationEngine)field.get(session)).register(tick->{throw new IllegalStateException("Injected UI failure");});
        c.pulse(2_000_000_000L);
        assertEquals(ERROR,c.state()); assertTrue(c.previousFrame()); assertEquals(first,c.lastFrame());
        assertEquals(STEP, c.lastFrame().orElseThrow().engineElapsed());
        assertEquals(STEP.multipliedBy(2),session.engineElapsed());
        assertTrue(c.errorLines().stream().anyMatch(s->s.contains("EXECUTION")));
        assertTrue(c.errorLines().stream().anyMatch(s->s.contains("Injected UI failure")));
        c.play(2_000_000_000L); c.pulse(3_000_000_000L); assertEquals(1,c.history().size());
        c.restart(); assertEquals(READY,c.state()); assertFalse(c.previousFrame()); assertTrue(c.failure().isEmpty());
    }
}
