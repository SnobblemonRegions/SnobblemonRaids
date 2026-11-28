package com.cobbleraids.veilclient;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.cobbleraids.veilclient.RibbonStrip.V;
class WingMotionTest {
 @Test void sameMillisecondStillKeepsTheShoulderAttached() {
  var motion=new WingMotion(5);motion.update(BOSS,MODEL,points(0,0),100);
  var p=motion.update(BOSS,MODEL,points(1,10),100);
  assertEquals(1,p.getFirst().x());assertEquals(0,p.getLast().y());
 }
 @Test void tipDampingUsesElapsedTimeAcrossThirtyAndOneFortyFourFps() {
  double[] tips=new double[2];int index=0;
  for(int fps:new int[]{30,144}) {
   var motion=new WingMotion(5);motion.update(BOSS,MODEL,points(0,0),0);
   for(int i=1;i<=fps;i++)motion.update(BOSS,MODEL,points(0,10),Math.round(i*1000.0/fps));
   tips[index++]=motion.update(BOSS,MODEL,points(0,10),1000).getLast().y();
  }
  assertEquals(tips[0],tips[1],1e-9);assertEquals(9.999546,tips[0],1e-6);
 }

 private static final UUID BOSS=new UUID(0,1); private static final Object MODEL=new Object();
 private static List<V> points(double root,double tip){return List.of(new V(root,0,0),new V(root,tip/3,1),new V(root,tip*2/3,2),new V(root,tip,3),new V(root,tip,4));}
 @Test void shouldersStayAttachedWhileTipsTrailSmoothly() {
  var motion=new WingMotion(5); motion.update(BOSS,MODEL,points(0,0),0);
  var p=motion.update(BOSS,MODEL,points(1,10),16);
  assertEquals(1,p.getFirst().x());assertTrue(p.getLast().y()>0&&p.getLast().y()<2);
 }
 @Test void anotherBossReloadOrLongFrameGapCannotInheritOldWingPositions() {
  var motion=new WingMotion(5); motion.update(BOSS,MODEL,points(0,0),0);
  assertEquals(points(4,10),motion.update(new UUID(0,2),MODEL,points(4,10),16));
  assertEquals(points(8,20),motion.update(new UUID(0,2),new Object(),points(8,20),32));
  assertEquals(points(12,30),motion.update(new UUID(0,2),MODEL,points(12,30),1000));
 }
}
