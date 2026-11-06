package com.cpz.sim.datacenter.ui.runtime;

import com.cpz.sim.datacenter.config.definition.DatacenterDefinition;
import com.cpz.sim.datacenter.history.DatacenterSimulationStepSnapshot;
import com.cpz.sim.datacenter.model.*;
import com.cpz.sim.datacenter.snapshot.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Joins one tick by full server location, preserving the backend slot order. */
final class RackDetail {
    record Row(ServerLocation location, ServerRole role, ServerTemperatureSnapshot temperature,
               ServerEnergySnapshot energy, ServerHealthSnapshot health) {
        boolean installed() { return role != null; }
        boolean incomplete() { return installed() && (temperature == null || energy == null || health == null); }
    }
    record Data(RackLocation location, RackOperationalSnapshot summary, List<Row> rows) { }
    static Data project(RoomMap.Rack rack, Map<ServerLocation, ServerRole> roles, DatacenterSimulationStepSnapshot snapshot) {
        var energy = index(snapshot.energySnapshot().servers(), ServerEnergySnapshot::location);
        var temperatures = index(snapshot.temperatureSnapshot().servers(), ServerTemperatureSnapshot::location);
        var health = index(snapshot.healthSnapshot().servers(), ServerHealthSnapshot::location);
        var rows = new ArrayList<Row>();
        for (var slot : rack.slots()) {
            var location = new ServerLocation(rack.location().column(), rack.location().rackCode(), slot);
            var role = roles.get(location);
            if (role == null && (energy.containsKey(location) || temperatures.containsKey(location) || health.containsKey(location)))
                throw new IllegalStateException("Snapshot for uninstalled server: " + location);
            rows.add(new Row(location, role, temperatures.get(location), energy.get(location), health.get(location)));
        }
        return new Data(rack.location(), snapshot.operationalSnapshot().findRack(rack.location()).orElse(null), List.copyOf(rows));
    }
    private static <T> Map<ServerLocation, T> index(List<T> items, Function<T, ServerLocation> location) {
        return items.stream().collect(Collectors.toUnmodifiableMap(location, Function.identity()));
    }
    static String provenance(DatacenterDefinition definition) {
        return switch (definition.dataInputMode()) {
            case UTILIZATION_DRIVEN -> "Entrada simulada: carga\nTemp: modelo; potencia: inferida";
            case POWER_DRIVEN -> "Entrada simulada: potencia " + definition.powerInputGranularity() + "\nCarga inferida; temp modelo"
                    + (definition.powerInputGranularity() == com.cpz.sim.datacenter.input.PowerInputGranularity.RACK ? "; W/servidor inferida" : "");
            case TEMPERATURE_DRIVEN -> "Entrada simulada: temp " + definition.temperatureInputGranularity() + "\nTemp asignada; carga y W inferidas";
        };
    }
}
