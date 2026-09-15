package com.cpz.sim.datacenter.ui.ui.selection;

import com.cpz.processing.controls.controls.button.Button;
import com.cpz.processing.controls.controls.indicator.Indicator;
import com.cpz.sim.datacenter.ui.app.ApplicationComponent;
import com.cpz.sim.datacenter.ui.app.ApplicationContext;
import com.cpz.sim.datacenter.ui.config.HotAisleDefinition;
import com.cpz.sim.datacenter.ui.simulation.SimulationContainer;
import com.cpz.sim.datacenter.ui.ui.UiComponentContainer;

import static com.cpz.sim.datacenter.ui.main.Launcher.PROPS;

/**
 * Owns selected column/rack/hot-aisle state and keeps selection controls highlighted.
 *
 * @author CPZ
 */
public class SelectionManager extends ApplicationComponent {

    private final SimulationContainer simulationContainer;
    private final UiComponentContainer uiComponentContainer;
    private String selectedColumn;
    private String selectedRack;
    private HotAisleDefinition selectedHotAisle;

    /**
     * Creates the selection manager.
     *
     * @param context Processing context
     * @param simulationContainer column-to-hot-aisle mapping and datacenter model
     * @param uiComponentContainer selection Buttons and Indicators
     */
    public SelectionManager(ApplicationContext context, SimulationContainer simulationContainer, UiComponentContainer uiComponentContainer) {
        super(context);
        this.simulationContainer = simulationContainer;
        this.uiComponentContainer = uiComponentContainer;
    }

    /** Selects {@code C01-R01}, resolves its hot aisle, and applies initial highlights. */
    public void initialize() {
        selectedColumn = "C01";
        selectedRack = "R01";
        resolveSelectedHotAisle();
        showSelectedRackHighlight();
        showSelectedAisleHighlight();
    }

    /**
     * Selects the rack row and, for shared aisles, the column represented by its left/right side.
     *
     * @param clickedRack suffix extracted from a {@code btnSelectedRack...} control code
     */
    public void updateSelectedRack(String clickedRack) {
        selectedRack = "R"
                + clickedRack
                .toLowerCase()
                .replace("right", "")
                .replace("left", "");
        if (!selectedColumn.equals(PROPS.getProperty("datacenter.first.column")) && !selectedColumn.equals(PROPS.getProperty("datacenter.last.column"))) {
            // Shared aisles list the left-side column first and right-side column last.
            if (clickedRack.toLowerCase().contains("left"))
                selectedColumn = selectedHotAisle.columns().getFirst();
            else if (clickedRack.toLowerCase().contains("right"))
                selectedColumn = selectedHotAisle.columns().getLast();
        }
        showSelectedRackHighlight();
    }

    /**
     * Selects a column, resolves its configured hot aisle, and moves both highlights.
     *
     * @param selectedColumn column code such as {@code C04}
     */
    public void updateSelectedColumn(String selectedColumn) {
        this.selectedColumn = selectedColumn;
        resolveSelectedHotAisle();
        showSelectedAisleHighlight();
        showSelectedRackHighlight();
    }

    /**
     * Returns the column used to resolve the selected rack.
     *
     * @return selected column code
     */
    public String selectedColumn() {
        return selectedColumn;
    }

    /**
     * Returns the selected rack code within the selected column.
     *
     * @return selected rack code
     */
    public String selectedRack() {
        return selectedRack;
    }

    /**
     * Returns the hot aisle containing the selected column.
     *
     * @return selected hot-aisle definition
     */
    public HotAisleDefinition selectedHotAisle() {
        return selectedHotAisle;
    }

    private void showSelectedRackHighlight() {
        int columnIndex = Integer.parseInt(selectedColumn.replace("C", ""));
        // Even columns appear on the left of shared aisles; odd columns appear on the right.
        String side = columnIndex % 2 == 0 ? "Left" : "Right";
        String selectedRackIndicatorCode = "indSelectedRack" + side + selectedRack.replace("R", "");
        String selectedButtonCode = selectedRackIndicatorCode.replace("ind", "btn");
        for (Button button : uiComponentContainer.buttonsSelectedAisleRack().values()) {
            if (selectedColumn.equals(PROPS.getProperty("datacenter.first.column")) && button.getCode().toLowerCase().contains("left"))
                button.setVisible(false);
            else if (selectedColumn.equals(PROPS.getProperty("datacenter.last.column")) && button.getCode().toLowerCase().contains("right"))
                button.setVisible(false);
            else
                button.setVisible(!button.getCode().equals(selectedButtonCode));
        }
        for (Indicator indicator : uiComponentContainer.indicatorsSelectedRack().values())
            indicator.setOn(indicator.getCode().equals(selectedRackIndicatorCode));
    }

    private void showSelectedAisleHighlight() {
        uiComponentContainer
                .indicatorsSelectedAisle()
                .values()
                .forEach(indicator -> indicator.setOn(false));
        String selectedAisleIndicatorCode = "indSelectedAisle";
        switch (selectedHotAisle.code()) {
            case "HA01" -> selectedAisleIndicatorCode += "C01";
            case "HA02" -> selectedAisleIndicatorCode += "C02-C03";
            case "HA03" -> selectedAisleIndicatorCode += "C04-C05";
            case "HA04" -> selectedAisleIndicatorCode += "C06-C07";
            case "HA05" -> selectedAisleIndicatorCode += "C08";
            default -> {
                return;
            }
        }
        uiComponentContainer.indicatorsSelectedAisle().get(selectedAisleIndicatorCode).setOn(true);
    }

    private void resolveSelectedHotAisle() {
        // Selection state is column-oriented; aisle metrics use the resolved group definition.
        selectedHotAisle = simulationContainer.hotAisleByColumn().get(selectedColumn);
        if (selectedHotAisle == null)
            throw new IllegalArgumentException("No hot aisle configured for column: " + selectedColumn);
    }
}
