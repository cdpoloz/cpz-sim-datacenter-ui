package com.cpz.sim.datacenter.ui.runtime;

import com.cpz.sim.datacenter.config.definition.HotAisleDefinition;
import com.cpz.sim.datacenter.config.json.JsonDatacenterConfigLoader;
import com.cpz.sim.datacenter.cooling.CoolingZoneDefinition;
import com.cpz.sim.datacenter.model.ServerLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeMetricsTest {
    @TempDir Path tmp;
    private RuntimeSession session(boolean coupled, boolean offline) throws Exception {
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        var root=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(Path.of("data/config/datacenter.json").toFile());
        if(!coupled) { root.remove("roomAirTemperature"); root.remove("rackCooling"); }
        if(offline) for(var server:root.withArray("servers")) ((com.fasterxml.jackson.databind.node.ObjectNode)server).put("status","OFFLINE");
        var path=tmp.resolve("fixture-"+coupled+"-"+offline+".json");mapper.writeValue(path.toFile(),root);
        var properties=new Properties();try(var in=Files.newInputStream(Path.of("data/config.properties"))){properties.load(in);}
        return new RuntimeSession(new JsonDatacenterConfigLoader().load(path),properties);
    }
    @Test void aisleUsesBackendAggregateForDistinctAndSharedZones() throws Exception {
        var session=session(true,false);var snapshot=session.snapshot().coolingSnapshot().orElseThrow();
        String code=snapshot.zones().getFirst().zoneCode();
        var cross=new CoolingZoneDefinition(code,Set.of(new ServerLocation("NORTH","R01","S01"),new ServerLocation("NORTH","R02","S01"),new ServerLocation("SOUTH","R01","S01")));
        var aisle=new HotAisleDefinition("CUSTOM",List.of("NORTH"));
        var projected=RuntimeMetrics.aisle(aisle,List.of(cross),Optional.of(snapshot));
        assertEquals(List.of(code),projected.zones());assertTrue(projected.shared());
        assertEquals(snapshot.aggregateZones("CUSTOM",List.of(code)),projected.group().orElseThrow());
        assertTrue(RuntimeMetrics.aisleText(projected).contains("zona compartida"));
        var group=projected.group().orElseThrow();
        assertEquals(snapshot.tickIndex(),group.tickIndex());
        assertTrue(RuntimeMetrics.aisleText(projected).contains(String.format(Locale.ROOT,"SUPPLY %.2f / EXHAUST %.2f m³/s",group.supplyAirflowCubicMetersPerSecond(),group.exhaustAirflowCubicMetersPerSecond())));
        assertTrue(RuntimeMetrics.aisle(null,List.of(cross),Optional.of(snapshot)).group().isEmpty());
        assertTrue(RuntimeMetrics.aisle(new HotAisleDefinition("none",List.of("WEST")),List.of(cross),Optional.of(snapshot)).group().isEmpty());
        assertTrue(RuntimeMetrics.aisle(aisle,List.of(cross),Optional.empty()).group().isEmpty());
        var actual=session.definition().layout().hotAisles();
        assertNotEquals(RuntimeMetrics.aisle(actual.getFirst(),session.coolingZones(),Optional.of(snapshot)).zones(),
                RuntimeMetrics.aisle(actual.getLast(),session.coolingZones(),Optional.of(snapshot)).zones());
    }
    @Test void summaryUsesSameSnapshotAndPausedChangesDoNotRewriteIt() throws Exception {
        for(boolean coupled:new boolean[]{true,false}) {
            var session=session(coupled,false);
            for (var unit:session.coolingUnits()) session.setCoolingEnabled(unit.code(),true);
            session.step();
            var before=session.snapshot();var s=before.operationalSnapshot();
            var text=RuntimeMetrics.summary(s);
            assertTrue(text.left().contains(String.format(Locale.ROOT,"%.1f°C",s.averageOnlineServerTemperatureCelsius())));
            assertTrue(text.right().contains(String.format(Locale.ROOT,"%.2f kW",s.currentItPowerWatts()/1000)));
            s.hottestRackLocation().ifPresent(r -> assertTrue(text.left().contains(r.column()+"/"+r.rackCode().value())));
            s.facilityEnergySnapshot().ifPresent(f -> { assertEquals(f.coolingPowerWatts(),s.coolingPowerWatts()); assertEquals(f.totalFacilityPowerWatts(),s.totalFacilityPowerWatts()); });
            var aisle=session.definition().layout().hotAisles().getFirst();
            var group=RuntimeMetrics.aisle(aisle,session.coolingZones(),before.coolingSnapshot());
            for (var unit:session.coolingUnits()) session.setCoolingEnabled(unit.code(),false);
            assertSame(before,session.snapshot());assertEquals(text,RuntimeMetrics.summary(session.snapshot().operationalSnapshot()));
            assertEquals(group,RuntimeMetrics.aisle(aisle,session.coolingZones(),session.snapshot().coolingSnapshot()));
            session.step();assertEquals(before.tickIndex()+1,session.snapshot().tickIndex());
            var next=RuntimeMetrics.aisle(aisle,session.coolingZones(),session.snapshot().coolingSnapshot()).group().orElseThrow();
            assertEquals(session.snapshot().coolingSnapshot().orElseThrow().tickIndex(),next.tickIndex());
            assertTrue(group.group().orElseThrow().supplyAirflowCubicMetersPerSecond()>0);
            assertEquals(0.0,next.supplyAirflowCubicMetersPerSecond());
            assertEquals(0.0,next.exhaustAirflowCubicMetersPerSecond());
        }
    }
    @Test void noOnlineNoRoomAirAndZeroItDoNotInventValues() throws Exception {
        var session=session(false,true);var snapshot=session.snapshot().operationalSnapshot();
        assertFalse(snapshot.hasOnlineServers()); assertFalse(snapshot.hasPue());
        var summary=RuntimeMetrics.summary(snapshot);
        assertTrue(summary.left().contains("Aire sala: --"));assertTrue(summary.left().contains("Servidores online: --"));
        assertTrue(summary.left().contains("Rack más caliente:\n--"));assertTrue(summary.right().contains("IT online: --"));
        assertTrue(summary.right().contains("PUE: --"));
        assertTrue(RuntimeMetrics.summary(null).left().contains("no disponibles"));
    }
    @Test void summaryColumnsStayInBoundsAfterResize() {
        for(var size:List.of(new int[]{1920,1011},new int[]{800,800})) {
            var layout=new RuntimeLayout(size[0],size[1]);var area=layout.contentBounds(RuntimeLayout.Area.SUMMARY).resolve(size[0],size[1]);
            var a=SummaryPanel.bounds(layout,size[0],size[1],0).resolve(size[0],size[1]);var b=SummaryPanel.bounds(layout,size[0],size[1],1).resolve(size[0],size[1]);
            assertTrue(a.x()>=area.x()-.01 && a.x()+a.width()<=b.x()+.01 && b.x()+b.width()<=area.x()+area.width()+.01);
            assertTrue(a.y()>=area.y()-.01 && a.y()+a.height()<=area.y()+area.height()+.01);
        }
    }
    @Test void switchingAisleMetricsKeepsSelectionAndSnapshot() throws Exception {
        var session=session(true,false);var room=new RoomMap(session.definition().layout());
        var sketch=new processing.core.PApplet();sketch.width=1920;sketch.height=1011;
        var layout=new RuntimeLayout(sketch.width,sketch.height);
        var props=new Properties();try(var in=Files.newInputStream(Path.of("data/config.properties"))){props.load(in);}
        var view=new RuntimeView(sketch,layout,null,room,session,new RuntimeClock(props,()->0));
        var target=new com.cpz.sim.datacenter.model.RackLocation("C02","R01");
        var point=room.geometry(layout,sketch.width,sketch.height).racks().stream().filter(p->p.rack().location().equals(target)).findFirst().orElseThrow().bounds().resolve(sketch.width,sketch.height);
        click(view,point.x(),point.y()); assertEquals(target,room.selected());
        var modeField=RuntimeView.class.getDeclaredField("aisleMode");modeField.setAccessible(true);
        var mode=(com.cpz.processing.controls.controls.button.Button)modeField.get(view);
        var metricField=RuntimeView.class.getDeclaredField("aisleMetrics");metricField.setAccessible(true);
        var label=(com.cpz.processing.controls.controls.label.Label)metricField.get(view);
        var before=session.snapshot();var b=mode.getTooltipBounds();click(view,b.centerX(),b.centerY());
        assertTrue(label.getText().contains("Cobertura"));assertTrue(label.getText().contains("SUPPLY "));assertTrue(label.getText().contains("EXHAUST "));
        assertEquals(target,room.selected());assertSame(before,session.snapshot());
        click(view,b.centerX(),b.centerY());assertTrue(label.getText().contains("Media online"));assertSame(before,session.snapshot());
    }
    private void click(RuntimeView view,float x,float y) {
        for(int action:new int[]{processing.event.MouseEvent.PRESS,processing.event.MouseEvent.RELEASE})
            view.mouseEvent(new processing.event.MouseEvent(this,0,action,0,(int)x,(int)y,1,1));
    }
}
