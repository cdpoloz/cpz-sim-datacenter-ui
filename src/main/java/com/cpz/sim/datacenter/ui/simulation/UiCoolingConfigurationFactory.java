package com.cpz.sim.datacenter.ui.simulation;

import com.cpz.sim.datacenter.cooling.CoolingConfiguration;
import com.cpz.sim.datacenter.cooling.CoolingSystemOptions;
import com.cpz.sim.datacenter.cooling.CoolingUnitDefinition;
import com.cpz.sim.datacenter.cooling.CoolingZoneDefinition;
import com.cpz.sim.datacenter.cooling.CoolingZoneInfluence;
import com.cpz.sim.datacenter.cooling.ExhaustCoolingUnitDefinition;
import com.cpz.sim.datacenter.cooling.SupplyCoolingUnitDefinition;
import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.datacenter.model.ServerLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Creates the temporary programmatic cooling configuration used by the UI.
 *
 * <p>This factory represents the cooling layout currently shown by the UI:
 * four logical supply units and three logical exhaust units. The missing
 * exhaust unit in the C03-C04 group is intentional and allows the UI to
 * demonstrate asymmetric cooling.</p>
 *
 * <p>The configuration is transitional and should be removed once the
 * datacenter backend supports loading cooling definitions from JSON.</p>
 *
 * @author CPZ
 */
public final class UiCoolingConfigurationFactory {

    private static final double SUPPLY_AIRFLOW_CUBIC_METERS_PER_SECOND = 8.0;
    private static final double SUPPLY_COOLING_CAPACITY_WATTS = 100_000.0;
    private static final double SUPPLY_AIR_TEMPERATURE_CELSIUS = 18.0;
    private static final double EXHAUST_AIRFLOW_CUBIC_METERS_PER_SECOND = 8.0;

    private static final boolean INITIALLY_ENABLED = false;

    private static final double NEAR_INFLUENCE_WEIGHT = 0.50;
    private static final double MIDDLE_INFLUENCE_WEIGHT = 0.30;
    private static final double FAR_INFLUENCE_WEIGHT = 0.20;

    private static final Set<String> NEAR_RACKS = Set.of("R06", "R07");
    private static final Set<String> MIDDLE_RACKS = Set.of("R04", "R05", "R08", "R09");

    private static final Set<String> FAR_RACKS = Set.of("R01", "R02", "R03", "R10", "R11", "R12");

    private static final List<CoolingGroup> COOLING_GROUPS = List.of(
            new CoolingGroup("C01-C02", Set.of("C01", "C02"), true),
            new CoolingGroup("C03-C04", Set.of("C03", "C04"), false),
            new CoolingGroup("C05-C06", Set.of("C05", "C06"), true),
            new CoolingGroup("C07-C08", Set.of("C07", "C08"), true)
    );

    /**
     * Creates the temporary cooling configuration for the supplied datacenter.
     *
     * @param datacenter datacenter represented by the UI
     * @return validated backend cooling configuration
     */
    public CoolingConfiguration create(Datacenter datacenter) {
        Objects.requireNonNull(datacenter, "datacenter must not be null");
        List<CoolingZoneDefinition> zones = new ArrayList<>();
        List<CoolingUnitDefinition> units = new ArrayList<>();
        for (CoolingGroup group : COOLING_GROUPS) {
            addZones(datacenter, group, zones);
            addSupplyUnit(group, units);
            if (group.hasExhaust()) addExhaustUnit(group, units);
        }
        return new CoolingConfiguration(zones, units, CoolingSystemOptions.defaults());
    }

    private void addZones(Datacenter datacenter, CoolingGroup group, List<CoolingZoneDefinition> zones) {
        zones.add(createZone(datacenter, group, "NEAR", NEAR_RACKS));
        zones.add(createZone(datacenter, group, "MIDDLE", MIDDLE_RACKS));
        zones.add(createZone(datacenter, group, "FAR", FAR_RACKS));
    }

    private CoolingZoneDefinition createZone(Datacenter datacenter, CoolingGroup group, String distanceCode, Set<String> rackCodes) {
        Set<ServerLocation> serverLocations = datacenter
                .getServers()
                .stream()
                .filter(server -> belongsToZone(server, group.columns(), rackCodes))
                .map(Server::getLocation)
                .collect(Collectors.toUnmodifiableSet());
        if (serverLocations.isEmpty())
            throw new IllegalArgumentException("Cooling zone '%s' does not contain installed servers".formatted(zoneCode(group, distanceCode)));
        return new CoolingZoneDefinition(zoneCode(group, distanceCode), serverLocations);
    }

    private boolean belongsToZone(Server server, Set<String> columns, Set<String> rackCodes) {
        ServerLocation location = server.getLocation();
        return columns.contains(location.column()) && rackCodes.contains(location.rackCode().value());
    }

    private void addSupplyUnit(CoolingGroup group, List<CoolingUnitDefinition> units) {
        units.add(
                new SupplyCoolingUnitDefinition(
                        "SUPPLY-" + group.code(),
                        SUPPLY_AIRFLOW_CUBIC_METERS_PER_SECOND,
                        SUPPLY_COOLING_CAPACITY_WATTS,
                        SUPPLY_AIR_TEMPERATURE_CELSIUS,
                        createInfluences(group),
                        INITIALLY_ENABLED
                )
        );
    }

    private void addExhaustUnit(CoolingGroup group, List<CoolingUnitDefinition> units) {
        units.add(
                new ExhaustCoolingUnitDefinition(
                        "EXHAUST-" + group.code(),
                        EXHAUST_AIRFLOW_CUBIC_METERS_PER_SECOND,
                        createInfluences(group),
                        INITIALLY_ENABLED
                )
        );
    }

    private List<CoolingZoneInfluence> createInfluences(CoolingGroup group) {
        return List.of(
                new CoolingZoneInfluence(zoneCode(group, "NEAR"), NEAR_INFLUENCE_WEIGHT),
                new CoolingZoneInfluence(zoneCode(group, "MIDDLE"), MIDDLE_INFLUENCE_WEIGHT),
                new CoolingZoneInfluence(zoneCode(group, "FAR"), FAR_INFLUENCE_WEIGHT)
        );
    }

    private String zoneCode(CoolingGroup group, String distanceCode) {
        return "ZONE-" + group.code() + "-" + distanceCode;
    }

    private record CoolingGroup(String code, Set<String> columns, boolean hasExhaust) {
        private CoolingGroup {
            if (code == null || code.isBlank()) throw new IllegalArgumentException("Cooling group code must not be null or blank");
            Objects.requireNonNull(columns, "Cooling group columns must not be null");
            if (columns.size() != 2) throw new IllegalArgumentException("Cooling group must contain exactly two columns");
            columns = Set.copyOf(columns);
        }
    }
}