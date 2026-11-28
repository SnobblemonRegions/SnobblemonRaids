package com.cobbleraids.veilclient;
import java.util.List;
/** Minecraft-free strip geometry shared by the renderer and folding regression. */
public final class RibbonStrip {
 public record V(double x,double y,double z) {
  public V add(V b){return new V(x+b.x,y+b.y,z+b.z);} public V sub(V b){return new V(x-b.x,y-b.y,z-b.z);}
  public V mul(double s){return new V(x*s,y*s,z*s);} public double dot(V b){return x*b.x+y*b.y+z*b.z;}
  public V cross(V b){return new V(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x);}
  public double length(){return Math.sqrt(dot(this));} public V unit(){double n=length();return n<1e-10?new V(0,0,0):mul(1/n);}
 }
 public record Section(V center,V side,V normal,double width,double u) {}
 public static Section[] build(List<V> points,double[] widths,V preferredSide) {
  int n=points.size();
  if(n<2||n>257||widths.length!=n)throw new IllegalArgumentException("Expected 2..257 ribbon samples");
  V[] tangents=new V[n]; double[] actual=widths.clone();
  for(int i=0;i<n;i++) {
   V p=points.get(i);
   if(!Double.isFinite(p.x)||!Double.isFinite(p.y)||!Double.isFinite(p.z)||!Double.isFinite(actual[i])||actual[i]<0)
    throw new IllegalArgumentException("Non-finite ribbon or negative width");
   V t=points.get(Math.min(n-1,i+1)).sub(points.get(Math.max(0,i-1))).unit();
   tangents[i]=t.length()<1e-8?(i==0?new V(0,0,1):tangents[i-1]):t;
   if(i>0&&i<n-1) {
    V before=p.sub(points.get(i-1)),after=points.get(i+1).sub(p);
    double length=Math.min(before.length(),after.length());
    double sine=Math.sqrt(Math.max(0,(1-Math.clamp(before.unit().dot(after.unit()),-1,1))/2));
    if(length<1e-8)actual[i]=0;
    else if(sine>1e-8)actual[i]=Math.min(actual[i],.3*length/(2*sine));
   }
  }
  // A bend's narrowing propagates smoothly into its neighbours, avoiding a pinched crease.
  for(int i=1;i<n;i++)actual[i]=Math.min(actual[i],actual[i-1]+points.get(i).sub(points.get(i-1)).length()*.35);
  for(int i=n-2;i>=0;i--)actual[i]=Math.min(actual[i],actual[i+1]+points.get(i).sub(points.get(i+1)).length()*.35);
  Section[] result=new Section[n]; V side=perpendicular(preferredSide,tangents[0]);
  for(int i=0;i<n;i++) {
   if(i>0) {
    V axis=tangents[i-1].cross(tangents[i]); double sine=axis.length(),cosine=Math.clamp(tangents[i-1].dot(tangents[i]),-1,1);
    if(sine>1e-8) {
     axis=axis.mul(1/sine);
     side=side.mul(cosine).add(axis.cross(side).mul(sine)).add(axis.mul(axis.dot(side)*(1-cosine)));
    }
    side=perpendicular(side,tangents[i]);
   }
   result[i]=new Section(points.get(i),side,tangents[i].cross(side).unit(),actual[i],i/(double)(n-1));
  }
  return result;
 }
 private static V perpendicular(V preferred,V tangent) {
  V result=preferred.sub(tangent.mul(preferred.dot(tangent)));
  if(result.length()<1e-8) {
   V fallback=Math.abs(tangent.y)<.8?new V(0,1,0):new V(1,0,0);
   result=fallback.sub(tangent.mul(fallback.dot(tangent)));
  }
  return result.unit();
 }
 /** C1 Hermite interpolation using chord-length parameterization and bounded tangents. */
 public static List<V> resample(List<V> anchors,int segments) {
  if(anchors.size()<2||segments<1||segments>256)throw new IllegalArgumentException("Invalid wing curve");
  int n=anchors.size();double[] distances=new double[n];
  for(int i=1;i<n;i++)distances[i]=distances[i-1]+anchors.get(i).sub(anchors.get(i-1)).length();
  double total=distances[n-1];var result=new java.util.ArrayList<V>(segments+1);
  for(int k=0;k<=segments;k++) {
   double distance=total*k/segments;int index=0;
   while(index<n-2&&distances[index+1]<distance)index++;
   double length=distances[index+1]-distances[index];double u=length<1e-8?0:(distance-distances[index])/length;
   V p=anchors.get(index),q=anchors.get(index+1);
   int left=Math.max(0,index-1),right=Math.min(n-1,index+2);
   V m0=q.sub(anchors.get(left)).mul(length/Math.max(1e-8,distances[index+1]-distances[left]));
   V m1=anchors.get(right).sub(p).mul(length/Math.max(1e-8,distances[right]-distances[index]));
   result.add(p.mul(2*u*u*u-3*u*u+1).add(m0.mul(u*u*u-2*u*u+u))
     .add(q.mul(-2*u*u*u+3*u*u)).add(m1.mul(u*u*u-u*u)));
  }
  return List.copyOf(result);
 }
}
