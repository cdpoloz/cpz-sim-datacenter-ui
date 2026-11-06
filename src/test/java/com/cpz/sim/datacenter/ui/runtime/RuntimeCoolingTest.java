package com.cpz.sim.datacenter.ui.runtime;

import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import com.cpz.sim.datacenter.cooling.CoolingUnitType;
import com.cpz.sim.datacenter.model.RackLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeCoolingTest {
    @TempDir Path tmp;
    private RuntimeSession fixture(boolean coupled, boolean noExhaust) throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var root = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(Path.of("data/config/datacenter.json").toFile());
        var cooling = (com.fasterxml.jackson.databind.node.ObjectNode) root.get("cooling");
        var supplies = cooling.withArray("supplyUnits");
        ((com.fasterxml.jackson.databind.node.ObjectNode)supplies.get(0)).put("code", "NORTH-FAN-A").put("initiallyEnabled", true);
        ((com.fasterxml.jackson.databind.node.ObjectNode)supplies.get(1)).put("code", "OTHER-FAN").put("initiallyEnabled", false);
        if (noExhaust) cooling.set("exhaustUnits", mapper.createArrayNode());
        if (!coupled) { root.remove("roomAirTemperature"); root.remove("rackCooling"); }
        var path = tmp.resolve("config-"+coupled+"-"+noExhaust+".json"); mapper.writeValue(path.toFile(), root);
        var props = new Properties(); try(var in=Files.newInputStream(Path.of("data/config.properties"))) { props.load(in); }
        return new RuntimeSession(new JsonDatacenterConfigLoader().load(path),props);
    }
    @Test void individualAndMasterOperateRealUnitsWithoutAdvancingPausedSnapshot() throws Exception {
        for (boolean coupled : new boolean[]{true,false}) {
            var session = fixture(coupled,false); var before = session.snapshot();
            assertTrue(session.coolingEnabled("NORTH-FAN-A")); assertFalse(session.coolingEnabled("OTHER-FAN"));
            assertEquals(1, session.coolingActive(CoolingUnitType.SUPPLY));
            session.setCoolingEnabled("OTHER-FAN",true);
            assertEquals(2, session.coolingActive(CoolingUnitType.SUPPLY));
            session.setCoolingGroup(CoolingUnitType.EXHAUST,true);
            assertEquals(2, session.coolingActive(CoolingUnitType.SUPPLY));
            session.setCoolingGroup(CoolingUnitType.SUPPLY,false);
            assertEquals(0, session.coolingActive(CoolingUnitType.SUPPLY));
            assertEquals(5, session.coolingActive(CoolingUnitType.EXHAUST));
            assertSame(before, session.snapshot());
            session.step(); assertEquals(before.tickIndex()+1,session.snapshot().tickIndex());
            assertFalse(session.snapshot().coolingSnapshot().orElseThrow().findUnit("NORTH-FAN-A").orElseThrow().enabled());
        }
    }
    @Test void rackCoolingTargetsFullLocationAndDoesNotRewriteLastTick() throws Exception {
        var session=fixture(true,false); var first = new RackLocation("C01","R01"); var other=new RackLocation("C02","R01");
        assertTrue(session.rackCoolingAvailable()); var unit=session.rackCooling(first).orElseThrow();
        assertTrue(session.rackCooling(other).isEmpty());
        var snapshot=session.snapshot(); var last=unit.lastTick();
        session.setRackCooling(first,!unit.enabled());
        assertNotEquals(unit.enabled(),session.rackCooling(first).orElseThrow().enabled());
        assertEquals(last,session.rackCooling(first).orElseThrow().lastTick()); assertSame(snapshot,session.snapshot());
        session.setRackCooling(other,true); session.setRackCooling(null,true);
        assertNotEquals(unit.enabled(),session.rackCooling(first).orElseThrow().enabled());
        session.step(); assertEquals(snapshot.tickIndex()+1,session.snapshot().tickIndex());
        assertEquals(!unit.enabled(),session.rackCooling(first).orElseThrow().lastTick().orElseThrow().equipment().enabled());
        var legacy=fixture(false,false); assertFalse(legacy.rackCoolingAvailable()); assertTrue(legacy.rackCooling(first).isEmpty());
    }
    @Test void noUnitsOfOneTypeDoesNotImplyActiveMaster() throws Exception {
        var session=fixture(true,true);
        assertEquals(0,session.coolingUnits().stream().filter(u -> u.type()==CoolingUnitType.EXHAUST).count());
        assertEquals(0,session.coolingActive(CoolingUnitType.EXHAUST));
        session.setCoolingGroup(CoolingUnitType.EXHAUST,true); assertEquals(0,session.coolingActive(CoolingUnitType.EXHAUST));
        var sketch=new processing.core.PApplet(); sketch.width=1920; sketch.height=1011;
        var panel=new CoolingRoomPanel(sketch,null,session,new RuntimeLayout(1920,1011));
        var masters=(List<?>)field(panel,"masters");
        assertFalse(((com.cpz.processing.controls.controls.button.Button)masters.getLast()).isEnabled());
    }
    @Test void controlsRespectVisibilityAndCurrentRackWithResize() throws Exception {
        var session=fixture(true,false); var room=RoomMap.load();
        var sketch=new processing.core.PApplet(); sketch.width=1920;sketch.height=1011;
        var layout=new RuntimeLayout(sketch.width,sketch.height);
        var panel=new CoolingRoomPanel(sketch,null,session,layout);
        var tab=(com.cpz.processing.controls.controls.button.Button) field(panel,"tab");
        var units=(List<?>)field(panel,"unitButtons");
        var first=(com.cpz.processing.controls.controls.button.Button)units.getFirst();
        var before=session.snapshot();
        click(panel, first.getTooltipBounds()); assertTrue(session.coolingEnabled("NORTH-FAN-A"));
        click(panel,tab.getTooltipBounds()); assertTrue(panel.showing());
        click(panel,first.getTooltipBounds()); assertFalse(session.coolingEnabled("NORTH-FAN-A"));
        var masters=(List<?>)field(panel,"masters");
        click(panel,((com.cpz.processing.controls.controls.button.Button)masters.getFirst()).getTooltipBounds());
        assertEquals(4,session.coolingActive(CoolingUnitType.SUPPLY));
        var navigation=(List<?>)field(panel,"navigation");
        click(panel,((com.cpz.processing.controls.controls.button.Button)navigation.getLast()).getTooltipBounds());
        assertEquals(1, units.stream().filter(item -> ((com.cpz.processing.controls.controls.button.Button)item).isVisible()).count());
        assertFalse(first.isVisible());
        click(panel,((com.cpz.processing.controls.controls.button.Button)navigation.getFirst()).getTooltipBounds());
        assertTrue(first.isVisible());
        sketch.width=800;sketch.height=800;panel.resize(new RuntimeLayout(800,800),800,800);
        click(panel,tab.getTooltipBounds()); assertFalse(panel.showing());
        click(panel,first.getTooltipBounds()); assertEquals(4,session.coolingActive(CoolingUnitType.SUPPLY));
        assertSame(before,session.snapshot());
        var rackPanel=new RackCoolingPanel(sketch,null,session,room,new RuntimeLayout(800,800));
        var rackTab=(com.cpz.processing.controls.controls.button.Button)field(rackPanel,"tab");
        var rackToggle=(com.cpz.processing.controls.controls.button.Button)field(rackPanel,"toggle");
        room.select(new RackLocation("C01","R01")); rackPanel.refresh(); click(rackPanel,rackTab.getTooltipBounds());
        assertTrue(rackToggle.isVisible()); boolean initial=session.rackCooling(room.selected()).orElseThrow().enabled();
        click(rackPanel,rackToggle.getTooltipBounds()); assertNotEquals(initial,session.rackCooling(room.selected()).orElseThrow().enabled());
        room.select(new RackLocation("C02","R01"));rackPanel.refresh();assertFalse(rackToggle.isVisible());
        click(rackPanel,rackToggle.getTooltipBounds()); assertNotEquals(initial,session.rackCooling(new RackLocation("C01","R01")).orElseThrow().enabled());
        room.clearSelection();rackPanel.refresh();assertFalse(rackToggle.isVisible());assertSame(before,session.snapshot());
    }
    @Test void coolingButtonsRemainInsideTheirAreasAtDifferentSizes() throws Exception {
        var session=fixture(true,false); var room=RoomMap.load();
        var sketch=new processing.core.PApplet(); sketch.width=1920;sketch.height=1011;
        var panel=new CoolingRoomPanel(sketch,null,session,new RuntimeLayout(1920,1011));
        var rack=new RackCoolingPanel(sketch,null,session,room,new RuntimeLayout(1920,1011));
        for (int[] size : new int[][]{{1920,1011},{800,800}}) {
            sketch.width=size[0]; sketch.height=size[1]; var layout=new RuntimeLayout(size[0],size[1]);
            panel.resize(layout,size[0],size[1]); rack.resize(layout,size[0],size[1]);
            var roomBounds=layout.contentBounds(RuntimeLayout.Area.ROOM).resolve(size[0],size[1]);
            for(String name:List.of("masters","unitButtons","navigation")) for(Object item:(List<?>)field(panel,name)) {
                var b=((com.cpz.processing.controls.controls.button.Button)item).getTooltipBounds();
                assertTrue(b.x() >= roomBounds.x()-.01 && b.y() >= roomBounds.y()-.01 && b.x()+b.width() <= roomBounds.x()+roomBounds.width()+.01 && b.y()+b.height() <= roomBounds.y()+roomBounds.height()+.01);
            }
            var rackBounds=layout.contentBounds(RuntimeLayout.Area.SELECTED_RACK).resolve(size[0],size[1]);
            for(String name:List.of("tab","toggle")) {
                var b=((com.cpz.processing.controls.controls.button.Button)field(rack,name)).getTooltipBounds();
                assertTrue(b.x() >= rackBounds.x()-.01 && b.y() >= rackBounds.y()-.01 && b.x()+b.width() <= rackBounds.x()+rackBounds.width()+.01 && b.y()+b.height() <= rackBounds.y()+rackBounds.height()+.01);
            }
        }
    }
    private Object field(Object object,String name) throws Exception { var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object); }
    private void click(Object panel,com.cpz.processing.controls.core.overlay.tooltip.TooltipBounds bounds) {
        for (var type : List.of(com.cpz.processing.controls.core.input.PointerEvent.Type.PRESS,com.cpz.processing.controls.core.input.PointerEvent.Type.RELEASE)) {
            var e=new com.cpz.processing.controls.core.input.PointerEvent(type,bounds.centerX(),bounds.centerY());
            if(panel instanceof CoolingRoomPanel room) room.pointer(e); else ((RackCoolingPanel)panel).pointer(e);
        }
    }
}
