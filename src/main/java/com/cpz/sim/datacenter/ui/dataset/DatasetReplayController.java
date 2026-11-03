package com.cpz.sim.datacenter.ui.dataset;

import com.cpz.sim.datacenter.dataset.*;
import com.cpz.sim.datacenter.model.*;
import java.util.*;
import java.util.function.Supplier;

/** Single owner-thread presentation controller; no timers, threads or electrical calculations.
 * History contains at most the backend plan's 1000 frames. All displayed clocks come from frames.
 */
public final class DatasetReplayController {
    public enum State { LOADING, INVALID, READY, RUNNING, PAUSED, COMPLETED, ERROR }
    private final Thread owner = Thread.currentThread();
    private State state = State.LOADING;
    private DatasetReplaySession session;
    private RuntimeException failure;
    private final List<DatasetReplayFrame> history = new ArrayList<>();
    private RackLocation rack;
    private ServerLocation server;
    private double stepsPerSecond = 1;
    private long nextPulse;

    public DatasetReplayController(Supplier<DatasetReplaySession> loader) {
        try {
            session = loader.get();
            if (session.state() != DatasetReplaySession.State.PREPARED)
                throw new IllegalArgumentException("A prepared session without frames is required");
            if (!racks().isEmpty()) selectRack(racks().getFirst().location());
            state = State.READY;
        } catch (RuntimeException exception) { failure = exception; state = State.INVALID; }
    }

    public static DatasetReplayController load(Properties properties) {
        return new DatasetReplayController(() -> {
            var options = DatasetReplayOptions.parse(properties);
            return DatasetReplaySession.load(options.configuration(), options.workbook(), options.configurationId(),
                    options.configurationVersion(), options.plan());
        });
    }

    private void checkOwner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Replay must remain on its owner thread");
    }
    private long interval() { return (long) (1_000_000_000d / stepsPerSecond); }
    public void play(long now) {
        checkOwner();
        if (state == State.READY || state == State.PAUSED) { state = State.RUNNING; nextPulse = now + interval(); }
    }
    public void pause() { checkOwner(); if (state == State.RUNNING) state = State.PAUSED; }
    public void speed(double value, long now) {
        checkOwner();
        if (!Double.isFinite(value) || value < .25 || value > 8) throw new IllegalArgumentException("Speed must be 0.25..8 steps/s");
        stepsPerSecond = value;
        nextPulse = now + interval();
    }
    /** A late wall pulse never catches up by executing several steps. */
    public void pulse(long now) {
        checkOwner();
        if (state != State.RUNNING || now - nextPulse < 0) return;
        nextPulse = now + interval();
        try {
            session.advance().ifPresent(history::add);
            if (session.state() == DatasetReplaySession.State.COMPLETED) state = State.COMPLETED;
        } catch (RuntimeException exception) { failure = exception; state = State.ERROR; }
    }
    public void restart() {
        checkOwner();
        if (session == null || state == State.INVALID) return;
        state = State.PAUSED;
        nextPulse = 0;
        history.clear();
        failure = null;
        try { session.restart(); state = State.READY; }
        catch (RuntimeException exception) { failure = exception; state = State.ERROR; }
    }
    public List<DatasetReplaySession.RackInventory> racks() {
        if (session == null) return List.of();
        return session.inventory().racks().stream().sorted(Comparator
                .comparing((DatasetReplaySession.RackInventory r) -> r.location().column())
                .thenComparing(DatasetReplaySession.RackInventory::row, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(r -> r.location().rackCode().value())).toList();
    }
    public void selectRack(RackLocation location) {
        checkOwner();
        if (racks().stream().noneMatch(r -> r.location().equals(location))) throw new IllegalArgumentException("Unknown rack identity");
        rack = location;
        server = servers().isEmpty() ? null : servers().getFirst().location();
    }
    public List<DatasetReplaySession.ServerInventory> servers() {
        if (session == null || rack == null) return List.of();
        return session.inventory().servers().stream().filter(s -> s.location().rackLocation().equals(rack))
                .sorted(Comparator.comparing(s -> s.location().slot())).toList();
    }
    public void selectServer(ServerLocation location) {
        checkOwner();
        if (servers().stream().noneMatch(s -> s.location().equals(location))) throw new IllegalArgumentException("Unknown server identity in selected rack");
        server = location;
    }
    public Optional<DatasetReplayFrame.ServerResult> selectedResult() {
        return lastFrame().flatMap(f -> f.servers().stream().filter(s -> s.roomCode().equals(session.inventory().roomCode())
                && s.location().equals(server)).findFirst());
    }
    public Optional<DatasetReplayFrame> lastFrame() { return history.isEmpty() ? Optional.empty() : Optional.of(history.getLast()); }
    public List<DatasetReplayFrame> history() { return List.copyOf(history); }
    public State state() { return state; }
    public Optional<RuntimeException> failure() { return Optional.ofNullable(failure); }
    public Optional<DatasetReplaySession> session() { return Optional.ofNullable(session); }
    public RackLocation selectedRack() { return rack; }
    public ServerLocation selectedServer() { return server; }
    public double stepsPerSecond() { return stepsPerSecond; }
    public boolean previousFrame() { return state == State.ERROR && lastFrame().isPresent(); }

    public List<String> errorLines() {
        if (failure == null) return List.of();
        var lines = new ArrayList<String>();
        DatasetReplayException structured = null;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof DatasetReplayException replay && structured == null) structured = replay;
            String message = cause instanceof DatasetReplayException replay && !replay.diagnostics().isEmpty()
                    ? "Validation returned " + replay.diagnostics().size() + " diagnostics" : cause.getMessage();
            lines.add(cause.getClass().getSimpleName() + ": " + message);
        }
        if (structured != null) {
            lines.addFirst("Category: " + structured.kind());
            structured.serverLocation().ifPresent(s -> lines.add("Failure server: " + s));
            structured.replayUtc().ifPresent(t -> lines.add("Failure query UTC: " + t));
            for (var issue : structured.diagnostics()) lines.add(issue.severity() + " | " + issue.sheet()
                    + " | row " + issue.excelRow() + " | " + issue.field() + " | " + issue.message());
        }
        return List.copyOf(lines);
    }
}
