package com.cobbleraids.veilclient;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.cobbleraids.veilclient.RibbonStrip.V;
class RibbonStripTest {
 @Test void duplicateJointsCollapseSafelyAndBadSamplesAreRejected() {
  var strip=RibbonStrip.build(List.of(new V(0,0,0),new V(0,0,0),new V(0,0,0)),new double[]{1,1,1},new V(0,1,0));
  for(var section:strip){assertEquals(0,section.width());assertEquals(1,section.side().length(),1e-9);}
  assertThrows(IllegalArgumentException.class,()->RibbonStrip.build(List.of(new V(0,0,0),new V(Double.NaN,0,1)),new double[]{1,1},new V(0,1,0)));
 }
 @Test void wingSplinePreservesEndpointsAndEvenSpacingOnStraightJoints() {
  var line=RibbonStrip.resample(List.of(new V(0,0,0),new V(0,0,1),new V(0,0,3)),6);
  assertEquals(new V(0,0,0),line.getFirst());assertEquals(new V(0,0,3),line.getLast());
  assertEquals(.5,line.get(1).z(),1e-9);assertEquals(1.5,line.get(3).z(),1e-9);
 }

 @Test void smallWingRotationsKeepStripEdgesContinuous() {
  List<V> path=new ArrayList<>(); double[] widths=new double[65]; Arrays.fill(widths,.4);
  for(int i=0;i<65;i++){double u=i/64.0;path.add(new V(u*8,0,0));}
  RibbonStrip.Section[] last=null;
  for(double cameraY:new double[]{-.001,0,.001}) {
   var strip=RibbonStrip.build(path,widths,new V(0,1,cameraY));
   for(int i=1;i<strip.length;i++) assertTrue(strip[i].side().dot(strip[i-1].side())>.95,
    "A strip edge flips near the camera-facing tangent at sample "+i);
   if(last!=null)assertTrue(last[32].side().dot(strip[32].side())>.99,"Small wing rotations must not flip the whole sheet");
   last=strip;
  }
 }
 @Test void ribbonTooWideForTightBendNarrowsBeforeItFolds() {
  List<V> path=new ArrayList<>();double[] widths=new double[65];Arrays.fill(widths,2.2);
  for(int i=0;i<65;i++){double a=i/64.0*Math.PI;path.add(new V(Math.cos(a),Math.sin(a),0));}
  var strip=RibbonStrip.build(path,widths,new V(0,0,1));
  assertTrue(strip[32].width()<=.31,"The inner edge of a one-block bend must not cross its center");
 }
}
