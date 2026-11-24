package com.cpz.sim.datacenter.ui.runtime;

import com.cpz.processing.controls.controls.geometry.ControlBounds;
import com.cpz.processing.controls.controls.geometry.ControlMeasure;
import com.cpz.processing.controls.controls.label.Label;
import com.cpz.processing.controls.controls.label.config.LabelStyleConfig;
import com.cpz.processing.controls.controls.label.style.DefaultLabelStyle;
import com.cpz.processing.controls.controls.label.style.VerticalAlign;
import processing.core.PApplet;
import processing.core.PFont;
import static com.cpz.sim.datacenter.ui.util.Constants.COLOR_WHITE_LABEL;

final class SummaryPanel {
    private final Label left, right;
    SummaryPanel(PApplet sketch, PFont font, RuntimeLayout layout) {
        left = label(sketch,font,"runtimeSummaryLeft",layout); right = label(sketch,font,"runtimeSummaryRight",layout);
        resize(layout, sketch.width, sketch.height);
    }
    private static Label label(PApplet sketch,PFont font,String code,RuntimeLayout layout) {
        var label = new Label(sketch,code,"",layout.contentBounds(RuntimeLayout.Area.SUMMARY));
        var style = new LabelStyleConfig(); style.font=font; style.textColor=COLOR_WHITE_LABEL; style.alignY=VerticalAlign.TOP; style.lineSpacingMultiplier=1.3f;
        label.setStyle(new DefaultLabelStyle(style)); label.setTextSize(ControlMeasure.relative(.013f)); return label;
    }
    static ControlBounds bounds(RuntimeLayout layout,int width,int height,int index) {
        var b=layout.contentBounds(RuntimeLayout.Area.SUMMARY).resolve(width,height);
        return ControlBounds.relative((b.x()+b.width()*.5f*index)/width,b.y()/height,b.width()*.48f/height,b.height()/height);
    }
    void resize(RuntimeLayout layout,int width,int height) { left.setBounds(bounds(layout,width,height,0)); right.setBounds(bounds(layout,width,height,1)); }
    void refresh(com.cpz.sim.datacenter.snapshot.DatacenterOperationalSnapshot snapshot) { var data=RuntimeMetrics.summary(snapshot); left.setText(data.left()); right.setText(data.right()); }
    void draw() { left.draw(); right.draw(); }
}
